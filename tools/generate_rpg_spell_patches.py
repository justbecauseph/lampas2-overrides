"""Generates the RPG Series spell resource patches (class balance decisions D3 P1 and D9, 17 Oct 2026, the
caster loot scaling calibration, 29 Oct 2026, and the HEAL healing share).

Reads the seven pinned RPG Series jars and, for every data/<ns>/spell/*.json entry:

- P1: a spell whose school is physical_melee, physical_melee_dual or physical_ranged, whose
  cost.cooldown.duration is above 0 and whose cost.cooldown.haste_affected is not already false gets
  "haste_affected": false appended as the last key of cost.cooldown;
- D9: the twelve audited status-effect apply limits get the decided health_base and spell_power_multiplier
  (the five frost multipliers re-derived by the caster loot calibration);
- FIRE: Fire Wall and the Meteor burning ground (Skill Tree fire_tier_3_spell_1_modifier_2) get their damage
  spell_power_coefficient multiplied by 0.4;
- HEAL: eleven healing spells add 0.1 x c x max health to their heal (c the upstream spell_power_coefficient,
  P the healing power). Eight healing impacts are rewritten: each reads minecraft:max_health, from the target or,
  where the table says so, from the caster, blended with the spell's school at weight 10 (plain) or with the 7.5
  healing and 2.5 melee blend that keeps the upstream melee share (blended), and its coefficient becomes c x 1.1.
  Three impacts (Lightwell Heal and the two relic perks, whose min_power clamps the blended power) are left
  unchanged, and one impact with minecraft:max_health and coefficient 0.1 x c is inserted directly after each.

Every upstream spell file round-trips byte-exactly through json.dumps(indent=2, ensure_ascii=False) with no
trailing newline (asserted below), so each replacement differs from upstream only in the decided keys.

Writes the replacements under src/main/resources/lampas2-overrides/resource-patches/, the byte-exact upstream
fixtures under src/test/resources/upstream/, and docs/evidence/rpg-spell-patches.manifest.json. With --check it
writes nothing and fails if any of those files differs from what the jars produce; the manifest is compared
as JSON, since the committed copy is the plan's scratch manifest byte for byte.

    python -I tools/generate_rpg_spell_patches.py [--mods <server mods dir>] [--check]

Re-pin by updating JARS and rerunning; RpgSeriesSpellPatches.java and compatibility-targets.json must follow.
"""

import argparse
import copy
import hashlib
import json
import pathlib
import sys
import zipfile

REPO = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_MODS = REPO.parent / "lampas-server-fabric" / "mods"
REPLACEMENTS = REPO / "src" / "main" / "resources" / "lampas2-overrides" / "resource-patches"
FIXTURES = REPO / "src" / "test" / "resources" / "upstream"
MANIFEST = REPO / "docs" / "evidence" / "rpg-spell-patches.manifest.json"

# jar file, mod id, declared version, jar SHA-256 (pack 2.3.54, pack.lock.json and dev server).
JARS = [
    ("archers-fabric-3.1.3.003+26.2.jar", "archers", "3.1.3.003+26.2",
     "4ee9c35f54d74f28f742f36a4f0b319d4d3936bc0731bd95cb823c17225db22d"),
    ("paladins-fabric-3.1.3.003+26.2.jar", "paladins", "3.1.3.003+26.2",
     "f36009e6a60fae66ca195fc9165a2d7a59c92bd55fd75b1e2e18620ffa4fc492"),
    ("rogues-fabric-3.1.3.003+26.2.jar", "rogues", "3.1.3.003+26.2",
     "77bdff881c01011bff6e6e722a1f47dc01ca7d377a1f08231d305fc11b08f113"),
    ("wizards-fabric-3.1.3.003+26.2.jar", "wizards", "3.1.3.003+26.2",
     "ced172215b3f5fb4301252d171e26c621fbebcc579fc67bd639fb2d92a8034a2"),
    ("spell_engine-fabric-1.10.9+26.2.jar", "spell_engine", "1.10.9+26.2",
     "bfc258875662071d76c448e603f05f0f519bf3e8a0c32207d232d820cab0ab2a"),
    ("skill_tree-fabric-1.6.2+26.2.jar", "skill_tree_rpgs", "1.6.2+26.2",
     "405ff761ba518c87a14d8923f0c8db5f01311b86d58ca78c3e1f2731d3469901"),
    ("relics-fabric-1.4.1+26.2.jar", "relics_rpgs", "1.4.1+26.2",
     "013eae223a352138342e5d629e5fec75ee7f19405b82589435993f403be8469f"),
]

