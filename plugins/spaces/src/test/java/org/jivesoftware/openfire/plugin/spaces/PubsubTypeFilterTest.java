/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.plugin.spaces;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.pubsub.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PubsubTypeFilterTest {

    @Test
    void noFilterMatchesAllTypes() {
        final var filter = PubsubTypeFilter.fromQuery(null);
        assertTrue(filter.matchesPayloadType(SpaceNodePolicy.SPACE_TYPE));
        assertTrue(filter.matchesPayloadType(""));
        assertTrue(filter.matchesPayloadType("urn:other:0"));
    }

    @Test
    void includedTypesFilterMatchesOnlySpaceType() {
        final Element query = DocumentHelper.createElement("query");
        query.addElement("filter", PubsubTypeFilter.NAMESPACE)
            .addElement("x", "jabber:x:data")
            .addElement("field").addAttribute("var", "included-types").addElement("value")
            .setText(SpaceNodePolicy.SPACE_TYPE);

        final var filter = PubsubTypeFilter.fromQuery(query);
        assertTrue(filter.constrained());
        assertTrue(filter.matchesPayloadType(SpaceNodePolicy.SPACE_TYPE));
        assertFalse(filter.matchesPayloadType("urn:other:0"));
    }

    @Test
    void constrainedFilterRetainsCollectionNodes() {
        final Element query = DocumentHelper.createElement("query");
        query.addElement("filter", PubsubTypeFilter.NAMESPACE)
            .addElement("x", "jabber:x:data")
            .addElement("field").addAttribute("var", "included-types").addElement("value")
            .setText(SpaceNodePolicy.SPACE_TYPE);
        final Node collection = mock(Node.class);
        when(collection.isCollectionNode()).thenReturn(true);

        assertTrue(PubsubTypeFilter.fromQuery(query).matchesNode(collection));
    }

    @Test
    void emptyIncludedTypesMatchesNothing() {
        // An included-types field that is present but carries no values means "include nothing",
        // not "include everything".
        final Element query = DocumentHelper.createElement("query");
        query.addElement("filter", PubsubTypeFilter.NAMESPACE)
            .addElement("x", "jabber:x:data")
            .addElement("field").addAttribute("var", "included-types");

        final var filter = PubsubTypeFilter.fromQuery(query);
        assertTrue(filter.constrained());
        assertFalse(filter.matchesPayloadType(SpaceNodePolicy.SPACE_TYPE));
        assertFalse(filter.matchesPayloadType(""));
        assertFalse(filter.matchesPayloadType("urn:other:0"));
    }

    @Test
    void filterWithoutFormMatchesEverything() {
        // A <filter/> with no data form imposes no constraint.
        final Element query = DocumentHelper.createElement("query");
        query.addElement("filter", PubsubTypeFilter.NAMESPACE);

        final var filter = PubsubTypeFilter.fromQuery(query);
        assertFalse(filter.constrained());
        assertTrue(filter.matchesPayloadType(SpaceNodePolicy.SPACE_TYPE));
        assertTrue(filter.matchesPayloadType("urn:other:0"));
    }
}
