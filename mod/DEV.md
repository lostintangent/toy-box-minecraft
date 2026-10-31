# Developing Toy Box Minecraft

Java 25, Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Loom 1.18.2 are
pinned in Gradle. Minecraft 26.x is unobfuscated; use the local game sources for API questions.

```sh
bun install
bun run demo
# Another terminal:
bun run dev --server http://127.0.0.1:3210 --channel studio-demo --dev
bun run state
bun run capture
bun run build
bun check
bun run test
```

The first Gradle run downloads the pinned game and libraries. `JAVA_HOME` selects a Java 25
installation; the launcher also tries the installed macOS JDK locations. The game automatically
opens the separate `Toy Box` development save. Pre-refactor development saves keep
their old namespaces and are not migrated into this one.

## Native client

The Java namespace is `dev.toybox.minecraft`; the mod/resource ID is `toybox_minecraft`.
`ClientFeatures` composes `ChannelClient` and the independent channel views listed in
[FEATURES.md](FEATURES.md). `ChannelStore` owns the immutable `ChannelModel.State` and connection status.
`ChannelReducer` applies the canonical wire algebra. `ChannelChanges` compares immutable
snapshots to produce presentation moments, with reconnect suppression at the observer boundary.

Read channel state during Minecraft extraction/client ticks, not while submitting render nodes.
`StationRenderer` captures common block bindings and panel geometry. `WorldUi` supplies opaque
plates and depth-aware text for world surfaces. `ChannelScreen` supplies native page navigation.
The composer owns a draft/send lifetime per channel so changing screens cannot duplicate a send.

Agents are local client entities; they do not host sessions or execute provider calls.
`AvatarArt` rasterizes each member's SVG crest and color into a generated robot skin and portrait.
`RosterLayout` retains surviving desk assignments; `WorkshopDesks` verifies clear pod footprints
before asking the integrated server to build. World changes use the integrated server’s
`execute` boundary.

Artifacts use the existing file HTTP read/watch routes. Each open reader owns its selection,
request bodies, watch and generation. Channel deletion or selection change closes old ownership.
Only public Channel state is available to these views.

## Development controls

| Variable | Meaning |
| --- | --- |
| `TOY_BOX_SERVER_URL` | Server base URL, including optional proxy prefix |
| `TOY_BOX_CHANNEL_ID` | Selected channel |
| `TOY_BOX_DEV` | Enable loopback DevBridge; off by default |
| `TOY_BOX_DEV_PORT` | DevBridge port; default 7879 |
| `TOY_BOX_MUTE` | Force muted game; default 1 |
| `TOY_BOX_FOCUS` | Allow window focus; default 0 |
| `TOY_BOX_AUTOWORLD` | Automatically open workshop; default 1 |
| `TOY_BOX_SHOTS_DIR` | Screenshot destination override |
| `TOY_BOX_DEV_TEST` | Add `dev.sdlKeys`, which types through SDL's event loop; off by default |

The options template disables VSync to avoid a macOS development startup stall, and disables
pause-on-focus-loss. Existing user options are not overwritten. Runtime configuration lives in
`config/toy-box-minecraft.json`; environment values take precedence.

## Inspection and capture

The opt-in bridge listens only on 127.0.0.1 and is intended for local development. Use the
provided CLI; `help` reports the commands actually registered by the current client.

| Tool | Responsibility |
| --- | --- |
| `launch.ts`, `gradle.ts` | Select Java 25, pass connection settings, invoke Fabric Loom |
| `export-fixture.ts`, `fixtures/` | Export the real Toy Box snapshot and reducer trace |
| `demo-server.ts` | Serve those fixtures through the public HTTP/SSE contract without running agents |
| `devcli.ts`, `lib/devclient.ts` | Control an already-running game through the opt-in bridge |
| `capture.ts`, `lib/contactsheet.ts` | Capture the channel views and assemble their inspection sheet |

The bridge uses Minecraft's `submit`, command dispatcher, screen handlers and screenshot API.
CLI `time` and `weather` are shortcuts for ordinary `dev.command` requests. The remaining
frame/chunk fences and FOV pin make captures deterministic; they are not a second game loop.
Channel/agent/anchor inspection reports Toy Box-specific state that Minecraft cannot name.
The liveness probe and bounded shutdown remain useful when the render thread stops responding.

[Fabric client GameTests](https://docs.fabricmc.net/develop/automatic-testing) provide UI and
screenshot checks in a dedicated test run. They overlap with batch capture, but do not replace
this interactive bridge's workflow. No second harness is added here. General JVM debugging
and profiling should use the normal Minecraft/Fabric development tools.

The bridge accepts JSON text frames. There is no recording/timeline engine, unused camera
accessor, binary-frame compatibility path, custom client-thread future wrapper, Windows-native
focus probe or unused screenshot-statistics payload. Diagnostic Java still ships in the jar;
the socket is disabled unless `TOY_BOX_DEV=1`.

```sh
bun tools/devcli.ts help
bun tools/devcli.ts state
bun tools/devcli.ts raw '{"type":"dev.channel"}'
bun tools/devcli.ts raw '{"type":"dev.channel","reconnect":true}'
bun tools/devcli.ts screen transcript
bun tools/devcli.ts screen tasks
bun tools/devcli.ts screen request
bun tools/devcli.ts screen artifacts
bun tools/devcli.ts screen routines
bun tools/devcli.ts raw '{"type":"dev.agents","settle":true}'
bun tools/devcli.ts shot workshop
bun tools/devcli.ts quit
```

`dev.transcript {text, submit}` opens the real composer and optionally sends a real Channel
message. On a live Toy Box server this may wake agents under the ordinary delivery policy.
Use the isolated demo for routine presentation checks. `dev.artifacts` reports reader state;
`dev.agents {agent?, settle?}` exposes each agent's physical presentation, and `dev.state` its
totals. No fake provider protocol, permission injection, goal parser or synthetic orchestration
timeline ships in the client.

`dev.mouse {x,y,scroll?}` clicks or scrolls the open screen in GUI coordinates (screenshot pixels
divided by `window.guiScale`). This exercises screen handlers without moving the desktop mouse.
`dev.agents.fx {agent,fx:"reaction",text:"love"}` previews the ordinary reaction animation.

The in-game `/toybox_minecraft anchors` command inspects published world anchors. Workshop
construction commands are available under `/toybox_minecraft hq` and use the edit-preserving
plan/apply machinery. Explicit overwriting options are for disposable QA worlds only.

## Contract checks

`bun run build` compiles both Fabric source sets, packages the jar and runs assertion-based
contract suites for native codec/reducer/diffs/audience/transport, roster/avatar geometry,
artifact reader lifecycle and composer ownership. `bun run test` exercises the isolated fixture
server, resource watches and capture utilities. `bun run fixture` exports canonical public state
from the sibling Toy Box repository using its real isolated database and snapshot reader.

For assets, create a virtual environment with `assets-src/requirements.txt`, then run
`assets-src/build.py --verify`, `assets-src/sync.py`, and `assets-src/sync.py --check`.
Inspect actual Minecraft screenshots after visual changes. Do not use a successful compile as
evidence that rendering is correct.

For local Minecraft sources, run `./gradlew mcSources` from `mod/`, then search `build/mcsrc`.
