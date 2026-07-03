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

import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.PacketError;

import java.util.List;
import java.util.Set;

/**
 * Validates pubsub node configuration for XEP-0503 spaces.
 */
public final class SpaceNodePolicy {

    public static final String SPACE_TYPE = "urn:xmpp:spaces:0";

    private static final Set<String> ALLOWED_ACCESS_MODELS = Set.of("open", "authorize", "whitelist");

    private SpaceNodePolicy() {
    }

    public sealed interface ValidationResult {
        record Valid() implements ValidationResult {}

        record Invalid(PacketError.Condition condition, String message) implements ValidationResult {}
    }

    public static ValidationResult validateNodeConfiguration(final DataForm form, final boolean creating) {
        if (form == null) {
            return creating ? new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "Space nodes require configuration") : new ValidationResult.Valid();
        }

        final FormField nodeTypeField = form.getField("pubsub#node_type");
        if (nodeTypeField != null) {
            final String nodeType = firstValue(nodeTypeField);
            if ("collection".equals(nodeType)) {
                return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "Collection nodes are not allowed on the spaces service");
            }
        }

        final FormField typeField = form.getField("pubsub#type");
        final String payloadType = typeField != null ? firstValue(typeField) : null;
        if (payloadType == null || payloadType.isBlank()) {
            // A missing OR blank pubsub#type cannot identify the node as a space. Required on creation;
            // a reconfiguration that simply omits the field leaves the existing type untouched.
            if (creating) {
                return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "pubsub#type is required for space nodes");
            }
        } else if (!SPACE_TYPE.equals(payloadType)) {
            return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "Invalid pubsub#type for a space node");
        }

        final FormField accessField = form.getField("pubsub#access_model");
        if (accessField != null) {
            final String accessModel = firstValue(accessField);
            if (accessModel != null && !ALLOWED_ACCESS_MODELS.contains(accessModel)) {
                return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "Unsupported access model for spaces");
            }
        }

        final FormField publishField = form.getField("pubsub#publish_model");
        if (publishField != null && PublisherModel.open.getName().equals(firstValue(publishField))) {
            return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "publish_model=open is not allowed for space nodes");
        }

        final ValidationResult persistResult = requireBooleanField(form, "pubsub#persist_items", true, creating);
        if (persistResult instanceof ValidationResult.Invalid invalid) {
            return invalid;
        }

        final ValidationResult retractResult = requireBooleanField(form, "pubsub#notify_retract", true, creating);
        if (retractResult instanceof ValidationResult.Invalid invalid) {
            return invalid;
        }

        final FormField purgeField = form.getField("pubsub#purge_offline");
        if (purgeField != null && Boolean.parseBoolean(firstValue(purgeField))) {
            return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, "purge_offline must be false for space nodes");
        }

        return new ValidationResult.Valid();
    }

    private static ValidationResult requireBooleanField(final DataForm form, final String variable, final boolean expected, final boolean creating) {
        final FormField field = form.getField(variable);
        if (field == null) {
            return creating
                ? new ValidationResult.Invalid(PacketError.Condition.not_acceptable, variable + " is required for space nodes")
                : new ValidationResult.Valid();
        }
        if (Boolean.parseBoolean(firstValue(field)) != expected) {
            return new ValidationResult.Invalid(PacketError.Condition.not_acceptable, variable + " must be " + expected + " for space nodes");
        }
        return new ValidationResult.Valid();
    }

    private static String firstValue(final FormField field) {
        final List<String> values = field.getValues();
        return values.isEmpty() ? null : values.get(0);
    }
}
