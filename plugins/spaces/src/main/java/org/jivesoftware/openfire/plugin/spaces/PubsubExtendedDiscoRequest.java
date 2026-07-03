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
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

/**
 * Detects XEP-0499 extended discovery requests in disco#items queries.
 */
public record PubsubExtendedDiscoRequest(boolean fullMetadata) {

    private static final String NAMESPACE = "urn:xmpp:pubsub-ext-disco:0";

    public static PubsubExtendedDiscoRequest fromQuery(final Element query) {
        if (query == null) {
            return new PubsubExtendedDiscoRequest(false);
        }
        final Element xElement = query.element(QName.get("x", "jabber:x:data"));
        if (xElement == null) {
            return new PubsubExtendedDiscoRequest(false);
        }
        final DataForm form = new DataForm(xElement);
        final FormField formType = form.getField("FORM_TYPE");
        if (formType == null || !NAMESPACE.equals(formType.getFirstValue())) {
            return new PubsubExtendedDiscoRequest(false);
        }
        final FormField fullMetadataField = form.getField("full_metadata");
        final boolean fullMetadata = fullMetadataField != null && Boolean.parseBoolean(fullMetadataField.getFirstValue());
        return new PubsubExtendedDiscoRequest(fullMetadata);
    }
}
