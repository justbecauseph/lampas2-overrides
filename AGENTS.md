# lampas2-overrides — Agent Operating Guidelines

This repo is a Fabric mod for Minecraft 26.2 holding compatibility fixes between mods that do not
know about each other: the Figura ↔ ReplayMod avatar bridge, Figura avatars in Chatting's chat
heads, Lootr item frames converted into Fast Item Frames blocks, and Better Lib's Fabric ZIP
filesystem startup fix. The first two features are
client-only; the Lootr ↔ Fast Item Frames bridge has common server hooks and client renderer hooks,
so the mod's declared environment is `*`. Read this file fully before touching anything; most of it
is knowledge that cost real time to establish and is not recoverable from the code.

Each feature lives in its own package with its own mixin config and gate plugin, and binds only the
members it needs. Keep it that way — one mod being absent or having moved a member must never
disable an unrelated feature.

## Hard rules

1. **Never add Figura or ReplayMod as a build dependency.** Every member of both is resolved by name
   at runtime through `FiguraApi` / `ReplayModApi`. This is not stylistic: ReplayMod's 26.2 build is
   produced by a source preprocessor and is published nowhere the build could resolve it from, and
   Figura here is a local work-in-progress port whose artifact moves. Lootr, Fast Item Frames,
   Puzzles Lib and Fabric API are different: they are intentional `compileOnly` dependencies from
   Modrinth/Fabric Maven. Do not bundle them or turn them into mandatory runtime dependencies.
2. **Never guess an external-mod member or conversion contract.** Confirm Figura and ReplayMod
   names and descriptors with `javap`, and compile Lootr/Fast Item Frames changes against the pinned
   artifacts in `gradle.properties`. Before release, compare against the jars actually deployed to
   the server and Prism instance. A wrong mixin descriptor is compile-clean and runtime-fatal.
3. **Preserve Lootr identity during entity-to-block conversion.** A converted frame must inherit
   the source entity UUID, reference inventory and opened state. It must remain an
   `ILootrBlockEntity`, and its `ILootrType` and wrapper service registrations must remain present;
   otherwise refreshes, per-player inventories or Lootr packets stop resolving it.
4. **Keep common and client hooks separated.** Lootr conversion, state, interaction and protection
   belong in `src/main`; rendering and client attack handling belong in `src/client`. There is no
   common mod initializer—the gated mixin config is what installs the server hooks.
5. **Reflective binding fails loud, mixins fail louder.** `FiguraApi.bind()` / `ReplayModApi.bind()`
   return the name of the first missing member and the bridge disables itself with that name in the
   log. The mixin configs use `defaultRequire: 1` and the animation-clock redirect requires all four
   of its call sites, so an upstream change stops the game at startup naming the target. Do not
   soften either into a silent fallback—a bridge that half-works produces corrupt or misleading
   behavior with no way to tell.
6. **Do not claim Figura rendering behaviour is verified.** See *Verifying changes*. The dev client
   cannot show you what an authenticated avatar looks like. Lootr/Fast Item Frames rendering can be
   verified in the live Fabric server and Prism instance.

## Reference jars

| What | Where |
|---|---|
| Figura 0.1.6+26.2 | `../figura-port/fabric/build/libs/figura-0.1.6+26.2.jar` — builds locally |
| ReplayMod 26.2-2.6.27 | `C:\Users\markj\AppData\Roaming\PrismLauncher\instances\26.2\minecraft\mods\replaymod-26.2-2.6.27.jar` |
| Chatting 3.1.0+26.2 | same `mods/` folder; sources at `../Chatting` (stonecutter — the `//? if` blocks mean the source you read may not be the 26.2 build, so trust the jar) |
| Lootr 1.24.39.121 | `../lampas-server-fabric/mods/lootr-fabric-26.2-1.24.39.121.jar`; sources at `../Lootr` |
| Fast Item Frames 26.2.0 | `../lampas-server-fabric/mods/FastItemFrames-v26.2.0-mc26.2.x-Fabric.jar`; sources at `../fast-item-frames` |
| Puzzles Lib 26.2.3 | `../lampas-server-fabric/mods/PuzzlesLib-v26.2.3-mc26.2.x-Fabric.jar` |
| Fabric API 0.157.0+26.2 | `../lampas-server-fabric/mods/fabric-api-0.157.0+26.2.jar` |
| Better Lib 2.1.0 | `../lampas-server-fabric/mods/better_lib-fabric-26.1-2.1.0.jar` |
| Figura sources | `../figura-port/common/src/main/java/org/figuramc/figura/` |
| ReplayMod sources | `../ReplayMod/src/main/java/com/replaymod/` — preprocessed, read `//#if MC>=…` blocks carefully |
| Minecraft 26.2 sources | `../figura-port/.mcsources/` — decompiled, authoritative |

