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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.disco.*;
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XEP-0503 spaces PubSub service at spaces.&lt;domain&gt;.
 */
public class SpacesPubSubService implements ServerItemsProvider, DiscoInfoProvider, DiscoItemsProvider,
        RoutableChannelHandler, PubSubService {

    private static final Logger Log = LoggerFactory.getLogger(SpacesPubSubService.class);

    public static final String SERVICE_ID = "spaces";

    private final String serviceName;
    private final PacketRouter router;
    private final RoutingTable routingTable;
    private final PubSubEngine engine;
    private final AdHocCommandManager manager;
    private final PubSubPersistenceProvider persistenceProvider;

    private final Map<Node.UniqueIdentifier, Node> nodes = new ConcurrentHashMap<>();
    private final Map<JID, Map<JID, String>> barePresences = new ConcurrentHashMap<>();

    private CollectionNode rootCollectionNode;
    private DefaultNodeConfiguration leafDefaultConfiguration;
    private DefaultNodeConfiguration collectionDefaultConfiguration;

    private IQDiscoInfoHandler iqDiscoInfoHandler;
    private IQDiscoItemsHandler iqDiscoItemsHandler;

    private volatile boolean started;

    public SpacesPubSubService(final String serviceName) {
        this.serviceName = serviceName;
        this.router = XMPPServer.getInstance().getPacketRouter();
        this.routingTable = XMPPServer.getInstance().getRoutingTable();
        this.engine = new PubSubEngine(router);
        this.manager = new AdHocCommandManager();
        this.manager.addCommand(new PendingSubscriptionsCommand(this));
        this.persistenceProvider = new DefaultPubSubPersistenceProvider();
        this.persistenceProvider.initialize();
    }

    public void initialize() {
        iqDiscoItemsHandler = XMPPServer.getInstance().getIQDiscoItemsHandler();
        iqDiscoInfoHandler = XMPPServer.getInstance().getIQDiscoInfoHandler();

        leafDefaultConfiguration = persistenceProvider.loadDefaultConfiguration(getUniqueIdentifier(), true);
        if (leafDefaultConfiguration == null) {
            leafDefaultConfiguration = createSpacesLeafDefaults();
            persistenceProvider.createDefaultConfiguration(getUniqueIdentifier(), leafDefaultConfiguration);
        }

        collectionDefaultConfiguration = persistenceProvider.loadDefaultConfiguration(getUniqueIdentifier(), false);
        if (collectionDefaultConfiguration == null) {
            collectionDefaultConfiguration = new DefaultNodeConfiguration(false);
            collectionDefaultConfiguration.setAccessModel(AccessModel.open);
            collectionDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            collectionDefaultConfiguration.setDeliverPayloads(false);
            collectionDefaultConfiguration.setNotifyConfigChanges(true);
            collectionDefaultConfiguration.setNotifyDelete(true);
            collectionDefaultConfiguration.setNotifyRetract(true);
            collectionDefaultConfiguration.setSubscriptionEnabled(true);
            collectionDefaultConfiguration.setAssociationPolicy(CollectionNode.LeafNodeAssociationPolicy.all);
            collectionDefaultConfiguration.setMaxLeafNodes(-1);
            persistenceProvider.createDefaultConfiguration(getUniqueIdentifier(), collectionDefaultConfiguration);
        }
    }

    static DefaultNodeConfiguration createSpacesLeafDefaults() {
        final DefaultNodeConfiguration defaults = new DefaultNodeConfiguration(true);
        defaults.setAccessModel(AccessModel.authorize);
        defaults.setPublisherModel(PublisherModel.publishers);
        defaults.setDeliverPayloads(true);
        defaults.setLanguage("English");
        defaults.setMaxPayloadSize(10 * 1024 * 1024);
        defaults.setNotifyConfigChanges(true);
        defaults.setNotifyDelete(true);
        defaults.setNotifyRetract(true);
        defaults.setPersistPublishedItems(true);
        defaults.setMaxPublishedItems(100);
        defaults.setPresenceBasedDelivery(false);
        defaults.setSendItemSubscribe(true);
        defaults.setSubscriptionEnabled(true);
        return defaults;
    }

    public void start() {
        if (started) {
            return;
        }
        persistenceProvider.loadNodes(this);

        final String rootNodeID = JiveGlobals.getProperty("plugin.spaces.root.nodeID", "");
        final Node existingRoot = getNode(rootNodeID);
        if (existingRoot instanceof CollectionNode collectionRoot) {
            rootCollectionNode = collectionRoot;
        } else {
            // No usable root persisted: a fresh install, a changed plugin.spaces.root.nodeID, or a lost
            // root row while leaf rows remain. (Re)create it rather than NPE/ClassCastException later.
            rootCollectionNode = createRootCollectionNode(rootNodeID);
        }

        // Mark the service started BEFORE registering with the disco handlers: addServerItemsProvider()
        // immediately calls getItems(), which returns null while !started -- which would silently skip
        // registration and leave the service undiscoverable.
        started = true;
        routingTable.addComponentRoute(getAddress(), this);
        engine.start(this);
        iqDiscoItemsHandler.addServerItemsProvider(this);
        iqDiscoInfoHandler.setServerNodeInfoProvider(getServiceDomain(), this);
        Log.info("Spaces PubSub service started at {}", getServiceDomain());
    }

    private CollectionNode createRootCollectionNode(final String rootNodeID) {
        final String creator = JiveGlobals.getProperty("plugin.spaces.root.creator");
        final JID creatorJID = creator != null
            ? new JID(creator)
            : new JID(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        final CollectionNode root = new CollectionNode(getUniqueIdentifier(), null, rootNodeID, creatorJID, collectionDefaultConfiguration);
        root.addOwner(creatorJID);
        root.saveToDB();
        return root;
    }

    public void stop() {
        if (!started) {
            return;
        }
        iqDiscoItemsHandler.removeServerItemsProvider(this);
        iqDiscoInfoHandler.removeServerNodeInfoProvider(getServiceDomain());
        routingTable.removeComponentRoute(getAddress());
        engine.shutdown(this);
        started = false;
        Log.info("Spaces PubSub service stopped at {}", getServiceDomain());
    }

    public void destroy() {
        stop();
        persistenceProvider.shutdown();
    }

    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    @Override
    public void process(final Packet packet) {
        try {
            if (packet instanceof IQ iq) {
                if (!validatePubSubIq(iq)) {
                    return;
                }
                try {
                    if (engine.process(this, iq) != null) {
                        return;
                    }
                } catch (Exception e) {
                    // PubSubEngine owns responses for IQs that it recognizes. It may have routed a response
                    // before failing, so emitting another response here would violate IQ request semantics.
                    Log.error("Error processing IQ in the spaces PubSub engine", e);
                    return;
                }
                processDisco(iq);
            } else if (packet instanceof Presence presence) {
                engine.process(this, presence);
            } else if (packet instanceof Message message) {
                engine.process(this, message);
            }
        } catch (Exception e) {
            Log.error("Error processing packet for spaces service", e);
            // Only request IQs (get/set) may be answered with an error; never reply to a result/error IQ.
            if (packet instanceof IQ iq && (iq.getType() == IQ.Type.get || iq.getType() == IQ.Type.set)) {
                final IQ reply = IQ.createResultIQ(iq);
                reply.setError(PacketError.Condition.internal_server_error);
                send(reply);
            }
        }
    }

    private boolean validatePubSubIq(final IQ iq) {
        final Element child = iq.getChildElement();
        if (child == null) {
            return true;
        }
        final String namespace = child.getNamespaceURI();
        if (!"http://jabber.org/protocol/pubsub".equals(namespace)
            && !"http://jabber.org/protocol/pubsub#owner".equals(namespace)) {
            return true;
        }

        final Element createElement = child.element("create");
        final boolean creating = createElement != null;

        Element configure = child.element("configure");
        if (configure == null && creating) {
            configure = createElement.element("configure");
        }

        // Extract the configuration data form, if any was supplied.
        DataForm form = null;
        if (configure != null) {
            final Element xElement = configure.element("x");
            if (xElement != null) {
                form = new DataForm(xElement);
            }
        }

        // Only node creation, or a reconfiguration that carries a data form, is subject to space policy.
        // A create with no data form (or an empty <configure/>) must still be validated: validateNodeConfiguration
        // with a null form rejects it as "requires configuration", closing the bypass that let a create with an
        // empty configure produce a node without the mandatory pubsub#type=urn:xmpp:spaces:0.
        if (!creating && form == null) {
            return true;
        }

        final SpaceNodePolicy.ValidationResult result = SpaceNodePolicy.validateNodeConfiguration(form, creating);
        if (result instanceof SpaceNodePolicy.ValidationResult.Invalid invalid) {
            sendErrorPacket(iq, invalid.condition(), null);
            return false;
        }
        return true;
    }

    private void processDisco(final IQ iq) {
        // Only request IQs (get/set) are answered; never reply to a result or error IQ (RFC 6120).
        if (iq.getType() != IQ.Type.get && iq.getType() != IQ.Type.set) {
            return;
        }
        final Element childElement = iq.getChildElement();
        final String namespace = childElement == null ? null : childElement.getNamespaceURI();
        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            router.route(iqDiscoInfoHandler.handleIQ(iq));
        } else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            router.route(iqDiscoItemsHandler.handleIQ(iq));
        } else {
            sendErrorPacket(iq, PacketError.Condition.service_unavailable, null);
        }
    }

    @Override
    public String getServiceID() {
        return SERVICE_ID;
    }

    @Override
    public JID getAddress() {
        return new JID(null, getServiceDomain(), null);
    }

    @Override
    public boolean canCreateNode(final JID creator) {
        if (JiveGlobals.getBooleanProperty("plugin.spaces.create.anyone", true)) {
            return true;
        }
        return isServiceAdmin(creator);
    }

    @Override
    public boolean isServiceAdmin(final JID user) {
        if (parseAdminJids(JiveGlobals.getProperty("plugin.spaces.sysadmin.jid", "")).contains(user.asBareJID())) {
            return true;
        }
        return InternalComponentManager.getInstance().hasComponent(user);
    }

    /**
     * Parses the comma-separated {@code plugin.spaces.sysadmin.jid} property into a set of bare JIDs. Blank
     * entries are skipped and malformed entries are logged and ignored rather than aborting the whole
     * discovery/creation request that triggered the lookup.
     */
    static Set<JID> parseAdminJids(final String property) {
        if (property == null || property.isBlank()) {
            return Set.of();
        }
        final Set<JID> admins = new HashSet<>();
        for (final String admin : property.split(",")) {
            final String trimmed = admin.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                admins.add(new JID(trimmed).asBareJID());
            } catch (final IllegalArgumentException e) {
                Log.warn("Ignoring malformed JID in plugin.spaces.sysadmin.jid: '{}'", trimmed);
            }
        }
        return admins;
    }

    @Override
    public boolean isInstantNodeSupported() {
        return true;
    }

    @Override
    public boolean isCollectionNodesSupported() {
        return true;
    }

    @Override
    public CollectionNode getRootCollectionNode() {
        return rootCollectionNode;
    }

    @Override
    public DefaultNodeConfiguration getDefaultNodeConfiguration(final boolean leafType) {
        return leafType ? leafDefaultConfiguration : collectionDefaultConfiguration;
    }

    @Override
    public Collection<String> getShowPresences(final JID subscriber) {
        return PubSubEngine.getShowPresences(this, subscriber);
    }

    @Override
    public void presenceSubscriptionNotRequired(final Node node, final JID user) {
        PubSubEngine.presenceSubscriptionNotRequired(this, node, user);
    }

    @Override
    public void presenceSubscriptionRequired(final Node node, final JID user) {
        PubSubEngine.presenceSubscriptionRequired(this, node, user);
    }

    @Override
    public boolean isMultipleSubscriptionsEnabled() {
        return JiveGlobals.getBooleanProperty("plugin.spaces.multiple-subscriptions", true);
    }

    @Override
    public Map<JID, Map<JID, String>> getSubscriberPresences() {
        return barePresences;
    }

    @Override
    public AdHocCommandManager getManager() {
        return manager;
    }

    @Override
    public Node getNode(final Node.UniqueIdentifier nodeID) {
        return nodes.get(nodeID);
    }

    @Override
    public Collection<Node> getNodes() {
        return nodes.values();
    }

    @Override
    public void addNode(final Node node) {
        nodes.put(node.getUniqueIdentifier(), node);
    }

    @Override
    public void removeNode(final Node.UniqueIdentifier nodeID) {
        nodes.remove(nodeID);
    }

    @Override
    public void broadcast(final Node node, final Message message, final Collection<JID> jids) {
        message.setFrom(getAddress());
        for (final JID jid : jids) {
            message.setTo(jid);
            message.setID(StringUtils.randomString(8));
            router.route(message);
        }
    }

    @Override
    public void send(final Packet packet) {
        router.route(packet);
    }

    @Override
    public void sendNotification(final Node node, final Message message, final JID jid) {
        message.setFrom(getAddress());
        message.setTo(jid);
        message.setID(StringUtils.randomString(8));
        router.route(message);
    }

    @Override
    public Iterator<DiscoServerItem> getItems() {
        if (!started) {
            return null;
        }
        return List.of(new DiscoServerItem(
            new JID(getServiceDomain()), "Spaces service", null, null, this, this)).iterator();
    }

    @Override
    public Iterator<Element> getIdentities(final String name, final String node, final JID senderJID) {
        final List<Element> identities = new ArrayList<>();
        if (name == null && node == null) {
            final Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "pubsub");
            identity.addAttribute("name", "Spaces service");
            identity.addAttribute("type", "service");
            identities.add(identity);
        } else if (name == null) {
            final Node pubNode = getNode(node);
            if (pubNode != null && canDiscoverNode(pubNode, senderJID)) {
                final Element identity = DocumentHelper.createElement("identity");
                identity.addAttribute("category", "pubsub");
                identity.addAttribute("type", pubNode.isCollectionNode() ? "collection" : "leaf");
                identities.add(identity);
            }
        }
        return identities.iterator();
    }

    @Override
    public Iterator<String> getFeatures(final String name, final String node, final JID senderJID) {
        final List<String> features = new ArrayList<>();
        if (name == null && node == null) {
            features.add("http://jabber.org/protocol/pubsub");
            features.add("http://jabber.org/protocol/pubsub#access-open");
            features.add("http://jabber.org/protocol/pubsub#config-node");
            features.add("http://jabber.org/protocol/pubsub#create-and-configure");
            features.add("http://jabber.org/protocol/pubsub#create-nodes");
            features.add("http://jabber.org/protocol/pubsub#delete-items");
            features.add("http://jabber.org/protocol/pubsub#delete-nodes");
            features.add("http://jabber.org/protocol/pubsub#get-pending");
            features.add("http://jabber.org/protocol/pubsub#instant-nodes");
            features.add("http://jabber.org/protocol/pubsub#item-ids");
            features.add("http://jabber.org/protocol/pubsub#meta-data");
            features.add("http://jabber.org/protocol/pubsub#member-affiliation");
            features.add("http://jabber.org/protocol/pubsub#modify-affiliations");
            features.add("http://jabber.org/protocol/pubsub#manage-subscriptions");
            features.add("http://jabber.org/protocol/pubsub#multi-items");
            features.add("http://jabber.org/protocol/pubsub#persistent-items");
            features.add("http://jabber.org/protocol/pubsub#publish");
            features.add("http://jabber.org/protocol/pubsub#publisher-affiliation");
            features.add("http://jabber.org/protocol/pubsub#purge-nodes");
            features.add("http://jabber.org/protocol/pubsub#retract-items");
            features.add("http://jabber.org/protocol/pubsub#retrieve-affiliations");
            features.add("http://jabber.org/protocol/pubsub#retrieve-default");
            features.add("http://jabber.org/protocol/pubsub#retrieve-items");
            features.add("http://jabber.org/protocol/pubsub#retrieve-subscriptions");
            features.add("http://jabber.org/protocol/pubsub#subscribe");
            features.add("http://jabber.org/protocol/pubsub#subscription-options");
            features.add("http://jabber.org/protocol/pubsub#publish-options");
            features.add(SpaceNodePolicy.SPACE_TYPE);
            features.add(PubsubTypeFilter.NAMESPACE);
            // Do not advertise XEP-0499 until all request controls and relationship metadata are supported.
        } else if (name == null) {
            final Node pubNode = getNode(node);
            if (pubNode != null && canDiscoverNode(pubNode, senderJID)) {
                features.add("http://jabber.org/protocol/pubsub");
            }
        }
        return features.iterator();
    }

    @Override
    public Set<DataForm> getExtendedInfos(final String name, final String node, final JID senderJID) {
        if (name == null && node != null) {
            final Node pubNode = getNode(node);
            if (pubNode != null && canDiscoverNode(pubNode, senderJID)) {
                final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(senderJID);
                return Set.of(pubNode.getMetadataForm(preferredLocale));
            }
        }
        return Set.of();
    }

    @Override
    public boolean hasInfo(final String name, final String node, final JID senderJID) {
        if (!started) {
            return false;
        }
        if (name == null && node == null) {
            return true;
        }
        if (name == null) {
            final Node pubNode = getNode(node);
            return pubNode != null && canDiscoverNode(pubNode, senderJID);
        }
        return false;
    }

    @Override
    public Iterator<DiscoItem> getItems(final String name, final String node, final JID senderJID) {
        return getItems(name, node, senderJID, null);
    }

    @Override
    public Iterator<DiscoItem> getItems(final String name, final String node, final JID senderJID, final Element query) {
        if (!started) {
            return null;
        }

        final PubsubTypeFilter typeFilter = PubsubTypeFilter.fromQuery(query);
        final PubsubExtendedDiscoRequest extDisco = PubsubExtendedDiscoRequest.fromQuery(query);
        final List<DiscoItem> answer = new ArrayList<>();
        final String serviceDomain = getServiceDomain();
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(senderJID);

        if (name == null && node == null) {
            for (final Node pubNode : rootCollectionNode.getNodes()) {
                if (!canDiscoverNode(pubNode, senderJID)) {
                    continue;
                }
                if (!typeFilter.matchesNode(pubNode)) {
                    continue;
                }
                answer.add(buildNodeDiscoItem(serviceDomain, pubNode, extDisco, preferredLocale));
            }
        } else if (name == null) {
            final Node pubNode = getNode(node);
            if (pubNode == null || !canDiscoverNode(pubNode, senderJID)) {
                return null;
            }
            if (pubNode.isCollectionNode()) {
                for (final Node nestedNode : pubNode.getNodes()) {
                    if (canDiscoverNode(nestedNode, senderJID) && typeFilter.matchesNode(nestedNode)) {
                        answer.add(buildNodeDiscoItem(serviceDomain, nestedNode, extDisco, preferredLocale));
                    }
                }
            } else {
                for (final PublishedItem publishedItem : pubNode.getPublishedItems()) {
                    answer.add(new DiscoItem(new JID(serviceDomain), publishedItem.getID(), node, null));
                }
            }
        }
        return answer.iterator();
    }

    private DiscoItem buildNodeDiscoItem(final String serviceDomain, final Node pubNode,
                                         final PubsubExtendedDiscoRequest extDisco, final Locale locale) {
        final DiscoItem item = new DiscoItem(
            new JID(serviceDomain),
            pubNode.getName(),
            pubNode.getUniqueIdentifier().getNodeId(),
            null);
        if (extDisco.fullMetadata()) {
            item.getElement().add(pubNode.getMetadataForm(locale).getElement());
        }
        return item;
    }

    boolean canDiscoverNode(final Node pubNode, final JID requester) {
        return SpaceNodeDiscovery.canDiscover(pubNode, requester, this::isServiceAdmin);
    }

    PubSubPersistenceProvider getPersistenceProvider() {
        return persistenceProvider;
    }

    private void sendErrorPacket(final IQ packet, final PacketError.Condition error, final Element pubsubError) {
        final IQ reply = IQ.createResultIQ(packet);
        final Element child = packet.getChildElement();
        if (child != null) {
            reply.setChildElement(child.createCopy());
        }
        reply.setError(error);
        if (pubsubError != null) {
            reply.getError().getElement().add(pubsubError);
        }
        router.route(reply);
    }
}
