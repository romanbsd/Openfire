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
package org.jivesoftware.openfire.plugin.spaces.muc;

import org.jivesoftware.openfire.muc.MUCRoom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.JID;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MucSpacesExtensionTest {

    @Mock
    private MUCRoom room;

    private final MucSpacesExtension extension = new MucSpacesExtension();

    @BeforeEach
    void useInMemoryStorage() {
        MucSpacesExtension.setStorageForTests(ParentIriStorage.inMemory());
    }

    @AfterEach
    void cleanup() {
        MucSpacesExtension.resetStorageAfterTests();
    }

    @Test
    void configFormIncludesPubsubField() {
        final DataForm form = new DataForm(DataForm.Type.form);
        extension.contributeConfigForm(form, room);
        assertNotNull(form.getField(MucSpacesExtension.ROOM_CONFIG_PUBSUB));
    }

    @Test
    void submitPersistsParentIri() {
        when(room.getJID()).thenReturn(new JID("test@conference.example.com"));
        final DataForm form = new DataForm(DataForm.Type.submit);
        form.addField(MucSpacesExtension.ROOM_CONFIG_PUBSUB, null, null)
            .addValue("xmpp:spaces.example.com?;node=dev");
        extension.processConfigSubmit(form, room);
        assertEquals("xmpp:spaces.example.com?;node=dev", MucSpacesExtension.getParentIri(room));
    }

    @Test
    void roomDestroyedRemovesParentIri() {
        final JID roomJID = new JID("test@conference.example.com");
        when(room.getJID()).thenReturn(roomJID);
        final DataForm form = new DataForm(DataForm.Type.submit);
        form.addField(MucSpacesExtension.ROOM_CONFIG_PUBSUB, null, null)
            .addValue("xmpp:spaces.example.com?;node=dev");
        extension.processConfigSubmit(form, room);
        assertNotNull(MucSpacesExtension.getParentIri(room));

        extension.roomDestroyed(1L, roomJID);
        assertNull(MucSpacesExtension.getParentIri(room));
    }

    @Test
    void discoIncludesParentWhenSet() {
        when(room.getJID()).thenReturn(new JID("test@conference.example.com"));
        final DataForm submit = new DataForm(DataForm.Type.submit);
        submit.addField(MucSpacesExtension.ROOM_CONFIG_PUBSUB, null, null)
            .addValue("xmpp:spaces.example.com?;node=dev");
        extension.processConfigSubmit(submit, room);

        final var features = new HashSet<String>();
        final var forms = new HashSet<DataForm>();
        extension.contributeRoomDiscoFeatures(features, room);
        extension.contributeRoomDiscoForms(forms, room);

        assertTrue(features.contains(MucSpacesExtension.SPACES_FORM_TYPE));
        assertEquals(1, forms.size());
        final DataForm spacesForm = forms.iterator().next();
        assertEquals("xmpp:spaces.example.com?;node=dev",
            spacesForm.getField(MucSpacesExtension.PARENT_FIELD).getFirstValue());
    }
}
