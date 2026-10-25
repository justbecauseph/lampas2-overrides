# Changelog

Releases before 1.0.2 are recorded only in the git history.

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
