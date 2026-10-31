# Validation: 2026-10-04

## Entrance, gallery and compact channel screens — October 4

The entrance now uses a 9 × 2 textured wordmark under a three-colour cube, replacing the
27 × 7 billboard. Gallery frames occupy the three bays between posts, backed with plaster.
Channel pages share one tab row (labels where they fit, named icon buttons otherwise) and
header actions. The composer owns its audience/send status inside the field; short screens
show one editable line and follow the caret through longer drafts. Transcript text is drawn
only when the full glyph row fits its viewport.

The personal minimalism review traced the changed screen layout, composer, button presentation,
block registration, generated models and blueprint placement. Deleted the now-unused link
button mode, wrapped-tab/footer layout and two layout fields. Page labels and icons live together
on the existing Page enum. Production Java is net **14 lines larger** (client UI shrank by five);
there is one new 22-line block class. Asset generators add 119 lines, 84 of which are the six
12-pixel icon drawings. No test sources or Toy Box core files changed.

Minecraft owns button interaction, focus, tooltips and narration; `MultilineTextField` still owns
selection, clipboard, wrapping and editing. The wordmark is one 256-byte texture sampled by
ordinary baked block-state models: no block entity, custom renderer, ticking, sign manager or
new world-update mechanism. Existing horizontal shape rotation and the edit-preserving blueprint
handle placement. The three-colour cube uses existing blocks, including vanilla waxed oxidized
copper. No separate responsive-layout or navigation framework was introduced.

`bun run build` passed all four Java suites; `bun run test` passed 7/7 tooling tests. Changed Java
passes Google Java Format. Asset validation passed three identical builds and synchronization
(330 files, zero outstanding changes).

In-game evidence is in `artifacts/shots/presentation`: the 21-view capture plus compact-screen
and composer close-ups. The isolated game uses port 7883 and a separate copy of the QA save;
its fixture uses 3222. The blueprint update removed the old billboard and retained the three
player-placed redstone blocks. The visual fixture substitutes an actual captured room PNG for
the black pixel; the canonical exported contract fixture remains unchanged.

At normal size and at Minecraft's 320 × 240 GUI minimum, inspected the sign, gallery, all channel
pages and composer. Native keyboard navigation retained drafts; Tab completed a mention;
Shift+Enter, number typing and caret movement worked; one actual Enter send reached the fixture
exactly once and its receipt cleared the draft. At compact size, icon tabs remained on one row,
long drafts scrolled to the caret, and the header's Change channel button opened the picker.
`interaction-checks.json` and `small-checks.json` record these checks. This is visual and behavioral
validation, not a performance benchmark. Offline sending retained the draft and showed its
error inside the composer. The isolated game and fixture were stopped afterward; the user’s
running game and real Toy Box server were not operated.

## World interactions and implementation review — October 3

The sign and five world interactions add 606 production Java lines and five source files to the
pre-feature tree. The largest addition is the shared image loader/texture owner (183 lines),
followed by its world renderer and screen. No test sources changed and Toy Box core is untouched.

The personal review traced every changed Java owner and its direct consumers, plus assets and
capture tooling. Removed the separate arrival and delivery maps: `AgentLife` owns those animation
clocks, and `AgentManager` follows the existing motion target. PNG decoding and resizing run off
the game thread through `NativeImage`; only texture registration runs on it. Removed the ineffective dependent
future cancellation field, retaining bounded requests and explicit release of obsolete images.
The image caption uses its existing layout result for scrolling. Task rendering and hit testing
share one layout; frames share the existing panel geometry/assets. No animation, interaction,
image-provider or resource framework was added.

Minecraft supplies the chest lid animation, avatar/book models, textures, sound attenuation and
redstone propagation. The gallery and image reader now support PNG through Minecraft's own
decoder, removing ImageIO and the pixel-conversion loop. Other formats retain their ordinary
external-opening path; the Channel attachment contract is unchanged. Each Toy Box startup
reapplies the existing
edit-preserving blueprint instead of checking individual old-world upgrade conditions.

`bun run build` passed all four Java suites; `bun run test` passed all seven tooling tests.
Changed Java passes Google Java Format. Asset generation passed three identical deterministic
builds, all resource checks, and synchronization (303 files, zero outstanding changes).

An isolated game on 7883, fixture on 3222 and separate Toy Box save exercised actual native chest
arrival, walking to a desk, artifact book delivery, waving departure and removal. Gallery clicks
opened the image reader; R added image, task and agent context to ordinary drafts. A vanilla
redstone lamp lit for a request and turned off after an actual composer reply. Reconnecting did
not replay notifications. Reopening the world updated its blueprint while preserving three
player-placed circuit blocks. No path failures were recorded.

