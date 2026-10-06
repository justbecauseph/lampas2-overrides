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
from typing import Any

REPO = Path(__file__).resolve().parents[2]
APPDATA_ROOT = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
ACTIVE_ROOT = APPDATA_ROOT / ".minecraft-lampas"
MC_VERSION = "26.2"
LOADER_VERSION = "0.19.5"
ARTIFACTS = {
    "beb": {
        "filename": "BEB-Fabric-26.1.2-6.0.0.jar",
        "id": "beb",
        "version": "6.0.0",
        "sha256": "08819eb4b0773d389b850dad7cc0c2134a1fcb5d3693d28e03cc8c309181fa8f",
    },
    "potions": {
        "filename": "BTP-Fabric-26.1.2-2.0.1.jar",
        "id": "beautiful_potions",
        "version": "2.0.1",
        "sha256": "53d132f68b9c97105699e3de3542ad9f3ef5ec4e4df5e147f00e5f8510e33a82",
    },
    "fabric_api": {
        "filename": "fabric-api-0.161.0+26.2.jar",
        "id": "fabric-api",
        "version": "0.161.0+26.2",
        "sha256": "e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a",
    },
}
OVERRIDE_ID = "lampas2-overrides"
EXPECTED_MODELS = {"beb": 695, "potions": 828}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require_java25(executable: str, tool_name: str) -> str:
    completed = subprocess.run(
        [executable, "-version"], text=True, capture_output=True, check=False
    )
    version_text = (completed.stdout + completed.stderr).strip()
    match = re.search(r'(?:version\s+["\']?|javac\s+)(\d+)', version_text, re.IGNORECASE)
    if completed.returncode != 0 or not match or int(match.group(1)) != 25:
        raise RuntimeError(f"Java 25 {tool_name} is required; detected: {version_text or '<unknown>'}")
    return version_text.splitlines()[0]


def metadata(path: Path) -> dict[str, Any]:
    try:
        with zipfile.ZipFile(path) as archive:
            return json.loads(archive.read("fabric.mod.json"))
    except (OSError, KeyError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        raise RuntimeError(f"Cannot read Fabric metadata from {path}: {error}") from error


def checked_artifact(spec: dict[str, str], root: Path) -> Path:
    path = root / "mods" / spec["filename"]
    if not path.is_file():
        raise RuntimeError(f"Required installed artifact is missing: {path}")
    actual_hash = sha256(path)
    if actual_hash != spec["sha256"]:
        raise RuntimeError(
            f"Artifact SHA-256 mismatch for {path}: expected={spec['sha256']} actual={actual_hash}"
        )
    data = metadata(path)
    if data.get("id") != spec["id"] or data.get("version") != spec["version"]:
        raise RuntimeError(
            f"Artifact metadata mismatch for {path}: id={data.get('id')} version={data.get('version')}"
        )
    return path


def record_artifact(path: Path) -> dict[str, Any]:
    data = metadata(path)
    return {
        "path": str(path.resolve()),
        "filename": path.name,
        "sha256": sha256(path),
        "size_bytes": path.stat().st_size,
        "mod_id": data.get("id"),
        "mod_version": data.get("version"),
    }


def ensure_mod_ids(mods_dir: Path, expected_override_count: int) -> list[dict[str, Any]]:
    records = []
    seen: dict[str, list[str]] = {}
    for path in sorted(mods_dir.glob("*.jar")):
        data = metadata(path)
        mod_id = data.get("id")
        if not isinstance(mod_id, str):
            raise RuntimeError(f"JAR has no Fabric mod id: {path}")
        seen.setdefault(mod_id, []).append(path.name)
        records.append(record_artifact(path))
    duplicates = {key: names for key, names in seen.items() if len(names) != 1}
    if duplicates:
        raise RuntimeError(f"Fixture has duplicate Fabric mod ids: {duplicates}")
    override_count = len(seen.get(OVERRIDE_ID, []))
    if override_count != expected_override_count:
        raise RuntimeError(
            f"Fixture override count is {override_count}, expected {expected_override_count}: {seen}"
        )
    return records


def write_probe_jar(classes_dir: Path, destination: Path) -> None:
    metadata_json = {
        "schemaVersion": 1,
        "id": "beautiful-items-probe",
        "version": "1.0.0",
        "name": "Beautiful Items Production Probe",
        "environment": "client",
        "entrypoints": {"client": ["BeautifulItemsProbe"]},
        "depends": {
            "fabricloader": ">=0.19.5",
            "minecraft": "~26.2",
            "fabric-api": "0.161.0+26.2",
        },
    }
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata_json, separators=(",", ":")))
        for class_file in sorted(classes_dir.rglob("*.class")):
            archive.write(class_file, class_file.relative_to(classes_dir).as_posix())