> **ReplayMod cannot be built from its repo on this machine.** `libs/ReplayStudio` uses
> `xyz.wagyourtail.jvmdowngrader` 0.7.2, whose `ShadeTransform` fails to configure under a Java 25
> Gradle daemon. Use the Prism jar above; it serves for both `javap` and `run/mods/`.

Minecraft 26.2 ships deobfuscated—no intermediary, no refmap, no remapping. `remap = false` on
external-mod mixins is documentation rather than necessity, but keep it.

## Verifying changes

Verification splits in two, and neither half substitutes for the other.

**Dev client** (`./gradlew runClient`, with the relevant optional-mod jars in `run/mods/`) proves
structure: that mixins apply, that Lootr frames convert without injection failures, that recording
writes into the `.mcpr`, and that post-processing carries data across. Check
`run/logs/debug.log`:

```bash
grep "Mixing .* from lampas2-overrides" run/logs/debug.log   # every mixin should appear
grep -i "lampas2.*\(Critical\|error\|fail\)" run/logs/debug.log
grep "lampas2-overrides/figura-replay" run/logs/latest.log
```

A mixin only applies when its target class loads: `MarkerProcessor` and `VideoRenderer` do not load
until a recording stops or a render starts.

For the Lootr/Fast Item Frames bridge, all five mixins should appear when a world loads:
`ItemFrameBlockEntityMixin`, `ItemFrameBlockMixin`, `ItemFrameHandlerMixin`,
`ClientEventHandlerMixin`, and `ItemFrameBlockRendererMixin`. The first three must also apply on a
dedicated server. The entity selector below finds only frames that have not converted yet, because
successful conversion removes the entity and leaves a block entity:

```mcfunction
/data get entity @e[type=lootr:item_frame,sort=nearest,limit=1] Pos
```

For Better Lib, dedicated-server startup must pass its `registerJsonVillagers` call without
`FileSystemAlreadyExistsException`, and `JsonVillagerLoaderMixin` must appear in `debug.log`.

**The user's live Prism instance** proves rendering. Build, copy
`build/libs/lampas2-overrides-1.0.0.jar` over the one in the instance's `mods/`, and ask them to
restart and check. The dev account is offline-mode, so Figura's backend auth fails with "Invalid
session" — which makes the dev client a good *negative* control (any avatar that appears came from
the bridge) and useless for anything visual.

The Lootr bridge must be deployed to both:

- `C:\Users\markj\source\repos\lampas-server-fabric\mods\`
- `C:\Users\markj\AppData\Roaming\PrismLauncher\instances\26.2-fabric\minecraft\mods\`

Restart both processes after replacing the jar. Live verification on 2026-08-13 confirmed that a
`lootr:item_frame` converts to a Fast Item Frames block, retains per-player Lootr behavior, and can
be repopulated by Lootr refresh after looting. The frame may appear absent/empty to the player who
looted it until it is refreshed; its Lootr identity and properties remain intact. Treat that as the
accepted current visual behavior, not as evidence that the block or Lootr data was deleted.

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
- **Chatting draws chat heads two ways.** Its default path calls vanilla
  `PlayerFaceExtractor#extractRenderState` (every shorter overload funnels into the eight-argument
  one); its *improved heads* option calls `chatting$draw`, which it adds to that same class and
  which blits the face itself without touching the vanilla extractor. Hooking one covers half the
  users.
- **Injecting into another mod's `@Unique` method needs a higher `priority`**, since the method only
  exists once that mod's mixin has been applied — Mixin applies higher priority values later. Pair
  it with `require = 0` when the feature is cosmetic: losing a path beats refusing to start.
- **Figura already replaces skin faces with avatar faces** in the tab list and permissions screen
  via `Avatar#renderPortrait`, using the trick of zeroing the vanilla face's size. Reuse that entry
  point rather than rendering an avatar head by hand, and pass a model scale of twice the face size
  as its tab list does.
- **Fast Item Frames discovers convertible entity types through its entity-type tag.** The bridge
  adds `lootr:item_frame` to `data/fastitemframes/tags/entity_type/item_frames.json`; conversion is
  then performed by Fast Item Frames itself rather than by a duplicate scanner.
