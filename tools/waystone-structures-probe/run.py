"""Disposable dedicated-server probe for Waystones blocks in the structures the pack's worldgen mods add.

Builds a fresh server under build/waystone-structures-probe/<label>/ with Structory Towers, Dungeons and
Taverns (its nova_structures shrines), Trek and Waystones, plus fabric-api and the dependency closure of
those roots, all taken from the deployed server's mods directory. It adds a small probe mod and, unless
--baseline, the overrides jar. The Waystones config comes from the config directory next to the mods
directory. Once the server has started, the probe places every target structure through the
/place structure code path on a flat world and writes waystone-structures-probe.json. This script then
writes results.json and teleports.txt in the server root.

Never point this at the live server directory: it creates and deletes its own root.
"""

import argparse
import collections
import hashlib
import io
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import threading
import time
import zipfile

REPO = pathlib.Path(__file__).resolve().parents[2]
PROBE_DIR = pathlib.Path(__file__).resolve().parent
RUNTIME_SOURCE = REPO / "run" / "phase2a-server-smoke"
DEFAULT_MODS = REPO.parent / "lampas-server-fabric" / "mods"

ROOTS = {
    "structory_towers": ("Structory_Towers_26.2_v1.0.17.jar", "1.0.17",
                         "226d8cb187e0d4fb75571db017c0abb26176aec16586c337a1b5620f3805a372"),
    "mr_dungeons_andtaverns": ("dungeons-and-taverns-5.3.2.jar", "5.3.2",
                               "7a1abeacfd6b973d9753928889d765e4002a086f4cb23756d8586e7d347d4bba"),
    "mr_trek": ("trek-B0.6.2.jar", "1-B0.6.2",
                "5ea0837a4b1ef02cc702d7e7e49a245f4857591696183b9d2fe56c574cbfc022"),
    "waystones": ("waystones-fabric-26.2-26.2.0.13.jar", "26.2.0.13",
                  "d6cb9d9f2f96bc7e4aaee1bf0868d5b9091e86667b03e04422d27a2eb575eff0"),
}
PLATFORM_IDS = {"minecraft", "java", "fabricloader", "fabric-loader"}
CONFIG_NAME = "waystones-common.toml"
MARKER = "WAYSTONE_STRUCTURES_PROBE"
PROBE_OUTPUT = "waystone-structures-probe.json"
PLAZA = "lampas:village/waystone_plaza"
COMPAT_MODS = ("structory_towers", "mr_dungeons_andtaverns", "mr_trek")
APPLIED = re.compile(r"Applied (\S+) \S+ compatibility resource: ")
LOG_LEVEL = re.compile(r"/(WARN|ERROR|FATAL)\]")
LOG_TOPIC = re.compile(r"template|pool|structure|jigsaw|waystone|DataFixer|Couldn't load|Exception", re.IGNORECASE)
LOG_CAP = 60
SERVE_MESSAGE = ('Server running on 127.0.0.1:25683 — type server console commands here (e.g. "op <name>"), '
                 '"stop" to shut down.')

PROPERTIES = """\
online-mode=false
server-ip=127.0.0.1
server-port=25683
level-name=probe-world
level-seed=1
level-type=minecraft\\:flat
generate-structures=false
difficulty=peaceful
gamemode=creative
force-gamemode=true
allow-flight=true
pause-when-empty-seconds=-1
spawn-protection=0
view-distance=10
simulation-distance=4
enable-rcon=false
management-server-enabled=false
sync-chunk-writes=false
"""


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_text(path):
    return path.read_text(encoding="utf-8", errors="replace")


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


def mod_version():
    text = read_text(REPO / "gradle.properties")
    match = re.search(r"^mod_version=(\S+)\s*$", text, re.MULTILINE)
    if match is None:
        raise SystemExit(f"mod_version is not set in {REPO / 'gradle.properties'}")
    return match.group(1)


def build_probe_mod(root):
    subprocess.run([str(REPO / ("gradlew.bat" if os.name == "nt" else "gradlew")), "-q",
                    "--init-script", str(PROBE_DIR / "classpath.init.gradle"), "waystoneStructuresProbeClasspath"],
                   cwd=REPO, check=True, shell=False)
    classpath = (REPO / "build" / "waystone-structures-probe" / "compile-classpath.txt").read_text(
        encoding="utf-8").strip()
    classes = root / "probe-classes"
    classes.mkdir()
    subprocess.run(["javac", "--release", "25", "-cp", classpath, "-d", str(classes),
                    str(PROBE_DIR / "WaystoneStructuresProbe.java")], check=True)
    jar = root / "mods" / "waystone-structures-probe.jar"
    metadata = {"schemaVersion": 1, "id": "waystone_structures_probe", "version": "1.0.0", "environment": "*",
                "entrypoints": {"main": ["WaystoneStructuresProbe"]}, "depends": {"fabric-api": "*"}}
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    return jar


