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

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.pubsub.Node;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.HashSet;
import java.util.Set;

/**
 * Parses XEP-0462 PubSub Type Filtering from a disco#items query.
 */
public record PubsubTypeFilter(Set<String> includedTypes, boolean constrained) {

    public static final String NAMESPACE = "urn:xmpp:pubsub-filter:0";

    public static PubsubTypeFilter fromQuery(final Element query) {
        if (query == null) {
            return unconstrained();
        }
        final Element filterElement = query.element(QName.get("filter", NAMESPACE));
        if (filterElement == null) {
            return unconstrained();
        }
        final Element xElement = filterElement.element(QName.get("x", "jabber:x:data"));
        if (xElement == null) {
            return unconstrained();
        }
        final DataForm form = new DataForm(xElement);
        final FormField includedField = form.getField("included-types");
        if (includedField == null) {
            return unconstrained();
        }
        // The included-types field is present: honor it exactly. An empty value list means "include
        // nothing", NOT "include everything".
        final Set<String> types = new HashSet<>();
        for (final String value : includedField.getValues()) {
            if (value != null) {
                types.add(value);
            }
        }
        return new PubsubTypeFilter(Set.copyOf(types), true);
    }

    private static PubsubTypeFilter unconstrained() {
        return new PubsubTypeFilter(Set.of(), false);
    }

    public boolean matchesPayloadType(final String payloadType) {
        if (!constrained) {
            return true;
        }
        final String normalized = payloadType == null || payloadType.isBlank() ? "" : payloadType;
        return includedTypes.contains(normalized);
    }

    public boolean matchesNode(final Node node) {
        return node.isCollectionNode() || matchesPayloadType(node.getPayloadType());
    }
}
