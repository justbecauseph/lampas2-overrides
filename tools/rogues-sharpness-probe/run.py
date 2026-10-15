"""Disposable dedicated-server probe for the Rogues 3.1.3.003+26.2 Sharpness resource patch.

Builds a fresh server under build/rogues-sharpness-probe/<label>/ with Rogues, Apothic Enchanting (which
ships a competing Sharpness file and edits the damage exclusive-set tag), the dependency closure of both
taken from the deployed server's mods directory, a small probe mod and, unless --baseline, the overrides
jar. Once the server has started, the probe writes rogues-sharpness-probe.json with:

- the Sharpness registry entry as the registry loader decoded it, re-encoded with the vanilla codec,
  its flat damage bonus per level and its exclusive set, plus Sharpness/Smite/Bane compatibility;
- the bytes the resource manager returns for data/minecraft/enchantment/sharpness.json through
  getResource, listResources and getResourceStack, with the pack order;
- the bytes Rogues' own pack returns through PackResources#getResource and #listResources.

Never point this at the live server directory: it creates and deletes its own root.
"""

import argparse
import hashlib
import io
import json
import pathlib
import re
import shutil
import subprocess
import time
import zipfile

REPO = pathlib.Path(__file__).resolve().parents[2]
PROBE_DIR = pathlib.Path(__file__).resolve().parent
RUNTIME_SOURCE = REPO / "run" / "phase2a-server-smoke"
DEFAULT_MODS = REPO.parent / "lampas-server-fabric" / "mods"

ROOTS = {
    "rogues": ("rogues-fabric-3.1.3.003+26.2.jar", "3.1.3.003+26.2",
               "77bdff881c01011bff6e6e722a1f47dc01ca7d377a1f08231d305fc11b08f113"),
    "apothic_enchanting": ("ApothicEnchanting-26.2-3.0.1.jar", "3.0.1",
                           "0280f58b6b95951e265ca82aac8528e4c8d5f508b4acdc6fec466a52556cdc4c"),
}
PLATFORM_IDS = {"minecraft", "java", "fabricloader", "fabric-loader"}
APPLIED_LINE = "Applied rogues 3.1.3.003+26.2 compatibility resource: data/minecraft/enchantment/sharpness.json"

