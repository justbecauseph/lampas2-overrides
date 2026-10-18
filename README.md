# lampas2-overrides

A Fabric mod for Minecraft 26.2 holding compatibility fixes between mods that do not know
about each other. Each feature is gated on the mods it bridges and is inert without them, except the
ungated hotbar slot resync after respawn.

| Feature | Needs | What it does |
|---|---|---|
| [Figura in ReplayMod](#figura--replaymod) | Figura + ReplayMod | Makes Figura avatars survive into recordings, playback and video exports |
| [Figura chat heads](#figura-chat-heads) | Figura + Chatting | Draws the Figura avatar's face in Chatting's chat heads instead of the vanilla skin |
| [Lootr fast item frames](#lootr--fast-item-frames) | Lootr + Fast Item Frames | Converts Lootr item-frame entities into blocks while preserving per-player loot |
| [Underground Village fixes](#underground-village-fixes) | Underground Village 2.1.1 | Repairs obsolete/absent-mod loot tables and worldgen structure/pool data defects |
| [Additional Lanterns chunk loading](#additional-lanterns-chunk-loading) | Additional Lanterns 1.1.2 | Prevents redstone neighbor checks from synchronously loading unloaded chunks |
| [Mob Filter worldgen safety and dimension context](#mob-filter-worldgen-safety-and-dimension-context) | Mob Filter | Prevents C2ME worldgen deadlock on rejected mobs and provides dimension context for worldgen rules |
| [Visual Workbench tag rebinding](#visual-workbench-tag-rebinding) | Visual Workbench + Puzzles Lib | Prevents replay loading and tag reload crashes from stale Visual Workbench tags |
| [Incendium tick optimization](#incendium-tick-optimization) | Incendium Legacy 5.5.1 | Removes a redundant 20 Hz entity-ID scan, throttles living-mob initialization, and stops helper markers accumulating in chunk [0, 0] |
| [Gravestones death inscription and glow](#gravestones-death-inscription-and-glow) | Gravestones | Suppresses technical death grave text and renders a glowing outline only on your own graves |
| [Jade nameplates and Custom Name](#jade-nameplates-and-custom-name) | Client Jade; server TAB name sync | Suppresses vanilla in-world entity/player nameplates and shows server-synced player display names in Jade |
| [Custom Name multi-word names](#custom-name-multi-word-names) | Custom Name 0.4.4-26.2 | Permits spaces in nickname, prefix, and suffix commands for non-operators |
| [Virtual Resource & Datapack Patches](#virtual-resource--datapack-patches) | Formations Overworld, Grim Kingdoms, Pyrite, Easter's Delight, Wilder Wild 4.3.3, Rogues 3.1.3.003+26.2, RPG Series spells | Transparently repairs malformed `pack.mcmeta` formats, worldgen resources, Rogues' Sharpness and RPG Series spell data at runtime |
| [Wilder Wild stone pool](#wilder-wild-stone-pool) | Wilder Wild 4.3.3 | Keeps the mesoglea stone pool inside C2ME's safe worldgen read/write radius |
| [Rogues Sharpness](#rogues-sharpness) | Rogues 3.1.3.003+26.2 | Restores flat Sharpness in place of Rogues' +8% base attack damage per level |
| [RPG Series spell patches](#rpg-series-spell-patches) | RPG Series 26.2 (Rogues, Wizards, Paladins, Archers, Spell Engine, Skill Tree, Relics) | Martial spell cooldowns ignore haste; crowd-control apply limits reach Pinnacle mobs |
| [Bee and spawner structure DFU validation](#bee-and-spawner-structure-dfu-validation) | Exact Trek and Stoneholm fixtures | Historical run on the removed Trinkets 4.1.0 profile; re-validation on 4.1.1 is pending |
| [Boat water-mask compatibility](#boat-water-mask-compatibility) | EMF 3.3.12 + audited boat providers | Restores the vanilla water mask for plain hulls when the selected Fresh Animations mask is incompatible |
| [Better Lib demo villager suppression](#better-lib-demo-villager-suppression) | Better Lib | Suppresses hardcoded demo villager registration that causes RemapException registry sync disconnects |
| [Beautiful item-model reloads](#beautiful-item-model-reloads) | Beautiful Enchanted Books 6.0.0 and/or Beautiful Potions 2.0.1 | Clears stale extra-model keys so each resource reload uses the keys registered for that reload |
| [IllagerBlabber voice registry](#illagerblabber-voice-registry) | IllagerBlabber 1.1.0 | Releases per-illager voice state on unload, follows illagers across dimensions, and drops per-tick lock and log overhead |
| [Hotbar slot resync after respawn](#hotbar-slot-resync-after-respawn) | Nothing (triggered by Debugify MC-143474) | Resends the selected hotbar slot after respawn so the server does not keep slot 0 |

## Beautiful item-model reloads

Beautiful Enchanted Books **6.0.0** and Beautiful Potions **2.0.1** keep their Fabric extra-model
keys in a static `REGISTERED_MODELS` map. Each resource reload creates new keys, while
`putIfAbsent` leaves the map pointing at keys from the earlier reload. Model resolution then asks
for a key that the current `ModelManager` does not know and receives `null`.

The client-only mixins clear that map at the start of each mod's exact
`initialize(Set, ModelLoadingPlugin.Context)` overload. Each mixin has an independent gate requiring
the matching mod ID, exact metadata version, and full installed-JAR SHA-256. Missing, unreadable,
multi-path, or changed artifacts leave only that target's mixin disabled.

Exact-artifact isolated client probes reproduce and repair the reload failure. Without the override,
the initial load resolved all 695 book and 828 potion keys, then a resource reload resolved none of
them. With the override, two successive reloads resolved all 695 and 828 keys. A resource-pack check
removed the selected Sharpness and Swiftness extra models as expected, and removing that pack restored
all keys. Blocking all matching models cleared both maps to zero, then restoration repopulated them.
Controlled item-resolver checks preserved the custom book/potion particle sprites after reload and
restoration. Both mixins appeared in the client log and both clients exited normally.

The resolver uses synthetic stacks without world registry synchronization. Full-pack behavior and visible
inventory rendering remain unverified. See [reload compatibility evidence](docs/beautiful-items-reload.md),
the [probe runner](tools/beautiful-items-probe/run.py), and [recorded probe data](docs/evidence/beautiful-items-reload.json).

## IllagerBlabber voice registry

IllagerBlabber **1.1.0** keeps every illager's voice state in static `ConcurrentHashMap`s on
`IllagerVoiceRegistry`, keyed by entity UUID, and never removes it. Each `IllagerVoiceManager` holds
its `AbstractIllager`, so every pillager, vindicator and evoker that ever ticked stays reachable until
the server stops. After a dimension change the new copy (same UUID) keeps using the manager that
holds the removed old copy, so its lines are voiced at the old position. Every tick, each illager
also interns its UUID string, locks on it and writes a tick marker whose "already processed" guard can
never fire, and new managers log at INFO.

On the server, Lampas2 Overrides takes over `IllagerVoiceRegistry.updateIllager` at HEAD. It reuses a
manager only when that manager holds this exact entity instance. Otherwise it clears the UUID's
per-entity state and creates a manager for the current instance, then runs the same manager update
and state update as upstream, without the intern, lock or tick marker. On entity unload the UUID's
state is cleared only if the unloading instance still owns it, so a newer same-UUID copy keeps its
state. All registry maps are cleared when a server stops. The per-illager INFO logs are demoted to
DEBUG.

The fix is common-side and runs on dedicated and integrated servers. It applies only when the
installed mod is exactly `illagerblabber` `1.1.0` with the audited JAR SHA-256; otherwise it logs a
warning naming the installed version and stays disabled. It changes only in-memory state, so it is
safe to add or remove on an existing world. IllagerBlabber is optional and is never bundled.

A disposable dedicated-server probe with 200 pillagers showed 200 managers and 200 dead pillagers
still reachable after the kill without the fix, and 0 of each with it. After a real Nether portal
crossing, the old copy stayed reachable without the fix and was released with it. Vanilla briefly
keeps entities removed during a tick in `EntityTickList`'s last snapshot; the probe forces one
mid-tick removal before counting so that vanilla retention is not mistaken for the leak. Audible
playback position on a client and long-uptime heap behaviour on the live server remain unverified.
See the [probe runner](tools/illagerblabber-probe/run.py) and [recorded probe data](docs/evidence/illagerblabber-registry.json).

## Mob Filter worldgen safety and dimension context

The inspected Mob Filter `0.28.1+26.2` artifact has two major defects during world generation:

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

These common-side mixins activate whenever Mob Filter is present, without checking its version. Their
compile-time and bytecode contract is pinned to Modrinth version `EDuF1CWZ` (`0.28.1+26.2`). Other
Mob Filter versions also activate, but have not been inspected or tested against this contract. C2ME,
Lithium, and Chunky are not activation requirements: Chunky exposed the issue through aggressive
generation, but the unsafe side effects belong to Mob Filter's worldgen logic. Both mixins keep a hard
`require = 1` contract, so startup fails if an installed version changes a call site.

A disposable dedicated-server probe with `0.28.1+26.2` verified that Overworld structure mobs are
admitted, Aria worldgen mobs are vetoed, and rejecting a zombie nautilus carrying a drowned avoids
removal on both the main thread and a worker thread. It also verified dimension-context restoration,
ordinary server-level discard, and startup without Mob Filter. The prior JAR reproduced the incorrect
Overworld rejection in the same fixture. Full-pack C2ME pregeneration and production recovery remain
unverified until the new JAR is deployed and the affected generation path is exercised.

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

### 2. Jade ↔ Custom Name bridge (gated on client `jade`)
Jade resolves player titles using the server-synced TAB display name. Custom Name runs on the
server; the client bridge needs only Jade and the standard player-info packet:
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

## Hotbar slot resync after respawn

The client sends `ServerboundSetCarriedItemPacket` only from `MultiPlayerGameMode#ensureHasSentCarriedItem`,
which runs every client tick and sends only when the selected slot differs from the private `carriedIndex`;
nothing else writes that field. Respawn keeps the same `MultiPlayerGameMode`. In vanilla the new client player
starts on slot 0, the stale `carriedIndex` differs from it, and the first tick sends 0, so both sides agree.

Debugify's MC-143474 fix (`dev.isxander.debugify.client.mixins.basic.mc143474.ClientPacketListenerMixin`,
client-side and enabled by default) copies the old player's selected slot onto the new one inside
`ClientPacketListener#handleRespawn` without sending a packet. The restored slot equals the stale
`carriedIndex`, so nothing is sent, while the server's new player stays on slot 0 (without `keepInventory`).
The client shows and swings the pre-death item; the server sees slot 0. Better Combat then logs
`No current attack hand!` with `Selected slot server: 0 | client: 4` and drops the attack, and other main-hand
actions also use the server's slot, until the player selects a different slot.

The client mixin sets `carriedIndex` to `-1` at the return of the five-argument `createPlayer` (the
three-argument overload delegates to it, and `handleRespawn` calls it before Debugify's injection point), so
the next client tick sends the real slot. On login and dimension change it resends the current slot once. The
fix is deliberately not gated on Debugify or any version, so it also covers any other mod that changes the
client's selected slot during respawn; `defaultRequire: 1` stops startup if a Minecraft update moves
`createPlayer` or removes `carriedIndex`. Players need the client jar; a server-only install changes nothing.

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
`puzzleslib`. Puzzles Lib 26.2.5, the version the build compiles against, keeps the `BlockConversionHelper`
bytecode of 26.2.4 (checked 2026-10-13).

## Incendium tick optimization

Incendium Legacy 5.5.1 scans every living non-player entity each tick to repair missing or
invalid entity IDs, even though the same clock initializes previously unseen mobs. Lampas2 Overrides
installs an always-enabled built-in datapack that removes that redundant scan and runs only the unseen
living-mob initialization pass every five ticks (4 Hz). Existing ticking mobs, frozen-state updates,
particles, player logic, altar items, and short-lived projectile initialization remain at 20 Hz.

The pack also makes the 15-bit ID rollover safe without relying on the removed validation pass. It
resets before assigning 32768, immediately reassigns every existing player and initialized mob, and
then assigns the entity that triggered the rollover exactly once.

The pack also stops Incendium's helper marker from multiplying. Upstream's `technical/summon_helper` runs
every 7 seconds and on every aim, keeps one `in.helper_entity` marker at overworld `0 0 0`, and relies on
`technical/load` running `forceload add 0 0` so that chunk stays loaded. On the live Lampas server the
force-load is gone after load (cause not yet identified; a server with only Fabric API and Incendium keeps it).
With the chunk unloaded, `@e` counted zero markers and every check summoned another into it; the live server
had accumulated 94,300 of them (26.8 MB of entity NBT in chunk [0, 0]) by 14 Oct 2026. The override re-adds the
force-load before every count, so the existing markers become visible and upstream's "count is not 1" branch
kills every duplicate and summons one. The first check after the chunk is re-added can add one extra marker
while the chunk's entities load; the next 7-second check removes it. If another mod blocks force-loading
outright, this override cannot help.

This feature fails closed using an explicit version profile for Incendium 5.5.1, the only supported release.
It registers `incendium_5_5_1_optimizations` only when Incendium 5.5.1 matches that profile's exact SHA-256
fingerprints. The optimization semantically rebases on upstream's `entity/chilling` call while removing the
redundant ID check.

A modified or unsupported Incendium release is left untouched and produces a warning in the server
log instead of receiving potentially stale function overrides.

## Wilder Wild stone pool

Wilder Wild `4.3.3` ships the stone-pool resource with a sampled radius of 12–15. FrozenLib's
circular waterlogged vegetation feature (audited in 3.0.1; 3.0.3 ships byte-identical classes) adds one block to the sampled radius before placement, then
checks the four horizontal neighbors of each placed block. At a chunk edge, the upstream maximum reaches two chunks away.
Historical C2ME 0.4.2-alpha.0.43 logs report unsafe worldgen reads from
`mesoglea_caves_stone_pool`.

The virtual resource patch keeps the feature type, stone ground, depth, vertical range, minimum
radius, and placement unchanged, and changes only `xz_radius.max_inclusive` from 15 to 14. The
largest sampled radius is then 15 and the four-neighbor checks reach at most 16 blocks, which fits
the active chunk and its one-chunk write radius even at positive, negative, edge, and corner
coordinates. The patch is version and SHA-256 gated; an unknown Wilder Wild release or changed
`stone_pool.json` remains untouched.

Verification on 4 October 2026 passed 103 unit tests at that run, exact installed-artifact and fixture
checks, and a disposable production-JAR dedicated-server probe with Wilder Wild 4.3 and
FrozenLib 3.0.1. The resource mixin applied, the selected stone-pool JSON matched the replacement,
and Minecraft decoded its configured-feature radius as 12–14 before normal shutdown.
Fresh cave generation with C2ME/Lithium and full-pack gameplay remain unverified.

On 16 October 2026 the patch moved from Wilder Wild `4.3` to `4.3.3`. The two JARs differ only in
`fabric.mod.json`, the manifest, and boat entity/item registration in `WWEntityTypes` and `WWItems`;
`stone_pool.json` (SHA-256 `59cf59a1…f2efe4`) and FrozenLib 3.0.3 are unchanged, so the analysis above
still applies. The dedicated-server probe was not re-run on 4.3.3.

## Rogues Sharpness

Rogues `3.1.3.003+26.2` (RPG Series) ships `data/minecraft/enchantment/sharpness.json` with a
`minecraft:attributes` effect: +8% of base attack damage per level (`add_multiplied_base`, linear
0.08 + 0.08 per level above the first). It scales base attack damage plus every flat attack-damage
modifier (weapon damage, Strength), unlike vanilla's flat bonus. Fabric API orders mod data packs
alphabetically by mod ID unless a mod declares `fabric:resource_load_order`, so Rogues' file overrides
Apothic Enchanting's and vanilla's. A version- and SHA-gated resource patch replaces it instead of a
later-loading pack, so a Rogues update is detected rather than silently overridden.

The virtual resource patch replaces Rogues' file with Apothic Enchanting 3.0.1's Sharpness (flat
`minecraft:damage` add, 1.0 + 0.5 per level above the first; Apothic Enchanting's costs, weight and
max level 5) plus `"exclusive_set": "#minecraft:exclusive_set/damage"`, which Apothic Enchanting's file
omits. The result is semantically equal to vanilla 26.2 Sharpness. Apothic Enchanting also ships an
`exclusive_set/damage` tag with a NeoForge-style `remove` of Sharpness; Fabric ignores `remove`, so the
decoded tag still contains Sharpness and Smite and Bane of Arthropods also exclude it through their
own sets. The patch requires mod ID `rogues`, version `3.1.3.003+26.2` and the original resource
SHA-256 `fe176357…f620e`; another release or a changed file keeps Rogues' resource and logs WARN.

Verification on 16 October 2026: unit tests cover the exact version and hash gate, the WARN on an
unregistered version or changed resource, the installed Rogues JAR (SHA-256 `77bdff88…08f113`) and
equality with Apothic Enchanting's file plus the exclusive set. The production-JAR dedicated-server
probe (`tools/rogues-sharpness-probe/run.py`) ran Rogues, Apothic Enchanting and their dependency
closure from the deployed server's mods directory:

| Run | Decoded Sharpness | Rogues pack `getResource` / `listResources` | Log |
|---|---|---|---|
| Without overrides | `minecraft:attributes` +8% `add_multiplied_base`, no flat damage | original `fe176357…` | — |
| With `lampas2-overrides-1.0.1.jar` | one `minecraft:damage` add: +1.0, 1.5, 2.0, 2.5, 3.0 at levels 1–5; no attribute effect; `exclusive_set` `#minecraft:exclusive_set/damage`; incompatible with Smite and Bane | replacement `b1b06c64…` on both | `Applied rogues 3.1.3.003+26.2 compatibility resource: data/minecraft/enchantment/sharpness.json` |
| Rogues declared as `3.1.3.004+26.2` | unchanged Rogues +8% `minecraft:attributes` | original `fe176357…` | WARN `Skipping rogues resource patch for data/minecraft/enchantment/sharpness.json: version 3.1.3.4+26.2 is not registered (patch targets 3.1.3.003+26.2); using upstream resource` |

The resource manager's top `sharpness.json` came from the `rogues` pack in every run, and the registry
loader's `listResources` path decoded the replacement. Evidence: `docs/evidence/rogues-sharpness.json`.
Damage in combat and full-pack behavior with every other mod are not covered by the probe.

Enchantments are a synced registry. For entries from packs the client also has, the server sends no
contents and each client decodes its own Rogues copy, so the patch must run on clients as well; the
pipeline ships this jar on both sides. A client without it shows Rogues' +8% modifier in tooltips while
the server deals flat damage. The client-side path has not been probed.

## RPG Series spell patches

Two class balance decisions of 17 October 2026 (D3 P1 and D9) change RPG Series spell data. Both are
version- and SHA-gated resource patches: 64 spell files in seven jars, one replacement per file even
where a file gets both changes.

**Martial cooldowns ignore haste (D3 P1).** Spell Engine 1.10.9's `SpellParameters.getCooldownDuration`
divides a spell's cooldown by the caster's haste for its school when the server's
`haste_affects_cooldown` and the spell's `cost.cooldown.haste_affected` are both true; the field defaults
to true. Physical melee haste comes from attack-speed modifiers plus spell haste, and physical ranged
haste from `ranged_weapon:haste`, so attack speed shortened melee cooldowns and Agile/Slipstream shortened
archer ones. AttributeFix's cap on `minecraft:attack_speed` does not limit it: Spell Engine sums modifier
amounts and never reads the capped value. Every spell in the physical_melee, physical_melee_dual or
physical_ranged school with a cooldown above 0 gets `"haste_affected": false` as the last key of
`cost.cooldown`:

| Mod (version) | Spells |
|---|---|
| Rogues & Warriors (3.1.3.003+26.2) | bear_trap, charge, mortal_strike, mutilate, shadow_step, shock_powder, shout, slice_and_dice, throw, throw_net, vanish |
| Spell Engine `rpg_series` (1.10.9+26.2) | cleave, fan_of_knives, flurry, ground_slam, impale, smash, swift_strikes, swipe, thrust, whirlwind |
| Paladins & Priests (3.1.3.003+26.2) | judgement |
| Archers (3.1.3.003+26.2) | barrage, entangling_roots, magic_arrow, power_shot, rain_of_arrows |
| Skill Tree (1.6.2+26.2) | 18 passives: archer tier 1-3, paladin tier 1 and 3, rogue tier 1-3, warrior tier 1 and 3, weapon_bow_passive_1, weapon_swipe_modifier_2 |
| Relics (1.4.1+26.2) | 14 procs and uses, including medium_proc_attack_speed, whose own attack speed used to shorten its 45 s cooldown |

`archers:spirit_wolf` already sets `haste_affected: false`, and `rogues:last_stand` is in the health
school, which has no haste source. Caster and healer cooldowns, the global switch and cast times stay
hasted.

**Crowd-control apply limits reach Pinnacle (D9).** A status effect with an `apply_limit` lands only when
`health_base + power x spell_power_multiplier >= target max health`. Twelve limits are raised so a
modelled Pinnacle user reaches the End top band (736.5 health at level 200) with at least 5% margin:

| Spell (impact) | Effect | Upstream | Patched | Modelled Pinnacle limit |
|---|---|---|---|---|
| Frost Nova, Frost Spikes [1] | wizards:frozen | 60+4P | 60+13.5P | 829 |
| Frostbolt, Frost Lance [1] | wizards:frost_slowness | 100+4P | 100+12.5P | 812 |
| Frost Blizzard [1] | wizards:frost_slowness | 80+4P | 80+12.5P | 792 |
| Judgement [1] | paladins:judgement | 50+2P | 50+7.5P | 823 |
| Shout [0] | rogues:demoralize | 50+2P | 50+9.5P | 848 |
| Throw, Throw Net [1] | rogues:shatter, rogues:net_trap | 100+2P | 100+8.5P | 814 |
| Shock Powder [0] | rogues:shock | 50+2P | 50+7.5P | 842 |
| Bear Trap [1] | rogues:bear_trap | 100+2P | 100+5.5P | 835 |
| Entangling Roots [0] | archers:entangling_roots | 50+5P | 780+5P | 785-815 at every tier |

The P values are the audit's scratch models with idealized gear. Roots' power is the archer's attack
damage (about 1, or 7 with Strength II), so its base is raised instead. Spell Engine refuses
movement-impairing and AI-disabling effects on `#spell_engine:bosses` regardless of the limit; that
covers every effect above except Demoralize and Shatter. Apotheosis elites and invaders are not bosses,
so their immunity comes from lampas-addons, which must ship in the same pack or earlier.

Each patch requires the mod ID, the version the upstream `fabric.mod.json` declares and the upstream
file's SHA-256; another release or a changed file keeps the upstream spell and logs WARN. The
replacements are generated: `python -I tools/generate_rpg_spell_patches.py --mods <server mods>`
rebuilds them and their test fixtures from the pinned jars, and `--check` verifies the committed files.
Every upstream spell file is written exactly as `json.dumps(indent=2)` writes it, so each replacement
differs from upstream only in the decided keys. Per-file SHA-256 values are in
`docs/evidence/rpg-spell-patches.manifest.json`.

Verification on 17 October 2026: unit tests check that the registry holds exactly the decided spells,
that each replacement is its upstream jar entry with only the decided keys changed (byte for byte), the
limit margins, the Loader-normalized version gate, and the WARN on an unregistered version or changed
file. `./gradlew test -PserverModsDir=../lampas-server-fabric/mods` also scans every spell in the
deployed jars for an unpatched hasted martial cooldown or apply limit. The dedicated-server probe
(`tools/rpg-spell-patch-probe/run.py`) ran the seven jars and their dependency closure, with a
FakePlayer at haste 3.0 (melee), 2.0 (ranged) and 2.0 (spell):

| Run | Applied lines | `rogues:charge` (12 s) | `wizards:fire_meteor` (10 s) | Frost Nova vs 736.5 health at P 56.99 | WARN |
|---|---|---|---|---|---|
| Without overrides | 0 | 4.0 s | 5.0 s | does not apply (60+4P) | none |
| With `lampas2-overrides-1.0.2.jar` | 64 | 12.0 s | 5.0 s | applies (60+13.5P); off at P 49.6, on at P 50.6 | none |
| Wizards declared `3.1.3.004+26.2` | 59 | 12.0 s | 5.0 s | does not apply (60+4P) | 5 x `is not registered` |
| Whirlwind entry tampered | 63 | 12.0 s | 5.0 s | applies | 1 x `SHA-256 mismatch`; Whirlwind stays hasted |

Evidence: `docs/evidence/rpg-spell-patches.json`. Spells are the synced `minecraft:spell` registry, and
clients decode known-pack entries from their own copy, so the jar must also run on clients; a client
without it shows hasted cooldowns in tooltips while the server enforces the patched ones. Combat with a
real player, the client-side decode, full-pack behavior and elite/invader immunity are not covered.

## Retired FrozenLib wind compatibility

Lampas2 Overrides applies no FrozenLib wind shim: an unpatched isolated client with FrozenLib 3.0 and Wilder Wild 4.3 showed no missing-extension failure. FrozenLib still changes the wind singleton during worker decoding, so general thread safety is not established. The probe and evidence for this observation were removed after commit `d8d84f1` (`docs/wind-state.md`).

## Boat water-mask compatibility

EMF 3.3.12 can replace Minecraft's shared boat water-patch layer with the Fresh Animations
`assets/minecraft/optifine/cem/boat_patch.jem` model. That model's animation expects `var.base_*`
values supplied by Fresh Animations hull models. Plain hulls from the audited Pyrite 0.18.3+26.2,
Promenade 5.6.0, Wilder Wild 4.3.3, BetterEnd 26.201.3, and BetterNether 26.201.3 providers do not supply those values.
BetterEnd and BetterNether each require `wover-item` 26.201.3.

The client-only compatibility hook runs at the end of each `BoatRenderer` construction. It checks
the exact provider layer, provider version, EMF version, selected resource bytes, and EMF's actual
`minecraft:optifine/cem/boat_patch.jem` root. It replaces the water patch with the vanilla
`BoatModel.createWaterPatch()` geometry only when the hull root is ordinary vanilla geometry.
Custom EMF hulls or animations, disabled or different resource packs, absent or mismatched versions,
the BetterEnd/BetterNether `wover-item` companion mismatch, and unlisted boat layers remain
untouched. The explicit provider list and verification limits are recorded in
[docs/boat-water-mask.md](docs/boat-water-mask.md). The audited EMF 3.3.12 identity and probe runs are recorded in
[the 2026-10-16 pipeline audit](docs/evidence/current-pipeline-audit-2026-10-16.json).

The isolated probe verifies artifact identities from the audited pipeline inventory:

```powershell
./tools/boat-water-mask-probe/run.ps1 -Mode baseline -Strict -MinecraftRoot <root> `
  -InventoryPath build/pipeline-validation-2026-10-16/inventory.json
./tools/boat-water-mask-probe/run.ps1 -Mode patched -Strict -MinecraftRoot <root> `
  -InventoryPath build/pipeline-validation-2026-10-16/inventory.json `
  -OverrideArtifactPath build/libs/lampas2-overrides-1.0.1.jar
```

`-MinecraftRoot` defaults to `%APPDATA%\.minecraft-lampas`, which no longer exists on the workstation;
the 2026-10-16 runs used a launcher-style root hardlinked from PrismLauncher's 26.2 libraries.
On 2026-10-16 both modes passed against the pack 2.3.44 inventory: the baseline reproduced the
displaced mask on all 108 supported hulls with EMF 3.3.12 and Wilder Wild 4.3.3, and the patched run
restored vanilla mask geometry for those 108 layers while leaving the other 26 renderers unchanged.

The runner verifies selected JAR metadata and SHA-256 before creating each fresh fixture,
preserves prior run evidence, discovers Biolith through the inventory, and uses
fixture-only FrozenLib and shutdown guards. The guards do not affect the installed client.
No visual, movement, or shader run is recorded for EMF 3.3.12. The probe establishes artifact identity,
resource provenance, and renderer geometry only; live rendering in the installed instance remains unverified.

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

## Better Lib demo villager suppression

Better Lib **2.1.2** bundles `data/better_lib/villagers/andesite_worker.json` and calls
`registerJsonVillagers()` at startup. That registers the demo `better_lib:andesite_worker` profession in
`POINT_OF_INTEREST_TYPE` and `VILLAGER_PROFESSION`. When Better Lib runs on a dedicated server, which
Underground Village depends on, and a connecting client lacks the mod, Fabric Registry Sync aborts with
`RemapException`.

The common-side `betterlib` mixin cancels `CommonClass.registerJsonVillagers()` at HEAD, so the demo
entries never enter the registries. It applies whenever Better Lib is installed and runs on dedicated
servers as well as clients. Better Lib 2.1.2 already fixed its ZIP filesystem startup and POI tag
defects upstream, so this mixin is the only Better Lib override.

## Virtual Resource & Datapack Patches

Minecraft 26.2 enforces strict pack format metadata checks via `PackMetadataSection` and `PackFormat`.
Several mods bundle data or built-in datapacks with malformed `pack.mcmeta` files (such as declaring
format ranges without `supported_formats`, or specifying obsolete maximum format versions like 81 instead of 107), malformed JSON files, or invalid values in worldgen data.

Lampas2 Overrides implements a low-overhead runtime virtual patch system (`ResourcePatchResolver`) that intercepts
mod resource streams. Each patch is strictly version-gated and SHA-256 fingerprinted: if an upstream mod updates
or fixes the bug, the patch fails closed and leaves the upstream resource untouched.

Patched mods and resources:

1. **Formations Overworld (1.0.5+c)**:
   - **Defect**: Root `pack.mcmeta` is missing `supported_formats`.
   - **Fix**: Virtually replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`. Its audited loot tables contain no exact `minecraft:chain` item reference, so no loot table is replaced.
   - **Evidence**: Candidate artifact identity and resource hashes are recorded in [`docs/evidence/formations-overworld-1.0.5c.json`](docs/evidence/formations-overworld-1.0.5c.json).
2. **Grim Kingdoms Lost Structures Ruins (2.0.3)**:
   - **Defect**: Missing `supported_formats` in root `pack.mcmeta`.
   - **Fix**: Virtually replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`. Zero-level enchantments in ten structure templates are repaired separately; see [Grim Kingdoms 2.0.3 zero-level enchantments](#grim-kingdoms-203-zero-level-enchantments).
3. **Pyrite (0.18.3+26.2)**:
   - **Defect**: All four built-in datapacks (`pyrite_azalea`, `pyrite_crafting_tables`, `pyrite_mushrooms`, and `pyrite_oddities`) declare `max_format: 81` in `pack.mcmeta`. In MC 26.2 (which requires format up to 107), Minecraft flags the datapacks as incompatible or fails to load them.
   - **Fix**: Replaces `pack.mcmeta` across all 4 built-in packs with `supported_formats: [48, 107]` and `max_format: 107`.
4. **Easter's Delight (1.3.1)**:
   - **Defect**: Built-in recipe override datapack `resourcepacks/farmersdelight_overrides/pack.mcmeta` is missing `supported_formats`.
   - **Fix**: Replaces `pack.mcmeta` with valid `supported_formats: [48, 107]`.
5. **Wilder Wild (4.3)**: The stone-pool radius repair is described in [Wilder Wild stone pool](#wilder-wild-stone-pool).
6. **Rogues (3.1.3.003+26.2)**: The Sharpness replacement is described in [Rogues Sharpness](#rogues-sharpness).
7. **RPG Series spells**: 64 spell files across seven jars, described in [RPG Series spell patches](#rpg-series-spell-patches).

A registered patch whose mod is installed at a different version is skipped with a WARN naming both
versions, as a changed SHA-256 is. Fabric Loader reports versions without leading zeros in numeric
components (Rogues `3.1.3.003+26.2` appears as `3.1.3.3+26.2` in the mod list); registered versions are
compared in that form, so register the version the upstream `fabric.mod.json` declares.

Moog's Voyager Structures 5.1.3 and Moog's Nether Structures 3.1.1 declare `supported_formats: [48, 121]` upstream,
which covers 26.2's data format 107.1, so neither needs a virtual patch. Better Lib 2.1.2 fixed its POI tag
upstream and needs no virtual patch.

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

The bridge is compiled against Lootr 1.24.41.124 and Puzzles Lib 26.2.5. Converted frames delegate Lootr's random
tick-scheduling offset to their existing per-instance state, so the value remains stable while the
frame instance is live.

## Figura chat heads

Chatting draws a chat head from the player's skin texture, so someone wearing a Figura avatar shows
up in chat as their vanilla face rather than the character everyone can see in the world. Figura
already substitutes its avatars for skin faces in the tab list and the permissions screen, through
`Avatar#renderPortrait`; this extends the same treatment to chat, so a chat head matches the face
Figura draws everywhere else.

The bridge intercepts `ChatHeads.draw` and its `drawFace` calls, using the `PlayerInfo` supplied
for that draw. This covers Chatting's cached texture and skin fallback paths. Shadow and main
face calls share one scope, which ends even if drawing throws. Players without an avatar keep
their skin face.

Activation requires Figura and Chatting. There is no Chatting version or artifact-hash gate.
The required draw descriptors were inspected in Chatting 3.2.3 with `javap` on 2026-10-13; a changed target causes a clear
mixin failure at startup. Authenticated avatar rendering still requires a live client check.

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
./gradlew test -PserverModsDir=../lampas-server-fabric/mods   # adds checks against the deployed RPG Series jars
./gradlew runClient      # dev client; put figura + replaymod jars in run/mods/ first
```

Neither Figura nor ReplayMod is a build dependency. The optional Lootr and Fast Item Frames bridge
uses compile-only artifacts for Lootr, Fast Item Frames, Puzzles Lib and Fabric API; none are bundled
into this mod. See [CLAUDE.md](CLAUDE.md) for how the bridges are structured and how changes get
verified.

### Trinkets entity schema repair (common-side)

The `trinkets_updated` 4.1.1+26.2 V1460 schema repair is gated on the exact version and V1460 class
SHA-256. Upstream's shape still drops modern flat slot inventories and unrelated Cardinal components during
typed round trips. The repair preserves grouped legacy slots, flat `Items`/`cosmetic` slots, unknown data,
and sibling components while retaining the vanilla tail responsible for item migration. It fails closed on
version, class-hash, or schema mismatch. See [the repair evidence and verification limits](docs/trinkets-dfu-repair.md).

## Bee and spawner structure DFU validation

Phase 2B validates the exact Trek and Stoneholm runtime fixtures that previously produced bee and spawner decode failures. That run used the removed Trinkets 4.1.0 repair profile, so the fixtures still need re-validation with 4.1.1. The complete fixture provenance, hashes, coordinates, and vanilla-reference comparisons are recorded in [docs/bee-spawner-dfu-validation.md](docs/bee-spawner-dfu-validation.md) and [docs/evidence/phase2b-bees-spawner.json](docs/evidence/phase2b-bees-spawner.json).

## Grim Kingdoms 2.0.3 zero-level enchantments

The virtual resource patcher repairs 10 audited Grim Kingdoms structure templates, removing
32 zero-level enchantment entries from their item components. This includes the "way of the
fisherman" rods and affected wooden hoes. Minecraft 26.2 requires serialized enchantment
levels in 1..255. Positive levels, names, lore, attributes, inventory slots, entities, blocks,
and DataVersion remain unchanged; vanilla STRUCTURE DFU still performs schema migration.

Each replacement requires mod ID `mr_grim_kingdomsloststructuresruins`, version `2.0.3`,
and the exact original resource SHA-256. Missing/unreadable or changed originals are not patched.
The source JAR is never modified. Resource hashes and all removed NBT paths are recorded in
`docs/evidence/grim-zero-enchantments.json`. Reproduce or verify against the audited JAR with
Python and `nbtlib==2.0.4`:

```text
python tools/repair_grim_zero_enchantments.py <grim-kingdoms-2.0.3.jar>
```

Add `--write` to regenerate the replacement assets and evidence. Gradle tests independently
read original and replacement fixtures with Minecraft NbtIo and compare complete NBT after
only the recorded zero-level removals, alongside version/hash/missing-resource gate tests.
Automated tests and packaging are verified; live structure placement remains unverified.
This prevents malformed items in subsequently loaded templates; it does not restore items
already lost from saved chests. Deployment is a separate step.
