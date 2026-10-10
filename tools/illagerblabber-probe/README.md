# IllagerBlabber registry probe

Launches a disposable Fabric dedicated server to measure IllagerBlabber 1.1.0's voice-registry
retention with and without the override. Run from the repository root:

```powershell
python -I tools/illagerblabber-probe/run.py --label patched
python -I tools/illagerblabber-probe/run.py --label baseline --baseline
python -I tools/illagerblabber-probe/run.py --label control-no-blabber --baseline --omit illagerblabber
```

Each run recreates `build/illagerblabber-probe/<label>/`. It copies the Fabric server launcher, libraries
and version files from `run/phase2a-server-smoke`, and takes IllagerBlabber, Fabric API and spark from the
`lampas-pipeline` Modrinth cache. IllagerBlabber's SHA-256 is checked before copying. Unless
`--baseline` is set, it also adds `build/libs/lampas2-overrides-1.0.0.jar` (override with
`--overrides`). The server is a flat, offline, player-less world on `127.0.0.1:25679`.

Live instance counts for `IllagerVoiceManager` and `Pillager` come from `jcmd GC.class_histogram`,
which forces a full GC first. The probe records them after summoning 200 NoAI pillagers, after killing
them, and after one item despawns during a tick in each dimension. Vanilla `EntityTickList` keeps
entities removed mid-tick in its last copy-on-write snapshot until another entity is added or removed
mid-tick, so counting without that despawn shows retained pillagers even with no mods installed. The
probe then walks one pillager through a real Nether portal and counts again.

`results.json`, the console log, raw histograms and the spark `.sparkheap` file stay in the run
directory; `--heap-dump` also writes a heap dump after the kill. Audible playback position needs a
client and is not covered here. The probe never touches the live server or the pipeline.
