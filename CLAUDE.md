# lampas2-overrides — Agent Operating Guidelines

This repo is a Fabric **client** mod for Minecraft 26.2 whose only feature is the Figura ↔ ReplayMod
avatar bridge. Read this file fully before touching anything; most of it is knowledge that cost real
time to establish and is not recoverable from the code.

## Hard rules

1. **Never add Figura or ReplayMod as a build dependency.** Every member of both is resolved by name
   at runtime through `FiguraApi` / `ReplayModApi`. This is not stylistic: ReplayMod's 26.2 build is
   produced by a source preprocessor and is published nowhere the build could resolve it from, and
   Figura here is a local work-in-progress port whose artifact moves. The build must stay resolvable
   from Minecraft and Fabric Loader alone.
2. **Never guess a Figura or ReplayMod member.** Confirm the name *and descriptor* with `javap`
   against the real jars (see *Reference jars*) before writing a mixin or a reflective lookup. A
   wrong descriptor is compile-clean and runtime-fatal.
3. **Reflective binding fails loud, mixins fail louder.** `FiguraApi.bind()` / `ReplayModApi.bind()`
   return the name of the first missing member and the bridge disables itself with that name in the
   log. The mixin config uses `defaultRequire: 1` and the animation-clock redirect requires all four
   of its call sites, so an upstream change stops the game at startup naming the target. Do not
   soften either into a silent fallback — a bridge that half-works produces replays with no avatars
   in them and no way to tell.
4. **Do not claim rendering behaviour is verified.** See *Verifying changes*. The dev client cannot
   show you what an avatar looks like.

## Reference jars

| What | Where |
|---|---|
| Figura 0.1.6+26.2 | `../figura-port/fabric/build/libs/figura-0.1.6+26.2.jar` — builds locally |
| ReplayMod 26.2-2.6.27 | `C:\Users\markj\AppData\Roaming\PrismLauncher\instances\26.2\minecraft\mods\replaymod-26.2-2.6.27.jar` |
| Figura sources | `../figura-port/common/src/main/java/org/figuramc/figura/` |
| ReplayMod sources | `../ReplayMod/src/main/java/com/replaymod/` — preprocessed, read `//#if MC>=…` blocks carefully |
| Minecraft 26.2 sources | `../figura-port/.mcsources/` — decompiled, authoritative |

> **ReplayMod cannot be built from its repo on this machine.** `libs/ReplayStudio` uses
> `xyz.wagyourtail.jvmdowngrader` 0.7.2, whose `ShadeTransform` fails to configure under a Java 25
> Gradle daemon. Use the Prism jar above; it serves for both `javap` and `run/mods/`.

Minecraft 26.2 ships deobfuscated — no intermediary, no refmap, no remapping. `remap = false` on the
Figura/ReplayMod mixins is documentation rather than necessity, but keep it.

## Verifying changes

Verification splits in two, and neither half substitutes for the other.

**Dev client** (`./gradlew runClient`, with the Figura and ReplayMod jars in `run/mods/`) proves
structure: that mixins apply, that recording writes into the `.mcpr`, that post-processing carries
data across. Check `run/logs/debug.log`:

```bash
grep "Mixing .* from lampas2-overrides" run/logs/debug.log   # every mixin should appear
grep -i "lampas2.*\(Critical\|error\|fail\)" run/logs/debug.log
grep "lampas2-overrides/figura-replay" run/logs/latest.log
```

A mixin only applies when its target class loads: `MarkerProcessor` and `VideoRenderer` do not load
until a recording stops or a render starts.

**The user's live Prism instance** proves rendering. Build, copy
`build/libs/lampas2-overrides-1.0.0.jar` over the one in the instance's `mods/`, and ask them to
restart and check. The dev account is offline-mode, so Figura's backend auth fails with "Invalid
session" — which makes the dev client a good *negative* control (any avatar that appears came from
the bridge) and useless for anything visual.

Zip-level and format work can be tested outside the game entirely; that is how `ReplayArchives` and
`PingLog` were verified, against real `.mcpr` files from `run/replay_recordings/`.

## Things that are true and non-obvious

- **`MarkerProcessor` runs on most recordings, not rarely.** ReplayMod writes a
  `_RM_START_CUT`/`_RM_SPLIT` marker pair whenever a recording is stopped by hand, and `apply`
  rebuilds the replay from scratch copying only metadata, markers, mod info and resource packs.
- **A single post-processing output keeps the input's filename**, because the input is moved into
  `raw/` first. Never decide "this output is the untouched original" by comparing paths — ask whether
  the file already has the entries.
- **ReplayMod's camera entity has a version-3 UUID** (`nameUUIDFromBytes("ReplayModCamera")`), and
  Figura's `checkInvalidPlayer` rejects anything that is not version 4. So the camera never gets an
  avatar fetched or rendered, for free — no special case needed.
- **`FiguraMod.getLocalPlayerUUID()` returns the camera's UUID during a replay**, which is why
  `isHost` is false for every replayed avatar, including the recorder's own.
- **`AvatarManager.setAvatar` removes the id from `FETCHED_USERS`**, so Figura would re-fetch and
  clobber the injected avatar on the next frame. `AvatarManagerMixin` cancelling `fetchBackend` is
  what stops that.
- **`Avatar#runPing` queues onto the avatar's event queue and the queued call drops itself if the
  avatar is not `loaded`.** Anything that fires pings must wait for the script.
- **`AvatarManager.getLoadedAvatar` returns null under panic mode and between levels.** Treating
  that as "this player took their avatar off" writes bogus removals into the recording; hence
  `FiguraApi.avatarsQueryable()`.
- **Video export never enters `Minecraft#runTick`**, which is why the bridge counts ticks with its
  own `Minecraft#tick` mixin rather than Fabric's lifecycle event, and drives animations from
  `VideoRenderer#updateForNextFrame`.

## Structure

```
figurareplay/
  FiguraReplayBridge      entry point, tick loop, animation clock, post-processing hooks
  AvatarRecorder          capture side, one Session per recording
  AvatarPlayback          playback side, avatar application and ping replay
  AvatarIndex, PingLog    on-disk formats
  ReplayArchives          zip-level entry copying, for post-processing carry-over
  FiguraApi, ReplayModApi members of each mod, resolved by name
  ReplayFiles             .mcpr entry read/write via ReplayStudio
  Reflection              nullable lookups; invocation failures throw BridgeException
  BridgeMixinPlugin       applies the mixins only when both mods are present
  mixin/                  each class documents what it hooks and why
```

Threading: the client thread owns everything except the recorder's serialisation executor, the
playback loader executor, `AvatarRecorder.stop` (also called from netty on `channelInactive`), and
`recordPing` (also called from Figura's websocket thread). Keep it that way, and keep `ReplayFiles`
access inside `synchronized (replayFile)` — ReplayStudio's `ZipReplayFile` uses plain `HashMap`s and
ReplayMod's own save service takes the same lock.

## Definition of done

1. `./gradlew build` passes.
2. Every new Figura/ReplayMod member confirmed against the real jar with `javap`.
3. Mixins observed applying in `run/logs/debug.log`, with no injection failures.
4. Anything visual confirmed by the user in their live instance, or explicitly reported as unverified.
5. New behaviour and any new limitation written into `README.md`.