PHYSICAL = {"spell_power:physical_melee", "spell_power:physical_melee_dual", "spell_power:physical_ranged"}

# (mod, path) -> (impact index, upstream health_base, upstream k, new health_base, new k)
# Frost k (29 Oct 2026): the smallest multiple of 0.5 with base + k x 119.19 >= 1.1 x 736.5, where 119.19 is the
# modelled Pinnacle frost power with realistic gear and Apotheosis caster loot.
D9 = {
    ("wizards", "data/wizards/spell/frost_nova.json"): (1, 60.0, 4.0, 60.0, 6.5),
    ("wizards", "data/wizards/spell/frost_spikes.json"): (1, 60.0, 4.0, 60.0, 6.5),
    ("wizards", "data/wizards/spell/frostbolt.json"): (1, 100.0, 4.0, 100.0, 6.0),
    ("wizards", "data/wizards/spell/frost_lance.json"): (1, 100.0, 4.0, 100.0, 6.0),
    ("wizards", "data/wizards/spell/frost_blizzard.json"): (1, 80.0, 4.0, 80.0, 6.5),
    ("paladins", "data/paladins/spell/judgement.json"): (1, 50.0, 2.0, 50.0, 7.5),
    ("rogues", "data/rogues/spell/shout.json"): (0, 50.0, 2.0, 50.0, 9.5),
    ("rogues", "data/rogues/spell/throw.json"): (1, 100.0, 2.0, 100.0, 8.5),
    ("rogues", "data/rogues/spell/throw_net.json"): (1, 100.0, 2.0, 100.0, 8.5),
    ("rogues", "data/rogues/spell/shock_powder.json"): (0, 50.0, 2.0, 50.0, 7.5),
    ("rogues", "data/rogues/spell/bear_trap.json"): (1, 100.0, 2.0, 100.0, 5.5),
    ("archers", "data/archers/spell/entangling_roots.json"): (0, 50.0, 5.0, 780.0, 5.0),
}

FIRE_FACTOR = 0.4
# (mod, path) -> (impact index, upstream spell_power_coefficient, new coefficient); new = upstream x FIRE_FACTOR,
# written out because 0.8 * 0.4 is 0.32000000000000006 in floating point.
FIRE = {
    ("wizards", "data/wizards/spell/fire_wall.json"): (0, 0.8, 0.32),
    ("skill_tree_rpgs", "data/skill_tree_rpgs/spell/fire_tier_3_spell_1_modifier_2.json"): (0, 0.3, 0.12),
}

HEAL_SHARE = 0.1
# The upstream power_blend of the two blended heals: physical melee at weight 1/3, plus its critical chance and damage.
HEAL_UPSTREAM_BLEND = [{"school": "spell_power:physical_melee", "power": True, "critical_chance": True,
                        "critical_damage": True, "weight": 0.33333334}]
