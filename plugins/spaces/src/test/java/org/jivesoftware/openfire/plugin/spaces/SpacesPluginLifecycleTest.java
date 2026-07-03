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

import org.jivesoftware.openfire.muc.MUCRoomConfigExtensionManager;
import org.jivesoftware.openfire.plugin.spaces.muc.MucSpacesExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xmpp.forms.DataForm;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SpacesPluginLifecycleTest {

    @AfterEach
    void cleanup() {
        MUCRoomConfigExtensionManager.getInstance().unregister(new MucSpacesExtension());
    }

    @Test
    void destroyPluginUnregistersMucExtension() throws Exception {
        final MucSpacesExtension extension = new MucSpacesExtension();
        MUCRoomConfigExtensionManager.getInstance().register(extension);

        final SpacesPlugin plugin = new SpacesPlugin();
        final Field mucField = SpacesPlugin.class.getDeclaredField("mucExtension");
        mucField.setAccessible(true);
        mucField.set(plugin, extension);

        plugin.destroyPlugin();

        final DataForm form = new DataForm(DataForm.Type.form);
        MUCRoomConfigExtensionManager.getInstance().contributeConfigForm(form, mock(org.jivesoftware.openfire.muc.MUCRoom.class));
        assertNull(form.getField(MucSpacesExtension.ROOM_CONFIG_PUBSUB));
    }
}
