# RPG Series spell patch probe

Launches a disposable Fabric dedicated server to check the 64 RPG Series spell patches (D3 P1 cooldown
haste, D9 apply limits) with production JARs. Run from the repository root after `./gradlew build`:

```powershell
python -I tools/rpg-spell-patch-probe/run.py --label patched
python -I tools/rpg-spell-patch-probe/run.py --label baseline --baseline
python -I tools/rpg-spell-patch-probe/run.py --label version-mismatch --declare-version wizards=3.1.3.004+26.2
python -I tools/rpg-spell-patch-probe/run.py --label tamper --tamper spell_engine:data/rpg_series/spell/whirlwind.json
```

From a worktree, pass `--mods-dir <lampas-server-fabric/mods>` and `--runtime-source
<main checkout>/run/phase2a-server-smoke`; the defaults resolve next to the repository root.

Each run recreates `build/rpg-spell-patch-probe/<label>/`. It copies the Fabric server launcher,
libraries and version files from the runtime source, checks the SHA-256 and declared version of the
seven RPG Series jars, and copies them with the dependency closure of their `depends` entries and
Fabric API. Unless `--baseline` is set, it adds `build/libs/lampas2-overrides-1.0.2.jar` (override with
`--overrides`). `--declare-version` rewrites only the version in the fixture's copy of that mod's
`fabric.mod.json`; `--tamper` appends a space to one entry of the fixture's copy of the jar. The server
is a flat, offline, player-less world on `127.0.0.1:25682`.

The probe mod (`RpgSpellPatchProbe.java`) is compiled against this project's main compile classpath,
which `classpath.init.gradle` writes, and reaches Spell Engine and Spell Power by reflection. On the
first server tick (after Spell Engine has classified movement-impairing effects) it writes
`rpg-spell-patch-probe.json`: for each spell in `docs/evidence/rpg-spell-patches.manifest.json` and the
controls `wizards:fire_meteor` and `paladins:flash_heal`, the decoded school,
cooldown and `haste_affected`; `SpellPower.getHaste` and `SpellParameters.getCooldownDuration` for a
FakePlayer with attack speed x2, `ranged_weapon:haste` +100 and `spell_power:haste` +100; for the twelve
D9 spells the decoded `apply_limit`, `SpellImpacts.underApplyLimit` against zombies at the Overworld,
Nether and End top-band health (517.5, 617, 736.5) at threshold -/+ 0.5 and at modelled Pinnacle
power, and the effect's boss-gate classification; and the SHA-256 and pack the resource manager serves
through `getResource` and `listResources`.

`results.json` compares all of that, plus the count of `Applied …/spell/…` lines and of
`is not registered` and `SHA-256 mismatch` WARNs, with values computed in `run.py` from the decision
table and the manifest's SHA-256 values, and lists every mismatch under `failures`. Combat with a real
player, the client-side registry decode and tooltips, and full-pack behavior are not covered. The probe
never touches the live server or the pipeline.
