"""Run copied inventory artifacts with plain KnotClient, without development output."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True

REPO = Path(__file__).resolve().parents[2]
TOOLS = Path(__file__).resolve().parent
ACTIVE = Path(os.environ.get("APPDATA", Path.home() / "AppData/Roaming")) / ".minecraft-lampas"
MOD_IDS = (
    "fabric-api", "entity_model_features", "entity_texture_features", "pyrite", "promenade",
    "wilderwild", "frozenlib", "betterend", "betternether", "bclib", "wover", "wunderlib",
    "biolith", "bloomingnature", "architectury",
)
PACK_KEYS = ("fresh-animations-extensions", "fresh-animations", "fa-player-extension")
MASK_HASH = "4d924242a3787ca708b0165961078bcf91bd455d0bb881a10b9a28a0a3fa725b"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def metadata(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        # Some inspected upstream descriptions contain literal newlines. Fabric's
        # metadata parser accepts them; identity/hash verification still uses exact bytes.
        return json.loads(archive.read("fabric.mod.json"), strict=False)


def require_java25(executable: str) -> None:
    check = subprocess.run([executable, "-version"], capture_output=True, text=True, check=False)
    match = re.search(r'(?:version\s+["\']?|javac\s+)(\d+)', check.stdout + check.stderr)
    if check.returncode or not match or int(match.group(1)) != 25:
        raise RuntimeError(f"Java 25 required: {check.stdout + check.stderr}")


def checked_artifact(record: dict, active_root: Path) -> Path:
    path = Path(record["path"]) if record.get("path") else (
        active_root / ("mods" if record["artifact_type"] == "mod" else "resourcepacks")
        / record["filename"])
    path = path.resolve(strict=True)
    actual = sha256(path)
    if not path.is_file() or actual != record["lock_sha256"]:
        raise RuntimeError(f"Inventory artifact hash mismatch: {path}; actual={actual}")
    if record["artifact_type"] == "mod":
        data = metadata(path)
        if data.get("id") != record["mod_id"] or data.get("version") != record["version"]:
            raise RuntimeError(f"Inventory Fabric identity mismatch: {path}")
    return path


def runtime_libraries(active: Path, loader_version: str) -> tuple[list[Path], dict]:
    loader_root = active / "libraries/net/fabricmc/fabric-loader"
    loader = loader_root / loader_version / f"fabric-loader-{loader_version}.jar"
    if metadata(loader).get("version") != loader_version:
        raise RuntimeError("Fabric Loader metadata differs from inventory")
    with zipfile.ZipFile(loader) as archive:
        installer = json.loads(archive.read("fabric-installer.json"))
    declared = next(item for item in installer["libraries"]["common"]
                    if item["name"].startswith("net.fabricmc:sponge-mixin:"))
    mixin_version = declared["name"].rsplit(":", 1)[1]
    mixin_root = active / "libraries/net/fabricmc/sponge-mixin"
    mixin = mixin_root / mixin_version / f"sponge-mixin-{mixin_version}.jar"
    digest = sha256(mixin)
    if declared.get("sha256") and digest != declared["sha256"]:
        raise RuntimeError("Fabric Loader selected Mixin artifact hash mismatch")
    libraries = [path.resolve() for path in sorted((active / "libraries").rglob("*.jar"))
                 if (not path.is_relative_to(loader_root) or path == loader)
                 and (not path.is_relative_to(mixin_root) or path == mixin)]
    return libraries, {"loader": str(loader), "loader_sha256": sha256(loader),
                       "mixin": str(mixin), "mixin_version": mixin_version,
                       "mixin_sha256": digest}


def compile_probe(run: Path, client: Path, libraries: list[Path], api: Path) -> Path:
    compile_dir = run / "compile"
    classes = compile_dir / "classes"
    modules = compile_dir / "fabric-api-modules"
    classes.mkdir(parents=True)
    modules.mkdir()
    source = compile_dir / "BoatWaterMaskProbe.java"
    shutil.copy2(TOOLS / source.name, source)
    with zipfile.ZipFile(api) as archive:
        for name in archive.namelist():
            if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                (modules / Path(name).name).write_bytes(archive.read(name))
    javac = shutil.which("javac")
    if not javac:
        raise RuntimeError("javac missing from PATH")
    require_java25(javac)
    cp = os.pathsep.join(map(str, [client, *libraries, *sorted(modules.glob("*.jar"))]))
    check = subprocess.run([javac, "--release", "25", "-cp", cp, "-d", str(classes),
                            str(source)], capture_output=True,
                           text=True, check=False)
    (compile_dir / "javac.stdout.txt").write_text(check.stdout, encoding="utf-8")
    (compile_dir / "javac.stderr.txt").write_text(check.stderr, encoding="utf-8")
    if check.returncode:
        raise RuntimeError(f"Probe compile failed: {compile_dir / 'javac.stderr.txt'}")
    probe = run / "boat-water-mask-probe.jar"
    with zipfile.ZipFile(probe, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(TOOLS / "fabric.mod.json", "fabric.mod.json")
        for source in sorted(classes.rglob("*.class")):
            archive.write(source, source.relative_to(classes).as_posix())
    return probe


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("baseline", "patched"))
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--override-jar", type=Path)
    parser.add_argument("--minecraft-root", type=Path, default=ACTIVE)
    parser.add_argument("--emf-artifact", type=Path)
    parser.add_argument("--emf-version")
    parser.add_argument("--emf-sha256")
    parser.add_argument("--timeout-seconds", type=int, default=300)
    parser.add_argument("--skip-launch", action="store_true")
    parser.add_argument("--strict", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if (args.mode == "patched") != bool(args.override_jar):
        raise RuntimeError("patched requires one --override-jar; baseline requires no override JAR")
    inventory_path = args.inventory.resolve(strict=True)
    inventory = json.loads(inventory_path.read_text(encoding="utf-8-sig"))
    active = args.minecraft_root.resolve(strict=True)
    emf_inputs = (args.emf_artifact, args.emf_version, args.emf_sha256)
    if any(emf_inputs) and not all(emf_inputs):
        raise RuntimeError("Explicit EMF input requires artifact, version and SHA-256 together")
    mod_records = []
    for mod_id in MOD_IDS:
        selected = [record for record in inventory["artifacts"] if record.get("mod_id") == mod_id]
        if len(selected) != 1:
            raise RuntimeError(f"Expected one inventory entry for {mod_id}: {len(selected)}")
        record = dict(selected[0])
        if mod_id == "entity_model_features" and all(emf_inputs):
            record.update(path=str(args.emf_artifact), version=args.emf_version,
                          lock_sha256=args.emf_sha256.lower())
        mod_records.append((record, checked_artifact(record, active)))
    pack_records = []
    for key in PACK_KEYS:
        selected = [record for record in inventory["artifacts"] if record.get("key") == key]
        if len(selected) != 1:
            raise RuntimeError(f"Expected one inventory entry for pack {key}")
        pack_records.append((selected[0], checked_artifact(selected[0], active)))
    with zipfile.ZipFile(pack_records[0][1]) as archive:
        actual_mask_hash = hashlib.sha256(archive.read("assets/minecraft/optifine/cem/boat_patch.jem")).hexdigest()
    if actual_mask_hash != MASK_HASH:
        raise RuntimeError(f"FA boat_patch.jem differs from audited resource: {actual_mask_hash}")
    override = args.override_jar.resolve(strict=True) if args.override_jar else None
    if override:
        data = metadata(override)
        if data.get("id") != "lampas2-overrides":
            raise RuntimeError("Selected override is not a lampas2-overrides production JAR")
        mod_records.append(({"mod_id": data["id"], "version": data["version"],
                             "lock_sha256": sha256(override)}, override))
    mc_version = inventory["minecraft"]
    loader_version = inventory["loader"]["version"]
    client = active / "versions" / mc_version / f"{mc_version}-client.jar"
    libraries, loader_identity = runtime_libraries(active, loader_version)
    with zipfile.ZipFile(client) as archive:
        if json.loads(archive.read("version.json"))["id"] != mc_version:
            raise RuntimeError("Installed client version differs from inventory")
    assets = active / "assets"
    asset_index = "32"
    if not (assets / "indexes" / f"{asset_index}.json").is_file():
        raise RuntimeError("Installed Minecraft 26.2 asset index missing")
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + args.mode + "-" + uuid.uuid4().hex[:8]
    run = REPO / "build/boat-mask-probe" / run_id
    fixture = run / "fixture"
    mods = fixture / "mods"
    packs = fixture / "resourcepacks"
    config = fixture / "config"
    mods.mkdir(parents=True)
    packs.mkdir()
    config.mkdir()
    copied_records = []
    manifest_lines = []
    for record, source in mod_records:
        destination = mods / source.name
        shutil.copy2(source, destination)
        if sha256(destination) != record["lock_sha256"]:
            raise RuntimeError(f"Copied artifact hash mismatch: {destination}")
        manifest_lines.append("\t".join((record["mod_id"], record["version"],
                                         record["lock_sha256"], str(destination.resolve()))))
        copied_records.append({"id": record["mod_id"], "version": record["version"],
                               "sha256": record["lock_sha256"], "source": str(source),
                               "fixture": str(destination.resolve())})
    for record, source in pack_records:
        destination = packs / source.name
        shutil.copy2(source, destination)
        if sha256(destination) != record["lock_sha256"]:
            raise RuntimeError(f"Copied resourcepack hash mismatch: {destination}")
        copied_records.append({"key": record["key"], "sha256": record["lock_sha256"],
                               "source": str(source), "fixture": str(destination.resolve())})
    emf_config = active / "config/entity_model_features.json"
    if emf_config.is_file():
        shutil.copy2(emf_config, config / emf_config.name)
    frozen_dir = config / "frozenlib"
    frozen_dir.mkdir()
    # Fixture-only downloader setting: production files remain untouched.
    (frozen_dir / "main.json5").write_text(
        '{\n\tpackDownloading: "pack_downloading.disabled",\n}\n', encoding="utf-8")
    api = next(source for record, source in mod_records if record["mod_id"] == "fabric-api")
    probe = compile_probe(run, client, libraries, api)
    shutil.copy2(probe, mods / probe.name)
    ids = [metadata(path)["id"] for path in mods.glob("*.jar")]
    if len(ids) != len(set(ids)) or ids.count("lampas2-overrides") != int(bool(override)):
        raise RuntimeError(f"Fixture has duplicate or unexpected override mod ids: {ids}")
    manifest = run / "runtime-artifacts.tsv"
    manifest.write_text("\n".join(manifest_lines) + "\n", encoding="utf-8")
    emf_record = next(record for record, _ in mod_records if record["mod_id"] == "entity_model_features")
    inputs = {"run_id": run_id, "mode": args.mode, "inventory": str(inventory_path),
              "inventory_sha256": sha256(inventory_path), "minecraft": mc_version,
              "client": str(client), "client_sha256": sha256(client), "loader": loader_identity,
              "fixtureOverrideCount": int(bool(override)), "artifacts": copied_records,
              "probe_sha256": sha256(probe), "fa_mask_sha256": actual_mask_hash,
              "probe_source_sha256": sha256(run / "compile/BoatWaterMaskProbe.java"),
              "runner_sha256": sha256(Path(__file__)),
              "harnessFrozenLibPackDownloading": "pack_downloading.disabled"}
    (run / "inputs.json").write_text(json.dumps(inputs, indent=2) + "\n", encoding="utf-8")
    java = shutil.which("java")
    if not java:
        raise RuntimeError("java missing from PATH")
    require_java25(java)
    command = [java, f"-Djava.library.path={active / 'natives'}",
               f"-Dorg.lwjgl.librarypath={active / 'natives'}", "-Dmixin.debug.verbose=true",
               "-Dmixin.debug.export=true", f"-Dboat.probe.strict={str(args.strict).lower()}",
               f"-Dboat.probe.mode={args.mode}", f"-Dboat.probe.artifacts={manifest.resolve()}",
               f"-Dboat.probe.emf.version={emf_record['version']}",
               f"-Dboat.probe.emf.sha256={emf_record['lock_sha256']}",
               "-cp", os.pathsep.join(map(str, [client, *libraries])),
               "net.fabricmc.loader.impl.launch.knot.KnotClient",
               "--username", "BoatProbe", "--version", mc_version,
               "--gameDir", str(fixture.resolve()), "--assetsDir", str(assets),
               "--assetIndex", asset_index, "--uuid", "00000000000000000000000000000001",
               "--accessToken", "0", "--userType", "msa", "--versionType", "release",
               "--width", "800", "--height", "600"]
    (run / "launch-arguments.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    print(f"Fixture: {fixture}", flush=True)
    if args.skip_launch:
        return 0
    process_log = run / "client-process.log"
    with process_log.open("w", encoding="utf-8") as log:
        completed = subprocess.run(command, cwd=fixture, stdout=log, stderr=subprocess.STDOUT,
                                   timeout=args.timeout_seconds, check=False,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    (run / "process-exit.json").write_text(json.dumps({"exit_code": completed.returncode}) + "\n", encoding="utf-8")
    frozen_config = (frozen_dir / "main.json5").read_text(encoding="utf-8")
    if not re.search(r'packDownloading\s*:\s*"pack_downloading\.disabled"', frozen_config):
        raise RuntimeError("FrozenLib rewrote fixture pack downloading to a different value")
    result_path = fixture / "boat-water-mask-probe-result.txt"
    if not result_path.is_file():
        raise RuntimeError(f"Client exit={completed.returncode} without result: {process_log}")
    result = result_path.read_text(encoding="utf-8")
    for stage in ("ON-1", "OFF", "ON-2", "OFF-2"):
        if f"BOAT_PROBE_CHECK stage={stage} PASS" not in result:
            raise RuntimeError(f"Probe stage {stage} did not pass: {result_path}")
    if completed.returncode or "BOAT_PROBE_RESULT PASS" not in result:
        raise RuntimeError(f"Probe exit={completed.returncode}; inspect {result_path}")
    if f"BOAT_PROBE_OVERRIDE mode={args.mode} count={int(bool(override))}" not in result:
        raise RuntimeError("Runtime override provenance missing")
    if override:
        logs = process_log.read_text(encoding="utf-8", errors="replace")
        marker = "Mixing BoatRendererMixin from lampas2-overrides.boatmask.mixins.json into net.minecraft.client.renderer.entity.BoatRenderer"
        applied = [line for line in logs.splitlines() if marker in line]
        if not applied:
            raise RuntimeError(f"Production boat mixin application not observed: {process_log}")
        (run / "mixin-validation.txt").write_text("\n".join(applied) + "\n", encoding="utf-8")
    print(f"PASS; client exit=0; evidence={run}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