def load_probe(output):
    if not output.exists():
        return None
    try:
        return json.loads(output.read_text(encoding="utf-8"))
    except ValueError:
        return None


def scan_log(text):
    applied, counts, problems = [], collections.Counter(), []
    for line in text.splitlines():
        match = APPLIED.search(line)
        if match and match.group(1) in COMPAT_MODS:
            applied.append(line.strip())
            counts[match.group(1)] += 1
        if LOG_LEVEL.search(line) and LOG_TOPIC.search(line):
            problems.append(line.strip())
    return applied, {mod: counts.get(mod, 0) for mod in COMPAT_MODS}, problems


def verdict(results):
    """Returns (exit code, message); the message is None when the run passes."""
    probe = results["probe"]
    if not results["marker_seen"]:
        return 1, f"no {MARKER} line in console.log"
    if probe is None:
        return 1, f"{PROBE_OUTPUT} is missing or unreadable (see console.log)"
    if probe.get("status") != "COMPLETE":
        return 1, f"probe status {probe.get('status')}: {probe.get('error')}"
    blocks = probe["summary"]["with_waystone_blocks"]
    if not results["baseline"] and blocks:
        return 1, f"{blocks} placements have waystone blocks in the patched run"
    return 0, None


def write_outputs(root, results, console, output, proc):
    results["exit_code"] = proc.poll()
    results["probe"] = load_probe(output)
    applied, counts, problems = scan_log(read_text(console))
    results["applied_lines"] = applied
    results["applied_counts"] = counts
    results["log_problem_count"] = len(problems)
    results["log_problems"] = problems[:LOG_CAP]
    _, results["verdict"] = verdict(results)
    (root / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    if results["probe"] is not None:
        lines = []
        for placement in results["probe"].get("placements", []):
            tp = placement.get("tp") or "(no tp)"
            blocks = len(placement.get("waystone_blocks") or [])
            lines.append(f"{placement['structure']} #{placement['attempt']}  {tp}  waystone_blocks={blocks}")
        (root / "teleports.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def print_summary(results):
    probe = results["probe"] or {}
    placements = probe.get("placements", [])
    rows = collections.OrderedDict()
    for placement in placements:
        row = rows.setdefault(placement["structure"], [0, 0, 0, 0])
        row[1] += 1
        row[0] += bool(placement.get("valid"))
        row[2] += len(placement.get("waystone_blocks") or [])
        row[3] += sum(PLAZA in piece["element"] for piece in placement.get("waystone_pieces") or [])
    print(f"{results['label']}: baseline={results['baseline']} probe={probe.get('status', 'MISSING')} "
          f"server_exit_code={results['exit_code']}")
    for structure, (valid, total, blocks, plaza) in rows.items():
        print(f"  {structure:<56} valid {valid}/{total}  waystone_blocks={blocks}  plaza_pieces={plaza}")
    summary = probe.get("summary", {})
    print(f"  targets={len(probe.get('targets', []))}  placements valid {summary.get('valid')}/"
          f"{summary.get('placements')}  with_waystone_blocks={summary.get('with_waystone_blocks')}  "
          f"waystone_block_total={summary.get('waystone_block_total')}  plaza_pieces={summary.get('plaza_pieces')}")
    errors = [p for p in placements if "error" in p]
    print(f"  placement errors: {len(errors)}")
    for placement in errors[:10]:
        print(f"    {placement['structure']} #{placement['attempt']}: {placement['error']}")
    print("  Applied lines: " + ", ".join(f"{mod}={n}" for mod, n in results["applied_counts"].items()))
    print(f"  log problems (WARN/ERROR/FATAL matching template, pool, structure, waystone, Exception): "
          f"{results['log_problem_count']} (first {LOG_CAP} in results.json)")


def wait_for_marker(proc, console, timeout):
    started = time.time()
    while time.time() - started < timeout and proc.poll() is None:
        if MARKER in read_text(console):
            return True
        time.sleep(2)
    return MARKER in read_text(console)


def stop_server(proc):
    if proc.poll() is None:
        try:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        except OSError:
            pass  # the server exited after poll(); wait() below still collects its status
    try:
        proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()


def forward_console(proc):
    """Copies lines typed on this terminal to the server console; sends stop on EOF."""
    try:
        for line in sys.stdin:
            if proc.poll() is not None:
                return
            proc.stdin.write(line if line.endswith("\n") else line + "\n")
            proc.stdin.flush()
    except (OSError, ValueError):
        return
    try:
        proc.stdin.write("stop\n")
        proc.stdin.flush()
    except (OSError, ValueError):
        pass


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Waystone structures probe; see README.md.")
    parser.add_argument("--label", required=True)
    parser.add_argument("--baseline", action="store_true", help="omit the overrides jar")
    parser.add_argument("--mods-dir", default=str(DEFAULT_MODS))
    parser.add_argument("--overrides", help="overrides jar (default build/libs/lampas2-overrides-<mod_version>.jar)")
    parser.add_argument("--attempts", type=int, default=3,
                        help="placements per target, at z = 512 * attempt")
    parser.add_argument("--timeout", type=int, default=1200, help="seconds to wait for the probe marker")
    parser.add_argument("--serve", action="store_true",
                        help="after the probe, keep the server running and forward this terminal's input to it")
    args = parser.parse_args()
    if args.attempts < 1:
        raise SystemExit("--attempts must be at least 1")
    mods_dir = pathlib.Path(args.mods_dir)
    config_source = mods_dir.parent / "config" / CONFIG_NAME

    overrides = None
    if not args.baseline:
        overrides = (pathlib.Path(args.overrides) if args.overrides
                     else REPO / "build" / "libs" / f"lampas2-overrides-{mod_version()}.jar")
        if not overrides.is_file():
            raise SystemExit(f"{overrides} is missing; run ./gradlew build -x test --console=plain or pass --overrides")
    for mod_id, (filename, version, expected) in ROOTS.items():
        jar = mods_dir / filename
        if not jar.is_file():
            raise SystemExit(f"{jar} is missing")
        digest = sha256(jar)
        if digest != expected:
            raise SystemExit(f"{jar} has SHA-256 {digest}, expected {expected}")
        with zipfile.ZipFile(jar) as archive:
            meta = mod_metadata(archive)
        if meta is None or (meta["id"], meta["version"]) != (mod_id, version):
            declared = (meta["id"], meta["version"]) if meta else None
            raise SystemExit(f"{jar} declares {declared}, expected {(mod_id, version)}")
    if not config_source.is_file():
        raise SystemExit(f"{config_source} is missing")

    root = REPO / "build" / "waystone-structures-probe" / args.label
    if root.exists():
        shutil.rmtree(root)
    (root / "mods").mkdir(parents=True)
    (root / "config").mkdir()
    (root / ".lampas" / "runtime").mkdir(parents=True)
    shutil.copy2(RUNTIME_SOURCE / ".lampas" / "runtime" / "fabric-server-launch.jar", root / ".lampas" / "runtime")
    shutil.copytree(RUNTIME_SOURCE / "libraries", root / "libraries")
    shutil.copytree(RUNTIME_SOURCE / "versions", root / "versions")
    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (root / "server.properties").write_text(PROPERTIES, encoding="utf-8")
    shutil.copy2(config_source, root / "config" / CONFIG_NAME)

    jars, metadata = dependency_closure(mods_dir, [*ROOTS, "fabric-api"])
    artifacts = []
    for jar in jars:
        shutil.copy2(jar, root / "mods" / jar.name)
        artifacts.append({"file": jar.name, "id": metadata[jar]["id"], "version": metadata[jar]["version"],
                          "sha256": sha256(jar)})
    if overrides is not None:
        shutil.copy2(overrides, root / "mods" / overrides.name)
        with zipfile.ZipFile(overrides) as archive:
            meta = mod_metadata(archive)
        artifacts.append({"file": overrides.name, "id": meta["id"], "version": meta["version"],
                          "sha256": sha256(overrides)})
    artifacts.append({"file": f"config/{CONFIG_NAME}", "sha256": sha256(config_source)})
    probe_jar = build_probe_mod(root)

    console = root / "console.log"
    output = root / PROBE_OUTPUT
    cmd = ["java", "-Xms1G", "-Xmx3G", "-Dfile.encoding=UTF-8", f"-DwaystoneProbe.attempts={args.attempts}",
           "-jar", str(root / ".lampas" / "runtime" / "fabric-server-launch.jar"), "nogui"]
    results = {"label": args.label, "baseline": args.baseline, "attempts": args.attempts,
               "artifacts": artifacts, "probe_mod_sha256": sha256(probe_jar), "marker_seen": False}
    with console.open("w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace")
        started = time.time()
        try:
            results["marker_seen"] = wait_for_marker(proc, console, args.timeout)
            results["seconds_to_marker"] = round(time.time() - started)
            if args.serve and results["marker_seen"]:
                write_outputs(root, results, console, output, proc)
                print_summary(results)
                print(SERVE_MESSAGE)
                print(f"Server console output: {console}")
                threading.Thread(target=forward_console, args=(proc,), daemon=True).start()
                proc.wait()
        finally:
            stop_server(proc)

    write_outputs(root, results, console, output, proc)
    code, message = verdict(results)
    if not (args.serve and results["marker_seen"]):
        print_summary(results)
    if message:
        print(f"FAILED: {message}")
    print(f"results: {root / 'results.json'}")
    print(f"teleports: {root / 'teleports.txt'}")
    sys.exit(code)


if __name__ == "__main__":
    main()
