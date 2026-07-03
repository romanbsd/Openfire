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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.PacketError;

import static org.junit.jupiter.api.Assertions.*;

class SpaceNodePolicyTest {

    @Test
    void acceptsValidSpaceConfiguration() {
        final DataForm form = validSpaceForm();
        final var result = SpaceNodePolicy.validateNodeConfiguration(form, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Valid.class, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"authorize", "whitelist", "open"})
    void acceptsAllowedAccessModels(final String accessModel) {
        final DataForm form = validSpaceForm();
        form.getField("pubsub#access_model").clearValues();
        form.getField("pubsub#access_model").addValue(accessModel);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Valid.class,
            SpaceNodePolicy.validateNodeConfiguration(form, true));
    }

    @Test
    void rejectsCollectionNodeType() {
        final DataForm form = validSpaceForm();
        form.addField("pubsub#node_type", null, FormField.Type.text_single).addValue("collection");
        final var result = SpaceNodePolicy.validateNodeConfiguration(form, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class, result);
    }

    @Test
    void rejectsWrongPayloadType() {
        final DataForm form = validSpaceForm();
        form.getField("pubsub#type").clearValues();
        form.getField("pubsub#type").addValue("urn:xmpp:example:0");
        final var result = SpaceNodePolicy.validateNodeConfiguration(form, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class, result);
    }

    @Test
    void rejectsOpenPublishModel() {
        final DataForm form = validSpaceForm();
        form.addField("pubsub#publish_model", null, FormField.Type.list_single).addValue("open");
        final var result = SpaceNodePolicy.validateNodeConfiguration(form, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class, result);
    }

    @Test
    void rejectsMissingTypeOnCreate() {
        final DataForm form = validSpaceForm();
        form.removeField("pubsub#type");
        final var result = SpaceNodePolicy.validateNodeConfiguration(form, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class, result);
        final var invalid = (SpaceNodePolicy.ValidationResult.Invalid) result;
        assertEquals(PacketError.Condition.not_acceptable, invalid.condition());
    }

    @Test
    void rejectsBlankTypeOnCreate() {
        // A present-but-blank pubsub#type must not pass as a space (previously slipped through).
        final DataForm form = validSpaceForm();
        form.getField("pubsub#type").clearValues();
        final var result = SpaceNodePolicy.validateNodeConfiguration(form, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class, result);
        assertEquals(PacketError.Condition.not_acceptable,
            ((SpaceNodePolicy.ValidationResult.Invalid) result).condition());
    }

    @Test
    void rejectsCreateWithNoConfigurationForm() {
        // A create carrying no data form (e.g. an empty <configure/>) must be rejected, not silently
        // allowed to create a node without the mandatory pubsub#type=urn:xmpp:spaces:0.
        final var result = SpaceNodePolicy.validateNodeConfiguration(null, true);
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class, result);
    }

    @Test
    void allowsReconfigurationWithNoForm() {
        // A reconfiguration (creating=false) that omits the form leaves the existing node untouched.
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Valid.class,
            SpaceNodePolicy.validateNodeConfiguration(null, false));
    }

    @Test
    void allowsReconfigurationOmittingType() {
        // Reconfiguring without re-sending pubsub#type must not be rejected.
        final DataForm form = validSpaceForm();
        form.removeField("pubsub#type");
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Valid.class,
            SpaceNodePolicy.validateNodeConfiguration(form, false));
    }

    @Test
    void rejectsPersistItemsFalse() {
        final DataForm form = validSpaceForm();
        form.getField("pubsub#persist_items").clearValues();
        form.getField("pubsub#persist_items").addValue("false");
        assertInstanceOf(SpaceNodePolicy.ValidationResult.Invalid.class,
            SpaceNodePolicy.validateNodeConfiguration(form, true));
    }

    private static DataForm validSpaceForm() {
        final DataForm form = new DataForm(DataForm.Type.submit);
        form.addField("pubsub#type", null, FormField.Type.text_single).addValue(SpaceNodePolicy.SPACE_TYPE);
        form.addField("pubsub#persist_items", null, FormField.Type.boolean_type).addValue("true");
        form.addField("pubsub#notify_retract", null, FormField.Type.boolean_type).addValue("true");
        form.addField("pubsub#access_model", null, FormField.Type.list_single).addValue("open");
        return form;
    }
}
