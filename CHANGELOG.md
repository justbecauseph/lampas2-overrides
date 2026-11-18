# Changelog

Releases before 1.0.2 are recorded only in the git history.

## 1.0.26

- **`/name other` requires permission level 2 again under Custom Name 0.4.4-26.2 with `require_permissions: false`.** With that setting, `CustomNameCommands#permissionCheck` returns an always-true predicate for every node, and `register` and `otherPlayerNameCommand` attach it to the `other` literal and to each `other prefix|suffix|nickname` literal, so any player could rename any other player. The common `customnamepermissions` mixin injects at `HEAD` of `permissionCheck` and returns `CommonPermissions.require(node, GAMEMASTERS)` only for the `customname:other` node, the predicate Custom Name builds when `require_permissions` is true. The other nodes keep the always-true predicate, so players still rename themselves. A permissions mod that sets `customname:other` overrides the level check, as Common Permissions does for any node. The mixin has its own config and gate plugin. It applies whenever any Custom Name version is installed, so an upgrade keeps the gate. On a version other than 0.4.4-26.2 it logs a WARN that the version is unaudited, and a moved member fails startup rather than opening `/name other`. `BYPASS_RESTRICTIONS` is read by a direct `checkPermission` call and is unchanged. The bridge reaches Custom Name's `OTHER` node and `CommonPermissions.require` by reflection and throws if either is missing. `CustomNamePermissionsMixinTest` reads the pinned Modrinth jar with ASM, which is byte-identical to the deployed jar, and pins the injection descriptor, the `requirePermissions` and `require` branch of `permissionCheck`, the only readers of `customname:other` (`register` and `otherPlayerNameCommand`), the only reader of `BYPASS_RESTRICTIONS` (`lambda$updatePlayerName$0`, behind `operatorsBypassRestrictions()`), and the full set of callers of `PlayerNameManager.updatePlayerName` and `CustomNameUtil.clearPlayerName`. `CustomNameOtherPermissionGameTests` runs Custom Name's real command tree on the GameTest server with `require_permissions` false and checks Brigadier's `canUse` for `/name other` at level 0, at level 2 and for a non-operator player, the own-name commands and the root `/itemname` and `/itemlore` for a player. A second case ops the mock player at level 2 through `PlayerList.op`, checks `/name other`, de-ops it and checks again. With the gate disabled the GameTests fail with `/name other is usable at permission level 0` and with the de-op assertion. Not changed: with `require_permissions` false, `operators_bypass_restrictions: true` would grant the restriction bypass to every player; the pipeline keeps it false. Checks run on 19 Nov 2026: `./gradlew build` passes with 285 unit tests (one skipped without `-PserverModsDir`, as before) and 17 GameTests; `./gradlew test -PserverModsDir=../lampas-server-fabric/mods` passes with 285 tests, none skipped. A dedicated server started from `build/run/server` with the deployed jar and Fabric API 0.161.0+26.2 logged `Enabled the Custom Name 0.4.4-26.2 /name other operator gate`, applied `CustomNameCommandsMixin` into `CustomNameCommands` with no injection failure, and reached `Done`. With that jar's `fabric.mod.json` version changed to 0.4.5-26.2, the same server logged the unaudited WARN, applied the mixin and reached `Done`. Not checked: a real client sending `/name other`, a permissions provider granting `customname:other`, and the live pipeline config, which also has a blacklist.

## 1.0.25

- **Magic Vibe Decorations blocks no longer tick.** Its Ender Amethyst, Nether Fire and Overworld Secrets crystals (with their druze, columns and crystallised forms) ran from a scheduled tick every 10 ticks that called `discard()` on Endermen and Phantoms within 10 blocks (no death or drops, so a nearby Gateway failed with `ENTITY_DISCARDED` and Apotheosis invaders vanished), dealt 50 generic damage to Creepers within 10 blocks, and set Zombies and Skeletons within 7.5 blocks on fire. The common `magicvibe` mixin cancels `BlockBehaviour.BlockStateBase#tick` at `HEAD` for any block in the `magic_vibe_decorations` namespace, so the effects, all server-sent particles of the mod's ticking blocks and the magic pot's bubble sound stop. It applies to any installed version, because 4.0.0 renamed every class but keeps the namespace; a version other than 3.0.1, the only one inspected, logs a WARN. A cancelled tick is not requeued, so removing the fix later leaves existing crystals inert until they are placed again. `MagicVibeTicksContractTest` checks against the 3.0.1 jar that these entity calls occur only in the 12 blocks' `tick` methods, that the mod has no other periodic hook, that every block extends `Block` directly, and that vanilla `BlockBehaviour#tick` is empty. `MagicVibeTicksGameTests` places the three crystals beside an Enderman, a Creeper and a Husk with the real 3.0.1 jar and checks that after 30 ticks all three are untouched and no tick is scheduled. With the mixin config removed, the GameTest fails because the Enderman is removed. Checks run on 18 Nov 2026: `./gradlew build` passes with 271 unit tests (one skipped without `-PserverModsDir`, as before) and 15 GameTests. Not checked: a dedicated server or client running the full pack.

