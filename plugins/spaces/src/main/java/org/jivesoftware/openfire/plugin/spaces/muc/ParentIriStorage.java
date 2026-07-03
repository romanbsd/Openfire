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

import org.jivesoftware.util.JiveGlobals;

import java.util.concurrent.ConcurrentHashMap;

interface ParentIriStorage {

    String get(String roomBareJid);

    void set(String roomBareJid, String parentIri);

    void remove(String roomBareJid);

    static ParentIriStorage jiveGlobals(final String propPrefix) {
        return new ParentIriStorage() {
            @Override
            public String get(final String roomBareJid) {
                return JiveGlobals.getProperty(propPrefix + roomBareJid, null);
            }

            @Override
            public void set(final String roomBareJid, final String parentIri) {
                JiveGlobals.setProperty(propPrefix + roomBareJid, parentIri);
            }

            @Override
            public void remove(final String roomBareJid) {
                JiveGlobals.deleteProperty(propPrefix + roomBareJid);
            }
        };
    }

    static ParentIriStorage inMemory() {
        return new ParentIriStorage() {
            private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

            @Override
            public String get(final String roomBareJid) {
                return values.get(roomBareJid);
            }

            @Override
            public void set(final String roomBareJid, final String parentIri) {
                values.put(roomBareJid, parentIri);
            }

            @Override
            public void remove(final String roomBareJid) {
                values.remove(roomBareJid);
            }
        };
    }
}
