# Openfire Spaces Plugin (XEP-0503)

Implements [XEP-0503: Server-side Spaces](https://xmpp.org/extensions/xep-0503.html) for Openfire.

## Requirements

- Openfire 5.2.0 or newer
- **JDK 21+** on the host running Openfire (plugin bytecode targets Java 21)

## Installation

Build the plugin JAR:

```bash
./mvnw -pl plugins/spaces package
```

Copy `plugins/spaces/target/spaces-openfire-plugin-assembly.jar` to your Openfire `plugins/` directory as `spaces.jar` and restart Openfire (or use the admin console to install).

## Service

The plugin exposes a dedicated PubSub service at **`spaces.<your-xmpp-domain>`** (configurable). Space nodes are leaf pubsub nodes with `pubsub#type = urn:xmpp:spaces:0`. Space content (MUC rooms, links, avatars) is published as standard pubsub items.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `plugin.spaces.enabled` | `true` | Enable or disable the spaces service |
| `plugin.spaces.subdomain` | `spaces` | Subdomain for the spaces PubSub service |
| `plugin.spaces.create.anyone` | `true` | Allow any registered user to create space nodes |
| `plugin.spaces.sysadmin.jid` | (empty) | Comma-separated bare JIDs with spaces service admin rights |
| `plugin.spaces.multiple-subscriptions` | `true` | Allow multiple subscriptions per JID |

Room parent space IRIs are stored per room as `plugin.spaces.room.parent.<room-bare-jid>`.

## Movim manual validation checklist

1. Ensure `spaces.<domain>` appears in server disco#items.
2. Create a **public** space (access model `open`) with `pubsub#type = urn:xmpp:spaces:0`.
3. Create a **private** space (`authorize` access model); verify it is hidden from strangers in disco#items.
4. Join a space via pubsub subscribe; approve pending subscription for private spaces.
5. Publish MUC room items (`<conference xmlns='urn:xmpp:bookmarks:1'/>`) to the space node.
6. Set `muc#roomconfig_pubsub` on a room to the space IRI; verify room disco#info includes `urn:xmpp:spaces:0` parent form.

## Protocol support

| XEP | Status |
|-----|--------|
| XEP-0503 Server-side Spaces | Supported (this plugin) |
| XEP-0462 PubSub Type Filtering | Supported |
| XEP-0499 Pubsub Extended Discovery | Partial (`full_metadata` on disco#items) |
| XEP-0060 member-affiliation | **Not implemented** (deferred SHOULD; see plugin README) |

## Development

```bash
./mvnw -pl plugins/spaces test
./mvnw -pl xmppserver -Dtest=MUCRoomConfigExtensionManagerTest test
```