PROPERTIES = """\
online-mode=false
server-ip=127.0.0.1
server-port=25681
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


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


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
    index, metadata = {}, {}
    for jar in sorted(mods_dir.glob("*.jar")):
        with zipfile.ZipFile(jar) as archive:
            meta = mod_metadata(archive)
            if not meta:
                continue
            metadata[jar] = meta
            for mod_id in provided_ids(archive):
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


def declare_version(source, target, version):
    """Copies a jar, changing only the version in its fabric.mod.json."""
    with zipfile.ZipFile(source) as archive, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
        for info in archive.infolist():
            data = archive.read(info.filename)
            if info.filename == "fabric.mod.json":
                meta = json.loads(data.decode("utf-8"), strict=False)
                meta["version"] = version
                data = json.dumps(meta, indent=2).encode("utf-8")
            out.writestr(info, data)


def build_probe_mod(root):
    subprocess.run([str(REPO / ("gradlew.bat" if (REPO / "gradlew.bat").exists() else "gradlew")), "-q",
                    "--init-script", str(PROBE_DIR / "classpath.init.gradle"), "roguesSharpnessProbeClasspath"],
                   cwd=REPO, check=True, shell=False)
    classpath = (REPO / "build" / "rogues-sharpness-probe" / "compile-classpath.txt").read_text(encoding="utf-8").strip()
    classes = root / "probe-classes"
    classes.mkdir()
    subprocess.run(["javac", "--release", "25", "-cp", classpath, "-d", str(classes),
                    str(PROBE_DIR / "RoguesSharpnessProbe.java")], check=True)
    jar = root / "mods" / "rogues-sharpness-probe.jar"
    metadata = {"schemaVersion": 1, "id": "rogues_sharpness_probe", "version": "1.0.0", "environment": "*",
                "entrypoints": {"main": ["RoguesSharpnessProbe"]}, "depends": {"fabric-api": "*"}}
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    return jar


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--baseline", action="store_true", help="omit the overrides jar")
    parser.add_argument("--mods-dir", default=str(DEFAULT_MODS))
    parser.add_argument("--overrides", default=str(REPO / "build" / "libs" / "lampas2-overrides-1.0.1.jar"))
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--declare-rogues-version",
                        help="control run: rewrite the version in the fixture's copy of Rogues' fabric.mod.json")
    args = parser.parse_args()
    mods_dir = pathlib.Path(args.mods_dir)

    for mod_id, (filename, version, expected) in ROOTS.items():
        jar = mods_dir / filename
        digest = sha256(jar)
        if digest != expected:
            raise SystemExit(f"{jar} has SHA-256 {digest}, expected {expected}")
        with zipfile.ZipFile(jar) as archive:
            meta = mod_metadata(archive)
        if (meta["id"], meta["version"]) != (mod_id, version):
            raise SystemExit(f"{jar} declares {meta['id']} {meta['version']}, expected {mod_id} {version}")

    root = REPO / "build" / "rogues-sharpness-probe" / args.label
    if root.exists():
        shutil.rmtree(root)
    (root / "mods").mkdir(parents=True)
    (root / ".lampas" / "runtime").mkdir(parents=True)
    shutil.copy2(RUNTIME_SOURCE / ".lampas" / "runtime" / "fabric-server-launch.jar", root / ".lampas" / "runtime")
    shutil.copytree(RUNTIME_SOURCE / "libraries", root / "libraries")
    shutil.copytree(RUNTIME_SOURCE / "versions", root / "versions")
    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (root / "server.properties").write_text(PROPERTIES, encoding="utf-8")

    jars, metadata = dependency_closure(mods_dir, [*ROOTS, "fabric-api"])
    artifacts = []
    for jar in jars:
        target = root / "mods" / jar.name
        entry = {"file": jar.name, "id": metadata[jar]["id"], "version": metadata[jar]["version"], "sha256": sha256(jar)}
        if args.declare_rogues_version and metadata[jar]["id"] == "rogues":
            declare_version(jar, target, args.declare_rogues_version)
            entry.update(declared_version=args.declare_rogues_version, fixture_sha256=sha256(target))
        else:
            shutil.copy2(jar, target)
        artifacts.append(entry)
    if not args.baseline:
        overrides = pathlib.Path(args.overrides)
        shutil.copy2(overrides, root / "mods" / overrides.name)
        with zipfile.ZipFile(overrides) as archive:
            meta = mod_metadata(archive)
        artifacts.append({"file": overrides.name, "id": meta["id"], "version": meta["version"],
                          "sha256": sha256(overrides)})
    probe_jar = build_probe_mod(root)

    console = root / "console.log"
    output = root / "rogues-sharpness-probe.json"
    cmd = ["java", "-Xms1G", "-Xmx3G", "-Dfile.encoding=UTF-8", "-Dmixin.debug.verbose=true",
           "-jar", str(root / ".lampas" / "runtime" / "fabric-server-launch.jar"), "nogui"]
    results = {"label": args.label, "baseline": args.baseline,
               "declared_rogues_version": args.declare_rogues_version, "artifacts": artifacts,
               "probe_mod_sha256": sha256(probe_jar)}
    with console.open("w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        started = time.time()
        while time.time() - started < args.timeout and proc.poll() is None:
            if "ROGUES_SHARPNESS_PROBE" in console.read_text(encoding="utf-8", errors="replace"):
                break
            time.sleep(2)
        results["seconds_to_probe"] = round(time.time() - started)
        if proc.poll() is None:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        try:
            proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
    results["exit_code"] = proc.returncode

    text = console.read_text(encoding="utf-8", errors="replace")
    results["probe"] = json.loads(output.read_text(encoding="utf-8")) if output.exists() else None
    results["applied_line"] = APPLIED_LINE in text
    results["resource_fix_lines"] = [line.strip() for line in text.splitlines() if "lampas2-overrides/resource-fix" in line
                                     or "compatibility resource" in line or "resource patch" in line]
    results["resource_mixin_applied"] = "ModNioPackResourcesMixin" in text and "lampas2-overrides.resourcefix" in text
    results["mixin_lines"] = sorted(set(re.findall(r"Mixing (\S+) from lampas2-overrides\.resourcefix\S*", text)))
    results["errors"] = [line.strip() for line in text.splitlines()
                         if re.search(r"/(ERROR|FATAL)\]", line) or "Exception" in line][:40]
    (root / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