# The new blend of the two blended heals. With the x 1.1 coefficient, weights 7.5 (healing) and 2.5 (melee) give
# c x (0.75 P_heal + 0.25 P_melee), the upstream power, plus 0.1 x c x max health; the melee crit blend is unchanged.
HEAL_BLENDED = [
    {"school": "spell_power:healing", "power": True, "weight": 7.5},
    {"school": "spell_power:physical_melee", "power": True, "weight": 2.5},
    {"school": "spell_power:physical_melee", "critical_chance": True, "critical_damage": True, "weight": 0.33333334},
]
# (mod, path) -> (impact location, upstream spell_power_coefficient, new coefficient, from_target, kind). The location is
# ("impacts", i) or ("modifiers", 0, "impacts", i). kind "plain": the upstream impact has no power_blend and is rewritten
# with a healing-school blend at weight 10 (new = upstream x 1.1). kind "blended": the upstream power_blend is
# HEAL_UPSTREAM_BLEND and is rewritten to HEAL_BLENDED (new = upstream x 1.1). kind "append": the upstream impact is kept
# unchanged and one impact is inserted directly after it, with minecraft:max_health and coefficient 0.1 x upstream
# (new is that share). min_power on the upstream impact clamps only the upstream part, so the appended share is not
# clamped. from_target False reads the attribute from the caster: the apply_to_caster modifier and the two relic perks.
# New coefficients are written out, because 1.2 * 1.1 is not exactly 1.32 in floating point.
HEAL = {
    ("paladins", "data/paladins/spell/circle_of_healing.json"): (("impacts", 0), 0.4, 0.44, True, "plain"),
    ("paladins", "data/paladins/spell/flash_heal.json"): (("impacts", 0), 1.2, 1.32, True, "plain"),
    ("paladins", "data/paladins/spell/heal.json"): (("impacts", 0), 0.5, 0.55, True, "plain"),
    ("paladins", "data/paladins/spell/holy_beam.json"): (("impacts", 0), 0.4, 0.44, True, "plain"),
    ("paladins", "data/paladins/spell/holy_shock.json"): (("impacts", 0), 0.4, 0.44, True, "plain"),
    ("paladins", "data/paladins/spell/lightwell_heal.json"): (("impacts", 0), 0.35, 0.035, True, "append"),
    ("relics_rpgs", "data/relics_rpgs/spell/greater_perk_defense_block.json"):
        (("impacts", 0), 0.25, 0.025, False, "append"),
    ("relics_rpgs", "data/relics_rpgs/spell/greater_perk_heal_danger.json"):
        (("impacts", 0), 0.3, 0.03, False, "append"),
    ("skill_tree_rpgs", "data/skill_tree_rpgs/spell/priest_tier_4_spell_2_modifier_1.json"): (("impacts", 0), 0.1, 0.11, True, "plain"),
    ("paladins", "data/paladins/spell/immolation.json"): (("impacts", 2), 0.5, 0.55, True, "blended"),
    ("skill_tree_rpgs", "data/skill_tree_rpgs/spell/paladin_tier_2_spell_2_modifier_2.json"):
        (("modifiers", 0, "impacts", 0), 0.25, 0.275, False, "blended"),
}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def dump(spell):
    return json.dumps(spell, indent=2, ensure_ascii=False).encode("utf-8")


def is_spell(name):
    parts = name.split("/")
    return len(parts) >= 4 and parts[0] == "data" and parts[2] == "spell" and name.endswith(".json")


def impact_slot(spell, location):
    """(list, index) holding the impact at location, such as ("impacts", 0) or ("modifiers", 0, "impacts", 0)."""
    node = spell
    for step in location[:-2]:
        node = node[step]
    return node[location[-2]], location[-1]


def location_text(location):
    return ".".join(f"{location[i]}[{location[i + 1]}]" for i in range(0, len(location), 2))