def create_filter_pack(client_jar: Path, resourcepacks_dir: Path) -> dict[str, Any]:
    with zipfile.ZipFile(client_jar) as archive:
        version = json.loads(archive.read("version.json"))
    if version.get("id") != MC_VERSION:
        raise RuntimeError(f"Client JAR version.json id mismatch: {version.get('id')!r}")
    pack_version = version.get("pack_version", {})
    major = pack_version.get("resource_major")
    minor = pack_version.get("resource_minor")
    if not isinstance(major, int) or not isinstance(minor, int):
        raise RuntimeError(f"Client JAR has no resource pack version: {pack_version}")
    pack_root = resourcepacks_dir / "beautiful-items-probe-filter"
    pack_root.mkdir(parents=True, exist_ok=False)
    filter_paths = [
        "models/item/enchanted_book/sharpness.json",
        "models/item/potion/swiftness/normal.json",
    ]
    pack_metadata = {
        "pack": {
            "description": "Beautiful Items production probe filter",
            "min_format": [major, minor],
            "max_format": major,
        },
        "filter": {
            "block": [
                {"namespace": "minecraft", "path": path}
                for path in filter_paths
            ]
        },
    }
    pack_file = pack_root / "pack.mcmeta"
    pack_file.write_text(json.dumps(pack_metadata, indent=2) + "\n", encoding="utf-8")
    exact_record = {
        "id": "file/beautiful-items-probe-filter",
        "path": str(pack_file.resolve()),
        "sha256": sha256(pack_file),
        "resource_format": [major, minor],
        "filtered_resources": filter_paths,
        "metadata": pack_metadata,
    }
    empty_root = resourcepacks_dir / "beautiful-items-probe-empty"
    empty_root.mkdir(parents=True, exist_ok=False)
    empty_filters = [
        {"namespace": ".*", "path": "models/item/.*"},
        {"namespace": "minecraft", "path": "models/item/enchanted_book/.*"},
        {"namespace": "minecraft", "path": "models/item/potion/.*"},
    ]
    empty_metadata = {
        "pack": {
            "description": "Beautiful Items probe empty model resources",
            "min_format": [major, minor],
            "max_format": major,
        },
        "filter": {"block": empty_filters},
    }
    empty_file = empty_root / "pack.mcmeta"
    empty_file.write_text(json.dumps(empty_metadata, indent=2) + "\n", encoding="utf-8")
    empty_record = {
        "id": "file/beautiful-items-probe-empty",
        "path": str(empty_file.resolve()),
        "sha256": sha256(empty_file),
        "resource_format": [major, minor],
        "filtered_resources": [".*:models/item/.*", "minecraft:models/item/enchanted_book/.*",
                               "minecraft:models/item/potion/.*"],
        "metadata": empty_metadata,
    }
    return {"single_resource": exact_record, "empty_models": empty_record}


def expected_feature_mixins(override_jar: Path) -> tuple[str, list[tuple[str, str]]]:
    config_name = "lampas2-overrides.beautifulitems.mixins.json"
    with zipfile.ZipFile(override_jar) as archive:
        mod = json.loads(archive.read("fabric.mod.json"))
        configs = [
            item.get("config")
            for item in mod.get("mixins", [])
            if isinstance(item, dict) and item.get("config") == config_name
        ]
        if len(configs) != 1:
            raise RuntimeError(f"Expected exactly one {config_name} in selected production JAR, got {configs}")
        config = json.loads(archive.read(config_name))
    package = config.get("package", "")
    mixins = config.get("client", [])
    expected_targets = {
        "BeautifulPotionsFabricMixin": "com.cerbon.beautiful_potions.fabric.BeautifulPotionsFabric",
        "BeautifulEnchantedBooksFabricMixin": "com.cerbon.beb.fabric.BeautifulEnchantedBooksFabric",
    }
    discovered = []
    for mixin in mixins:
        simple_name = mixin.rsplit(".", 1)[-1]
        if simple_name in expected_targets:
            discovered.append((simple_name, expected_targets[simple_name]))
    if {name for name, _ in discovered} != set(expected_targets):
        raise RuntimeError(f"Feature mixin config does not name both expected client mixins: {mixins}")
    return config_name, discovered


