# Changelog

Releases before 1.0.2 are recorded only in the git history.

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
