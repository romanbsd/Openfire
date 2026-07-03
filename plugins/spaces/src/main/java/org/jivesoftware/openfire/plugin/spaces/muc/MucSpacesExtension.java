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

import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MUCRoomConfigExtension;
import org.jivesoftware.openfire.plugin.spaces.SpaceNodePolicy;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Collection;
import java.util.Set;

/**
 * MUC room configuration and disco extension for XEP-0503 space parent advertising. Also listens for room
 * destruction to clean up the persisted parent-space association.
 */
public class MucSpacesExtension implements MUCRoomConfigExtension, MUCEventListener {

    public static final String ROOM_CONFIG_PUBSUB = "muc#roomconfig_pubsub";
    public static final String SPACES_FORM_TYPE = SpaceNodePolicy.SPACE_TYPE;
    public static final String PARENT_FIELD = "parent";

    private static final String PROP_PREFIX = "plugin.spaces.room.parent.";

    private static volatile ParentIriStorage storage = ParentIriStorage.jiveGlobals(PROP_PREFIX);

    static void setStorageForTests(final ParentIriStorage testStorage) {
        storage = testStorage;
    }

    static void resetStorageAfterTests() {
        storage = ParentIriStorage.jiveGlobals(PROP_PREFIX);
    }

    @Override
    public void contributeConfigForm(final DataForm form, final MUCRoom room) {
        form.addField(ROOM_CONFIG_PUBSUB, "Associated pubsub node", FormField.Type.text_single);
    }

    @Override
    public void populateConfigForm(final DataForm form, final MUCRoom room) {
        final String parent = getParentIri(room);
        final FormField field = form.getField(ROOM_CONFIG_PUBSUB);
        if (field != null) {
            field.clearValues();
            if (parent != null && !parent.isBlank()) {
                field.addValue(parent);
            }
        }
    }

    @Override
    public void processConfigSubmit(final DataForm completedForm, final MUCRoom room) {
        final FormField field = completedForm.getField(ROOM_CONFIG_PUBSUB);
        if (field == null) {
            return;
        }
        final String value = field.getFirstValue();
        setParentIri(room, value == null || value.isBlank() ? null : value.trim());
    }

    @Override
    public void contributeRoomDiscoFeatures(final Collection<String> features, final MUCRoom room) {
        final String parent = getParentIri(room);
        if (parent != null && !parent.isBlank()) {
            features.add(SPACES_FORM_TYPE);
        }
    }

    @Override
    public void contributeRoomDiscoForms(final Set<DataForm> forms, final MUCRoom room) {
        final String parent = getParentIri(room);
        if (parent == null || parent.isBlank()) {
            return;
        }

        // XEP-0503: advertise the parent space in a dedicated urn:xmpp:spaces:0 form.
        final DataForm spacesForm = new DataForm(DataForm.Type.result);
        spacesForm.addField("FORM_TYPE", null, FormField.Type.hidden).addValue(SPACES_FORM_TYPE);
        spacesForm.addField(PARENT_FIELD, "Space parent", FormField.Type.text_single).addValue(parent);
        forms.add(spacesForm);

        // XEP-0503: for backwards compatibility, also point muc#roomconfig_pubsub at the space node.
        for (final DataForm form : forms) {
            final FormField formType = form.getField("FORM_TYPE");
            if (formType != null && "http://jabber.org/protocol/muc#roominfo".equals(formType.getFirstValue())) {
                form.addField(ROOM_CONFIG_PUBSUB, "Associated pubsub node", FormField.Type.text_single).addValue(parent);
            }
        }
    }

    @Override
    public void roomDestroyed(final long roomID, final JID roomJID) {
        storage.remove(roomJID.toBareJID());
    }

    // Remaining MUCEventListener callbacks are not relevant to space-parent bookkeeping.
    @Override public void roomCreated(final long roomID, final JID roomJID) {}
    @Override public void roomClearChatHistory(final long roomID, final JID roomJID) {}
    @Override public void occupantJoined(final JID roomJID, final JID user, final String nickname) {}
    @Override public void occupantLeft(final JID roomJID, final JID user, final String nickname) {}
    @Override public void occupantNickKicked(final JID roomJID, final String nickname) {}
    @Override public void nicknameChanged(final JID roomJID, final JID user, final String oldNickname, final String newNickname) {}
    @Override public void messageReceived(final JID roomJID, final JID user, final String nickname, final Message message) {}
    @Override public void privateMessageRecieved(final JID toJID, final JID fromJID, final Message message) {}
    @Override public void roomSubjectChanged(final JID roomJID, final JID user, final String newSubject) {}

    public static String getParentIri(final MUCRoom room) {
        return storage.get(room.getJID().toBareJID());
    }

    private static void setParentIri(final MUCRoom room, final String parentIri) {
        final String roomBareJid = room.getJID().toBareJID();
        if (parentIri == null) {
            storage.remove(roomBareJid);
        } else {
            storage.set(roomBareJid, parentIri);
        }
    }
}