Inspected the 21-view capture and the final affected image/animation close-ups in
`artifacts/shots/world-features`; `moments.json` and `interactions.json` record the interactions.
The black gallery image is the canonical fixture's single black pixel. Audio remained muted, so
spatial playback was not auditioned. These checks do not claim a performance benchmark or
multiplayer support. The user's running game and real Toy Box server were not operated. The isolated game and fixture
were stopped after validation.

## Channel and diagnostics minimalism — October 3

Production Java shrank from 20,819 to 20,545 lines (274 removed), and from 135 to 133 files.
The channel package accounts for 24 removed lines; most reduction is inherited diagnostics.
All six Java test files and seven tooling tests are unchanged. Toy Box core is untouched.

- Channel mutations use `CompletableFuture.handleAsync` directly. The codec owns the durable
  event-type dispatch; the transport no longer repeats that list. Connection generations use
  the existing lock and a volatile integer rather than an additional atomic mechanism.
  Obsolete-callback guards, timeouts, quiet recovery and non-retrying sends remain.
- File opening shares one workspace-link path. The unused inbound-time getters are removed.
  Raw inline attachments remain because they are still part of the actual public contract.
- Development handlers use `Minecraft.submit`. Deleted the unused command-invocation API,
  binary-frame compatibility path, camera accessor, Windows focus probe and screenshot statistics
  with no consumers. Time/weather shortcuts now invoke vanilla commands. Frame waits use one
  active-list pass and the same condition mechanism for frame counts and readiness.

Retained the canonical reducer, immutable model, audience rules, transcript labels and live
changes: each has actual consumers. The API remains the server's protocol, with one observed
snapshot shared by every view. The tools inventory and Fabric GameTest tradeoff are documented
in `mod/DEV.md`; no alternative client model or testing framework was introduced.

Validation: `bun run build` passed all four Java suites, including the shared TypeScript/Java
conformance trace and HTTP/SSE lifetime checks. `bun run test` passed 7/7. Changed Java passes
Google Java Format; modified tooling passes Node syntax checks. The jar excludes both deleted
classes. The isolated game/fixture on 7883/3222 used a separate QA save. Inspected the 17-view
capture and reconnect transcript in `artifacts/shots/channel-tools`. A real composer reply
arrived exactly once; reading did not acknowledge the request; reconnect retained messages and
drafts without replaying notifications. Frame fences completed, and native client-thread errors
returned through the bridge. `smoke.json` records the evidence. No performance benchmark is claimed.
The isolated game and fixture were stopped afterward; the user's running game and server were
not operated.

## Agents and world minimalism — October 3

Production Java shrank from 21,223 to 20,819 lines (404 removed), and from 137 to 135
source files. The six test files are unchanged. This pass touched only the Minecraft client,
world construction entry point, layout publication and their documentation.

- `AgentParticles` defines effect recipes over Minecraft's `SingleQuadParticle`; the separate
  array pool, particle tick loop, billboard geometry and render submission are gone. Native
  `RandomSource`, `Mth` and `ARGB` replace equivalent animation helpers. Effect placement,
  palette, fading, twinkle and paper flutter remain; particle timing follows vanilla physics.
- Agent lookup uses the client level's existing entity index. Routing reuses the tick's checked
  floor cache. Unused motion counters and speech timestamps are removed.
- Station clicks use one Fabric `UseBlockCallback`, without a parallel handler registry.
  World changes use `server.execute` directly and one map of block changes; native block-box
  iteration and respawn data replace manual iteration/command construction.
- The unused anchor-listener API is deleted. World inspection and SDL test input live in
  `client.dev`; inspection now uses the actual `Seats` rules instead of duplicating them.

The avatar renderer/model, skin pipeline, block-entity render states, collision shapes and book
model already use native Minecraft APIs. The local pathfinder remains appropriate for client-only
avatars (vanilla navigation expects a `Mob`). Nameplate layout owns readability around agents,
monitors and speech bubbles. `Blueprint`/`BlueprintStore` retain the distinct responsibility of merging a
new generated design without erasing player edits. Registered IDs, saved layouts, channel
protocol and authored world geometry are unchanged.

Validation: `bun run build` passed all four Java contract suites; `bun run test` passed all
seven tooling tests. Changed Java files pass Google Java Format, and the jar contains none of
the deleted or moved classes at their former paths. No library-behavior tests were added.