def fail_result(result_file: Path, message: str) -> None:
    if result_file.is_file():
        text = result_file.read_text(encoding="utf-8")
        text = text.replace("status=PASS\n", "status=FAIL\n", 1)
        text += f"runner.failure={message}\n"
        result_file.write_text(text, encoding="utf-8")
    raise RuntimeError(message)


def loader_mixin_artifact(active_root: Path, loader_jar: Path) -> tuple[Path, str, str]:
    with zipfile.ZipFile(loader_jar) as archive:
        installer = json.loads(archive.read("fabric-installer.json"))
    selected = next(
        (item for item in installer["libraries"]["common"]
         if item.get("name", "").startswith("net.fabricmc:sponge-mixin:")),
        None,
    )
    if selected is None:
        raise RuntimeError(f"Fabric Loader metadata has no selected Sponge Mixin library: {loader_jar}")
    coordinate = selected["name"]
    version = coordinate.rsplit(":", 1)[1]
    path = active_root / "libraries" / "net" / "fabricmc" / "sponge-mixin" / version / f"sponge-mixin-{version}.jar"
    if not path.is_file():
        raise RuntimeError(f"Selected Fabric Loader Mixin library is missing: {path}")
    actual_hash = sha256(path)
    expected_hash = selected.get("sha256")
    if expected_hash and actual_hash != expected_hash:
        raise RuntimeError(f"Selected Sponge Mixin SHA-256 mismatch: expected={expected_hash} actual={actual_hash}")
    return path, version, actual_hash


def selected_libraries(active_root: Path, loader_jar: Path, mixin_jar: Path) -> list[Path]:
    loader_directory = (active_root / "libraries" / "net" / "fabricmc" / "fabric-loader").resolve()
    mixin_directory = (active_root / "libraries" / "net" / "fabricmc" / "sponge-mixin").resolve()
    selected_loader = loader_jar.resolve()
    selected_mixin = mixin_jar.resolve()
    return [
        path
        for path in sorted((active_root / "libraries").rglob("*.jar"))
        if (
            (not path.resolve().is_relative_to(loader_directory) or path.resolve() == selected_loader)
            and (not path.resolve().is_relative_to(mixin_directory) or path.resolve() == selected_mixin)
        )
    ]