## 1.0.24

- **Signs with a blank first line show their other lines under Signs Faster 1.0.3.** Signs Faster's `SignTextureManager.getOrCreateRegion` returned null whenever `getMessages(false)[0]` was empty, and a null region draws nothing. The mod had already cancelled vanilla's sign text, so a sign with a blank first line showed no text even when lines 2 to 4 had text. The texture generator already skips empty lines one at a time, so the first-line check was the only defect. The client-only `signsfaster` mixin wraps that one `String.isEmpty` call and returns true only when every line of `getMessages(false)` is empty. It calls the original check first, so a sign whose first line has text costs one extra branch and nothing more. It applies only when `signfasterlegacy` is exactly `1.0.3`. Any other installed version logs a WARN naming it and is left unchanged. The mixin targets the class by name, references no Signs Faster type, and adds only a soft `suggests` entry for `signfasterlegacy` 1.0.3 to `fabric.mod.json`. `SignsFasterContractTest` checks the Signs Faster 1.0.3 jar, pinned as the test-only Modrinth version `UsPHgp6z`, with ASM: `getOrCreateRegion` reads line 0, calls `String.isEmpty` once directly after `Component.getString` and returns null on an empty line, `generate` jumps from its one `String.isEmpty` check to the loop increment and back edge (a `continue`), the jar's `fabric.mod.json` declares the gated ID and version, and the mixin's target, handler and `@At` match. `SignsFasterBlankCheckTest` covers the all-empty and text-on-one-line cases. Checks run on 18 Nov 2026: `./gradlew build` passes, with 266 unit tests (one skipped without `-PserverModsDir`, as before) and the GameTests. A dev client with only Fabric API 0.161.0 and Signs Faster 1.0.3 in `run/mods/` logged `Enabled the Signs Faster 1.0.3 blank-first-line sign text fix`, and `SignTextureManagerMixin` applied into `org.lolip_p.signfasterlegacy.util.SignTextureManager` with no injection failure. Not checked: a sign in a world (text on lines 2 to 4 under a blank line 1) and the full pack.

## 1.0.23

- **Voxy errors no longer appear in chat.** Voxy 0.2.19-beta's `Logger.error` writes each error to the log and then posts it to chat through `Logger.showInHUD`, and Voxy has no setting to turn the chat copy off (its only switch, `Logger.SHUTUP`, also silences the log). `NodeManager`'s render-thread race `Tried processing request for pos: … but its type was a request, ignoring!` fills players' chat this way. The client-only `voxychat` mixin wraps that one `showInHUD` call in `Logger.error` with a false condition, so Voxy errors still reach `latest.log`. Voxy's direct `showInHUD` callers in `DebugUtils` are unchanged. It applies whenever Voxy is installed, with no version gate; the mixin binds by name and stops startup if the call moves. `./gradlew build` passes, including `VoxyChatContractTest`, which checks against the pinned Voxy jar that `error` has exactly one `showInHUD` call and `warn` has none. Checked on 18 Nov 2026 in a dev client with Voxy 0.2.19-beta, Sodium 0.9.2 and Fabric API 0.161.0: the client reached the title screen, logged `Enabled Voxy error suppression in chat`, and `VoxyLoggerMixin` applied into `me.cortex.voxy.common.Logger`. Not checked: a Voxy error actually being kept out of chat in a world, and the full pack.

## 1.0.22