The isolated 7883/3222 game and fixture used a separate QA save. Inspected the 17-view capture
in `artifacts/shots/agents-world`, including monitor/nameplate close-ups, plus native sparkles,
confetti, reactions and speech. The physical task wall and monitor opened their actual channel
screens through Minecraft's use mapping. A reply cleared the request/podium, and the lead walked
to its waiting seat: movement was observed, no forced settling, zero path failures. A normal
rebuild updated zero generated blocks and kept a deliberately placed gold block; the test block
was then removed. The reachability diagnostic checked 80 routes with no unreachable or misplaced
anchors. Its existing furniture-climbing advisories are recorded separately, not treated as a
new bug-hunting task. `smoke.json` records the interaction evidence.

The isolated game and fixture were stopped after validation. The user's game and Toy Box server
were not operated. No runtime performance benchmark is claimed.

## Native screen widgets — October 3

The screen pass removes 385 production Java lines and one source file overall. `KitButton`
uses Minecraft's button lifecycle, focus and narration with Toy Box artwork. The server field
uses `EditBox`; the channel composer uses `MultilineTextField` and retains only its channel
keys and paper presentation. The old text model, editing-key dispatcher and field renderer
are deleted. Portrait drawing is shared by the channel screens. No tests were added or changed,
and no Toy Box core, protocol, world or generated-asset code changed.

`bun run build` passed all four Java contract suites; `bun run test` passed all seven tooling
tests. Changed Java files pass Google Java Format. The jar contains the new widgets and none
of the three deleted input classes.

In an isolated game and fixture (7883/3222), exercised initial input focus, Tab mention
completion, Shift+Enter, caret movement, selection, copy/cut/multiline paste, keyboard and mouse
tab navigation, retained drafts, task/member composition actions, picker editing/refresh and
channel selection. Enter posted exactly one message and the receipt cleared that draft.
An offline Enter attempt preserved its unsent draft.
Screenshots in `artifacts/shots/native-widgets` cover every page, keyboard focus, a long draft,
and 320×240 logical layouts with wrapped tabs and scrolled tasks. The small footer omits the
Escape hint when channel controls need its space. Existing rich-content scrolling remains local;
there is no new page framework or widget reconciliation layer. The isolated game and fixture
were stopped afterward; the user’s existing game and Toy Box server were not operated.


## Minecraft minimalism pass — October 3

This pass changed only the Minecraft project. Production Java shrank from 23,028 to 21,608
lines (1,420 removed) and from 147 to 138 classes. Test sources shrank by 22 lines; no tests were
added. [The client map](../mod/FEATURES.md) records the resulting ownership.

Removed the prototype room and builder registry, unused agent extensions, duplicate pose/status
state, unreachable animations, unused UI options, per-page registration wrappers and redundant
connection/draft fields. The public Channel reducer/protocol and registered world/resource IDs
retain their contracts.

The final `bun run build` passed all four Java suites, including the canonical TypeScript/Java
conformance trace and real HTTP/SSE lifecycle tests. All seven `bun run test` tooling tests passed.
The packaged jar contains none of the deleted classes.

An isolated Minecraft instance used its own save, fixture server and bridge (3222/7883). The
17-view capture is in `artifacts/shots/minimalism`; final picker/composer images include the last
text-field reductions. Inspected frames include legible monitors/nameplates, selected tabs,
avatars, task cards and the live Markdown reader. The final game verified nine-member hydration,
draft retention across pages, exactly one ordinary message post, live request acknowledgement,
and reconnect without replaying sounds or duplicating messages. A non-forced world rebuild
preserved a deliberately placed gold block (then removed from the disposable save).

The existing Toy Box server, user channel and user game were not operated during this pass.
The isolated game and fixture server were stopped afterward.

The sections below record earlier implementation validation; their intermediate protocol and
line-count descriptions are historical.


## Channel picker and transcript polish

- Toy Box adds one 14-line catalog route over its existing `listChannels` operation, plus
  generated route registration and documentation. `bun check` and all 1,290 core tests pass.
  The mod build passes all four Java suites; all seven tooling tests pass. New assertions cover
  transcript grouping boundaries and persistence of the selected server/channel.
- A separate game and two isolated fixture channels exercised first-use selection, switching
  in both directions, and per-channel drafts. Both channels loaded nine agents; returning
  restored the original unsent draft. Each selection received one snapshot, with no bells or
  chimes replayed. Restarting the isolated game reconnected from the saved selection.
  An unreachable catalog left the active observation connected.
- Actual screenshots show grouped joins, agent portraits/colors, reaction chips, attachment
  names and the pending-question highlight. A reaction preview shows hearts and a head nod
  with no speech bubble. Evidence: `artifacts/shots/channel-polish/`.
