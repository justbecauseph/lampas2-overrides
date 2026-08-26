# lampas2-overrides

A Fabric mod for Minecraft 26.2 holding compatibility fixes between mods that do not know
about each other. Each feature is gated on the mods it bridges and is inert without them.

| Feature | Needs | What it does |
|---|---|---|
| [Figura in ReplayMod](#figura--replaymod) | Figura + ReplayMod | Makes Figura avatars survive into recordings, playback and video exports |
| [Figura chat heads](#figura-chat-heads) | Figura + Chatting | Draws the Figura avatar's face in Chatting's chat heads instead of the vanilla skin |
| [Lootr fast item frames](#lootr--fast-item-frames) | Lootr + Fast Item Frames | Converts Lootr item-frame entities into blocks while preserving per-player loot |
| [Better Lib startup](#better-lib-startup) | Better Lib | Prevents Better Lib from reopening Fabric Loader's shared mod-jar filesystem |
| [Underground Village loot](#underground-village-loot) | Underground Village | Repairs obsolete and absent-mod Stoneholm loot data |
| [Additional Lanterns chunk loading](#additional-lanterns-chunk-loading) | Additional Lanterns 1.1.2 | Prevents redstone neighbor checks from synchronously loading unloaded chunks |
| [Visual Workbench tag rebinding](#visual-workbench-tag-rebinding) | Visual Workbench + Puzzles Lib | Prevents replay loading and tag reload crashes from stale Visual Workbench tags |
| [Name Tag Upgrade selection drag](#name-tag-upgrade-selection-drag) | Name Tag Upgrade 26.2.0 | Prevents a client crash when dragging selection left in a scrolled name field |
| [Incendium tick optimization](#incendium-tick-optimization) | Incendium Legacy 5.5.0 | Removes a redundant 20 Hz entity-ID scan and throttles living-mob initialization |
| [Gravestones death inscription and glow](#gravestones-death-inscription-and-glow) | Gravestones 1.4.2 | Suppresses technical death grave text and renders a glowing outline only on your own graves |
| [Jade nameplates and Custom Name](#jade-nameplates-and-custom-name) | Jade (+ Custom Name) | Suppresses vanilla in-world entity/player nameplates and syncs Custom Name player display names into Jade |

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

## Gravestones death inscription and glow

Gravestones normally renders an inscription on technical player death graves showing the owner's
name, death date, and death time.

Lampas2 Overrides implements a client-only, version-gated override for Gravestones:
- **Technical/death graves render no text at all**: `TechnicalGravestoneBlockEntityRenderer#getSignText`
  returns a blank `SignText`, eliminating in-world text inscriptions.
- **Your own graves receive a vanilla-style glowing outline**: local player ownership is determined
  during `extractRenderState` and attached to the render state. During `AbstractGravestoneBlockEntityRenderer#submit`,
  an outline pass for the grave's block model is submitted using `RenderTypes.outline(TextureAtlas.LOCATION_BLOCKS)`.
- **Other players' graves render with no outline and no inscription**.
- **Aesthetic/decorative gravestones remain untouched**, keeping their custom text and normal rendering.
- **Player skulls/heads on the grave remain untouched**, honoring Gravestones' `SHOW_HEADS` configuration.

The mixin is client-only and version-gated to Gravestones 1.4.2 (`1.4.2+26.2+A`).

## Name Tag Upgrade selection drag

Name Tag Upgrade 26.2.0 crashes with a `StringIndexOutOfBoundsException` when dragging text selection
past the left edge of a formatted text field whose content is horizontally scrolled (`displayPos > 0`).

In `FormattableEditBox.findClickedPositionInText`, the mouse offset `mouseX - textX` is bounded only
from above with `Math.min(..., innerWidth)`. Dragging to the left yields a negative offset, which
causes `WidthLimitedCharSink` to fail its non-negative budget check before skipping characters up to
`displayPos`. The unadvanced sink position `0` then reaches `content.substring(displayPos, 0)`.

Lampas2 Overrides clamps the mouse offset to `Math.max(0, offset)` at the `Math.min` call site, ensuring
the character sink advances through the scrolled prefix without altering Name Tag Upgrade's formatting
and cursor calculation logic. The mixin is client-only and version-gated to Name Tag Upgrade 26.2.0.

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

Incendium Legacy 5.5.0 scans every living non-player entity each tick to repair missing or invalid
entity IDs, even though the same clock initializes previously unseen mobs. Lampas2 Overrides installs
an always-enabled built-in datapack that removes that redundant scan and runs only the unseen
living-mob initialization pass every five ticks (4 Hz). Existing ticking mobs, frozen-state updates,
particles, player logic, altar items, and short-lived projectile initialization remain at 20 Hz.

The pack also makes the 15-bit ID rollover safe without relying on the removed validation pass. It
resets before assigning 32768, immediately reassigns every existing player and initialized mob, and
then assigns the entity that triggered the rollover exactly once.

This feature fails closed. It registers only when Fabric Loader reports Incendium version 5.5.0 and
the original `clocks/main`, `entity_id/check`, and `entity_id/reset` functions match the verified
SHA-256 fingerprints. A modified or updated Incendium jar is left untouched and produces a warning
in the server log instead of receiving potentially stale function overrides.

## Additional Lanterns chunk loading

Additional Lanterns 1.1.2 hooks `ServerLevel` neighbor updates (`updateNeighborsAt` and
`updateNeighborsAtExceptFromFacing`) and inspects each adjacent block to convert powered vanilla
lanterns into Additional Lanterns equivalents.

At chunk boundaries, the inspected position can belong to an unloaded chunk. `Level#getBlockState`
then synchronously requests that chunk from `ServerChunkCache`, stalling the server thread.

Lampas2 Overrides cancels Additional Lanterns' `VanillaLanternEvents.handleLanternRedstone` call
when the target position's chunk is not currently loaded in `ServerChunkCache`. Loaded chunks retain
the original behavior. The mixin is version-gated to Additional Lanterns 1.1.2.

## Underground Village loot

Underground Village 2.1.1 bundles three Create integration loot tables that hard-reference Create
items even when Create is absent, plus a cleric table using the removed `minecraft:set_nbt` loot
function. Before registry-aware loot validation, this compatibility layer substitutes empty tables
for the Create-only rooms when Create is not installed and upgrades the cleric potion entries to
Minecraft 26.2's `minecraft:set_potion` format. Other Stoneholm loot tables are untouched.

## Better Lib startup

Better Lib 2.1.0 scans its bundled JSON villager definitions by opening its own jar as a ZIP
filesystem. Fabric Loader 0.19.3 already has that filesystem open, so the second open throws
`FileSystemAlreadyExistsException` and aborts the common mod entrypoint. This compatibility fix
reuses the existing filesystem behind a close shield: Better Lib can scan its resources normally,
but its try-with-resources block cannot close Fabric Loader's shared filesystem afterward.

The mixin is common and therefore fixes both dedicated-server and client startup. It is gated on
the `better_lib` mod id and is inert when Better Lib is absent. A second gated mixin also removes
Better Lib's stale `andesite_worker` and `ore_trader` entries after job-site tags have been merged.
Both demo professions are disabled in Better Lib 2.1.0, so leaving their generated tag entries in
place prevents Minecraft from resolving the tag while serving no gameplay content.

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