def heal_impact(mod, path, spell, location, old, new, from_target, kind):
    """Rewrites one HEAL impact in place, or appends its share impact, and returns the change text. Fails hard on any
    unexpected upstream shape."""
    if kind == "append":
        if abs(new - old * HEAL_SHARE) > 1e-9:
            raise SystemExit(f"{mod} {path}: share {new:g} is not {old:g} x {HEAL_SHARE:g}")
    elif abs(new - old * (1 + HEAL_SHARE)) > 1e-9:
        raise SystemExit(f"{mod} {path}: {new:g} is not {old:g} x {1 + HEAL_SHARE:g}")
    if spell.get("school") != "spell_power:healing":
        raise SystemExit(f"{mod} {path}: top-level school {spell.get('school')} is not spell_power:healing")
    container, index = impact_slot(spell, location)
    impact = container[index]
    action = impact.get("action") or {}
    heal = action.get("heal") or {}
    if action.get("type") != "HEAL" or heal.get("spell_power_coefficient") != old:
        raise SystemExit(f"{mod} {path} {location_text(location)}: not a HEAL impact with spell_power_coefficient {old:g}")
    if "attribute" in impact or "school" in impact:
        raise SystemExit(f"{mod} {path} {location_text(location)} already has an attribute or school")
    if action.get("apply_to_caster") and from_target:
        raise SystemExit(f"{mod} {path}: an apply_to_caster impact must not read attribute_from_target")
    source = "target" if from_target else "caster"
    if kind in ("plain", "append") and "power_blend" in impact:
        raise SystemExit(f"{mod} {path} {location_text(location)}: {kind} heal has a power_blend")
    if kind == "append":
        appended = {"attribute": "minecraft:max_health"}
        if from_target:
            appended["attribute_from_target"] = True
        appended["action"] = {"type": "HEAL", "heal": {"spell_power_coefficient": new}}
        container.insert(index + 1, appended)
        appended_location = location[:-1] + (location[-1] + 1,)
        return (f"{location_text(appended_location)} appended: action.heal.spell_power_coefficient {new:g} x {source} "
                f"max_health")
    if kind == "plain":
        blend = [{"school": spell["school"], "power": True, "weight": 10.0}]
        blend_text = "spell_power:healing x10"
    elif kind == "blended":
        if impact.get("power_blend") != HEAL_UPSTREAM_BLEND:
            raise SystemExit(f"{mod} {path} {location_text(location)}: power_blend is not the decided melee blend")
        blend = copy.deepcopy(HEAL_BLENDED)
        blend_text = "spell_power:healing x7.5 and spell_power:physical_melee x2.5 (melee crit blend kept)"
    else:
        raise SystemExit(f"{mod} {path}: unknown heal kind {kind}")
    rebuilt = {"attribute": "minecraft:max_health"}
    if from_target:
        rebuilt["attribute_from_target"] = True
    rebuilt["power_blend"] = blend
    rebuilt.update((key, value) for key, value in impact.items() if key != "power_blend")
    rebuilt["action"]["heal"]["spell_power_coefficient"] = new
    container[index] = rebuilt
    return (f"{location_text(location)}.action.heal.spell_power_coefficient {old:g} -> {new:g}; "
            f"power = {source} max_health blended with {blend_text}")


def patch_spell(mod, path, raw):
    """Returns (replacement bytes, changes) for one upstream spell, or (None, []) when nothing applies."""
    spell = json.loads(raw.decode("utf-8"))
    if dump(spell) != raw:
        raise SystemExit(f"{mod} {path} does not round-trip through json.dumps(indent=2); diffs would not be minimal")
    changes = []
    cooldown = (spell.get("cost") or {}).get("cooldown") or {}
    if spell.get("school") in PHYSICAL and cooldown.get("duration", 0) > 0 and cooldown.get("haste_affected", True):
        if "haste_affected" in cooldown:
            raise SystemExit(f"{mod} {path}: explicit haste_affected true; appending would not keep it last")
        cooldown["haste_affected"] = False
        changes.append("cost.cooldown.haste_affected=false")
    limit = D9.get((mod, path))
    if limit is not None:
        index, old_base, old_k, new_base, new_k = limit
        apply_limit = spell["impacts"][index]["action"]["status_effect"]["apply_limit"]
        if (apply_limit["health_base"], apply_limit["spell_power_multiplier"]) != (old_base, old_k):
            raise SystemExit(f"{mod} {path}: upstream apply_limit {apply_limit} is not {old_base:g}+{old_k:g}P")
        apply_limit["health_base"], apply_limit["spell_power_multiplier"] = new_base, new_k
        changes.append(f"impacts[{index}].action.status_effect.apply_limit "
                       f"{old_base:g}+{old_k:g}P -> {new_base:g}+{new_k:g}P")
    elif b'"apply_limit"' in raw:
        raise SystemExit(f"{mod} {path} has an apply_limit outside the decided D9 table")
    coefficient = FIRE.get((mod, path))
    if coefficient is not None:
        index, old, new = coefficient
        if abs(new - old * FIRE_FACTOR) > 1e-9:
            raise SystemExit(f"{mod} {path}: {new:g} is not {old:g} x {FIRE_FACTOR:g}")
        damage = spell["impacts"][index]["action"]["damage"]
        if damage["spell_power_coefficient"] != old:
            raise SystemExit(f"{mod} {path}: upstream spell_power_coefficient {damage['spell_power_coefficient']} "
                             f"is not {old:g}")
        damage["spell_power_coefficient"] = new
        changes.append(f"impacts[{index}].action.damage.spell_power_coefficient {old:g} -> {new:g}")
    heal = HEAL.get((mod, path))
    if heal is not None:
        location, old, new, from_target, kind = heal
        changes.append(heal_impact(mod, path, spell, location, old, new, from_target, kind))
    return (dump(spell), changes) if changes else (None, [])