- **Eleven healing spells add 0.1 x c x max health to their heal, so Flash Heal adds 12% of max health.** Circle of Healing, Flash Heal, Heal, Holy Beam, Holy Shock, Immolation, Lightwell Heal, Sacred Refuge, Seal of Light and the two relic perks healed only `c x P` (c the upstream `spell_power_coefficient`, P the healing power), which is small against the 70 to 380+ max health that tier bonuses give Lampas players. Eight impacts (all but Lightwell Heal and the relic perks) get `"attribute": "minecraft:max_health"` and a `power_blend`, and their coefficient becomes c x 1.1. Six of them blend the healing school at weight 10, so Spell Engine 1.10.9 computes their power as (max health + 10P) / 11 and the heal as c x P + 0.1 x c x max health. Immolation and Seal of Light blend 7.5 healing and 2.5 melee instead, which keeps their upstream melee share and crit blend. The other three (Lightwell Heal and the relic perks Greater Perk Defense Block and Greater Perk Heal Danger) keep their upstream impact unchanged and get one impact appended after it, with `minecraft:max_health` and coefficient 0.1 x c. Their upstream `min_power` (8.0 and 10.0 on the relic perks) clamps only the upstream part, so the share is not cut when healing power is below it. Max health is the target's for eight of the eleven and the caster's for the other three: the two relic perks have no `attribute_from_target`, and Seal of Light is `apply_to_caster`. Lightwell Heal's share is appended rather than blended because the lightwell casts with a cached power of 1.0 + 0.5 x its owner's healing power, and a blended entry would read the owner's full healing power.

  The generator's `HEAL` table writes the eleven replacements. Each is the upstream JSON with only the decided keys changed, or with the appended impact, and each is version- and SHA-gated like the other RPG Series spell patches (registry 108 to 119). `RpgSeriesSpellPatchTest` evaluates each replacement's total from its JSON alone at max health 380 and healing power 47, and checks the two relic perks at healing power 1 (7.0 and 9.0 at max health 200). Not handled: the share is inside the blended power, so skill-tree power modifiers, charge, channel and haste multiply it too (Flash Heal with a +10% power modifier adds 13.2%), and healing power is read live when the impact lands. Whether the three appended shares take the same multipliers is not checked. Tooltips estimate a target heal with the viewer's own max health. A direct heal on a NEUTRAL mob heals it by a share of that mob's pool; in 26.2 hoglins are NEUTRAL under `player_relation_to_passives`, and a server `player_relations` entry `"minecraft:hoglin": "HOSTILE"` would close that. That entry is not changed here.

  Checks run on 17 Nov 2026: `python -I tools/generate_rpg_spell_patches.py --check` passes; `./gradlew build` passes (257 unit tests, one skipped without `-PserverModsDir`, and the 14 GameTests); `./gradlew test -PserverModsDir=../lampas-server-fabric/mods` passes with 257 tests, none skipped, including the deployed-jar scan; `./gradlew runGameTest` passes with 14 required tests. The three heal GameTests cast test spells with the same shapes: on a 380 max health ally the upstream heal gives 48 and the share gives 93.6, a 100 max health caster's share gives 12.5, and an upstream 0.25 with min_power 8 plus an appended 0.025 share heals 11.5 at context power 1. Not verified: the eleven real spells on a live server or in combat, the client's decode of the replacements, tooltips, and the probe (not rerun; its control is now `paladins:divine_protection`).

## 1.0.21

