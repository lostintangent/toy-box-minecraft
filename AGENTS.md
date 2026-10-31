# Toy Box Minecraft

This project owns the Minecraft mod, its assets, world presentation, client configuration,
and development tooling. Toy Box owns Channels, Workers, Sessions, Providers, and durable work.

Connect through the versioned public Channel HTTP/SSE API. Keep Minecraft coordinates, skins,
desk assignment, camera state, animation, and transient speech in this project. Do not add an
agent runtime, scheduler, membership database, or provider integration here. Shared screens
show only public Channel messages/statuses/artifacts. Never infer approval from attention
acknowledgement or translate member removal into a stop/pause action.

The Java rendering mechanics and procedural asset tools originate from AgentCraft. Preserve
LICENSE and THIRD_PARTY_NOTICES.md. Use native Channel nouns throughout the client;
`client.channel` holds the immutable public model, canonical reducer and pure live changes.
Observation resumes from the applied state revision; an unavailable replay falls back to a snapshot.
Snapshot or `resumed` establishes the live connection, so catch-up never replays world moments.
There is no Foreman compatibility layer. Java uses `dev.toybox.minecraft` and resources use
`toybox_minecraft`. Fixtures must be exported from Toy Box's real snapshot reader through
`bun run fixture`; do not hand-author a competing schema.

- `mod/`: Fabric mod; Java 25, pinned Minecraft/Fabric versions in gradle.properties.
- `assets-src/`: deterministic visual assets and validation.
- `tools/`: launch/build helpers, isolated fixture server, DevBridge, and screenshot capture.
- `fixtures/`: public Channel contract examples, without private session state.

Run `bun check` and `bun run test` for the Bun tooling, `bun run build` for the mod, and the
asset validation when changing generated resources. Exercise real connection/reconnect and
message interactions after transport changes. For visual changes, inspect generated previews
and in-game screenshots. Report any validation that could not run accurately.
