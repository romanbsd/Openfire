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
import org.xmpp.packet.JID;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacesPubSubServiceAdminTest {

    @Test
    void parsesValidAdminJids() {
        final Set<JID> admins = SpacesPubSubService.parseAdminJids("a@example.com, b@example.com/res");
        assertTrue(admins.contains(new JID("a@example.com")));
        assertTrue(admins.contains(new JID("b@example.com")), "resource must be stripped to bare JID");
        assertEquals(2, admins.size());
    }

    @Test
    void skipsBlankAndMalformedEntriesWithoutThrowing() {
        // A leading comma, blank entries, and an invalid JID must be ignored, not abort the whole lookup.
        // (The pre-fix code called new JID() on every entry, so a bad value threw IllegalArgumentException.)
        final Set<JID> admins = assertDoesNotThrow(
            () -> SpacesPubSubService.parseAdminJids(", ,good@example.com,bad@@jid"));
        assertTrue(admins.contains(new JID("good@example.com")));
    }

    @Test
    void emptyOrNullPropertyYieldsNoAdmins() {
        assertTrue(SpacesPubSubService.parseAdminJids("").isEmpty());
        assertTrue(SpacesPubSubService.parseAdminJids(null).isEmpty());
        assertFalse(SpacesPubSubService.parseAdminJids("   ").iterator().hasNext());
    }
}
