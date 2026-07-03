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

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRoomConfigExtensionManager;
import org.jivesoftware.openfire.plugin.spaces.muc.MucSpacesExtension;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Openfire plugin implementing XEP-0503 Server-side Spaces.
 */
public class SpacesPlugin implements Plugin {

    private static final Logger Log = LoggerFactory.getLogger(SpacesPlugin.class);

    private SpacesPubSubService spacesService;
    private MucSpacesExtension mucExtension;

    @Override
    public void initializePlugin(final PluginManager manager, final File pluginDirectory) {
        final String subdomain = JiveGlobals.getProperty("plugin.spaces.subdomain", "spaces");
        spacesService = new SpacesPubSubService(subdomain);
        spacesService.initialize();
        mucExtension = new MucSpacesExtension();

        if (JiveGlobals.getBooleanProperty("plugin.spaces.enabled", true)) {
            MUCRoomConfigExtensionManager.getInstance().register(mucExtension);
            MUCEventDispatcher.addListener(mucExtension);
            spacesService.start();
        } else {
            Log.info("Spaces plugin is disabled by configuration");
        }
        Log.info("Spaces plugin initialized (subdomain: {})", subdomain);
    }

    @Override
    public void destroyPlugin() {
        if (spacesService != null) {
            spacesService.destroy();
            spacesService = null;
        }
        if (mucExtension != null) {
            MUCEventDispatcher.removeListener(mucExtension);
            MUCRoomConfigExtensionManager.getInstance().unregister(mucExtension);
            mucExtension = null;
        }
        Log.info("Spaces plugin destroyed");
    }

    SpacesPubSubService getSpacesService() {
        return spacesService;
    }
}
