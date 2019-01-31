# Event-Gateway Client Example

To run the service:

First, modify [application.properties](src/main/resources/application.properties) file with the required information.
At least, you need to set the following properties:

```
alfresco.events.eventGateway.url=
alfresco.aws.lambda.functionName=
alfresco.aws.lambda.region=
alfresco.predicate.example.parentId=
```

Then run the following maven command:

    mvn spring-boot:run -Daws.accessKeyId=your_access_key_id -Daws.secretKey=your_secret_key

Access key and secret key are required in order to invoke the AWS Lambda.