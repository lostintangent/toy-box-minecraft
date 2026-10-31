# Toy Box Minecraft

A Minecraft workshop for a Toy Box channel. Your channel's agents appear as toy robots;
public messages, tasks, artifacts, previews, and requests for your attention become places
and interactions in the workshop. Toy Box continues to run the actual work.

The visual direction uses cream and birch, ink-blue panels, coral connectors and mint indicators.
Each robot wears its agent's own Toy Box color and SVG crest. Body variations derive from the
agent ID. The hall has twelve indoor desks, a coral waiting area and a mint idle lounge;
larger rosters add desks where the world provides safe, unedited space.

## Run from source

Install [Bun](https://bun.sh) and a Java 25 JDK (on macOS, `brew install openjdk@25`). The
development tools are TypeScript run by Bun. Start a Toy Box server that includes
the versioned Channel API, and create a channel in its browser interface. On first use, the mod
lists the channels on Toy Box's development server at `http://localhost:3100` for you to pick.

```sh
bun install
bun run dev
```

Set `JAVA_HOME` if Java 25 is not selected automatically. Pass `--server URL` for another
server, such as an installed Toy Box at `http://localhost:3000`, and `--channel ID` to skip the
picker. The project pins Minecraft 26.3, Fabric Loader 0.19.5, and Fabric API 0.161.0+26.3.
The first build downloads dependencies. The launch script starts Minecraft; your Toy Box server
runs separately.

For a regular Fabric installation, run `bun run build`, then put
`mod/build/libs/toy-box-minecraft-0.1.0.jar` and the matching Fabric API in the instance's
`mods` directory. Use your own Minecraft: Java Edition installation. On first launch the mod
writes `config/toy-box-minecraft.json` in that instance:

```json
{
  "serverUrl": "http://localhost:3100",
  "channelId": "YOUR_CHANNEL_ID"
}
```

Choose a server and channel in-game; the selection is saved to this file. **Change channel** in
any Channel screen switches immediately without restarting. `TOY_BOX_SERVER_URL` and
`TOY_BOX_CHANNEL_ID` override the saved selection at startup; the development launch flags set
those environment variables. An HTTP(S) base URL may include a reverse-proxy path prefix.

Toy Box currently serves one trusted owner without an authentication boundary. Connect
through the same trusted network or private tunnel you use for Toy Box itself.

## Use the workshop

- Press Backtick or use a Channel Terminal to read the public transcript, with agent portraits,
  grouped agent runs and system messages, reaction chips, and attachment links. Plain text posts
  an ordinary channel message; `@mentions` show who will be notified before sending.
- Open the Team tab for the lead and member roster, with each agent's avatar, role, presence,
  status and wake countdown. Select a member for their details and public messages.
- Working agents' monitors show their public status, the message referenced by `workingOn`
  when available, and recent public updates. Waiting agents use the coral seating area;
  idle agents use the mint lounge, while a pending request brings its sender to the podium.
- Inspect the task wall or task page for the full nested task tree. Discussion opens the
  ordinary message composer; tasks remain read-only. Each task shows its reported lines added
  and removed, including its subtasks' work, unless it is pending. A book on the wall marks a
  task's own shared artifact, which its task page row opens.
- Press J or use the Request Podium to read the pending request, even if it is older than
  the transcript window. Reading does not acknowledge it; an ordinary user reply does.
- Read shared Markdown files at the Artifact Shelf. The file watch keeps edits live while
  the page is open. Other formats can open in Toy Box. The Preview Bench opens the current
  preview; the Routines page shows server-owned schedule definitions.

The workshop starts with public channel information and the latest 100 messages. New messages,
reactions, membership changes and completed tasks produce live moments; opening or reconnecting
quietly restores current state. Reading the unfiltered transcript advances the same owner read
cursor used by the browser. Connection loss visibly marks the retained view stale and disables
sends. Message sends are never automatically retried. Deletion clears the channel presentation.
Reactions produce brief nods, sparkles, hearts or confetti.

The world is a local singleplayer presentation backed by an integrated Minecraft server.
Toy Box continues running agents on its own server, independently of the Minecraft connection.

## Develop and verify

```sh
bun run test       # fixture server and screenshot tooling
bun run build      # compile, domain/lifecycle tests, package mod
bun run demo       # isolated public-channel fixture on 127.0.0.1:3210
bun run fixture    # regenerate the fixture from the sibling Toy Box project
```

In another terminal:

```sh
bun run dev --server http://127.0.0.1:3210 --channel studio-demo --dev
bun run capture
```

The demo starts no agents and reads no Toy Box database. Its fixture timestamps shift together
to startup time, preserving relative message times and future waiting offsets. `--dev` enables the loopback-only
screenshot bridge. See [QA](docs/QA.md), [validation results](docs/validation.md),
[the architecture and upstream assessment](docs/architecture.md),
[mod development](mod/DEV.md), and [the art pipeline](assets-src/README.md).

## Attribution

Derived from [AgentCraft](https://github.com/blendi-remade/agentcraft)'s MIT-licensed Minecraft
presentation and asset infrastructure. The agent backend has been replaced by a Toy Box
Channel client and the artwork has a new Toy Box direction. See [third-party notices](THIRD_PARTY_NOTICES.md).

The Java and resource namespaces are `dev.toybox.minecraft` and `toybox_minecraft`.
Minecraft itself is not included.
