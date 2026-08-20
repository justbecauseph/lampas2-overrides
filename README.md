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
| [Plasmo Voice permissions](#plasmo-voice-permissions) | Plasmo Voice 2.1.14 | Prevents wildcard permissions from becoming invalid Fabric permission identifiers |

## Plasmo Voice permissions

Plasmo Voice 2.1.14's Fabric permission adapter converts dotted permission strings into Fabric
Permission API v1 identifiers. Wildcard permissions such as `pv.addon.broadcast.*` and
`pv.activation.*` cannot be identifiers because `*` is not valid in an identifier path. When the
Broadcast add-on checks its command permission while Minecraft sends the command tree, the
resulting `IdentifierException` aborts player placement and disconnects the player as
`Invalid player data`.

This compatibility mixin declines only permission strings containing `*` before SLIB calls
`PermissionNode.of`. SLIB can then consult its next permission backend and, if none resolves the
wildcard, use its normal OP/default fallback. Concrete permissions such as
`pv.addon.broadcast.server` still go through Fabric Permission API v1 unchanged. The mixin is
version-gated to Plasmo Voice 2.1.14 so an upstream implementation is not shadowed automatically.

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
