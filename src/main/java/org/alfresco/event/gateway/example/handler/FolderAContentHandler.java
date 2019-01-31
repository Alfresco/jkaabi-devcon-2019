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
package org.alfresco.event.gateway.example.handler;

import org.alfresco.event.model.EventV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Jamal Kaabi-Mofrad
 */
@Component
public class FolderAContentHandler implements EventHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FolderAContentHandler.class);

    @Override
    public void onReceive(EventV1 event)
    {
        LOGGER.info("Handling Folder A events. Event type: {}, nodeId: {}", event.getType(), event.getResource().getId());
    }
}
