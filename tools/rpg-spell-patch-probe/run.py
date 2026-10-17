"""Disposable dedicated-server probe for the RPG Series spell patches (D3 P1 cooldown haste, D9 apply limits).

Builds a fresh server under build/rpg-spell-patch-probe/<label>/ with the seven pinned RPG Series jars, their
dependency closure taken from the deployed server's mods directory, a small probe mod and, unless --baseline,
the overrides jar. Once the server has started, the probe writes rpg-spell-patch-probe.json with, for every
spell in docs/evidence/rpg-spell-patches.manifest.json and the hasted controls:

- the decoded minecraft:spell entry's school, cost.cooldown.duration and haste_affected;
- SpellPower.getHaste and SpellParameters.getCooldownDuration for a FakePlayer with attack speed x2,
  ranged_weapon:haste +100 and spell_power:haste +100;
- for the D9 spells, the decoded apply_limit and SpellImpacts.underApplyLimit against a zombie whose max
  health is the Overworld, Nether or End top band, at threshold -/+ 0.5 and at the modelled Pinnacle power,
  plus whether Spell Engine's boss gate classifies the effect as movement impairing or AI disabling;
- the SHA-256 and source pack the resource manager serves through getResource and listResources.

Expected values come from DECISION and TOP_BAND below and the manifest's upstream/replacement SHA-256
values, never from the overrides jar. results.json lists every mismatch under "failures".

Never point this at the live server directory: it creates and deletes its own root under build/.
"""

import argparse
import hashlib
import io
import json
import os
import pathlib
import re
import shutil
import subprocess
import time
import zipfile

REPO = pathlib.Path(__file__).resolve().parents[2]
PROBE_DIR = pathlib.Path(__file__).resolve().parent
DEFAULT_RUNTIME = REPO / "run" / "phase2a-server-smoke"
DEFAULT_MODS = REPO.parent / "lampas-server-fabric" / "mods"

ROOTS = {
    "rogues": ("rogues-fabric-3.1.3.003+26.2.jar", "3.1.3.003+26.2",
               "77bdff881c01011bff6e6e722a1f47dc01ca7d377a1f08231d305fc11b08f113"),
    "wizards": ("wizards-fabric-3.1.3.003+26.2.jar", "3.1.3.003+26.2",
                "ced172215b3f5fb4301252d171e26c621fbebcc579fc67bd639fb2d92a8034a2"),
    "paladins": ("paladins-fabric-3.1.3.003+26.2.jar", "3.1.3.003+26.2",
                 "f36009e6a60fae66ca195fc9165a2d7a59c92bd55fd75b1e2e18620ffa4fc492"),
    "archers": ("archers-fabric-3.1.3.003+26.2.jar", "3.1.3.003+26.2",
                "4ee9c35f54d74f28f742f36a4f0b319d4d3936bc0731bd95cb823c17225db22d"),
    "spell_engine": ("spell_engine-fabric-1.10.9+26.2.jar", "1.10.9+26.2",
                     "bfc258875662071d76c448e603f05f0f519bf3e8a0c32207d232d820cab0ab2a"),
    "skill_tree_rpgs": ("skill_tree-fabric-1.6.2+26.2.jar", "1.6.2+26.2",
                        "405ff761ba518c87a14d8923f0c8db5f01311b86d58ca78c3e1f2731d3469901"),
    "relics_rpgs": ("relics-fabric-1.4.1+26.2.jar", "1.4.1+26.2",
                    "013eae223a352138342e5d629e5fec75ee7f19405b82589435993f403be8469f"),
}
PLATFORM_IDS = {"minecraft", "java", "fabricloader", "fabric-loader"}

