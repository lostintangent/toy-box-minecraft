# Workshop validation

The fixture server speaks Toy Box's public Channel API. It is isolated, starts no agents, and
never reads a real Toy Box database. Use it for repeatable visual development:

```sh
bun install
bun run demo
# In another terminal, with Java 25 available:
bun run dev --server http://127.0.0.1:3210 --channel studio-demo --dev
# After the workshop opens:
bun run capture
```

Capture connects to the existing game's local DevBridge, takes sixteen views, and writes screenshots,
a contact sheet, and a state manifest under artifacts/shots. It starts or stops no server/game.
Inspect the actual PNGs before claiming visual correctness. The bridge is enabled only for
development; screenshots use real framebuffers, not illustrated mockups.

Review the monitor and nameplate close-ups at full resolution: every letter must remain legible,
including a selected tab and the request bubble. Check the task wall headers and ownership,
the complete member portrait, and the separate working desks, waiting area and idle lounge.
The normal nine-agent fixture must fit indoors; the default hall has twelve desks.

Check the following against a real Toy Box server as well:

1. Both the browser and mod represent the same selected channel and agent identities.
2. A task-only update reaches the wall without a transcript message.
3. Public messages arrive once; reconnect does not repeat speech bubbles or notifications.
4. Sending a message from the transcript publishes it in the browser and uses normal mention routing.
5. Lead attention is visible and a reply clears the attention according to Channel semantics.
6. Removing a member removes its displayed agent; no fixed demonstration cast leaks into live state.
7. Losing the connection retains last-known information, visibly marks it stale, and disables sends.
8. Reconnecting replaces the state; deleting a channel does not leave an apparently live workshop.
9. Artifact Markdown is readable in-world, edits update while open, deleting the file clears the reader, and preview/browser actions open the appropriate destination.
10. A roster larger than six retains distinct native avatar colors/crests and safe, stable desks.
11. Opening, navigating away from, and reopening a pending send cannot post it again.
12. Waiting countdowns, reactions, system messages, routines and completed-task moments match the public snapshot.

`bun run test` verifies the fixture and screenshot utilities. `bun run build` compiles/packages
the Java mod. Asset changes also require the deterministic generator and asset validation.
