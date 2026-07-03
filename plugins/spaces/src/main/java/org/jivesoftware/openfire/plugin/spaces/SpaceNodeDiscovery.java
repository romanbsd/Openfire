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

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.xmpp.packet.JID;

import java.util.function.Predicate;

/**
 * XEP-0503 private space discovery rules.
 */
public final class SpaceNodeDiscovery {

    private SpaceNodeDiscovery() {
    }

    public static boolean canDiscover(final Node pubNode, final JID requester,
                                      final Predicate<JID> isServiceAdmin) {
        if (pubNode == null) {
            return false;
        }
        if (requester != null && isServiceAdmin.test(requester)) {
            return true;
        }
        if (pubNode.getAccessModel() == AccessModel.open) {
            return true;
        }
        if (requester == null) {
            return false;
        }
        if (pubNode.isAdmin(requester)) {
            return true;
        }

        // An outcast can never discover a non-open node, even if a stale/lingering subscription remains.
        // Check this before the subscription short-circuit and for every non-open access model.
        final NodeAffiliate affiliate = pubNode.getAffiliate(requester.asBareJID());
        if (affiliate != null && affiliate.getAffiliation() == NodeAffiliate.Affiliation.outcast) {
            return false;
        }

        final JID bareRequester = requester.asBareJID();
        if (hasActiveSubscription(pubNode, requester)
            || (!requester.equals(bareRequester) && hasActiveSubscription(pubNode, bareRequester))) {
            return true;
        }
        if (pubNode.getAccessModel() == AccessModel.whitelist) {
            return affiliate != null
                && (affiliate.getAffiliation() == NodeAffiliate.Affiliation.member
                    || affiliate.getAffiliation() == NodeAffiliate.Affiliation.owner
                    || affiliate.getAffiliation() == NodeAffiliate.Affiliation.publisher);
        }
        return false;
    }

    private static boolean hasActiveSubscription(final Node pubNode, final JID requester) {
        for (final NodeSubscription subscription : pubNode.getSubscriptionsByJID(requester)) {
            if (subscription.isActive()) {
                return true;
            }
        }
        return false;
    }
}
