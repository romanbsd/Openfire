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
 distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.plugin.spaces;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpacesPubSubServiceDiscoveryTest {

    @Mock
    private Node openNode;

    @Mock
    private Node privateNode;

    @Mock
    private NodeSubscription activeSubscription;

    @Test
    void openSpaceVisibleToAnyone() {
        when(openNode.getAccessModel()).thenReturn(AccessModel.open);
        assertTrue(SpaceNodeDiscovery.canDiscover(openNode, new JID("stranger@example.com"), jid -> false));
    }

    @Test
    void authorizeSpaceHiddenFromStranger() {
        when(privateNode.getAccessModel()).thenReturn(AccessModel.authorize);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(privateNode.getSubscriptionsByJID(any())).thenReturn(List.of());
        assertFalse(SpaceNodeDiscovery.canDiscover(privateNode, new JID("stranger@example.com"), jid -> false));
    }

    @Test
    void authorizeSpaceVisibleToBareJidSubscriberWhenRequestedFromFullJid() {
        when(privateNode.getAccessModel()).thenReturn(AccessModel.authorize);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(activeSubscription.isActive()).thenReturn(true);
        when(privateNode.getSubscriptionsByJID(any())).thenAnswer(invocation ->
            invocation.getArgument(0).equals(new JID("member@example.com"))
                ? List.of(activeSubscription)
                : List.of());
        assertTrue(SpaceNodeDiscovery.canDiscover(privateNode, new JID("member@example.com/resource"), jid -> false));
    }

    @Test
    void whitelistSpaceHiddenFromRemovedMemberWithNoneAffiliation() {
        final NodeAffiliate affiliate = mock(NodeAffiliate.class);
        when(affiliate.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.none);
        when(privateNode.getAccessModel()).thenReturn(AccessModel.whitelist);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(privateNode.getSubscriptionsByJID(any())).thenReturn(List.of());
        when(privateNode.getAffiliate(any())).thenReturn(affiliate);
        assertFalse(SpaceNodeDiscovery.canDiscover(privateNode, new JID("removed@example.com"), jid -> false));
    }

    @Test
    void whitelistSpaceVisibleToPublisher() {
        final NodeAffiliate affiliate = mock(NodeAffiliate.class);
        when(affiliate.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.publisher);
        when(privateNode.getAccessModel()).thenReturn(AccessModel.whitelist);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(privateNode.getSubscriptionsByJID(any())).thenReturn(List.of());
        when(privateNode.getAffiliate(any())).thenReturn(affiliate);
        assertTrue(SpaceNodeDiscovery.canDiscover(privateNode, new JID("publisher@example.com"), jid -> false));
    }

    @Test
    void whitelistSpaceVisibleToMember() {
        final NodeAffiliate affiliate = mock(NodeAffiliate.class);
        when(affiliate.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.member);
        when(privateNode.getAccessModel()).thenReturn(AccessModel.whitelist);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(privateNode.getSubscriptionsByJID(any())).thenReturn(List.of());
        when(privateNode.getAffiliate(any())).thenReturn(affiliate);
        assertTrue(SpaceNodeDiscovery.canDiscover(privateNode, new JID("member@example.com"), jid -> false));
    }

    @Test
    void outcastCannotDiscoverAuthorizeSpaceEvenWithActiveSubscription() {
        final NodeAffiliate outcast = mock(NodeAffiliate.class);
        when(outcast.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.outcast);
        when(privateNode.getAccessModel()).thenReturn(AccessModel.authorize);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(privateNode.getAffiliate(any())).thenReturn(outcast);
        // A banned user must not discover a private space even if a stale active subscription lingers:
        // the outcast check must precede the subscription short-circuit.
        lenient().when(activeSubscription.isActive()).thenReturn(true);
        lenient().when(privateNode.getSubscriptionsByJID(any())).thenReturn(List.of(activeSubscription));
        assertFalse(SpaceNodeDiscovery.canDiscover(privateNode, new JID("banned@example.com"), jid -> false));
    }

    @Test
    void outcastCannotDiscoverWhitelistSpace() {
        final NodeAffiliate outcast = mock(NodeAffiliate.class);
        when(outcast.getAffiliation()).thenReturn(NodeAffiliate.Affiliation.outcast);
        when(privateNode.getAccessModel()).thenReturn(AccessModel.whitelist);
        when(privateNode.isAdmin(any())).thenReturn(false);
        when(privateNode.getAffiliate(any())).thenReturn(outcast);
        assertFalse(SpaceNodeDiscovery.canDiscover(privateNode, new JID("banned@example.com"), jid -> false));
    }
}
