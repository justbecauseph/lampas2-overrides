# lampas2-overrides — Agent Operating Guidelines

This repo is a Fabric mod for Minecraft 26.2 holding compatibility fixes between mods that do not
know about each other: the Figura ↔ ReplayMod avatar bridge, Figura avatars in Chatting's chat
heads, Lootr item frames converted into Fast Item Frames blocks, Better Lib's Fabric ZIP filesystem
startup fix, Underground Village's stale loot data,
Additional Lanterns 1.1.2 unloaded-chunk redstone checks, Visual Workbench tag reloads under
Puzzles Lib, Name Tag Upgrade 26.2.0 mouse drag crashes, Gravestones death inscriptions and glowing outline, Jade entity nameplate suppression, and Jade ↔ Custom Name display name bridge, plus a version-gated Incendium Legacy 5.5.0 tick-function optimization. The first two
features, Visual Workbench, Name Tag Upgrade, Gravestones, Jade nameplates, and Jade Custom Name are client-only; the Lootr ↔ Fast Item Frames bridge has common server
hooks and client renderer hooks, so the mod's declared environment is `*`. Read this file fully
before touching anything; most of it is knowledge that cost real time to establish and is not
recoverable from the code.

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
| Fabric API 0.158.0+26.2 | `../lampas-server-fabric/mods/fabric-api-0.158.0+26.2.jar` |
| Better Lib 2.1.0 | `../lampas-server-fabric/mods/better_lib-fabric-26.1-2.1.0.jar` |
| Underground Village 2.1.1 | `../lampas-server-fabric/mods/underground_village-fabric-26.1-2.1.1.jar` |
| Additional Lanterns 1.1.2 | `../lampas-server-fabric/mods/additionallanterns-1.1.2-fabric-mc26.2.jar` |
| Jade 26.2.11 | `../lampas-server-fabric/mods/Jade-mc26.2-Fabric-26.2.11.jar` |
| Custom Name 0.4.4 | `../lampas-server-fabric/mods/customname-fabric-0.4.4-26.2.jar` |
| Name Tag Upgrade 26.2.0 | `run/mods/NameTagUpgrade-v26.2.0-mc26.2.x-Fabric.jar` |
| Gravestones 1.4.2 | `../lampas-server-fabric/mods/gravestones-1.4.2+26.2+A.jar` |
| Incendium Legacy 5.5.0 | `../lampas-server-fabric/mods/Incendium_Legacy_26.2_v5.5.0.jar` |
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
For Stoneholm, no parse errors should remain for `andesite_worker`, `brass_worker`,
`copper_worker`, or `cleric`; the compatibility logger should name all four repairs.

For Incendium, dedicated-server startup must log
`Enabled the version-gated Incendium 5.5.0 performance datapack`, and `/datapack list enabled` must
include the Lampas2 Incendium pack. Test the ID rollover by setting `$current.id` in `in.eid` to
`32767`, then spawning a new living mob.
After at most five ticks, the new mob and all previously initialized mobs and players must have
unique IDs in `0..32767`; no entity may retain 32768 or lose its `in.eid_*` bit tags. A version or
fingerprint mismatch must instead log that the performance datapack remains disabled.

**Live testing and deployment** are managed declaratively via `lampas-pipeline` (`../lampas-pipeline`):

1. Copy the built jar `build/libs/lampas2-overrides-1.0.0.jar` to `../lampas-pipeline/pack/custom/lampas2-overrides-1.0.0.jar`.
2. In `../lampas-pipeline`, run:
   ```bash
   bun run lampasctl resolve
   bun run lampasctl validate
   bun run lampasctl manifest
   bun run lampasctl publish
   ```
3. Deploy the server via `python deploy_pack.py` in `../lampas-server-fabric` (which ingests `lampas-pipeline/manifest/server-manifest.json`).
4. Client instances sync from `lampas-pipeline` manifests via the Lampas Launcher or `lampasctl install`.

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
- **Better Lib's bundled villager professions are disabled demos.** Its `andesite_worker.json` is
  entirely commented out and its `ModOreTrader.register()` call is commented out, but generated
  data still adds both ids to `minecraft:acquirable_job_site`. `TagLoaderMixin` removes only entries
  with those ids and Better Lib as their source after all tag resources have merged; do not replace
  the whole tag or enable the demo professions.
- **`VanillaLanternEvents.handleLanternRedstone` calls `Level#getBlockState` on every neighbor update.**
  At chunk boundaries, this causes `ServerChunkCache` to synchronously load or generate the adjacent
  unloaded chunk on the server thread. Checking `ServerChunkCache#hasChunk` before executing the
  method prevents the stall while leaving loaded-chunk lantern conversion unaffected.
- **Incendium's entity ID is a 15-bit hit-matching key shared by players and living mobs.** Its
  `entity_id/init` increments `$current.id` before assignment, so rollover must start when the
  counter is 32767, reassign all existing owners, exclude the caller during that repair, and let
  `entity_id/check` initialize the caller once after reset. Non-living entities in the `other` tag
  do not call the ID allocator.
- **The Incendium optimization is gated twice.** The initializer requires the exact 5.5.0 version
  and fingerprints the three upstream functions it replaces before registering its always-enabled
  built-in datapack. Update both gates only after auditing a new upstream jar.
- **Visual Workbench dynamically creates replacement crafting-table blocks and copies the source block's bound tags into them.**
  Puzzles Lib's `copyBoundTags` assumes a target's tags are either empty or identical. ReplayMod
  playback and repeated client configuration reloads cause client tags to be reloaded in the same
  process, leaving the generated target block with stale tags and triggering an `IllegalStateException`.
  Rebinding tags is permitted only when the target block belongs to the `visualworkbench` namespace;
  Puzzles Lib's strict behavior remains intact for all other callers/targets. Do NOT broaden the
  patch to all `BlockConversionHelper` calls.
