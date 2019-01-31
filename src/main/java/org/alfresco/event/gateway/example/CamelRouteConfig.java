/*
 * Copyright 2019 Alfresco Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.alfresco.event.gateway.example;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import org.alfresco.event.databind.EventObjectMapperFactory;
import org.alfresco.event.model.EventV1;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.amqp.AMQPComponent;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;

/**
 * @author Jamal Kaabi-Mofrad
 */
@Configuration
public class CamelRouteConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelRouteConfig.class);

    // Topic endpoint
    private static final String EVENT_TOPIC_URL = "/api/public/events/versions/1/events";
    private static final String CAMEL_BASE_TOPIC_URI = "amqpConnection:topic:";
    private static final String CLIENT_ID = "bae3-event-example";
    private static final String SUBSCRIPTION_NAME = "event-example";
    private static final String DURABLE_TOPIC_CONFIG = "?clientId=" + CLIENT_ID + "&durableSubscriptionName=" + SUBSCRIPTION_NAME;

    // Retrieving topic endpoint retry config
    private static final long RETRY_BACK_OFF_PERIOD = 2000L;
    private static final int RETRY_MAX_ATTEMPTS = 30;

    // AWS Lambda config
    private static final String APPLICATION_NAME = "event-gateway-example";
    private static final String VERSION = "0.1";
    private static final String AWS_LAMBDA_BASE_URI = "aws-lambda://";
    private static final String AWS_INVOKE_LAMBDA_CONFIG = "?operation=invokeFunction&awsLambdaClient=#awsLambdaClient";

    // Camel predicates
    private static final NodeTypePredicate IS_CONTENT = new NodeTypePredicate("cm:content");
    private static final NodeTypePredicate IS_FOLDER = new NodeTypePredicate("cm:folder");


    @Value("${alfresco.events.broker.activemq.url}")
    private String activemqUrl;

    @Value("${alfresco.events.eventGateway.url}")
    private String eventGatewayUrl;

    @Value("${alfresco.events.topic}")
    private String topicName;

    @Value("${alfresco.aws.lambda.functionName}")
    private String awsLambdaFunctionName;

    @Value("${alfresco.aws.lambda.region}")
    private String awsLambdaRegion;

    @Value("${alfresco.predicate.example.parentId}")
    private String parentNodeId;

    private String topicEndpoint;

    @PostConstruct
    public void init()
    {
        if (eventGatewayUrl.endsWith("/"))
        {
            eventGatewayUrl = eventGatewayUrl.substring(0, eventGatewayUrl.length() - 1);
        }
        topicEndpoint = eventGatewayUrl + EVENT_TOPIC_URL;
    }

    @Bean
    public RoutesBuilder simpleRoute()
    {
        return new RouteBuilder()
        {
            @Override
            public void configure()
            {
                // Construct Camel from uri
                final String topicUri = CAMEL_BASE_TOPIC_URI + topicEndpoint().eventTopic + DURABLE_TOPIC_CONFIG;
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Setting camel Route to consume event from: {}", topicUri);
                }

                // Construct Camel Lambda uri
                final String awsLambdaUri = AWS_LAMBDA_BASE_URI + awsLambdaFunctionName + AWS_INVOKE_LAMBDA_CONFIG;
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Setting aws lambda uri: {}", awsLambdaUri);
                }

                // Create a predicate for content based router
                final ParentNodeIdPredicate IS_FOLDER_A_CONTENT = new ParentNodeIdPredicate(parentNodeId);

                from(topicUri).id("ExampleRoute")
                            .unmarshal(publicDataFormat())
                            .filter(IS_CONTENT)
                                .choice()
                                    .when(IS_FOLDER_A_CONTENT).bean("folderAContentHandler")
                                    .otherwise().bean("allContentHandler")
                                .end()
                            .end()
                            .filter(IS_FOLDER)
                                .marshal(publicDataFormat())
                                .log("Sending the event to AWS Lambda ${body}")
                                .to(awsLambdaUri)
                            .end();
            }
        };
    }

    @Bean
    public DataFormat publicDataFormat()
    {
        return new JacksonDataFormat(EventObjectMapperFactory.createInstance(), EventV1.class);
    }

    @Bean
    public AMQPComponent amqpConnection()
    {
        JmsConnectionFactory jmsConnectionFactory = new JmsConnectionFactory();
        jmsConnectionFactory.setRemoteURI(topicEndpoint().brokerUri);
        return new AMQPComponent(jmsConnectionFactory);
    }

    @Bean
    public RetryTemplate retryTemplate()
    {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(RETRY_BACK_OFF_PERIOD);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(RETRY_MAX_ATTEMPTS);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

    @Bean
    public EventTopicEntity topicEndpoint()
    {
        return getTopicEndpointWithRetry();
    }

    @Bean
    public AWSLambda awsLambdaClient()
    {
        // Reads credentials from ENV-variables
        AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();

        ClientConfiguration clientConfig = getClientConfig();
        return AWSLambdaClientBuilder
                    .standard()
                    .withRegion(awsLambdaRegion)
                    .withClientConfiguration(clientConfig)
                    .withCredentials(credentialsProvider)
                    .build();
    }

    private EventTopicEntity getTopicEndpointWithRetry()
    {
        final AtomicInteger counter = new AtomicInteger(1);
        try
        {
            final RestTemplate restTemplate = new RestTemplate();
            final RetryTemplate retryTemplate = retryTemplate();
            return retryTemplate.execute(retryContext -> {
                try
                {
                    ResponseEntity<EntryEntity> restExchange = restTemplate.exchange(topicEndpoint,
                                HttpMethod.OPTIONS, null, EntryEntity.class);
                    return restExchange.getBody().entry;
                }
                catch (Exception ex)
                {
                    LOGGER.info("Couldn't get the topic info. Retrying using FixedBackOff [interval={}ms, maxAttempts={}, currentAttempts={}].",
                                RETRY_BACK_OFF_PERIOD, RETRY_MAX_ATTEMPTS, counter.getAndIncrement());
                    throw ex;
                }
            });
        }
        catch (Exception ex)
        {
            LOGGER.info("Couldn't get the topic info after {} tries. Falling back to default values.", RETRY_MAX_ATTEMPTS);
            return new EventTopicEntity(topicName, activemqUrl);
        }
    }

    private ClientConfiguration getClientConfig()
    {
        ClientConfiguration config = new ClientConfiguration();

        StringBuilder userAgent = new StringBuilder(ClientConfiguration.DEFAULT_USER_AGENT);
        userAgent.append(" ")
                    .append(APPLICATION_NAME)
                    .append("/")
                    .append(VERSION);
        config.setUserAgentPrefix(userAgent.toString());
        return config;
    }

    public static class EntryEntity
    {
        private EventTopicEntity entry;

        public EntryEntity()
        {
            //NOOP
        }

        public void setEntry(EventTopicEntity entry)
        {
            this.entry = entry;
        }

        public EventTopicEntity getEntry()
        {
            return entry;
        }
    }

    public static class EventTopicEntity
    {
        private String eventTopic;
        private String brokerUri;

        public EventTopicEntity()
        {
            //NOOP
        }

        public EventTopicEntity(String eventTopic, String brokerUri)
        {
            this.eventTopic = eventTopic;
            this.brokerUri = brokerUri;
        }

        public String getEventTopic()
        {
            return eventTopic;
        }

        public void setEventTopic(String eventTopic)
        {
            this.eventTopic = eventTopic;
        }

        public String getBrokerUri()
        {
            return brokerUri;
        }

        public void setBrokerUri(String brokerUri)
        {
            this.brokerUri = brokerUri;
        }
    }
}

