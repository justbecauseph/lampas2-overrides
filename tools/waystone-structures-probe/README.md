# Waystone structures probe

Launches a disposable Fabric dedicated server that places the Structory Towers, nova_structures shrine and
Trek village structures and records which of them contain Waystones blocks, using production JARs. Run from
the repository root after `./gradlew build -x test --console=plain`:

```powershell
python -I tools/waystone-structures-probe/run.py --label patched
python -I tools/waystone-structures-probe/run.py --label baseline --baseline
python -I tools/waystone-structures-probe/run.py --label inspect --serve
```

Each run recreates `build/waystone-structures-probe/<label>/`. It copies the Fabric server launcher,
libraries and version files from `run/phase2a-server-smoke`. It checks the SHA-256 and declared id and
version of Structory Towers, Dungeons and Taverns, Trek and Waystones in `../lampas-server-fabric/mods`
(override with `--mods-dir`), then copies them with fabric-api and the dependency closure of their
`depends` entries. Unless `--baseline` is set, it adds `build/libs/lampas2-overrides-<mod_version>.jar`,
with `mod_version` read from `gradle.properties` (override with `--overrides`). It copies
`config/waystones-common.toml` from the config directory next to the mods directory. The server is a flat,
offline, player-less world on `127.0.0.1:25683`, in creative mode on peaceful difficulty. The world seed is
fixed (`level-seed=1`), so reruns of a label place the same structures.

The probe mod (`WaystoneStructuresProbe.java`) is compiled against this project's main compile classpath,
which `classpath.init.gradle` writes. On server start it places each target structure at x = 512 * target
index and z = 512 * attempt, on the surface. It runs `Structure.generate` and `StructureStart.placeInChunk`
the way `PlaceCommand.placeStructure` does. `--attempts` (default 3) sets the placements per target. After
each placement the probe runs the chunk side of a server tick, so the chunks it loaded unload instead of
filling the heap. The probe writes `waystone-structures-probe.json` with each placement's bounding box,
piece count, Waystones pool pieces, Waystones blocks and a teleport command, plus a summary.

The runner then stops the server and writes two files in the server root:

- `results.json`: artifact hashes, the probe JSON, the `Applied ... compatibility resource:` lines for the
  three content mods, WARN/ERROR lines about templates, pools, structures or waystones (first 60), and the
  exit code.
- `teleports.txt`: one line per placement with its teleport command and Waystones block count.

The exit code is 0 when the probe status is COMPLETE. A patched run (no `--baseline`) exits 1 when any
placement contains Waystones blocks, and that rule also applies with `--serve`. Placement errors appear in
the summary and in the JSON and do not change the exit code.

With `--serve`, the runner writes both files after the marker, leaves the server running, forwards lines
typed in its terminal to the server console, and sends `stop` at end of input. Server output goes to
`console.log`. The world stays in `build/waystone-structures-probe/<label>/probe-world`.

Limits: the world is flat. Placement uses the `/place structure` code path, but biome checks are skipped and
structure placement spacing is not exercised, so the grid positions are not real world positions.
`nova_structures:shrine_tower` is invalid in all three placements on this surface (y = -60), so its contents
are not checked. Nothing is rendered. Visual inspection needs a client that joins `127.0.0.1:25683`, which has not been tested. The probe
never touches the live server or the pipeline.
