# Rogues Sharpness probe

Launches a disposable Fabric dedicated server to check the Rogues 3.1.3.003+26.2 Sharpness resource
patch with production JARs. Run from the repository root after `./gradlew build`:

```powershell
python -I tools/rogues-sharpness-probe/run.py --label patched
python -I tools/rogues-sharpness-probe/run.py --label baseline --baseline
python -I tools/rogues-sharpness-probe/run.py --label version-mismatch --declare-rogues-version 3.1.3.004+26.2
```

Each run recreates `build/rogues-sharpness-probe/<label>/`. It copies the Fabric server launcher,
libraries and version files from `run/phase2a-server-smoke`, checks the SHA-256 and declared version of
Rogues and Apothic Enchanting in `../lampas-server-fabric/mods` (override with `--mods-dir`), and copies
both with the dependency closure of their `depends` entries from that directory. Unless `--baseline` is
set, it adds `build/libs/lampas2-overrides-1.0.1.jar` (override with `--overrides`).
`--declare-rogues-version` rewrites only the version in the fixture's copy of Rogues'
`fabric.mod.json`, to exercise the unregistered-version WARN. The server is a flat, offline,
player-less world on `127.0.0.1:25681`.

The probe mod (`RoguesSharpnessProbe.java`) is compiled against this project's main compile classpath,
which `classpath.init.gradle` writes. When the server has started it writes
`rogues-sharpness-probe.json`: the Sharpness registry entry re-encoded with `Enchantment.DIRECT_CODEC`,
its flat damage bonus per level, its exclusive set and Smite/Bane compatibility; the bytes the resource
manager returns through `getResource`, `listResources` and `getResourceStack` with the pack order; and
the bytes Rogues' own pack returns through `PackResources#getResource` and `#listResources`. The runner
then stops the server and writes `results.json` with artifact hashes, the resource-fix log lines and
whether `Applied rogues 3.1.3.003+26.2 compatibility resource: data/minecraft/enchantment/sharpness.json`
appeared. Combat damage needs a player and is not covered. The probe never touches the live server or
the pipeline.