def build_probe(run_root: Path, source: Path, client_jar: Path, libraries: list[Path], fabric_api: Path) -> Path:
    compile_root = run_root / "compile"
    modules_root = compile_root / "fabric-api-modules"
    classes_root = compile_root / "classes"
    modules_root.mkdir(parents=True)
    classes_root.mkdir(parents=True)
    with zipfile.ZipFile(fabric_api) as api_archive:
        module_entries = [
            name for name in api_archive.namelist()
            if name.startswith("META-INF/jars/") and name.endswith(".jar")
        ]
        if not module_entries:
            raise RuntimeError(f"Fabric API has no nested module JARs: {fabric_api}")
        for name in module_entries:
            (modules_root / Path(name).name).write_bytes(api_archive.read(name))
    classpath_entries = [client_jar, *libraries, *sorted(modules_root.glob("*.jar"))]
    compile_cp = os.pathsep.join(str(path) for path in classpath_entries)
    javac = shutil.which("javac")
    jar_tool = shutil.which("jar")
    if not javac or not jar_tool:
        raise RuntimeError("Java 25 javac and jar tools must be available on PATH")
    require_java25(javac, "javac")
    command = [javac, "--release", "25", "-cp", compile_cp, "-d", str(classes_root), str(source)]
    completed = subprocess.run(command, cwd=run_root, text=True, capture_output=True, check=False)
    (compile_root / "javac.stdout.txt").write_text(completed.stdout, encoding="utf-8")
    (compile_root / "javac.stderr.txt").write_text(completed.stderr, encoding="utf-8")
    if completed.returncode != 0:
        raise RuntimeError(
            f"Probe compilation failed with exit code {completed.returncode}; see {compile_root}"
        )
    probe_jar = run_root / "beautiful-items-probe.jar"
    write_probe_jar(classes_root, probe_jar)
    return probe_jar


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the isolated Beautiful Enchanted Books / BeautifulPotions client regression probe."
    )
    parser.add_argument("mode", choices=("baseline", "patched"))
    parser.add_argument(
        "--override-jar",
        type=Path,
        help="The single built production lampas2-overrides JAR used only in patched mode.",
    )
    parser.add_argument("--minecraft-root", type=Path, default=ACTIVE_ROOT)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    active_root = args.minecraft_root.resolve()
    if args.mode == "patched" and args.override_jar is None:
        raise RuntimeError("patched mode requires --override-jar <production-jar>")
    if args.mode == "baseline" and args.override_jar is not None:
        raise RuntimeError("baseline mode must not receive an override JAR")

    beb = checked_artifact(ARTIFACTS["beb"], active_root)
    potions = checked_artifact(ARTIFACTS["potions"], active_root)
    fabric_api = checked_artifact(ARTIFACTS["fabric_api"], active_root)
    client_jar = active_root / "versions" / MC_VERSION / f"{MC_VERSION}-client.jar"
    asset_index = active_root / "assets" / "indexes" / "32.json"
    natives = active_root / "natives"
    loader_jar = active_root / "libraries" / "net" / "fabricmc" / "fabric-loader" / LOADER_VERSION / f"fabric-loader-{LOADER_VERSION}.jar"
    for required in (client_jar, asset_index, natives, loader_jar):
        if not required.exists():
            raise RuntimeError(f"Required installed client input is missing: {required}")
    if metadata(loader_jar).get("version") != LOADER_VERSION:
        raise RuntimeError(f"Fabric Loader metadata version mismatch in {loader_jar}")
    mixin_jar, mixin_version, mixin_hash = loader_mixin_artifact(active_root, loader_jar)

    override_source = args.override_jar.resolve() if args.override_jar else None
    override_record = None
    if override_source:
        if not override_source.is_file():
            raise RuntimeError(f"Production override JAR is missing: {override_source}")
        override_metadata = metadata(override_source)
        if override_metadata.get("id") != OVERRIDE_ID:
            raise RuntimeError(
                f"Selected production JAR has mod id {override_metadata.get('id')!r}, expected {OVERRIDE_ID!r}"
            )
        override_record = record_artifact(override_source)

    tools_root = Path(__file__).resolve().parent
    source = tools_root / "BeautifulItemsProbe.java"
    if not source.is_file():
        raise RuntimeError(f"Probe Java source is missing: {source}")
    build_root = REPO / "build" / "beautiful-items-probe"
    build_root.mkdir(parents=True, exist_ok=True)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:10]
    run_root = build_root / run_id
    run_root.mkdir(parents=True, exist_ok=False)

    fixture = run_root / "fixture"
    mods_dir = fixture / "mods"
    (fixture / "config").mkdir(parents=True)
    resourcepacks_dir = fixture / "resourcepacks"
    resourcepacks_dir.mkdir(parents=True)
    filter_pack = create_filter_pack(client_jar, resourcepacks_dir)
    mods_dir.mkdir(parents=True)
    copied: dict[str, Path] = {}
    for role, source_path in (("beb", beb), ("potions", potions), ("fabric_api", fabric_api)):
        target = mods_dir / source_path.name
        shutil.copy2(source_path, target)
        if sha256(target) != sha256(source_path):
            raise RuntimeError(f"Fixture copy hash mismatch: {target}")
        copied[role] = target
    if override_source:
        target = mods_dir / override_source.name
        shutil.copy2(override_source, target)
        if sha256(target) != override_record["sha256"]:
            raise RuntimeError(f"Selected override fixture copy hash mismatch: {target}")
        copied["override"] = target
        mixin_config_name, feature_mixins = expected_feature_mixins(override_source)
    else:
        mixin_config_name, feature_mixins = None, []

    libraries = selected_libraries(active_root, loader_jar, mixin_jar)
    probe_jar = build_probe(run_root, source, client_jar, libraries, fabric_api)
    shutil.copy2(probe_jar, mods_dir / probe_jar.name)
    fixture_mods = ensure_mod_ids(mods_dir, 1 if args.mode == "patched" else 0)
    result_file = run_root / "result.txt"
    process_log = run_root / "client-process.log"
    inputs = {
        "run_id": run_id,
        "mode": args.mode,
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "minecraft_version": MC_VERSION,
        "fabric_loader_version": LOADER_VERSION,
        "client_jar": {
            "path": str(client_jar.resolve()),
            "sha256": sha256(client_jar),
            "size_bytes": client_jar.stat().st_size,
        },
        "asset_index": str(asset_index.resolve()),
        "natives": str(natives.resolve()),
        "filter_pack": filter_pack,
        "libraries_count": len(libraries),
        "fabric_loader_mixin": {
            "path": str(mixin_jar.resolve()),
            "version": mixin_version,
            "sha256": mixin_hash,
        },
        "artifacts": {
            "beb": record_artifact(beb),
            "potions": record_artifact(potions),
            "fabric_api": record_artifact(fabric_api),
            "override": override_record,
            "probe": record_artifact(mods_dir / probe_jar.name),
        },
        "fixture_mods": fixture_mods,
        "result_file": str(result_file.resolve()),
        "process_log": str(process_log.resolve()),
    }
    (run_root / "inputs.json").write_text(json.dumps(inputs, indent=2) + "\n", encoding="utf-8")

    java = shutil.which("java")
    if not java:
        raise RuntimeError("Java 25 java executable must be available on PATH")
    require_java25(java, "java runtime")
    classpath = os.pathsep.join([str(client_jar), *(str(path) for path in libraries)])
    jvm_args = [
        java,
        f"-Djava.library.path={natives}",
        f"-Dorg.lwjgl.librarypath={natives}",
        "-Dorg.lwjgl.util.Debug=false",
        "-Dmixin.debug.verbose=true",
        "-Dmixin.debug.export=true",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        f"-DbeautifulItemsProbe.mode={args.mode}",
        f"-DbeautifulItemsProbe.resultFile={result_file.resolve()}",
        f"-DbeautifulItemsProbe.expected.beb.jar={(mods_dir / beb.name).resolve()}",
        f"-DbeautifulItemsProbe.expected.potions.jar={(mods_dir / potions.name).resolve()}",
        f"-DbeautifulItemsProbe.expected.override.jar={(copied['override'].resolve() if 'override' in copied else '')}",
        f"-DbeautifulItemsProbe.expected.override.sha256={(override_record['sha256'] if override_record else '')}",
        "-cp",
        classpath,
        "net.fabricmc.loader.impl.launch.knot.KnotClient",
        "--username", "BeautifulProbe",
        "--version", MC_VERSION,
        "--gameDir", str(fixture.resolve()),
        "--assetsDir", str((active_root / "assets").resolve()),
        "--assetIndex", "32",
        "--uuid", "00000000000000000000000000000001",
        "--accessToken", "0",
        "--userType", "msa",
        "--versionType", "release",
        "--width", "800",
        "--height", "600",
    ]
    (run_root / "launch-arguments.json").write_text(
        json.dumps({"executable": java, "jvm_and_game_args": jvm_args[1:]}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"RunId: {run_id}")
    print(f"Mode: {args.mode}")
    print(f"Fixture: {fixture}")
    print(f"Logs: {process_log}")
    print("Launching plain Fabric KnotClient with installed Minecraft 26.2 and Fabric Loader 0.19.5.")
    try:
        with process_log.open("w", encoding="utf-8") as log:
            completed = subprocess.run(
                jvm_args,
                cwd=fixture,
                stdout=log,
                stderr=subprocess.STDOUT,
                timeout=args.timeout_seconds,
                check=False,
            )
    except subprocess.TimeoutExpired:
        raise RuntimeError(
            f"KnotClient exceeded {args.timeout_seconds}s; process log: {process_log}"
        )

    if not result_file.is_file():
        raise RuntimeError(
            f"Client exited {completed.returncode} without a result file; process log: {process_log}"
        )
    result_text = result_file.read_text(encoding="utf-8")
    print(result_text, end="" if result_text.endswith("\n") else "\n")
    if completed.returncode != 0:
        fail_result(result_file, f"KnotClient exit code was {completed.returncode}; expected normal exit 0; see {process_log}")
    result = dict(line.split("=", 1) for line in result_text.splitlines() if "=" in line)
    if result.get("status") != "PASS" or result.get("mode") != args.mode:
        fail_result(result_file, f"Probe did not report PASS for {args.mode}; see {result_file}")
    for role, expected_count in EXPECTED_MODELS.items():
        if result.get(f"{role}.initial.models") != str(expected_count):
            fail_result(result_file, f"Unexpected {role} initial model count; see {result_file}")
        if result.get(f"{role}.initial.hits") != str(expected_count):
            fail_result(result_file, f"Unexpected {role} initial cache hits; see {result_file}")
        for stage in ("reload1", "reload2", "restored"):
            expected_hits = expected_count if args.mode == "patched" else 0
            if result.get(f"{role}.{stage}.models") != str(expected_count):
                fail_result(result_file, f"Unexpected {role} {stage} model count; see {result_file}")
            if result.get(f"{role}.{stage}.hits") != str(expected_hits):
                fail_result(result_file, f"Unexpected {role} {stage} cache hits; see {result_file}")
            if result.get(f"{role}.{stage}.selectedKeyPresent") != "true":
                fail_result(result_file, f"{role} selected model key is absent at {stage}; see {result_file}")
        filter_expected_models = expected_count - (1 if args.mode == "patched" else 0)
        filter_expected_hits = filter_expected_models if args.mode == "patched" else 0
        filter_expected_presence = "false" if args.mode == "patched" else "true"
        if result.get(f"{role}.filtered.models") != str(filter_expected_models):
            fail_result(result_file, f"Unexpected {role} filtered model count; see {result_file}")
        if result.get(f"{role}.filtered.hits") != str(filter_expected_hits):
            fail_result(result_file, f"Unexpected {role} filtered cache hits; see {result_file}")
        if result.get(f"{role}.filtered.selectedKeyPresent") != filter_expected_presence:
            fail_result(result_file, f"Unexpected {role} selected key presence under filter; see {result_file}")
        empty_expected_models = 0 if args.mode == "patched" else expected_count
        if result.get(f"{role}.empty.models") != str(empty_expected_models):
            fail_result(result_file, f"Unexpected {role} empty-stage model count; see {result_file}")
        if result.get(f"{role}.empty.hits") != "0":
            fail_result(result_file, f"Unexpected {role} empty-stage cache hits; see {result_file}")
        empty_expected_presence = "false" if args.mode == "patched" else "true"
        if result.get(f"{role}.empty.selectedKeyPresent") != empty_expected_presence:
            fail_result(result_file, f"Unexpected {role} selected key presence in empty stage; see {result_file}")
    for stage in ("initial", "reload1", "reload2", "filtered", "restored"):
        custom_sprite = stage == "initial" or (
            args.mode == "patched" and stage not in ("filtered", "empty")
        )
        expected_sprites = {
            "beb": "minecraft:item/enchanted_book/sharpness" if custom_sprite
            else "minecraft:item/enchanted_book",
            "potions": "minecraft:item/potion/swiftness/normal" if custom_sprite
            else "minecraft:item/potion_overlay",
        }
        for role, expected_sprite in expected_sprites.items():
            key = f"resolver.{stage}.{role}ParticleSprite"
            if result.get(key) != expected_sprite:
                fail_result(result_file, f"Unexpected {role} {stage} resolver sprite; expected={expected_sprite}")
    if not result.get("resolver.empty.skipped", "").startswith("all item model resources are blocked;"):
        fail_result(result_file, "Empty-stage resolver skip was not recorded; see result file")
    for role in ("beb", "potions"):
        source_path = copied[role].resolve()
        if Path(result.get(f"{role}.origin", "")).resolve() != source_path:
            fail_result(result_file, f"Fabric Loader origin did not resolve to the exact {role} fixture JAR")
        if result.get(f"{role}.sha256") != ARTIFACTS[role]["sha256"]:
            fail_result(result_file, f"Runtime {role} artifact SHA-256 did not match the pinned artifact")
    if override_source:
        if Path(result.get("override.origin", "")).resolve() != copied["override"].resolve():
            fail_result(result_file, "Fabric Loader origin did not resolve to the selected production override JAR")
        if result.get("override.sha256") != override_record["sha256"]:
            fail_result(result_file, "Runtime override artifact SHA-256 did not match the selected JAR")
        log_text = process_log.read_text(encoding="utf-8", errors="replace")
        applied = []
        for mixin_class, target_class in feature_mixins:
            marker = f"Mixing {mixin_class} from {mixin_config_name} into {target_class}"
            matching_lines = [line.strip() for line in log_text.splitlines() if marker in line]
            if not matching_lines:
                fail_result(result_file, f"Fabric Loader did not log required mixin application: {marker}")
            applied.extend(matching_lines)
        (run_root / "mixin-validation.txt").write_text("\n".join(applied) + "\n", encoding="utf-8")
        with result_file.open("a", encoding="utf-8") as output:
            output.write(f"mixin.config={mixin_config_name}\n")
            for index, line in enumerate(applied, start=1):
                output.write(f"mixin.applied.{index}={line}\n")
    with result_file.open("a", encoding="utf-8") as output:
        output.write("runner.status=PASS\n")
    print(f"PASS; client exit=0; evidence={run_root}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