- **`FormattableEditBox.findClickedPositionInText` permits negative mouse offsets when dragging left.**
  In Name Tag Upgrade 26.2.0, a negative offset prevents `WidthLimitedCharSink` from advancing past
  `displayPos` characters, resulting in `substring(displayPos, 0)` when `displayPos > 0`. Clamping the
  first argument of `Math.min` to `0` leaves all formatting-aware cursor logic intact without needing
  broader patches in `FormattedStringSplitter` or `WidthLimitedCharSink`.
- **`TechnicalGravestoneBlockEntityRenderer` constructs sign text dynamically while the grave body is a block model.**
  Overriding `getSignText(TechnicalGravestoneBlockEntity)` to return a blank `SignText` suppresses player name,
  death date, and death time for all death graves without touching aesthetic gravestones (which use `AestheticGravestoneBlockEntityRenderer`).
- **Grave ownership is extracted into the render state during `extractRenderState`.**
  This adheres to Minecraft 26.2's render pipeline separation: the local player UUID is matched against `GraveOwner#getUuid()`
  during state extraction, allowing `AbstractGravestoneBlockEntityRenderer#submit` to submit a block model outline pass
  via `RenderTypes.outline(TextureAtlas.LOCATION_BLOCKS)` without touching entity/level state during drawing.
- **`EntityRenderer#submitNameDisplay` is the single funnel for in-world name tags and scoreboard below-name text.**
  `AvatarRenderer` (players) and all standard `EntityRenderer` subclasses delegate to the five-argument
  `submitNameDisplay(EntityRenderState, PoseStack, SubmitNodeCollector, CameraRenderState, int offset)` method.
  Wrapping and suppressing `SubmitNodeCollector#submitNameTag` within this method hides floating nameplates and
  scoreboard text without early-aborting `submitNameDisplay`, preserving Figura's `NameplateRenderContext`
  lifecycle (`begin` at HEAD, `end` at RETURN) and leaving render-state extraction and `TextDisplayRenderer`
  text geometry intact.
- **Custom Name's above-player display creates fake `TextDisplay` passenger entities.**
  When `display_above_player.enabled` is `true`, Custom Name spawns two clientbound `TextDisplay` entities
  riding the player. Because these render via `DisplayRenderer.TextDisplayRenderer`, they bypass
  `EntityRenderer#submitNameDisplay`. To let Jade own above-player presentation, `display_above_player.enabled`
  must remain `false` in `eclipsescustomname.json`.
- **Client `Player#getDisplayName()` does not contain Custom Name's formatted name.**
  Custom Name syncs formatted player display names over `ClientboundPlayerInfoUpdatePacket.UPDATE_DISPLAY_NAME`,
  populating `PlayerInfo#getTabListDisplayName()`. `ObjectNameProviderMixin` redirects `Entity#getDisplayName()`
  inside `ObjectNameProvider#getEntityName` to `JadeCustomNameResolver` so Jade shows the synced player display name.

## Structure

```
compat/
  Reflection              nullable lookups; invocation failures throw BridgeException
chatheads/
  ChatHeadAvatars         entry point; arms on Chatting's head lookup, draws on the face hooks
  ChatPlayerResolver      resolves multi-word and custom TAB display names to PlayerInfo
  FiguraPortraits         Figura's portrait members, resolved by name
  mixin/                  ChatHeads (detect, arm/disarm) and PlayerFaceExtractor (both draw paths)
jadenameplates/
  JadeNameplatesMixinPlugin applies when Jade is present
  mixin/                    cancels EntityRenderer#submitNameDisplay
jadecustomname/
  JadeCustomNameMixinPlugin applies when Jade + Custom Name exist
  JadeCustomNameResolver    resolves PlayerInfo tabListDisplayName with fallback
  mixin/                    redirects Entity#getDisplayName in ObjectNameProvider
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
  mixin/                     fixes Better Lib's ZIP open and stale job-site tag entries
stoneholm/
  StoneholmMixinPlugin      applies loot repairs only when Underground Village is present
  mixin/                     suppresses absent-Create tables and upgrades legacy potion functions
additionallanterns/
  AdditionalLanternsMixinPlugin applies only to the affected Additional Lanterns 1.1.2 release
  mixin/                     skips handleLanternRedstone on unloaded target chunks
visualworkbench/
  VisualWorkbenchMixinPlugin applies only when Visual Workbench + Puzzles Lib exist
  VisualWorkbenchTagFix     policy check and tag rebinding helper
  mixin/                    intercepts copyBoundTags for visualworkbench:* blocks
nametagupgrade/
  NameTagUpgradeMixinPlugin applies only to the affected Name Tag Upgrade 26.2.0 release
  mixin/                    clamps mouse offset in FormattableEditBox#findClickedPositionInText
gravestones/
  GravestonesMixinPlugin    applies whenever Gravestones is present
  OwnedGraveRenderState     duck interface carrying ownership and block state across render phases
  mixin/                    suppresses death grave text and submits model glowing outline
incendium/
  IncendiumOptimization     fingerprints Incendium and registers the built-in pack
  IncendiumCompatibility    exact version and upstream-function fingerprint gate
resourcepacks/incendium_5_5_0_optimizations/
  data/incendium/function/  clock and entity-ID overrides for verified Incendium 5.5.0 only
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