def workstream(change):
    if change.startswith("cost"):
        return "P1"
    if ".heal." in change:
        return "HEAL"
    return "FIRE" if ".damage." in change else "D9"


def build(mods_dir):
    entries = []
    files = {}
    seen_limits = set()
    seen_coefficients = set()
    seen_heals = set()
    for jar_name, mod, version, jar_sha in JARS:
        jar = mods_dir / jar_name
        if sha256(jar.read_bytes()) != jar_sha:
            raise SystemExit(f"{jar} does not match its pinned SHA-256 {jar_sha}")
        with zipfile.ZipFile(jar) as archive:
            metadata = json.loads(archive.read("fabric.mod.json").decode("utf-8"), strict=False)
            if (metadata["id"], metadata["version"]) != (mod, version):
                raise SystemExit(f"{jar_name} declares {metadata['id']} {metadata['version']}")
            for name in sorted(n for n in archive.namelist() if is_spell(n)):
                raw = archive.read(name)
                replacement, changes = patch_spell(mod, name, raw)
                if replacement is None:
                    continue
                if (mod, name) in D9:
                    seen_limits.add((mod, name))
                if (mod, name) in FIRE:
                    seen_coefficients.add((mod, name))
                if (mod, name) in HEAL:
                    seen_heals.add((mod, name))
                files[REPLACEMENTS / mod / version / name] = replacement
                files[FIXTURES / mod / version / name] = raw
                entries.append({
                    "mod": mod, "version": version, "jar": jar_name, "path": name,
                    "upstream_sha256": sha256(raw), "replacement_sha256": sha256(replacement),
                    "changes": changes,
                    "workstreams": sorted({workstream(c) for c in changes}),
                })
    missing = set(D9) - seen_limits
    if missing:
        raise SystemExit(f"Decided apply limits not found in the jars: {sorted(missing)}")
    missing = set(FIRE) - seen_coefficients
    if missing:
        raise SystemExit(f"Decided fire coefficients not found in the jars: {sorted(missing)}")
    missing = set(HEAL) - seen_heals
    if missing:
        raise SystemExit(f"Decided heals not found in the jars: {sorted(missing)}")
    return entries, files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=pathlib.Path, default=DEFAULT_MODS)
    parser.add_argument("--check", action="store_true", help="verify the committed files; write nothing")
    args = parser.parse_args()

    entries, files = build(args.mods)
    p1 = sum("P1" in e["workstreams"] for e in entries)
    d9 = sum("D9" in e["workstreams"] for e in entries)
    both = sum(e["workstreams"] == ["D9", "P1"] for e in entries)
    fire = sum("FIRE" in e["workstreams"] for e in entries)
    heal = sum("HEAL" in e["workstreams"] for e in entries)
    print(f"{len(entries)} replacements; {p1} P1; {d9} D9; {both} both; {fire} FIRE; {heal} HEAL")

    committed = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.is_file() else None
    key = lambda e: (e["mod"], e["path"])
    manifest_current = committed is not None and sorted(committed, key=key) == sorted(entries, key=key)

    if args.check:
        stale = [str(path.relative_to(REPO)) for path, data in files.items()
                 if not path.is_file() or path.read_bytes() != data]
        if not manifest_current:
            stale.append(str(MANIFEST.relative_to(REPO)))
        # Spell files the jars no longer produce, e.g. from a release before a re-pin.
        for tree in (REPLACEMENTS, FIXTURES):
            for _, mod, _, _ in JARS:
                for path in sorted((tree / mod).glob("*/data/*/spell/**/*.json")):
                    if path not in files:
                        stale.append(str(path.relative_to(REPO)) + " (not produced by the pinned jars)")
        if stale:
            print("Differs from the jars:\n  " + "\n  ".join(stale))
            return 1
        print("All replacements, fixtures and the manifest match the pinned jars.")
        return 0

    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    # Keep the committed bytes (the plan records their SHA-256) unless the content changed.
    if not manifest_current:
        MANIFEST.write_bytes(json.dumps(entries, indent=1).encode("utf-8"))
    for entry in entries:
        print(entry["mod"], entry["path"], entry["upstream_sha256"], "->", entry["replacement_sha256"],
              "|", "; ".join(entry["changes"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