# D9 decision: spell id -> (impact index, upstream base, upstream k, new base, new k, modelled Pinnacle power).
DECISION = {
    "wizards:frost_nova": (1, 60, 4, 60, 13.5, 56.99),
    "wizards:frost_spikes": (1, 60, 4, 60, 13.5, 56.99),
    "wizards:frostbolt": (1, 100, 4, 100, 12.5, 56.99),
    "wizards:frost_lance": (1, 100, 4, 100, 12.5, 56.99),
    "wizards:frost_blizzard": (1, 80, 4, 80, 12.5, 56.99),
    "paladins:judgement": (1, 50, 2, 50, 7.5, 103.04),
    "rogues:shout": (0, 50, 2, 50, 9.5, 84.0),
    "rogues:throw": (1, 100, 2, 100, 8.5, 84.0),
    "rogues:throw_net": (1, 100, 2, 100, 8.5, 84.0),
    "rogues:shock_powder": (0, 50, 2, 50, 7.5, 105.65),
    "rogues:bear_trap": (1, 100, 2, 100, 5.5, 133.7),
    "archers:entangling_roots": (0, 50, 5, 780, 5, 1.0),
}
# BALANCE.md top-band health at level 200: zombie/piglin 20 + 2.5 or 3.0 per level, enderman 40 + 3.5 per level.
TOP_BAND = {"overworld": 20 + 199 * 2.5, "nether": 20 + 199 * 3.0, "end": 40 + 199 * 3.5}
# Caster and healer spells keep hasted cooldowns.
CONTROLS = {"wizards:fire_meteor": "wizards", "paladins:flash_heal": "paladins"}

PROPERTIES = """\
online-mode=false
server-ip=127.0.0.1
server-port=25682
level-name=probe-world
level-type=minecraft\\:flat
generate-structures=false
difficulty=easy
pause-when-empty-seconds=-1
spawn-protection=0
view-distance=4
simulation-distance=4
enable-rcon=false
management-server-enabled=false
sync-chunk-writes=false
"""


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def mod_metadata(archive):
    try:
        return json.loads(archive.read("fabric.mod.json").decode("utf-8"), strict=False)
    except KeyError:
        return None


def provided_ids(archive, depth=0):
    """Mod IDs a jar provides, including its nested jar-in-jar mods."""
    ids = set()
    metadata = mod_metadata(archive)
    if metadata:
        ids.add(metadata["id"])
        ids.update(metadata.get("provides", []))
        for nested in metadata.get("jars", []):
            if depth < 3:
                try:
                    with zipfile.ZipFile(io.BytesIO(archive.read(nested["file"]))) as inner:
                        ids |= provided_ids(inner, depth + 1)
                except KeyError:
                    pass
    return ids


def dependency_closure(mods_dir, roots):
    index, nested_index, metadata = {}, {}, {}
    for jar in sorted(mods_dir.glob("*.jar")):
        with zipfile.ZipFile(jar) as archive:
            meta = mod_metadata(archive)
            if not meta:
                continue
            metadata[jar] = meta
            for mod_id in [meta["id"], *meta.get("provides", [])]:
                index.setdefault(mod_id, jar)
            for mod_id in provided_ids(archive):
                nested_index.setdefault(mod_id, jar)
    # A top-level mod wins over another jar's jar-in-jar copy of it.
    for mod_id, jar in nested_index.items():
        index.setdefault(mod_id, jar)
    selected, queue, missing = set(), list(roots), []
    while queue:
        mod_id = queue.pop()
        if mod_id in PLATFORM_IDS:
            continue
        jar = index.get(mod_id)
        if jar is None:
            missing.append(mod_id)
            continue
        if jar in selected:
            continue
        selected.add(jar)
        queue.extend(metadata[jar].get("depends", {}).keys())
    if missing:
        raise SystemExit(f"Dependencies not found in {mods_dir}: {sorted(set(missing))}")
    return sorted(selected), metadata


def rewrite_jar(source, target, version=None, tampered=()):
    """Copies a jar, optionally changing its declared version and appending a space to the named entries."""
    with zipfile.ZipFile(source) as archive, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
        for info in archive.infolist():
            data = archive.read(info.filename)
            if version and info.filename == "fabric.mod.json":
                meta = json.loads(data.decode("utf-8"), strict=False)
                meta["version"] = version
                data = json.dumps(meta, indent=2).encode("utf-8")
            if info.filename in tampered:
                data += b" "
            out.writestr(info, data)


