"""Disposable dedicated-server probe for the IllagerBlabber 1.1.0 registry leak.

Builds a fresh server under build/illagerblabber-probe/<label>/ with Fabric API, IllagerBlabber 1.1.0,
spark and (unless --baseline) the overrides jar, then:

1. summons N NoAI pillagers in force-loaded chunks and lets them tick,
2. records live-object heap histograms (jcmd GC.class_histogram, which forces a full GC),
3. kills them, then forces one mid-tick entity removal so vanilla EntityTickList drops its stale
   copy-on-write snapshot (which otherwise keeps the last tick's dead mobs reachable in any setup),
4. walks one pillager through a real Nether portal and lets the new copy tick in the Nether.

A pillager that has crossed dimensions leaves an old instance behind. The vendor manager keeps
that old instance reachable and keeps voicing it; the fix replaces the manager and releases it on
unload. Audible playback position needs a client and is not covered here.

Never point this at the live server directory: it creates and deletes its own root.
"""

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import time

REPO = pathlib.Path(__file__).resolve().parents[2]
PIPELINE_CACHE = REPO.parent / "lampas-pipeline" / ".lampas" / "cache" / "modrinth"
RUNTIME_SOURCE = REPO / "run" / "phase2a-server-smoke"

MODS = {
    "illagerblabber": (PIPELINE_CACHE / "WS4FswTq" / "UrxK3Zzq" / "illagerblabber-26.2-Fabric-1.1.0.jar",
                       "ceecc1248d29786d4b1b595e50dd61f9be49b647f015d30b4373c544fcd9b0e5"),
    "fabric-api": (PIPELINE_CACHE / "P7dR8mSH" / "ewUK83HI" / "fabric-api-0.161.0+26.2.jar", None),
    "spark": (PIPELINE_CACHE / "l6YH9Als" / "e3hsPc1o" / "spark-1.10.187-fabric.jar", None),
}
MANAGER_CLASS = "com.leclowndu93150.illagerblabber.stuff.voice.IllagerVoiceManager"
PILLAGER_CLASS = "net.minecraft.world.entity.monster.illager.Pillager"