- **Conversion must capture Lootr state before the source entity disappears.** The
  `ItemFrameHandlerMixin` tail hook runs after Fast Item Frames places its block entity but while the
  original `LootrItemFrame` argument is still available, allowing the UUID, reference inventory and
  opened state to be transferred.
- **The physical object is a Fast Item Frames block entity, but its logical inventory remains
  Lootr-owned per player.** Never use Fast Item Frames' shared `getItem()` as the authoritative loot
  after conversion. `LootrFastItemFrameActions` resolves the player's `ILootrInventory`, clears only
  that personal slot, fires Lootr triggers and sends Lootr updates.
- **Lootr resolves the converted block through Java services.** The `ILootrType` and
  `ILootrBlockEntityWrapper` files under `META-INF/services/` are runtime behavior, not packaging
  debris. Removing or shading them breaks refresh and packet lookup even though conversion still
  appears to work.
- **An emptied frame is not a deleted frame.** After a player takes their item, client rendering can
  show the converted frame as absent or empty. A Lootr refresh repopulates it, and its UUID and
  Lootr properties remain present. Confirm block-entity state before diagnosing this as data loss.
- **Better Lib borrows Fabric Loader's open ZIP filesystem.** The redirect returns a close-shield
  only after `FileSystems.newFileSystem` reports that the filesystem already exists. The shield's
  `close()` must remain a no-op; returning the shared filesystem directly lets Better Lib's
  try-with-resources block close a filesystem owned by the loader.

## Structure

```
compat/
  Reflection              nullable lookups; invocation failures throw BridgeException
chatheads/
  ChatHeadAvatars         entry point; arms on Chatting's head lookup, draws on the face hooks
  FiguraPortraits         Figura's portrait members, resolved by name
  mixin/                  ChatHeads (arm/disarm) and PlayerFaceExtractor (both draw paths)
figurareplay/
  FiguraReplayBridge      entry point, tick loop, animation clock, post-processing hooks
  AvatarRecorder          capture side, one Session per recording
  AvatarPlayback          playback side, avatar application and ping replay
  AvatarIndex, PingLog    on-disk formats
  ReplayArchives          zip-level entry copying, for post-processing carry-over
  FiguraApi, ReplayModApi members of each mod, resolved by name
  ReplayFiles             .mcpr entry read/write via ReplayStudio
  BridgeMixinPlugin       applies the mixins only when both mods are present
  mixin/                  each class documents what it hooks and why
lootrfastframes/
  LootrFastFramesMixinPlugin  applies common/client mixins only when Lootr + Fast Item Frames exist
  LootrFastItemFrame          state bridge mixed into Fast Item Frames' block entity
  FixedLootrInstance          preserves the source Lootr entity UUID
  LootrFastItemFrameActions   per-player loot, protection, rotation and trigger behavior
  LootrFastItemFrameType      Lootr data type registered through ServiceLoader
  LootrFastItemFrameWrapper   resolves marked Fast Item Frames block entities for Lootr
  mixin/                      conversion, state persistence, interaction and client rendering hooks
betterlib/
  BetterLibMixinPlugin       applies the startup fix only when Better Lib is present
  BorrowedFileSystem         close-shield for Fabric Loader's shared mod-jar filesystem
  mixin/                     redirects Better Lib's conflicting ZIP filesystem open
```

Threading: the client thread owns everything except the recorder's serialisation executor, the
playback loader executor, `AvatarRecorder.stop` (also called from netty on `channelInactive`), and
`recordPing` (also called from Figura's websocket thread). Keep it that way, and keep `ReplayFiles`
access inside `synchronized (replayFile)` — ReplayStudio's `ZipReplayFile` uses plain `HashMap`s and
ReplayMod's own save service takes the same lock.

The Lootr/Fast Item Frames bridge introduces no background executor. Common state and interaction
remain on the logical server thread; its render-state and attack hooks remain on the client thread.

## Definition of done

1. `./gradlew build` passes.
2. Every new Figura/ReplayMod member confirmed against the real jar with `javap`; every direct
   Lootr/Fast Item Frames/Puzzles Lib API use compiled against the pinned artifact and checked
   against the deployed jar versions.
3. Relevant mixins observed applying in `run/logs/debug.log`, with no injection failures. For common
   features, also verify the dedicated server starts with its common mixins applied.
4. Anything visual confirmed by the user in their live instance, or explicitly reported as
   unverified.
5. New behaviour and any new limitation written into `README.md` and, when it changes agent-facing
   architecture or verification knowledge, this file.