def build_probe_mod(root):
    subprocess.run([str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew")), "-q",
                    "--init-script", str(PROBE_DIR / "classpath.init.gradle"), "rpgSpellPatchProbeClasspath"],
                   cwd=REPO, check=True, shell=False)
    classpath = (REPO / "build" / "rpg-spell-patch-probe" / "compile-classpath.txt").read_text(encoding="utf-8").strip()
    classes = root / "probe-classes"
    classes.mkdir()
    subprocess.run(["javac", "--release", "25", "-cp", classpath, "-d", str(classes),
                    str(PROBE_DIR / "RpgSpellPatchProbe.java")], check=True)
    jar = root / "mods" / "rpg-spell-patch-probe.jar"
    metadata = {"schemaVersion": 1, "id": "rpg_spell_patch_probe", "version": "1.0.0", "environment": "*",
                "entrypoints": {"main": ["RpgSpellPatchProbe"]},
                "depends": {"fabric-api": "*"}}
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    return jar


def spell_id(path):
    parts = path.split("/")
    return f"{parts[1]}:{parts[-1][:-5]}"


def targets(manifest, patched):
    """targets.json for the probe mod; `patched(entry)` says whether that entry's replacement should apply."""
    spells = []
    for entry in manifest:
        sid = spell_id(entry["path"])
        target = {"id": sid, "mod": entry["mod"], "path": entry["path"], "p1": "P1" in entry["workstreams"],
                  "expect_patched": patched(entry)}
        if sid in DECISION:
            impact, old_base, old_k, new_base, new_k, pinnacle = DECISION[sid]
            base, k = (new_base, new_k) if target["expect_patched"] else (old_base, old_k)
            target.update(impact=impact, expect_health_base=base, expect_multiplier=k, checks=[])
            for band, health in TOP_BAND.items():
                threshold = (health - base) / k
                for label, power in (("threshold-0.5", threshold - 0.5), ("threshold+0.5", threshold + 0.5),
                                     ("pinnacle", pinnacle)):
                    if power >= 1.0:  # Spell Engine clamps impact power to min_power 1.0
                        target["checks"].append({"band": band, "max_health": health, "at": label,
                                                 "power": round(power, 3), "expected": base + k * power >= health})
        spells.append(target)
    for sid, mod in CONTROLS.items():
        spells.append({"id": sid, "mod": mod, "p1": False, "control": True, "expect_patched": False})
    return {"spells": spells}


