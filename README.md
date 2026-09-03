# lampas2-overrides

A Fabric mod for Minecraft 26.2 holding compatibility fixes between mods that do not know
about each other. Each feature is gated on the mods it bridges and is inert without them.

| Feature | Needs | What it does |
|---|---|---|
| [Figura in ReplayMod](#figura--replaymod) | Figura + ReplayMod | Makes Figura avatars survive into recordings, playback and video exports |
| [Figura chat heads](#figura-chat-heads) | Figura + Chatting | Draws the Figura avatar's face in Chatting's chat heads instead of the vanilla skin |
| [Lootr fast item frames](#lootr--fast-item-frames) | Lootr + Fast Item Frames | Converts Lootr item-frame entities into blocks while preserving per-player loot |
| [Better Lib startup](#better-lib-startup) | Better Lib | Prevents Better Lib from reopening Fabric Loader's shared mod-jar filesystem |
| [Underground Village fixes](#underground-village-fixes) | Underground Village 2.1.1 | Repairs obsolete/absent-mod loot tables and worldgen structure/pool data defects |
| [Additional Lanterns chunk loading](#additional-lanterns-chunk-loading) | Additional Lanterns 1.1.2 | Prevents redstone neighbor checks from synchronously loading unloaded chunks |
| [Mob Filter worldgen safety and dimension context](#mob-filter-worldgen-safety-and-dimension-context) | Mob Filter | Prevents C2ME worldgen deadlock on rejected mobs and provides dimension context for worldgen rules |
| [Visual Workbench tag rebinding](#visual-workbench-tag-rebinding) | Visual Workbench + Puzzles Lib | Prevents replay loading and tag reload crashes from stale Visual Workbench tags |
| [Incendium tick optimization](#incendium-tick-optimization) | Incendium Legacy 5.5.0, 5.5.1 | Removes a redundant 20 Hz entity-ID scan and throttles living-mob initialization |
| [Gravestones death inscription and glow](#gravestones-death-inscription-and-glow) | Gravestones | Suppresses technical death grave text and renders a glowing outline only on your own graves |
| [Jade nameplates and Custom Name](#jade-nameplates-and-custom-name) | Jade (+ Custom Name) | Suppresses vanilla in-world entity/player nameplates and syncs Custom Name player display names into Jade |
| [Custom Name multi-word names](#custom-name-multi-word-names) | Custom Name 0.4.4-26.2 | Permits spaces in nickname, prefix, and suffix commands for non-operators |
| [Virtual Resource & Datapack Patches](#virtual-resource--datapack-patches) | MVS, MNS, Formations Overworld, Grim Kingdoms, Pyrite, Easter's Delight, Better Lib | Transparently repairs malformed `pack.mcmeta` formats and POI tags at runtime |

## Mob Filter worldgen safety and dimension context

Mob Filter `0.28.0+26.2` has two major defects during world generation:

1. **Unsafe `Entity.remove` worldgen discard (deadlock)**:
   When rejecting disallowed mobs from `WorldGenRegion#addFreshEntity`, Mob Filter calls
   `Entity.remove(DISCARDED)` before returning `false`. During threaded C2ME worldgen, that
   removal can enter `LivingEntity` dismount collision resolution, ask Lithium for a chunk, and
   synchronously join the server thread while the same chunk is still waiting for its C2ME
   `FEATURES` stage:

   ```text
   Mob Filter rejection
     -> Entity.remove
     -> dismount collision search
     -> Lithium getChunk
     -> server thread
     -> same C2ME generation future
     -> deadlock
   ```

   Lampas2 Overrides suppresses only that `Entity.remove(...)` call inside Mob Filter's
   `WorldGenRegion_addFreshEntity` callback. Mob Filter still performs its complete rule evaluation
   and its existing `CallbackInfoReturnable#setReturnValue(false)`, so rejected worldgen mobs remain
   absent. The `ServerLevel_addFreshEntity` callback is deliberately untouched; normal server-thread
   rejections retain their ordinary removal behavior.

2. **Missing worldgen dimension context**:
   Mob Filter's `WorldgenThreadSpawnAttempt` cannot resolve the dimension on its own, returning
   `null` from `getDimensionId()`. Its `DimensionCheck` treats `null` as matching every dimension rule.
   Consequently, dimension-specific rules (such as disabling mob spawning in a custom dimension like
   `lampas:aria`) match all worldgen spawns across every dimension, silently wiping out Overworld
   village villagers, iron golems, and cats during structure placement.

   Lampas2 Overrides wraps `WorldGenRegion_addFreshEntity` to capture the region level's dimension
   (`worldGenRegion.getLevel().dimension().identifier()`) in a thread-local context
   (`WorldgenDimensionContext`), and injects into `WorldgenThreadSpawnAttempt#getDimensionId` to return
   that scoped dimension. This allows dimension-restricted rules to evaluate against the actual dimension
   being generated without leaking across threads or tasks.

These compatibility mixins are common-side and enabled only for the inspected Mob Filter version
`0.28.0+26.2`. C2ME, Lithium, and Chunky are not activation requirements: Chunky exposed the issue
through aggressive generation, but the unsafe side effects belong to Mob Filter's worldgen logic.
Other Mob Filter versions are deliberately skipped until their bytecode and behavior are reviewed.
Both mixins keep a hard `require = 1` contract, failing loudly during startup if a call site changes.

## Jade nameplates and Custom Name

This integration comprises two independent client features:

### 1. Jade nameplates (gated on `jade`)
When Jade is installed, entity and player identification is handled through Jade's HUD tooltip rather
than floating vanilla world nameplates:
- **Suppresses vanilla in-world nameplate rendering**: Wraps and suppresses `SubmitNodeCollector#submitNameTag`
  calls inside `EntityRenderer#submitNameDisplay`. This hides floating player usernames, custom-named mobs,
  named pets, armor-stand nameplates, below-name scoreboard objective text, and Figura entity nameplates, while
  allowing surrounding method lifecycles (such as Figura's `NameplateRenderContext.begin/end`) to execute cleanly.
- **Preserves render-state extraction and data queries**: Render-state properties (`state.nameTag`,
  `state.scoreText`, `state.nameTagAttachment`) and entity data methods (`getName()`, `getCustomName()`,
  `getDisplayName()`, `hasCustomName()`) remain fully populated, allowing Jade and other mods to inspect
  them normally.
- **Genuine `TextDisplay` entities remain visible**: Content rendered via `DisplayRenderer.TextDisplayRenderer`
  does not rely on entity nameplates and continues to render uninterrupted.
- **Fail-safe**: When Jade is not installed, the mixin is disabled and vanilla nameplates render normally.

### 2. Jade ↔ Custom Name bridge (gated on `jade` + `eclipsescustomname`)
When both Jade and Custom Name are present, Jade resolves player titles using Custom Name's synced
player display names instead of the plain client `Player#getDisplayName()`:
- **Data flow**:
  ```text
  Custom Name (server)
      ↓ (ClientboundPlayerInfoUpdatePacket.UPDATE_DISPLAY_NAME)
  PlayerInfo.tabListDisplayName (client)
      ↓
  JadeCustomNameResolver (Lampas2 Overrides)
      ↓
  Jade ObjectNameProvider#getEntityName
  ```
- **Transparent fallback**: If a player has no custom display name or `PlayerInfo` is missing, the resolver
  falls back immediately to vanilla `Entity#getDisplayName()`. Unnamed and custom-named mobs, villagers,
  and item entities follow Jade's standard logic.
- **Custom Name configuration**: Custom Name's optional floating above-head labels use fake clientbound
  `TextDisplay` passengers rather than vanilla nameplates. Keep `display_above_player.enabled = false`
  (the default) in `eclipsescustomname.json` so Jade handles all entity identity presentation.

## Custom Name multi-word names

In Custom Name 0.4.4-26.2, `CustomNameUtil#playerNameArgumentToComponent` passes `operatorsBypassRestrictions`
directly as the `spaceAllowed` parameter to `nameArgumentToComponent`. When a player without operator permissions
executes `/name nickname`, `/name prefix`, or `/name suffix`, `spaceAllowed` evaluates to `false`. The underlying
component parser silently truncates the argument at the first ASCII space character:
```text
/name nickname The Administrator  ->  silently sets nickname to "The"
/name prefix [Admin] The Boss     ->  silently sets prefix to "[Admin]"
```

Lampas2 Overrides applies a server-safe, common mixin to `CustomNameUtil` that sets `spaceAllowed = true` for
`playerNameArgumentToComponent`. This allows non-operator players to set multi-word nicknames, prefixes, and suffixes
while keeping name length limits, regex validation, and blacklist enforcement fully active.
The mixin is common-side (runs on both dedicated servers and clients) and is version-gated to Custom Name `0.4.4-26.2`.

## Gravestones death inscription and glow

Gravestones normally renders an inscription on technical player death graves showing the owner's
name, death date, and death time.

Lampas2 Overrides implements a client-only override whenever Gravestones is present:
- **Technical/death graves render no text at all**: `TechnicalGravestoneBlockEntityRenderer#getSignText`
  returns a blank `SignText`, eliminating in-world text inscriptions.
- **Your own graves receive a vanilla-style glowing outline**: local player ownership is determined
  during `extractRenderState` and attached to the render state. During `AbstractGravestoneBlockEntityRenderer#submit`,
  an outline pass for the grave's block model is submitted using `RenderTypes.outline(TextureAtlas.LOCATION_BLOCKS)`.
- **Other players' graves render with no outline and no inscription**.
- **Aesthetic/decorative gravestones remain untouched**, keeping their custom text and normal rendering.
- **Player skulls/heads on the grave remain untouched**, honoring Gravestones' `SHOW_HEADS` configuration.

The mixin is client-only and is not restricted to a specific Gravestones version.

## Visual Workbench tag rebinding

Visual Workbench dynamically creates replacement crafting-table blocks and copies the source
block's bound tags into them.

Puzzles Lib's `BlockConversionHelper.copyBoundTags` assumes a target block's tags are either empty or
identical. ReplayMod playback and repeated client configuration reloads trigger client tag updates
in the same process, leaving the generated target block with stale tags and causing Puzzles Lib to throw
`IllegalStateException`.

Lampas2 Overrides intercepts `BlockConversionHelper.copyBoundTags` and rebinds the tags when the target
block belongs to the `visualworkbench` namespace. Puzzles Lib's strict invariant remains untouched for
all other targets and callers. The mixin is client-only and gated on both `visualworkbench` and
`puzzleslib`.

## Incendium tick optimization

Incendium Legacy 5.5.0 and 5.5.1 scan every living non-player entity each tick to repair missing or
invalid entity IDs, even though the same clock initializes previously unseen mobs. Lampas2 Overrides
installs an always-enabled built-in datapack that removes that redundant scan and runs only the unseen
living-mob initialization pass every five ticks (4 Hz). Existing ticking mobs, frozen-state updates,
particles, player logic, altar items, and short-lived projectile initialization remain at 20 Hz.

The pack also makes the 15-bit ID rollover safe without relying on the removed validation pass. It
resets before assigning 32768, immediately reassigns every existing player and initialized mob, and
then assigns the entity that triggered the rollover exactly once.

This feature fails closed using explicit version profiles:
- **Incendium 5.5.0**: registers `incendium_5_5_0_optimizations` when verified against 5.5.0's exact SHA-256 fingerprints.
- **Incendium 5.5.1**: registers `incendium_5_5_1_optimizations` when verified against 5.5.1's exact SHA-256 fingerprints. The 5.5.1 optimization semantically rebases on upstream's new `entity/chilling` call while removing the redundant ID check.

A modified or unsupported Incendium release is left untouched and produces a warning in the server
log instead of receiving potentially stale function overrides.

## Additional Lanterns chunk loading

Additional Lanterns 1.1.2 hooks `ServerLevel` neighbor updates (`updateNeighborsAt` and
`updateNeighborsAtExceptFromFacing`) and inspects each adjacent block to convert powered vanilla
lanterns into Additional Lanterns equivalents.

At chunk boundaries, the inspected position can belong to an unloaded chunk. `Level#getBlockState`
then synchronously requests that chunk from `ServerChunkCache`, stalling the server thread.

Lampas2 Overrides cancels Additional Lanterns' `VanillaLanternEvents.handleLanternRedstone` call
when the target position's chunk is not currently loaded in `ServerChunkCache`. Loaded chunks retain
the original behavior. The mixin is version-gated to Additional Lanterns 1.1.2.

## Underground Village fixes

Underground Village 2.1.1 contains several upstream data bugs:

1. **Loot table defects**: Bundles three Create integration loot tables that hard-reference Create items even when Create is absent, plus a cleric table using the removed `minecraft:set_nbt` function. Before registry-aware loot validation, this compatibility layer substitutes empty tables for Create-only rooms when Create is absent and upgrades cleric potion functions to `minecraft:set_potion`.
2. **Worldgen structure & template pool defects**:
   - `poi/v4/founten.nbt` was corrupted in commit `23b7f51f` ("Fix Pool") with invalid gzip CRC/ISIZE and malformed NBT tags; repaired using the clean parent structure.
   - `poi/v4/sidebed_bedroom.nbt` and `abandoned_poi/v4/sidebed_bedroom.nbt` reference non-existent `stoneholm:iron_golm`; corrected to `stoneholm:iron_golem`.
   - `poi/v4/tall_bedroom.nbt` references non-existent `stoneholm:villager`; corrected to `stoneholm:villagers`.
   - `better_villagers_point_of_interest.json` and `better_villagers_abandoned_point_of_interest.json` reference non-existent `stoneholm:addons/better_villager/poi/...` (singular); corrected to `stoneholm:addons/better_villagers/poi/...` (plural).

Worldgen data fixes are packaged in an always-enabled built-in datapack (`stoneholm_2_1_1_fixes`), version-gated to Underground Village 2.1.1 and protected by SHA-256 fingerprints of all six upstream target resources. Other Stoneholm structures and pools are untouched.

## Better Lib startup

Better Lib 2.1.0 and 2.1.1 scan bundled JSON villager definitions by opening their own jar as a ZIP
filesystem. Fabric Loader 0.19.3 already has that filesystem open, so the second open throws
`FileSystemAlreadyExistsException` and aborts the common mod entrypoint. This compatibility fix
reuses the existing filesystem behind a close shield: Better Lib can scan its resources normally,
but its try-with-resources block cannot close Fabric Loader's shared filesystem afterward.

The mixin is common and therefore fixes both dedicated-server and client startup. It is gated on
the `better_lib` mod id and is inert when Better Lib is absent. A second gated mixin also removes
Better Lib's stale `andesite_worker` and `ore_trader` entries after job-site tags have been merged.
Both demo professions are disabled in Better Lib 2.1.0 and 2.1.1, so leaving their generated tag entries in
place prevents Minecraft from resolving the tag while serving no gameplay content.

## Virtual Resource & Datapack Patches

Minecraft 26.2 enforces strict pack format metadata checks via `PackMetadataSection` and `PackFormat`.
Several mods bundle data or built-in datapacks with malformed `pack.mcmeta` files (such as declaring
format ranges without `supported_formats`, or specifying obsolete maximum format versions like 81 instead of 107)
or malformed JSON files.

Lampas2 Overrides implements a low-overhead runtime virtual patch system (`ResourcePatchResolver`) that intercepts
mod resource streams. Each patch is strictly version-gated and SHA-256 fingerprinted: if an upstream mod updates
or fixes the bug, the patch fails closed and leaves the upstream resource untouched.

Patched mods and resources:

1. **Moog's Voyager Structures (MVS 5.0.11 & 5.0.14)**:
   - **Defect**: Upstream 5.0.11 and 5.0.14 declare `pack_format: 48` without `supported_formats` or declare `min_format: 48, max_format: 107.1` without `supported_formats`, causing Minecraft 26.2's `PackMetadataSection` parser to reject the pack.
   - **Fix**: Virtually replaces `pack.mcmeta` with valid `supported_formats: [48, 107]` and `max_format: [107, 1]`.
   - *Note*: MVS 5.1.1 fixed this upstream and requires no patch.
2. **Moog's Nether Structures (MNS 3.0.0)**:
   - **Defect**: Missing `supported_formats` in root `pack.mcmeta`.
   - **Fix**: Virtually replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`.
3. **Formations Overworld (1.0.5+a)**:
   - **Defect**: Missing `supported_formats` in root `pack.mcmeta`.
   - **Fix**: Virtually replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`.
4. **Grim Kingdoms Lost Structures Ruins (2.0.3)**:
   - **Defect**: Missing `supported_formats` in root `pack.mcmeta`.
   - **Fix**: Virtually replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`.
5. **Pyrite (0.18.3+26.2)**:
   - **Defect**: All four built-in datapacks (`pyrite_azalea`, `pyrite_crafting_tables`, `pyrite_mushrooms`, and `pyrite_oddities`) declare `max_format: 81` in `pack.mcmeta`. In MC 26.2 (which requires format up to 107), Minecraft flags the datapacks as incompatible or fails to load them.
   - **Fix**: Replaces `pack.mcmeta` across all 4 built-in packs with `supported_formats: [48, 107]` and `max_format: 107`.
6. **Easter's Delight (1.3.1)**:
   - **Defect**: Built-in recipe override datapack `resourcepacks/farmersdelight_overrides/pack.mcmeta` is missing `supported_formats`.
   - **Fix**: Replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`.
7. **Better Lib (2.1.1)**:
   - **Defect**: Bundles `data/minecraft/tags/point_of_interest_type/acquirable_job_site.json` prefixed with illegal JSON comments (`//{`), causing strict JSON parsers to throw exceptions on tag reload.
   - **Fix**: Virtually substitutes the clean, comment-free POI tag definition.

## Lootr ↔ Fast Item Frames

Fast Item Frames normally converts vanilla item-frame entities into block entities, but does not
recognize Lootr's custom `lootr:item_frame` entity. This bridge adds that entity type to the
conversion set and transfers its UUID, reference inventory and opened state into the resulting Fast
Item Frames block entity. The converted frame continues to use Lootr's per-player inventory,
protection, advancement and refresh behavior instead of becoming a shared vanilla item frame.

The common hooks run on the server and the renderer hooks run on the client, so the mod must be
installed on both sides when this feature is used. Lootr and Fast Item Frames remain optional; the
bridge is not applied unless both are present.

Live testing confirmed conversion, per-player looting and Lootr refresh behavior. Once a player
takes their item, the converted frame renders empty for that player; refreshing it through Lootr
repopulates it, and its Lootr identity and properties remain intact.

## Figura chat heads

Chatting draws a chat head from the player's skin texture, so someone wearing a Figura avatar shows
up in chat as their vanilla face rather than the character everyone can see in the world. Figura
already substitutes its avatars for skin faces in the tab list and the permissions screen, through
`Avatar#renderPortrait`; this extends the same treatment to chat, so a chat head matches the face
Figura draws everywhere else.

Both of Chatting's drawing paths are covered: its default path goes through vanilla
`PlayerFaceExtractor#extractRenderState`, while its *improved heads* option blits the face itself
via a method Chatting adds to that same class. Players without an avatar keep their skin face.

Chatting's sender detector is also enhanced with `ChatPlayerResolver` to associate multi-word,
formatted, or custom TAB display names (e.g. from CustomName or server prefixes such as
`The Administrator` or `[Admin] The Administrator`) with their authentic `PlayerInfo`. Display names
are used strictly to discover the player; the player's real UUID remains the identity for Figura avatar
lookups and permissions.

Chat screenshots pick it up too, since they resolve the head owner the same way.

## Figura ↔ ReplayMod

### Why a bridge is needed

Figura avatars are fetched from Figura's own backend over a websocket, keyed by player UUID. Nothing
about them travels over the Minecraft protocol, so a ReplayMod recording — which is a capture of the
packet stream — contains no trace of them. Play such a replay back and the best Figura can do is ask
the backend what those players are wearing *right now*: wrong for anyone who has since changed
avatar, and nothing at all for players who went offline, deleted the avatar, or were wearing a local
one that was never uploaded.

The same is true of an avatar's *state*. A toggled animation is a Lua variable its owner flips with
a keybind and broadcasts with `pings.setSomething(state)`; the broadcast goes over the backend, so
without the pings every avatar plays back with every toggle off.

Two further problems only show up when exporting video, and both make avatars look broken rather
than absent:

- Figura advances Blockbench animations off the wall clock. An export renders frames at whatever
  rate the machine manages, not at real time, so animations run at whatever ratio those two happen
  to differ by — frequently 10× or worse. The same applies, less dramatically, to watching a replay
  at anything other than 1× speed.
- Figura applies and clears animation transforms in `Minecraft#runTick`. ReplayMod's export pipeline
  drives `Minecraft#tick` and the world renderer directly and never enters `runTick`, so exported
  footage shows every avatar frozen in whatever pose it held when rendering started.

### What it does

| | |
|---|---|
| **While recording** | Watches the tab list and copies each player's avatar into the `.mcpr` as it is seen. Blobs are keyed by content hash, so players sharing an avatar cost one copy, and a player swapping avatars mid-recording is recorded as a change with a timestamp. Serialisation and archive writes happen off-thread. |
| **Script state** | Records the pings that carry it. `Avatar#runPing` is where received pings and the owner's own both land, so recording there captures exactly what the recording client was rendering. |
| **While watching** | Applies the recorded avatars for the current point in the replay and suppresses Figura's backend fetch for those players, so a recorded avatar cannot be replaced by a current one. Replays play back with no network connection at all. Pings fire as their timestamps pass. |
| **Animation timing** | Replaces Figura's animation clock with one derived from the replay's own timeline for as long as a replay is open, so animations track playback speed, stop when playback is paused, and advance one video frame at a time during an export. |
| **Video export** | Drives Figura's animation apply/clear once per exported frame — once per *frame*, not once per view, so cubic and stereoscopic exports do not advance animations several times over. |
| **Post-processing** | Carries the data into the files ReplayMod's cut/split pass produces. That pass copies only metadata, markers, mod info and resource packs into its fresh output files, and it runs on most recordings — ReplayMod writes a `_RM_START_CUT`/`_RM_SPLIT` pair whenever you stop recording by hand — so without this almost every replay would come out empty. |
| **On close** | Hands every player it touched back to Figura, restoring the local avatar you had loaded before opening the replay. |

Storage layout inside the `.mcpr`:

```
figura/index.json           changes as [{time, uuid, hash}], time in ms into the recording
figura/avatars/<hash>.nbt   gzipped avatar data, one entry per distinct avatar
figura/pings.bin            (time, uuid, pingId, payload) records, in order
```

A player's *first* recorded avatar applies from the start of the replay regardless of its timestamp.
That timestamp records when the recording client finished downloading the avatar, not when the
player put it on, so honouring it literally would leave players briefly vanilla-skinned for reasons
that have nothing to do with what was recorded.

### Known limitations

- **Change timestamps are not remapped across cuts and splits.** The data is carried into
  post-processed replays, but the times in the index still refer to the original recording. Because
  a player's first avatar applies from the start regardless, this is invisible unless someone
  changed avatar mid-recording *and* the replay was cut or split — in which case that switch can
  land early or late. Remapping properly means reproducing ReplayMod's internal cut/split state
  machine, which is not worth the risk of putting avatars at confidently wrong times.
- **Seeking backwards restarts the avatar scripts.** Lua state cannot be rewound, so a backward seek
  reloads every avatar and re-runs its pings from the beginning. That is exact below 2000 pings; past
  it the oldest are skipped, which is invisible for the usual `pings.setX(state)` shape — a later
  ping overwrites what a skipped one would have set — but can lose state for a ping that flips a
  value rather than setting it. Ping replay also waits (up to 10s) for scripts to finish loading,
  since Figura silently drops pings aimed at an unloaded script.
- **Avatars are not host-mode during playback.** Figura decides `isHost` from the local player UUID,
  which during a replay is ReplayMod's camera entity. Avatars that gate visuals on `host:isHost()`
  therefore render the way other players saw them, not the way the recorder did. This also means a
  replayed avatar's script cannot send chat, set the clipboard, or react to keybinds — which is why
  it has been left as is.
- **`client:getSystemTime()` still returns real time.** Scripts that animate off it drift during an
  export. Only `TimeController`, which drives Blockbench animations, is switched to the replay clock.

## Installing

Requires Minecraft 26.2, Fabric Loader 0.19.3+ and Java 25. Each feature additionally needs the
mods it bridges; anything absent simply switches that feature off. Drop the jar
from [Releases](https://github.com/justbecauseph/lampas2-overrides/releases) — or from the `build`
workflow's artifacts — into your `mods` folder.

## Building

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # dev client; put figura + replaymod jars in run/mods/ first
```

Neither Figura nor ReplayMod is a build dependency. The optional Lootr and Fast Item Frames bridge
uses compile-only artifacts for Lootr, Fast Item Frames, Puzzles Lib and Fabric API; none are bundled
into this mod. See [CLAUDE.md](CLAUDE.md) for how the bridges are structured and how changes get
verified.