- **Incendium's per-player held-item checks run only for players holding or wearing an Incendium item.** Incendium 5.5.1's `player/main` runs as every player every tick, and seven consecutive lines in it (Voltaic Trident, Hefty Pickaxe, Scout's Battle Helm, Firestorm, the two loaded-crossbow arrow checks, Sentry's Wrath) each build loot-table predicate contexts on the player's slots; the 200-player load test measured about 0.6–1.2 ms per tick for them in one plaza. Each of those lines' selectors includes a predicate that needs an item whose `custom_data` holds `{incendium:{item:'…'}}` in the main hand, off hand or head (checked in the 5.5.1 jar, including the referenced `holding/*/mainhand` and `offhand` files; the two arrow checks also test `holding/loaded`, which reads only the crossbow's charged projectiles, together with `holding/firestorm` or `holding/holy_wrath`). The `incendium_5_5_1_optimizations` pack now replaces `player/main` with upstream's text in which those seven lines moved, unchanged and in order, to `player/lampas_held_items`, called from the same place only when `@s[predicate=incendium:lampas/holds_incendium_item]` (those three slots, `custom_data` `{incendium:{}}`) matches. The selector form, as upstream uses, makes a predicate that fails to load evaluate false instead of dropping all of `player/main`. In 26.2 `CustomData.matchedBy` uses `NbtUtils.compareNbt(…, true)`, which treats the expected compound as a subset, so the empty `incendium` compound matches every Incendium item.
- **Incendium's frozen-mob walk runs only after a Chilling Blade hit.** The clock ran `execute as @e[type=#incendium:mobs,scores={in.frozen=1..},predicate=incendium:random/10]` every tick, which visits every loaded entity; in the spread-out load test it was about 8 ms per tick. `item/chilling_blade/on_hit`, the only function in 5.5.1 that sets `in.frozen`, now also sets `$lampas.frozen` in `in.dummy` to 200, and the clock runs the walk (moved to `clocks/lampas_frozen`) only while that score is positive. `lampas_frozen` counts it down and sets it back to 200 when it runs out while any loaded mob is still frozen. Known side effect: a mob still frozen when its chunk unloads, or frozen before this version is deployed, shows no freeze particles and does not count down after it loads again until the next Chilling Blade hit anywhere; likewise a player hit in PvP who logs out or stays dead while the score runs out. The profile now also fingerprints `player/main`, `item/chilling_blade/on_hit`, `entity/chilling` and the ten predicate files the seven held-item lines use.
  `./gradlew build` passes. `IncendiumCompatibilityTest` rebuilds upstream `player/main` and `on_hit` from the override files and compares their SHA-256 with the profile, decodes `{incendium:{}}` through `NbtPredicate.CODEC` and matches it against Incendium-style custom data, and pins `clocks/lampas_frozen`. On 17 Nov 2026 a temporary, uncommitted GameTest ran on this repo's GameTest server with the real Incendium 5.5.1 jar (SHA-256 `556ac19e…f3b5`) in its `mods/` folder and the Incendium clock running. The pack was selected, and with items from Incendium's own loot tables a loaded Sentry's Wrath in the main hand and a loaded Firestorm in the off hand were unloaded by their `check_arrow` lines, a Scout's Battle Helm on the head raised `in.scouts_helmet`, and a player with the helm in the chest slot and a loaded Sentry's Wrath in hotbar slot 5 failed the predicate and kept the crossbow loaded. A zombie hit through `item/chilling_blade/on_hit` got `in.frozen` 120 and `$lampas.frozen` 200, counted down while armed, was re-armed when the score was run out, and stopped counting while disarmed. With the predicate changed to `{incendium:{nope:1b}}` and the arming line removed, both tests failed. Not checked: the freeze particles themselves (they come from the same `entity/chilling` call that counts down), a real server or client, and the tick-time saving.

## 1.0.20

- **Gardener's Dream's pot-break check no longer stalls the server with many players online.** Gardener's Dream 2.6 runs `gardenersdream:pot_break_check` every 2.0 s by default, and it ran `pot_broken` as each online player. `pot_broken` has four `@e[type=block_display,tag=...]` selectors with no positional option, so each walks every loaded entity in every level, and it never reads the player it runs as. At 200 players that was 800 full entity walks per firing; the 200-player load test measured a 0.9–1.0 s stall every 40 ticks. The `gardeners_dream_optimizations` datapack replaces `pot_break_check` so `pot_broken` runs once per firing while at least one player is online, with upstream's three reschedule lines unchanged. It is gated on neither the mod version nor the upstream functions' content: `DatapackReplacement.registerAnyVersion` registers it whenever `mr_gardeners_dream` is installed, and the other datapacks keep their exact-version and SHA-256 gate. The equivalence was checked on 2.6 only; a later release that changes `pot_broken`, `plant_break`, `destroy_halfpot` or the reschedule lines still gets this override and must be re-audited by hand. `GardenersDreamCompatibilityTest` and `CompatibilityManifestTest` cover the profile and the replacement's structure. On 17 Nov 2026 a temporary, uncommitted GameTest on this repo's GameTest server ran with the real Gardener's Dream 2.6 jar in its `mods/` folder, and again with a copy whose `fabric.mod.json` declares version 2.7. Both runs enabled the pack (the second logged `Enabled the Gardener's Dream performance datapack for 2.7 (functions audited on 2.6)`) and selected it after `mr_gardeners_dream`. With one mock player online, one `function gardenersdream:pot_break_check` removed orphaned `smallpp` and `gd_halfpot_pot` displays in the Overworld and a `widepp` display in the Nether and dropped their plants, and a `smallpp` display on a flower pot stayed. Not checked: a real server with player-placed pots, and the tick-time saving.

## 1.0.19

- **Structory: Towers, Dungeons and Taverns shrines and Trek villages no longer generate waystones.** Sixteen template pools place waystones: seven under `structory_towers:waystones/*` (used by 23 tower structures), `nova_structures:shrine/waystone/waystone` (used by 11 shrine entry pieces) and eight under `trek:village/waystone/*` (seven of them used by 21 village town centers; none references `swamp`). Twelve place Waystones blocks. The four Structory `*fwaystone` pools place Fabric Waystones (`fwaystones:`) blocks, which decode to air because that mod is not in the pack; they are emptied too. The Trek pools place Waystones' 6x6x6 `waystones:village/*` plaza, so the patch repoints them instead of emptying them: each now points to `lampas:village/waystone_plaza/common` (seven pools) or `lampas:village/waystone_plaza/desert` (the `desert` pool), copies of Waystones 26.2.0.13's plaza templates with the two waystone blocks set to air, so Trek town centers keep the plaza and lose the waystone. The repointed plaza also no longer goes through Waystones' 100-block spacing check for `village/*/waystone` pieces, so it is placed in every Trek town center that has room for it. Each Structory and Dungeons replacement is the upstream pool with its single element set to `minecraft:empty_pool_element`, so the jigsaw places nothing. Each Trek replacement is the upstream pool with only its element's `location` changed. Each patch is gated on the exact mod version and the upstream SHA-256 and logs a WARN on any other version. Structures that start generating after the update change. Waystones stay in existing chunks, and a structure whose start was computed before the update keeps its stored waystone piece and still places it when its remaining chunks generate. Unit tests (`WaystonePoolPatchTest`) cover the exact version and SHA-256 gate, the WARN paths and that each replacement changes only the pool elements, and that the two plaza templates equal Waystones 26.2.0.13's with the two waystone blocks set to air. `tools/waystone-structures-probe` placed the 41 affected structures three times each on a disposable flat-world server (15 Nov 2026): without this mod 80 of 120 valid placements held Waystones blocks, with it none did, and six Trek village types placed the `lampas:village/waystone_plaza` piece. Not covered: `nova_structures:shrine_tower` and `trek:village/swamp_vanilla`, natural generation in real terrain, and how the structures look in a client.

## 1.0.18

- **The client no longer crashes at startup with Fancy World Animations 1.3.4 under EntityCulling 1.12.0.** Startup stopped with `@Shadow field culling was not located`. EntityCulling 1.12.0 (commit `f56e4a5` moved its occlusion library into the mod) retyped `CullTask.culling` from the external `com.logisticscraft.occlusionculling.OcclusionCullingInstance` to its own `OcclusionCulling` interface. Fancy World Animations 1.3.4 still shadows the old type in `CullTaskMixin`, and its `fwa.client.mixins.json` is `required`. The fix bundles MixinSquared 0.3.7-beta.3 as a nested jar. For the exact pair Fancy World Animations 1.3.4 and EntityCulling 1.12.0, a MixinSquared canceller removes only that mixin, and a client mixin re-adds the same animation culling at the TAIL of `CullTask#cullEntities` against the 1.12.0 types. Any other pair leaves Fancy World Animations' mixin unchanged. Upstream status: Fancy World Animations `main` 1.3.6 for Minecraft 26.3 fixed this on 12 Nov 2026 by using `EntityCullingAPIVersionless.getCULLING_EVENT()`, an API added in EntityCulling commit `2f3f417` after 1.12.0 and present in no release; its 26.2 branch is still 1.3.4.
  `./gradlew build` passes, including `FwaEntityCullingContractTest` against both pinned jars. Checked on 13 Nov 2026 in a dev client with Fancy World Animations 1.3.4 and EntityCulling 1.12.0: the client reached the title screen, MixinSquared logged `Cancelled mixin fr.madu59.fwa.mixin.client.compat.entityculling.CullTaskMixin`, and `CullTaskAnimationCullingMixin` applied into `dev.tr7zw.entityculling.CullTask` without an injection failure. Not checked: animation culling in a world, the full pack, and a dedicated server with C2ME, which nests MixinSquared 0.3.7-beta.1, selecting this mod's 0.3.7-beta.3 outside the GameTest server.

## 1.0.17

- **Status Effect Bars' duration bars now sit on the icons VanillaHUD draws.** VanillaHUD 3.5.10 wraps the two effect background `blitSprite` calls in `Hud#extractEffects` and shifts their x/y arguments (the harmful row moves up 26 px when there are no beneficial effects), but leaves the method's `x` and `y` locals unchanged. Status Effect Bars 1.0.12 draws its bar from those unshifted locals, so the bar sat where vanilla would have put the icon. The client-only `statuseffectbars` mixins record where each background blit is actually drawn and pass that position to Status Effect Bars. The alignment applies whenever both mods are installed, with no version gate. Unit and contract tests pass. Checked on 11 Nov 2026 in a dev client with Status Effect Bars 1.0.12, VanillaHUD 3.5.10 and OneConfig 1.6.2: all three mixins applied and, with Poison as the only effect, the bar sat on the icon in the top row. Not checked in the full pack.

## 1.0.16

- **Lootr containers no longer fetch their saved data every tick.** Lootr 1.24.41.124's `ILootrAPI#handleInstanceTick` looks up each loaded container's `LootrInventoryStore` (`LootrAPI.getData`) before checking its own `tick_delay` schedule, then discards it on every tick but one in 20–39. A common `HEAD` inject on `LootrAPI#handleInstanceTick`, gated on the exact Lootr 1.24.41.124 jar, returns early on those ticks; decay and refresh timing are unchanged. The 8 Nov 2026 production spark profile sampled the lookup at 0.44 ms/tick with one player. `LootrTickGateVendorContractTest` pins the upstream bytecode the gate relies on; the production saving is not yet measured. The `lootr_version` compile pin moves from the version number, which resolved to Lootr's NeoForge build, to the Fabric build's Modrinth version ID `HegkPUoM`; the `lootrfastframes` classes compile byte-identical.

## 1.0.15

- **JEI no longer aborts GUI startup with `VerifyError` when Just Enough Filters 3.2.0 is installed ([pSUNSET/JustEnoughFilters#11](https://github.com/pSUNSET/JustEnoughFilters/issues/11)).** Starting in JEI 30.47.0.239 (`mezz/JustEnoughItems@44bf26e`), JEI's published jar is shrunk with R8, which reuses dead parameter local-variable slots inside `IngredientFilter#getIngredientListUncached(String)` and `IngredientListOverlayController#updateBounds(IGuiProperties, Set)`. Just Enough Filters 3.2.0 declares those target parameters in `jef$applyFilters` and `jef$updateJefBounds` without reading them, so Mixin emitted `aload_1` at `RETURN`/`TAIL` and `IngredientFilter` failed JVM bytecode verification (`Type 'java/util/List' is not assignable to 'java/lang/String'`). A client mixin plugin gated on the exact Just Enough Filters 3.2.0 jar and any installed JEI version replaces the unused parameter `ALOAD` instructions before both injected handler calls with `ACONST_NULL` in `postApply`. `JustEnoughFiltersContractTest` inspects JEI 30.48.0.244, reproduces the `VerifyError` on the unpatched woven bytecode, and verifies that the patched classes pass the JVM verifier; in-game JEI overlay rendering is unverified.

## 1.0.14

- **Punchy! Moves no longer checks Modrinth for updates.** Its client entrypoint starts a Modrinth API request on every
  launch and draws a clickable update toast when it finds a newer release, with no setting to turn it off. The pack pins
  the version, so `UpdateNotification#initialize` is cancelled before the request starts. The descriptor was checked with
  `javap` in Punchy! Moves 1.0.4 and 1.0.5. A client gametest read `UpdateChecker#started` as false with this mixin and
  true without it.
- **The Figura root-pose fix still holds on Punchy! Moves 1.0.5.** 1.0.5 brings back vanilla's elytra pitch alongside its
  root-part pose. In the client gametest the avatar still stood upright without the 1.0.13 mixin, and with it followed
  the vanilla model's glide angle.

## 1.0.13

- **Figura avatars lie flat for elytra flight and swimming under Punchy! Moves.** Punchy! Moves tilts the body through
  the player model's root part and turns off vanilla's tilt; Figura never applied the root part, so avatars stood
  upright. Figura's `Avatar#render` now runs under the player model's root transform. Checked in a client gametest with
  a local avatar; not checked on a live server with authenticated avatars.

## 1.0.12

- **The creative inventory no longer crashes on the Wilder Delights tab.** Wilder Delights 1.0.0-26.2 builds its tab
  icon from `WWItems.SPLIT_COCONUT` read as an `Item`, but Wilder Wild 4.3 declares that field as `DeferredItem<Item>`,
  so drawing the tab threw `NoSuchFieldError`. A version-gated redirect replaces that field read with a registry lookup
  of `wilderwild:split_coconut`. `WilderDelightsTabIconContractTest` pins the Wilder Delights jar and checks the redirect
  against its bytecode. In the dev client with Wilder Wild 4.3.3 the mixin applied at startup; the tab icon was not
  checked in game.

## 1.0.11

- **Aeternium weapons can be crafted again.** Seven BetterEnd-gated Paladins and Rogues 3.1.3.003+26.2 recipes wrote the
  handle key as `["#c:wood_sticks", "minecraft:stick"]`, which the 26.2 ingredient codec rejects, so the server dropped
  them at every start. Version- and SHA-gated resource patches replace the key with `"minecraft:stick"`, matching
  upstream 3.1.4.1+26.3. Crafting in-game is unverified.
- **Builder's Boundry wall clocks change face again.** `diesse:acs/actions/clock/all` failed to load on 26.2 because
  `time query daytime` now reads `daytime` as a timeline id. The Builder's Boundry datapack replaces it with
  `time of minecraft:overworld query day`, which works in every dimension, and fixes the upstream `18000..18099` and
  `15000999` ranges. The 0.2 profile now also
  fingerprints the upstream clock function. The query was run over RCON on the production server; the clock faces were not checked in-game.

## 1.0.10

- **BetterEnd and BetterNether boat water masks are no longer version-pinned.** Pack 2.5.4 moved BetterX to
  26.201.5, which dropped both providers (40 boat layers) from the exact-version gate. Their boat files and
  wover-item's boat classes are byte-identical to 26.201.4, and the boat code has not changed functionally since 26.201.2, so both
  profiles now accept any release and the wover-item companion requirement is removed. The layer allowlist,
  the plain-hull and EMF `boat_patch.jem` root checks, the selected resource SHA-256 and the EMF 3.3.12 gate
  are unchanged; Pyrite, Promenade and Wilder Wild stay pinned. No boat probe ran for this change.
- Compatibility records updated for Chatting 3.2.5, Figura build 11f80e6a, ReplayMod 2.6.28 and Better Lib 2.1.3,
  each checked with `javap` against pack 2.5.4.

## 1.0.9

- **Fire Wall and the Meteor burning ground deal 40% of their upstream damage.** The caster loot scaling calibration
  (29 Oct 2026) could not hold a Haven fire wizard at 80% Fire Wall uptime at or below 70% of the warrior's damage
  through Apotheosis loot and World Tier augments alone, so both clouds' damage `spell_power_coefficient` is multiplied
  by 0.4: `wizards:fire_wall` 0.8 -> 0.32 and `skill_tree_rpgs:fire_tier_3_spell_1_modifier_2` (Meteor's burning
  ground) 0.3 -> 0.12. Hits still land, crit and trigger on-hit passives; Meteor's own hit is unchanged. Gated on
  Wizards 3.1.3.003+26.2 and Skill Tree 1.6.2+26.2 and each file's upstream SHA-256, like the other spell patches.
- **Frost crowd-control multipliers re-derived for Apotheosis caster loot.** With staff affixes, staff gems and the
  new tier augments, the modelled Pinnacle frost power is 119.19 (D9 used 56.99). Keeping each health_base and taking the
  smallest multiple of 0.5 with base + k x 119.19 >= 1.1 x 736.5: Frost Nova and Frost Spikes k 13.5 -> 6.5
  (60 + 6.5P, 834.7 at P 119.19), Frostbolt and Frost Lance 12.5 -> 6 (100 + 6P, 815.1) and Frost Blizzard
  12.5 -> 6.5 (80 + 6.5P, 854.7), all above the End top band (736.5). A frost wizard without Apotheosis loot
  (modelled P 67.27 at Pinnacle) now holds smaller mobs: 497-517 health instead of 921-968, below the Overworld
  top band (517.5).
- Unit tests cover the new values, the frost rule at every tier's modelled frost power and both fire files against the
  deployed jars. On 29 Oct 2026 lampas-yggdrasil's balance GameTests ran this build (SHA-256 ef14f50d...) on a
  Fabric GameTest server: all 66 spell patches logged as applied with no WARN, and Fire Wall cast by a mock player
  landed spell power x 0.32 in all 40 hit checks (Haven to Pinnacle, zombie and piglin, crit on and off). The frost
  apply limits and the burning-ground coefficient have unit tests only, `tools/rpg-spell-patch-probe` was not rerun,
  and the client-side registry decode is unverified.

## 1.0.8

- **Thrown-trident crits.** Critical Strike rolls ranged crits in `AbstractArrow.onHitEntity`, which `ThrownTrident`
  overrides without calling, so thrown tridents never crit and a trident's crit affixes, gems and enchantments only
  helped melee hits (lampas-two-planning crit compatibility audit, 25 Oct 2026). `tridentcrits` takes the thrower's
  Critical Strike chance and multiplier in `TridentItem.releaseUsing`, while the trident is still in hand, and rolls the
  crit around the trident's own `hurtOrSimulate` call: the damage is multiplied by the multiplier and
  `balance_ranged_damage_multiplier`, the damage source is marked critical as Critical Strike's own crits are, and
  Critical Strike's crit sound and particles play. `enable_ranged_criticals: false` turns it off. The thrower's Spell
  Engine on-crit passives still do not fire from a trident, and a trident that changes dimension or is saved in flight
  loses its snapshot. Applies whenever Critical Strike is installed; its members are resolved by name, and
  `CriticalStrikeContractTest` pins the inspected 1.0.6+26.2. Covered by a GameTest with the real Critical Strike jar,
  including a trident whose own modifier gives the crit chance; crit FX and dev-server play are unverified.

## 1.0.7

- **Builder's Boundry 0.2 tick functions.** Upstream `diesse:acs/main` ran 26 untyped `@e[tag=acs.*]` selectors every
  tick, each a full walk of the loaded entity list; it was the largest single contributor to the `#minecraft:tick`
  function cost in the 19 Oct production profile (lampas-two-planning `docs/records/profiles/2026-10-19-spark-profile.md`).
  The version-gated `builders_boundry_0_2_optimizations` datapack replaces `acs/main` with one
  `@e[tag=acs.acc.block]` walk every 4 ticks that dispatches per furniture entity with `@s` checks, calling the
  upstream delete, face and action functions in the upstream order and stopping after one removes the entity. Light
  re-placement, clock faces, cutting-board rotation and support/facing give-back now react within 4 ticks instead of 1.
  Gated on the exact `mr_builders_boundry` version and the SHA-256 of the seven upstream functions it depends on.
- **Dungeons and Taverns 5.3.2 Swift Soar tick.** Upstream `nova_structures:tick` ran
  `attribute @s movement_speed modifier remove nova_structures:boost_player_fov` for every player every tick; it
  fails for everyone not riding a sprinting happy ghast and built a `CommandSyntaxException` message each time
  (~24 µs per player-tick in the profile). The version-gated `dungeons_and_taverns_5_3_2_optimizations` datapack
  tags passengers in `swift_soar_1..3` when the modifier is added (`lampas.dnt_fov_boost`) and limits the per-tick
  removal to tagged players who are no longer riding, with a one-time per-player migration (`lampas.dnt_fov_migrated`)
  for modifiers applied before the override. The chart-bundle line and the three enchantment functions are otherwise
  verbatim.
- Shared `lampas2overrides.datapack` package (`DatapackReplacement`, `ResourceFingerprint`) for version- and
  fingerprint-gated built-in datapacks; the Incendium code is unchanged and still carries its own copy.
- Runtime behaviour of both packs on a dedicated server is unverified at release time; see `compatibility-targets.json`.

## 1.0.6

- **Voxy LODs under Iris shader packs.** With Voxy 0.2.19-beta and Iris 1.11.4+mc26.2, shader packs drew LOD terrain
  as sky ([MCRcortex/voxy#700](https://github.com/MCRcortex/voxy/issues/700)): Iris 1.11.4 keeps the game reverse-Z,
  while Voxy still forced forward-Z under packs. The client-only fix backports Voxy's upstream `263` branch handling:
  Voxy stays reverse-Z, the pack's Voxy patch goes through Iris's `DepthTransformer`, `vxProj`/`vxViewProj` are
  converted to forward-Z, and `vxNearPlane`/`vxFarPlane` are added. It is gated on both exact artifacts (version and
  JAR SHA-256) and is temporary until Voxy releases the fix for 26.2. In-game LOD rendering is unverified.

## 1.0.5

- **Boat water mask re-pinned to BetterEnd and BetterNether 26.201.4** (with `wover-item` 26.201.4), the versions in
  pack 2.3.61. Under 1.0.4 the exact-version gate turned the fix off for both mods, so their 40 boat and chest-boat
  layers rendered the water plane inside the hull with Fresh Animations Extensions. Their boat files and wover-item's boat
  renderer classes are byte-identical to 26.201.3, which is retired.

## 1.0.4

- **Spell crowd-control immunity** moved here from lampas-addons 1.0.5. Apotheosis invaders and elites, the mounts they
  ride, and mobs tagged `lampas.cc_immune` no longer take RPG Series crowd control: Spell Engine's movement-impairing and
  AI-disabling effects, Shatter and Demoralize (`#lampas:spell_crowd_control`), and harmful knock-ups and drags. Other
  debuffs, damage and damage knockback still apply. `/lampas ccimmunity check <targets>` (op level 2) reports why a mob is
  or is not protected. The tag names and the command are unchanged.
- Unlike in lampas-addons, the hooks are not limited to Spell Engine 1.10.9+26.2: they apply to any installed Spell Engine
  and fail startup loudly at the first spell impact if a wrapped call has moved. 1.10.9+26.2 is the inspected and tested
  release.
- Ship with lampas-addons 1.0.6, which drops its copy. Running lampas-addons 1.0.5 next to this release applies both copies
  of the hooks; both copies make the same decision, so the result should not change, but that pairing was not tested.

## 1.0.3

- **Hotbar slot resync after respawn.** Debugify's MC-143474 fix restores the pre-death hotbar slot on the client
  without sending it, so the server stayed on slot 0: Better Combat dropped attacks with `No current attack hand!`
  and other main-hand actions used the wrong item until the player changed slots. A client mixin resets
  `MultiPlayerGameMode.carriedIndex` whenever a local player is created, so the next tick sends the real slot. It is
  not gated on Debugify or any version, and needs the client jar.

## 1.0.2

- **Martial spell cooldowns ignore haste** (class balance D3 P1). 59 RPG Series spells in the physical_melee,
  physical_melee_dual and physical_ranged schools with a cooldown get `cost.cooldown.haste_affected: false`:
  11 Rogues & Warriors, 10 Spell Engine weapon skills, Paladin Judgement, 5 Archers, 18 Skill Tree passives and
  14 Relics procs. Attack speed, ranged haste and spell haste no longer shorten them; caster and healer cooldowns,
  the global `haste_affects_cooldown` switch and cast times are unchanged.
- **Crowd-control apply limits reach Pinnacle** (D9). Twelve status-effect apply limits are raised: Frost Nova and
  Frost Spikes 60+13.5P, Frostbolt and Frost Lance 100+12.5P, Frost Blizzard 80+12.5P, Judgement 50+7.5P,
  Shout 50+9.5P, Throw and Throw Net 100+8.5P, Shock Powder 50+7.5P, Bear Trap 100+5.5P, and Entangling Roots a
  flat 780+5P. Apotheosis elite and invader immunity comes from lampas-addons and must ship in the same pack.
- All 64 spell patches are resource patches gated on the exact mod version (Rogues, Wizards, Paladins and Archers
  3.1.3.003+26.2; Spell Engine 1.10.9+26.2; Skill Tree 1.6.2+26.2; Relics 1.4.1+26.2) and the upstream file's
  SHA-256. Another release or a changed file keeps the upstream spell and logs a WARN.
- Spells are a synced registry, so the jar must stay on both server and client.