def evaluate(probe, served):
    """Every mismatch between the probe's observations and the decision."""
    failures = []
    if probe is None or probe.get("status") != "COMPLETE":
        return [f"probe did not complete: {probe and probe.get('error')}"]
    if not probe.get("haste_affects_cooldown"):
        failures.append("Spell Engine haste_affects_cooldown is off; the cooldown checks would prove nothing")
    for spell in probe["spells"]:
        sid = spell["id"]
        if not spell.get("registered"):
            failures.append(f"{sid} missing from the spell registry")
            continue
        unhasted = spell["p1"] and spell["expect_patched"]
        if spell["haste_affected"] == unhasted:
            failures.append(f"{sid} haste_affected={spell['haste_affected']}, expected {not unhasted}")
        if spell["cooldown_duration"] > 0 and spell["haste"] < 1.5:
            failures.append(f"{sid} haste {spell['haste']} too low to tell hasted from unhasted cooldowns")
        want = spell["cooldown_duration"] if unhasted else spell["cooldown_duration"] / spell["haste"]
        if abs(spell["effective_cooldown"] - want) > 1e-3:
            failures.append(f"{sid} cooldown {spell['effective_cooldown']} != {want}")
        if "impact" in spell:
            if abs(spell["health_base"] - spell["expect_health_base"]) > 1e-4 \
                    or abs(spell["spell_power_multiplier"] - spell["expect_multiplier"]) > 1e-4:
                failures.append(f"{sid} apply_limit {spell['health_base']}+{spell['spell_power_multiplier']}P, "
                                f"expected {spell['expect_health_base']}+{spell['expect_multiplier']}P")
            for check in spell["limit_checks"]:
                if check["applies"] != check["expected"]:
                    failures.append(f"{sid} underApplyLimit {check}")
        if "path" in spell:
            want_sha = served[(spell["mod"], spell["path"])]
            resource = spell["resource"]
            for key in ("get_resource_sha256", "list_resources_sha256"):
                if resource.get(key) != want_sha:
                    failures.append(f"{sid} {key} {resource.get(key)}, expected {want_sha}")
    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--baseline", action="store_true", help="omit the overrides jar")
    parser.add_argument("--mods-dir", default=str(DEFAULT_MODS))
    parser.add_argument("--runtime-source", default=str(DEFAULT_RUNTIME),
                        help="server directory providing the Fabric launcher, libraries and versions")
    parser.add_argument("--overrides", default=str(REPO / "build" / "libs" / "lampas2-overrides-1.0.2.jar"))
    parser.add_argument("--manifest", default=str(REPO / "docs" / "evidence" / "rpg-spell-patches.manifest.json"))
    parser.add_argument("--declare-version", action="append", default=[], metavar="MOD=VERSION",
                        help="control run: rewrite the version in the fixture's copy of that mod's fabric.mod.json")
    parser.add_argument("--tamper", action="append", default=[], metavar="MOD:PATH",
                        help="control run: append a space to that entry of the fixture's copy of the mod's jar")
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()
    mods_dir = pathlib.Path(args.mods_dir)
    runtime = pathlib.Path(args.runtime_source)
    declared = dict(item.split("=", 1) for item in args.declare_version)
    tampered = {}
    for item in args.tamper:
        mod, path = item.split(":", 1)
        tampered.setdefault(mod, set()).add(path)

    for mod_id, (filename, version, expected) in ROOTS.items():
        jar = mods_dir / filename
        digest = sha256(jar.read_bytes())
        if digest != expected:
            raise SystemExit(f"{jar} has SHA-256 {digest}, expected {expected}")
        with zipfile.ZipFile(jar) as archive:
            meta = mod_metadata(archive)
        if (meta["id"], meta["version"]) != (mod_id, version):
            raise SystemExit(f"{jar} declares {meta['id']} {meta['version']}, expected {mod_id} {version}")

    manifest = json.loads(pathlib.Path(args.manifest).read_text(encoding="utf-8"))

    def patched(entry):
        return (not args.baseline and entry["mod"] not in declared
                and entry["path"] not in tampered.get(entry["mod"], set()))

    root = REPO / "build" / "rpg-spell-patch-probe" / args.label
    if root.exists():
        shutil.rmtree(root)
    (root / "mods").mkdir(parents=True)
    (root / ".lampas" / "runtime").mkdir(parents=True)
    shutil.copy2(runtime / ".lampas" / "runtime" / "fabric-server-launch.jar", root / ".lampas" / "runtime")
    shutil.copytree(runtime / "libraries", root / "libraries")
    shutil.copytree(runtime / "versions", root / "versions")
    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (root / "server.properties").write_text(PROPERTIES, encoding="utf-8")

    jars, metadata = dependency_closure(mods_dir, [*ROOTS, "fabric-api"])
    artifacts = []
    served = {}
    for jar in jars:
        mod_id = metadata[jar]["id"]
        target = root / "mods" / jar.name
        entry = {"file": jar.name, "id": mod_id, "version": metadata[jar]["version"], "sha256": sha256(jar.read_bytes())}
        if mod_id in declared or mod_id in tampered:
            rewrite_jar(jar, target, declared.get(mod_id), tampered.get(mod_id, ()))
            entry.update(declared_version=declared.get(mod_id), tampered=sorted(tampered.get(mod_id, ())),
                         fixture_sha256=sha256(target.read_bytes()))
        else:
            shutil.copy2(jar, target)
        artifacts.append(entry)
    for item in manifest:
        upstream = item["upstream_sha256"]
        if item["path"] in tampered.get(item["mod"], set()):
            with zipfile.ZipFile(root / "mods" / ROOTS[item["mod"]][0]) as archive:
                upstream = sha256(archive.read(item["path"]))
        served[(item["mod"], item["path"])] = item["replacement_sha256"] if patched(item) else upstream
    if not args.baseline:
        overrides = pathlib.Path(args.overrides)
        shutil.copy2(overrides, root / "mods" / overrides.name)
        with zipfile.ZipFile(overrides) as archive:
            meta = mod_metadata(archive)
        artifacts.append({"file": overrides.name, "id": meta["id"], "version": meta["version"],
                          "sha256": sha256(overrides.read_bytes())})
    (root / "targets.json").write_text(json.dumps(targets(manifest, patched), indent=2) + "\n", encoding="utf-8")
    probe_jar = build_probe_mod(root)

    console = root / "console.log"
    output = root / "rpg-spell-patch-probe.json"
    cmd = ["java", "-Xms1G", "-Xmx3G", "-Dfile.encoding=UTF-8", "-Dmixin.debug.verbose=true",
           "-jar", str(root / ".lampas" / "runtime" / "fabric-server-launch.jar"), "nogui"]
    results = {"label": args.label, "baseline": args.baseline, "declared_versions": declared,
               "tampered": {mod: sorted(paths) for mod, paths in tampered.items()}, "artifacts": artifacts,
               "probe_mod_sha256": sha256(probe_jar.read_bytes())}
    with console.open("w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        started = time.time()
        while time.time() - started < args.timeout and proc.poll() is None:
            if "RPG_SPELL_PATCH_PROBE" in console.read_text(encoding="utf-8", errors="replace"):
                break
            time.sleep(2)
        results["seconds_to_probe"] = round(time.time() - started)
        if proc.poll() is None:
            try:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
            except OSError:
                pass  # the server exited after poll(); results are still collected below
        try:
            proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
    results["exit_code"] = proc.returncode

    text = console.read_text(encoding="utf-8", errors="replace")
    probe = json.loads(output.read_text(encoding="utf-8")) if output.exists() else None
    applied = re.findall(r"Applied (\S+) (\S+) compatibility resource: (data/[^/\s]+/spell/\S+)", text)
    not_registered = [line.strip() for line in text.splitlines()
                      if "resource patch for data/" in line and "/spell/" in line and "is not registered" in line]
    mismatched = [line.strip() for line in text.splitlines()
                  if "/spell/" in line and "SHA-256 mismatch" in line]
    expected_applied = sorted((m["mod"], m["version"], m["path"]) for m in manifest if patched(m))
    expected_not_registered = sum(1 for m in manifest if not args.baseline and m["mod"] in declared)
    expected_mismatched = 0 if args.baseline else sum(len(paths) for paths in tampered.values())

    failures = evaluate(probe, served)
    if sorted(applied) != expected_applied:
        failures.append(f"{len(applied)} spell Applied lines, expected {len(expected_applied)}")
    if len(not_registered) != expected_not_registered:
        failures.append(f"{len(not_registered)} 'is not registered' WARNs, expected {expected_not_registered}")
    if len(mismatched) != expected_mismatched:
        failures.append(f"{len(mismatched)} spell SHA-256 mismatch WARNs, expected {expected_mismatched}")
    spell_errors = [line.strip() for line in text.splitlines()
                    if re.search(r"spell", line, re.I) and (re.search(r"/(ERROR|FATAL)\]", line) or "Exception" in line)]
    if spell_errors:
        failures.append(f"{len(spell_errors)} spell ERROR or exception lines")

    results.update(
        probe=probe,
        applied_count=len(applied),
        expected_applied_count=len(expected_applied),
        not_registered_warnings=not_registered,
        sha256_mismatch_warnings=mismatched,
        resource_mixin_applied="ModNioPackResourcesMixin" in text and "lampas2-overrides.resourcefix" in text,
        spell_errors=spell_errors[:40],
        errors=[line.strip() for line in text.splitlines()
                if re.search(r"/(ERROR|FATAL)\]", line) or "Exception" in line][:40],
        failures=failures,
        passed=not failures,
    )
    (root / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: results[key] for key in ("label", "applied_count", "expected_applied_count",
                                                     "failures", "passed", "seconds_to_probe", "exit_code")}, indent=2))
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
