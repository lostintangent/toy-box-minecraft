# Client map

`ClientFeatures.init()` starts one channel observer, then its Minecraft views. The public
vocabulary comes from Toy Box: Channel, Agent, Presence, Message, Reaction, Task, Request,
Artifact and Routine. Minecraft supplies screens, agents in the world, and stations.

| Package | Owns | Main operations |
| --- | --- | --- |
| `client.channel` | Public snapshot, events, reducer, live changes, connection and configuration | Connect, reduce, post a message, mark read |
| `client.screens` | Channel picker, transcript/composer, team/member, tasks, request and routines | Choose a channel, open a page, compose a reply |
| `client.screens.artifacts` | Artifact page, live file reader and Markdown layout | Select, read, watch |
| `client.agents` | Channel identities as local robots, desk assignment, movement, nameplates and live effects | Place, move, animate, render |
| `client.world` | Station renderers, interactions and channel-driven block state | Render channel state, open its screen, apply block changes |
| `world` and `world.layout` | One edit-preserving world builder, desk expansion, world rules and anchors | Build, preserve edits, publish anchors |
| `client.images` | Public PNG loading and bounded GPU textures shared by frames and reader | Load, show, release |
| `client.hud` | Channel summary, connection trouble, key bindings and notices | Show the channel, trouble and live moments |
| `client.ui` | Toy Box widget styling, drawing and palette | Style, draw |
| `client.dev` | Opt-in loopback inspection and capture | Inspect, preview, capture |

`ChannelReducer` mirrors Toy Box's canonical transitions. `ChannelState` owns the current
snapshot; its listeners receive live presentation changes. Recovery snapshots replace state
quietly. Screens and world views read that same state; neither maintains another channel model.

Within `client.channel`, `ChannelModel` and `ChannelEvent` are the public values;
`ChannelCodec` owns wire decoding and `ChannelReducer` applies the server's transitions.
`ChannelChanges` derives presentation moments rather than another protocol. `ChannelTransport`
owns HTTP/SSE and reconnects; `ChannelClient` binds its lifecycle and capabilities to Minecraft.
`ChannelConfig` identifies the server/channel, and `ChannelConnection` describes this observer.
Audience preview and transcript labels remain shared pure channel functions. No additional
projection, session cache, delivery scheduler or AgentCraft vocabulary sits between these pieces.

`Screens` owns page navigation and keyboard entry points. `WorldStations` binds Minecraft blocks
to their renderers and screens. Agent right-clicks open the member screen directly. These are
ordinary composition points, without a feature registry or extension hooks.

`ChannelScreen` registers Minecraft widgets for navigation and actions. `KitButton` supplies
Toy Box artwork over vanilla button behavior. The picker uses `EditBox`; `MessageInput` uses
Minecraft's `MultilineTextField` for editing and wrapping, adding channel submission, mention
completion and the growing paper field. Draft/send ownership remains in `MessageDraft`.
Rich transcript/list content and the split artifact reader retain their own layout.

Agents use Minecraft's `AvatarRenderer`, player model, walk cycle and skin textures. Toy Box
adds seat-aware poses and legible nameplates. `AgentParticles` only defines effect recipes and
their twinkle/fade/flutter; vanilla particles own their lifecycle and rendering. `AgentMotion`
and `GridPathfinder` remain local because the figures are avatars, not server AI mobs.
`Seats` owns chair detection, including the rules used by world diagnostics.

World station clicks use Fabric's `UseBlockCallback` directly. `WorldProjection` applies a
small map of changed block states on the integrated server thread. `WorldLauncher` opens or
creates the saved world. Each Toy Box startup reapplies the current blueprint, preserving edits.
`WorldBuilder` and `Landscape` describe its construction in a `Blueprint`;
`BlueprintStore` retains the previous design so rebuilds preserve player edits. `Anchors` publishes
one immutable layout of named positions and orientations for agents, stations and cameras.

The entrance sign is ordinary block lettering. Live members emerge from a native chest and walk
into the room; departures wave and walk to the entrance. An artifact's public author carries a
book to the library. `AgentLife` owns their animation timing; movement follows the same anchors
and paths as ordinary presence. Recovery snapshots do not replay these moments. Gallery frames show the
latest three PNG attachments and open the image beside its public message. Other formats use
the ordinary external-opening path. NativeImage owns decoding and resizing; the bounded texture
cache releases its images on eviction and channel changes. Native spatial
sounds use the Blocks volume category and respect `TOY_BOX_MUTE`; request podiums emit redstone
strength 15 until the request is cleared. These effects never change server work.

Backtick opens the transcript, Enter opens a targeted channel terminal, and J opens the request.
R adds the targeted agent, task card or gallery image to an ordinary public message draft.
Bindings are adjustable in Minecraft Controls. The composer posts ordinary text; `@mentions`
preview recipients while the server owns delivery. Reading advances the owner's read cursor;
it does not acknowledge a request or grant provider permissions. Task row addresses are display
positions, never mutation identities. `TaskRows` presents diffs as Toy Box's checklists do: own
and subtask counts, compact, omitted while pending. Tasks find their artifact by the `id` the
server supplies; artifacts never roll up to parents.

Java packages use `dev.toybox.minecraft`; registered resources use `toybox_minecraft`. The world
and its save folder are named `Toy Box`; the launcher opens it or creates it when absent.
Layout IDs, block IDs, anchors and the `/toybox_minecraft hq` build command retain their identifiers.

See [development](DEV.md), [architecture](../docs/architecture.md), and [QA](../docs/QA.md).