PROPERTIES = """\
online-mode=false
server-ip=127.0.0.1
server-port=25679
level-name=probe-world
level-type=minecraft\\:flat
generate-structures=false
difficulty=easy
gamemode=creative
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


def histogram(pid, save_to=None):
    out = subprocess.run(["jcmd", str(pid), "GC.class_histogram"], capture_output=True, text=True, timeout=120).stdout
    if save_to is not None:
        save_to.write_text(out, encoding="utf-8")
    counts = {MANAGER_CLASS: 0, PILLAGER_CLASS: 0}
    for line in out.splitlines():
        match = re.match(r"\s*\d+:\s+(\d+)\s+\d+\s+(\S+)", line)
        if match and match.group(2) in counts:
            counts[match.group(2)] = int(match.group(1))
    return {"IllagerVoiceManager": counts[MANAGER_CLASS], "Pillager": counts[PILLAGER_CLASS]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--baseline", action="store_true", help="omit the overrides jar")
    parser.add_argument("--count", type=int, default=200)
    parser.add_argument("--heap-dump", action="store_true", help="write a live-object heap dump after the kill")
    parser.add_argument("--omit", action="append", default=[], choices=sorted(MODS),
                        help="leave a dependency out, for control runs")
    parser.add_argument("--overrides", default=str(REPO / "build" / "libs" / "lampas2-overrides-1.0.0.jar"))
    args = parser.parse_args()

    root = REPO / "build" / "illagerblabber-probe" / args.label
    if root.exists():
        shutil.rmtree(root)
    (root / "mods").mkdir(parents=True)
    (root / ".lampas" / "runtime").mkdir(parents=True)
    shutil.copy2(RUNTIME_SOURCE / ".lampas" / "runtime" / "fabric-server-launch.jar", root / ".lampas" / "runtime")
    shutil.copytree(RUNTIME_SOURCE / "libraries", root / "libraries")
    shutil.copytree(RUNTIME_SOURCE / "versions", root / "versions")
    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (root / "server.properties").write_text(PROPERTIES, encoding="utf-8")

    artifacts = {}
    for name, (path, expected) in MODS.items():
        if name in args.omit:
            continue
        digest = sha256(path)
        if expected and digest != expected:
            raise SystemExit(f"{name} {path} has SHA-256 {digest}, expected {expected}")
        shutil.copy2(path, root / "mods" / path.name)
        artifacts[name] = {"file": path.name, "sha256": digest}
    if not args.baseline:
        overrides = pathlib.Path(args.overrides)
        shutil.copy2(overrides, root / "mods" / overrides.name)
        artifacts["lampas2-overrides"] = {"file": overrides.name, "sha256": sha256(overrides)}

    console = root / "console.log"
    cmd = ["java", "-Xms1G", "-Xmx2G", "-Dfile.encoding=UTF-8", "-Dmixin.debug.verbose=true",
           "-jar", str(root / ".lampas" / "runtime" / "fabric-server-launch.jar"), "nogui"]
    results = {"label": args.label, "baseline": args.baseline, "omitted": args.omit, "count": args.count, "artifacts": artifacts}
    with console.open("w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)

        def send(*lines, wait=0.0):
            for line in lines:
                proc.stdin.write(line + "\n")
            proc.stdin.flush()
            if wait:
                time.sleep(wait)

        def churn():
            for dimension in ("minecraft:overworld", "minecraft:the_nether"):
                send(f"execute in {dimension} run summon minecraft:item 8 100 8 "
                     "{Age:5999s,NoGravity:1b,Item:{id:\"minecraft:stick\",count:1}}")
            time.sleep(3)

        started = time.time()
        done = False
        while time.time() - started < 300 and proc.poll() is None:
            if "Done (" in console.read_text(encoding="utf-8", errors="replace"):
                done = True
                break
            time.sleep(1)
        results["boot_done"] = done
        if done:
            send("forceload add 0 0 63 63", "execute in minecraft:the_nether run forceload add 0 0 31 31", wait=3)
            results["before"] = histogram(proc.pid, root / "histogram-before.txt")

            send(*[f"summon minecraft:pillager {8 + (i % 48)} -60 {8 + (i // 48) * 2} {{NoAI:1b}}"
                   for i in range(args.count)], wait=10)
            results["after_summon"] = histogram(proc.pid, root / "histogram-after_summon.txt")

            send("kill @e[type=minecraft:pillager]", wait=5)
            send("kill @e[type=minecraft:item]", wait=2)
            send("execute if entity @e[type=minecraft:pillager]", wait=1)
            results["after_kill"] = histogram(proc.pid, root / "histogram-after_kill.txt")
            if args.heap_dump:
                subprocess.run(["jcmd", str(proc.pid), "GC.heap_dump", str(root / "after_kill.hprof")],
                               capture_output=True, timeout=300)
            # Vanilla EntityTickList keeps its last copy-on-write snapshot, which still names entities
            # removed during that tick, until another entity is added or removed mid-tick. One item that
            # despawns during its own tick forces that swap without touching IllagerBlabber state.
            churn()
            results["after_churn"] = histogram(proc.pid, root / "histogram-after_churn.txt")

            # The traveller ticks in the Overworld, crosses a real Nether portal, then ticks in the Nether.
            send("summon minecraft:pillager 40.5 -60 40.5 {NoAI:1b,Invulnerable:1b,Tags:[\"probe_traveller\"]}",
                 wait=5)
            churn()
            results["traveller_overworld"] = histogram(proc.pid, root / "histogram-traveller_overworld.txt")
            send("setblock 40 -60 40 minecraft:nether_portal", wait=10)
            send("execute in minecraft:the_nether if entity "
                 "@e[type=minecraft:pillager,tag=probe_traveller,x=0,y=0,z=0,distance=..30000]",
                 "execute in minecraft:overworld if entity "
                 "@e[type=minecraft:pillager,tag=probe_traveller,x=0,y=0,z=0,distance=..30000]",
                 "data get entity @e[type=minecraft:pillager,tag=probe_traveller,limit=1] Pos", wait=5)
            churn()
            results["traveller_nether"] = histogram(proc.pid, root / "histogram-traveller_nether.txt")

            send("spark heapsummary --save-to-file", wait=15)
            send("stop")
        try:
            proc.wait(timeout=120)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
    results["exit_code"] = proc.returncode

    text = console.read_text(encoding="utf-8", errors="replace")
    results["enable_line"] = "Enabled version-gated IllagerBlabber 1.1.0 registry fixes" in text
    results["mixins_applied"] = sorted(set(re.findall(r"Mixing (\S+) from lampas2-overrides\.illagerblabber", text)))
    results["injection_failures"] = [line for line in text.splitlines()
                                     if "illagerblabber" in line.lower()
                                     and any(t in line for t in ("InvalidInjection", "InjectionError", "MixinApplyError",
                                                                 "Critical injection failure", "Exception"))]
    results["vendor_info_lines"] = sum(text.count(s) for s in (
        "VOICE MANAGER CREATED FOR", "CREATING NEW VOICE MANAGER FOR", "already processed this tick"))
    results["traveller_pos"] = re.findall(r"has the following entity data: (\[[^\]]*\])", text)[-1:]
    results["entity_count_checks"] = re.findall(r"Test (?:passed|failed)[^\r\n]*", text)
    results["spark_files"] = [str(p.relative_to(root)) for p in root.rglob("*.sparkheap")]
    out = root / "results.json"
    out.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
