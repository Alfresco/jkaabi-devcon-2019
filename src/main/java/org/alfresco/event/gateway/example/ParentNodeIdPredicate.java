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

import java.util.List;

import org.alfresco.event.model.EventV1;
import org.alfresco.event.model.HierarchyEntry;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

/**
 * @author Jamal Kaabi-Mofrad
 */
public class ParentNodeIdPredicate implements Predicate
{
    private final String parentNodeId;

    public ParentNodeIdPredicate(String parentNodeId)
    {
        this.parentNodeId = parentNodeId;
    }

    @Override
    public boolean matches(Exchange exchange)
    {
        // After event unmarshalling so casting is safe.
        EventV1<?> body = (EventV1<?>) exchange.getIn().getBody();
        List<HierarchyEntry> parents = body.getResource().getPrimaryHierarchy();
        for (HierarchyEntry parent : parents)
        {
            if (parentNodeId.equals(parent.getId()))
            {
                return true;
            }
        }
        return false;
    }
}