- The catalog uses HTTP/1.1, matching the existing stream. A direct Java probe against the
  real development server returned its catalog in 118 ms; the default HTTP/2 upgrade timed out.
  The rebuilt picker displayed the real server's channels and attention flags successfully.
  The permanent capture set now includes the picker, alongside transcript and world close-ups.
- The user's existing game and channel were left alone. The checks below describe earlier
  protocol work; this presentation pass does not change streaming, reducers or browser caching.

## Granular protocol update

- The full mod build passed all four Java contract suites. The channel suite includes a
  generated 24-step TypeScript/Java conformance trace, including roster reorder, quiet task/model/read
  changes, message-derived metadata/request state, sparse/baseline presence, and a reaction on a
  pending question outside the loaded history. `bun run fixture` regenerates the trace from real
  Toy Box snapshots and its canonical reducer. All seven Node tooling tests passed.
- The new game connected to the real Toy Box server with two agents and the latest 100 public
  messages. Its ordinary composer committed `@stream-no-recipient Native granular protocol verified`
  once as message `01b2ab13-92a4-41f3-be2a-3a215a33e53f`, sequence 105; the shared read cursor
  advanced to 105 while full-snapshot count stayed one. An intentional reconnect restored the
  100-message window with snapshot count two and no replayed bells or chimes.
- Evidence: `/tmp/toy-box-minecraft-granular-{before,after-post,reconnect}.json` and
  `artifacts/shots/granular-protocol-transcript.png`. The screenshot was inspected: the public
  preview-change message, committed native message, and empty composer are visible and readable.
- A browser rename reached the game as `Shared channel protocol` without increasing the
  snapshot count. The user then began interacting with the running game; UI automation and
  fixture cleanup stopped. The game remains connected to that real-server fixture. Deletion
  and restoration were not repeated in this run; their automated transport tests passed.
- The earlier integration measurements below describe the previous context-based stream.
  Current protocol and ownership are documented in `docs/architecture.md`.

## Toy Box core

- `bun check` passed: formatting, lint and TypeScript, with no warnings or errors.
- `bun test --timeout 30000` passed: 1,290 tests across 182 files, zero failures.
  The timeout allowance avoids an unrelated CLI type-library test's default five-second
  limit under concurrent Minecraft/browser load; no unrelated test was changed.
- Independent implementation review found and fixed inconsistent snapshot reads and two
  history-page races involving reactions, structural sharing and deduplicated requests.
  No concrete review findings remain.
- Durable snapshot fields now come from one transaction. One observation path handles
  bootstrap, replay, and absence. Absent/blank cursors bootstrap a snapshot; valid cursors,
  including zero, replay missing events and current context. Unrecoverable cursors replace
  cached history. Missing channels send a terminal deleted event, including on connection;
  JSON reads retain HTTP 404. Observer cancellation removes subscriptions.
- Ordinary updates read only context, not the latest 100 messages, artifacts or routines.
  The matched 100-message probe measured 577 bytes after a status change versus 111,260 bytes
  in the previous implementation, with zero full-snapshot reads versus one. Removing the
  probe's redundant channel ID gives the production-shaped update 526 bytes. Measurements
  cover JSON payload, not SSE framing or end-to-end latency; one context read still occurs.
- Two real browser clients received sends and reads, paged older history, disconnected and
  reconnected without losing that history. The browser retains its TanStack cache/RPC
  bootstrap and mutations; its live stream uses the same versioned SSE route as Minecraft.
  The latest pane check suppressed workspace callbacks and verified name/member updates,
  unread positioning after another client's read, draft/history preservation, and deletion on
  reconnect. Evidence: `/tmp/toy-box-pane-dogfood-evidence.json`.
- Final core changes against the existing staged baseline are 348 net production lines,
  607 test lines, 57 generated route lines and 42 documentation lines. The staged
  baseline was preserved; no provider/runtime or database schema changes were introduced.

## Mod, tooling and assets

- `bun run build` passed and packaged `mod/build/libs/toy-box-minecraft-0.1.0.jar`.
- All four Java contract suites passed: channel codec/reducer/audience/HTTP/SSE lifetime;
  agent presentation and placement (11,514 checks); artifact reader (25 checks); and
  composer draft/send ownership.
- `bun run test` passed all seven tooling tests, including cursor replay, compact updates,
  read receipts, resource watches and demo timestamp shifting.
- Procedural asset verification passed deterministic rebuilding, resource/model/skin
  validation and synchronization: 283 generated files match the mod resources.
- Java packages and resource IDs use the Toy Box namespace. Foreman models and outbound
  intents are removed. MIT license and upstream attribution remain.

