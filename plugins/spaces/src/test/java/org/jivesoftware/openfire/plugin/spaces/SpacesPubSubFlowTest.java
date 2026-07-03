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

import org.junit.jupiter.api.Test;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Space node configuration validation for create/configure flows.
 */
class SpacesPubSubFlowTest {

    @Test
    void leafNodesArePrivateByDefault() {
        assertEquals(AccessModel.authorize, SpacesPubSubService.createSpacesLeafDefaults().getAccessModel());
    }

    @Test
    void createRequiresSpaceTypeAndPersistItems() {
        final DataForm incomplete = new DataForm(DataForm.Type.submit);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class,
            SpaceNodePolicy.validateNodeConfiguration(incomplete, true));

        final DataForm valid = new DataForm(DataForm.Type.submit);
        valid.addField("pubsub#type", null, FormField.Type.text_single).addValue(SpaceNodePolicy.SPACE_TYPE);
        valid.addField("pubsub#persist_items", null, FormField.Type.boolean_type).addValue("true");
        valid.addField("pubsub#notify_retract", null, FormField.Type.boolean_type).addValue("true");
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Valid.class,
            SpaceNodePolicy.validateNodeConfiguration(valid, true));
    }
}