## Actual Minecraft validation

Minecraft 26.3 ran on macOS with Java 25. The final visual set is under
`artifacts/shots/toy-box-native-complete`, with sixteen permanent views including monitor
and nameplate close-ups, the Team roster, task wall, waiting area and idle lounge.
Actual PNG inspection confirmed readable world text and selected tabs, full avatar portraits,
owner badges and parent labels on task cards, and the channel's own name in the HUD.
“Studio demo” is the isolated fixture channel's name; no demo label is embedded in mod code.

The nine-agent canonical fixture uses native colors/marks and fits indoors. The hall has twelve
stable desk bays. Working agents use desks, idle agents use the mint lounge, and waiting agents
use the coral seating area with public reasons/countdowns. A pending request takes podium
priority. Replying through the actual composer cleared the request and moved the lead into the
waiting area. The fixture post and read acknowledgement used detail/context events while the
received full-snapshot count remained one. Demo timestamps are relative to server startup.

Against the real Toy Box server, the native client loaded the channel and its two agents.
`Minecraft efficient stream verified @stream-no-recipient` committed exactly once as message
`4db87e6d-ce5d-43a0-80bf-fd8d74b5131a`, sequence 104. The shared read cursor advanced to 104;
subsequent browser checks advanced it to 106. The mod still had only its initial full snapshot
until an intentional reconnect, which quietly restored the current window. No provider Session
records existed for those validation agents. Evidence is recorded in `efficient-real-*.json`.
Deleting the fixture in the browser delivered terminal deletion to Minecraft, cleared its
channel and both robots, closed the request podium and switched off the active station lamps.
The owned browser fixtures were removed; the existing Toy Box server was left running.

Earlier exploratory browser validation accidentally selected an unknown mention and started
onboarding a temporary member; that exact member and Session were removed. Later real-server
checks used explicit unknown recipients without selecting the mention-creation action.

Connection-loss validation retained a stale view, disabled sending and preserved an unsent
draft. Restoring the server did not automatically post that draft. File-reader contracts cover
live edits, deletion, a stalled response body and missing watch heartbeats. Actual game captures
verify Markdown rendering; these checks do not establish multiplayer or other-OS support.

## Shared Channel domain refinement · 2026-10-04

The Java client consumes sequence-based attention, `resumed` after cached replay, and one
attachment record per resource. `bun run fixture` now generates 25 conformance transitions
from real database writes and checks SQL metadata against the TypeScript reducer after each
transaction. Java consumes those same expected states. The build and all seven tooling tests pass.

Two browser sessions and Minecraft observed a temporary channel on the real Toy Box dev server.
Reading preserved its request; a browser reply cleared it in all clients. Member creation,
profile edits, removal, rename, shared reads, and native posting propagated live. Profile edits
caused no catalog refetch in the observing browser. The native post appeared exactly once.
Both attachment forms displayed; Minecraft reconnected successfully, and deletion cleared its
world state and the browser panes. The temporary channel, member session, browsers, and game
were cleaned up; the pre-existing dev server was left running.

Browser replay from revision 117 delivered message 118, read 119, message 120, then `resumed`.
Holding that final event kept `seenThrough` at 111 while message 112 remained unread. Releasing
it established the New divider before message 112; a forced disconnect resumed from revision 121
without another snapshot or moving that divider. Screenshots and server observations are in
`/tmp/toybox-domain-dogfood-20261004`; the browser divider is captured in Toy Box's
`.playwright-cli/page-2026-10-04T16-03-44-617Z.yml`.

Changing modules during dogfooding caused a transient Vite HMR StoreProvider error; fresh reloads
rendered correctly. This pass validates Channels, not a new Session or Terminal feature.

## Remaining scope

Request choices are not invented by the client. Continuous detail changes can safely defer an older history page after its bounded
retry; the next scroll trigger can try again. These checks do not claim a general latency or
throughput benchmark for the complete server.


## Cached reconnect and message-diff performance — October 4

The Java HTTP/SSE contract now verifies cursor reconnect, quiet replay, live effects after
`resumed`, and replacement snapshots when replay is unavailable. Against a real isolated Toy Box
server, 30 reconnects required one initial snapshot (previously 31 total) and retained the same
message list. The unchanged-message guard reduced status-diff allocation from ~50 KB to ~4 KB
at 100 messages, and ~4.9 MB to ~3.9 KB at 10,000. These are local microbenchmarks, not game FPS.
Java build/contracts and the seven tooling tests passed. No Minecraft game was relaunched for
these transport/model-only changes. Evidence: `/tmp/toybox-channel-performance-20261004/`.
