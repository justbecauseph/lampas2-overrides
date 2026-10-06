# Boat water-mask compatibility

EMF `3.3.5`, `3.3.8`, and `3.3.11` use the same compatibility repair. Each exact artifact can replace Minecraft's shared boat water-patch layer with the selected
Fresh Animations `boat_patch.jem` from the audited `FA+All_Extensions-v1.9.2` pack. That
model uses `var.base_*` animation values from
Fresh Animations hull models. A plain hull from an audited provider can therefore leave
the water patch displaced, causing water outside the hull to be rendered transparent.

The client-only hook runs at the end of each `BoatRenderer(Context, ModelLayerLocation)`
constructor. It preserves the constructor's original models and replaces only the
water patch when every gate passes. The replacement is rebuilt from
`BoatModel.createWaterPatch()` and retains `RenderTypes.waterMask()`.

The audited provider profiles are:

- Pyrite `0.18.3+26.2`: `boat/<name>` and `chest_boat/<name>` for `azalea`,
  `black_stained`, `blue_stained`, `brown_mushroom`, `brown_stained`, `cyan_stained`,
  `dragon_stained`, `glow_stained`, `gray_stained`, `green_stained`, `honey_stained`,
  `light_blue_stained`, `light_gray_stained`, `lime_stained`, `magenta_stained`,
  `nostalgia_stained`, `orange_stained`, `pink_stained`, `poisonous_stained`,
  `purple_stained`, `red_mushroom`, `red_stained`, `rose_stained`, `star_stained`,
  `white_stained`, and `yellow_stained`.
- Promenade `5.6.0`: `boat/{sakura,maple,palm}` and
  `chest_boat/{sakura,maple,palm}`.
- Wilder Wild `4.2.11-mc26.2` and the separately audited `4.3`: `boat/{baobab,willow,cypress,palm,maple}` and
  `chest_boat/{baobab,willow,cypress,palm,maple}`.
- BetterEnd `26.201.2` with `wover-item` `26.201.2`: `boat/<name>_boat` and
  `chest_boat/<name>_chest_boat` for `dragon_tree`, `helix_tree`, `jellyshroom`,
  `lacugrove`, `lucernia`, `mossy_glowshroom`, `pythadendron`, `tenanea`, and
  `umbrella_tree`.
- BetterNether `26.201.2` with `wover-item` `26.201.2`: `boat/<name>_boat` and
  `chest_boat/<name>_chest_boat` for `anchor_tree`, `crimson`, `gloomwood`,
  `mushroom_fir`, `nether_mushroom`, `nether_sakura`, `rubeus`, `stalagnate`,
  `warped`, `wart`, and `willow`.

These profiles cover 108 ordinary and chest-boat model-layer IDs.

The BetterEnd `end_lotus` and BetterNether `nether_reed` raft layers are excluded because
their providers construct `RaftRenderer`, which has a different rendering contract.
BloomingNature is also excluded from this phase because its installed renderer does not
construct the shared water patch; it needs a separate repair target.

Activation requires one of these immutable EMF version and artifact profiles:

| EMF metadata version | EMF artifact SHA-256 |
|---|---|
| `3.3.5` | `72b2d489d03bf2ea07b5693ef26cd03572a42dda181e095aed85b3ab39cce549` |
| `3.3.8` | `714686cefe56a7e46fa1e13ecdeddcb55ddfbb9715ae5b1ffd7573c1928d9fdd` |
| `3.3.11` | `26b13c2ee755932e74f80ecf6baf16a25582568ae7482cf472751abb98e7b14a` |

The mixin plugin selects the hash from the metadata version and rejects swapped, unknown, absent,
unreadable, multi-path, and wrong-hash artifacts. Pyrite additionally
requires artifact SHA-256
`6c378632fcadd0501a41bd4af90aec58b8cf03a065f69cd9837160c97ed81831`. Wilder Wild `4.3`
additionally requires artifact SHA-256
`9569288654cee9becfb075a3379d9c8eb5f4a9b06180d7f1936d5bc530f5368c`; its profile is independent
of the retained `4.2.11-mc26.2` profile. The artifact result
is cached per loaded mod container, while selected resources are checked on each renderer
construction so a resource reload cannot reuse an old pack decision.

For each renderer, the hook checks the model-layer namespace, path, and `main` layer against
the profile, then inspects the actual hull root. A plain `ModelPart` is eligible; an EMF hull
with custom model or animation content, an unknown root implementation, or failed reflection
is left untouched. The existing water root must be the EMF root whose directory context reports
the selected identifier `minecraft:optifine/cem/boat_patch.jem`, with both custom model and
animation markers set. The resource manager must resolve that identifier and its bytes must
match SHA-256 `4d924242a3787ca708b0165961078bcf91bd455d0bb881a10b9a28a0a3fa725b`.

JUnit tests cover every provider's positive layer allowlist, entity-style and raft exclusions,
version and resource failures, custom-root gate inputs, artifact path and hash checks, and
geometry equality with the vanilla water patch. The isolated client probe checks the reflective
EMF roots, actual selected resource provenance, constructor behavior across resource reloads,
and the resulting renderer model state. The historical visual matrix below covers the selected
EMF `3.3.5`/`3.3.8` shader-off and shader-on client views.

## Verification — 2026-10-06

The current pipeline inventory selects EMF `3.3.11`, ETF `7.2.5`, Wilder Wild `4.3`,
FrozenLib `3.0.1`, Fabric API `0.161.0+26.2`, and Architectury `21.1.11`.
The new runner reads that inventory and verifies full SHA-256 and Fabric metadata for every
copied mod, and full SHA-256 for the three selected FA packs. A missing inventory path for
FA Player falls back to its installed filename and still requires the inventory's exact digest.
Each run is retained in a fresh `build/boat-mask-probe/<run-id>/` directory.

The unpatched baseline uses no overrides artifact or development output. Runtime checks require
one exact PATH origin, metadata version, and matching full-JAR digest for every fixture mod.
With FA Extensions enabled, all 108 supported provider hulls remained plain while their shared
water masks were EMF models with non-vanilla geometry. With Extensions disabled, their masks
returned to vanilla geometry. The baseline completed ON-1, OFF, ON-2 and OFF-2 with PASS
and normal process exit `0`. This reproduces the incompatible model condition on the current
artifacts; an absent version gate alone was not used to establish a defect.

The patched probe used exactly one production JAR, SHA-256
`7efacfbb125d93ab9eb17de2d796c61feaf3d04547cd62294f1ba0f5ad319a71`. All four strict stages
completed with PASS and process exit `0`. Every supported mask matched vanilla geometry:
Pyrite 52, Promenade 6, Wilder Wild 10, BetterEnd 18 and BetterNether 22. Recorded hull
geometry signature prefixes matched the baseline for all 134 inspected renderers, and the 26 control
and excluded renderer snapshots remained identical. Vanilla oak retained its EMF hull and mask
with Extensions enabled. The boat mixin's application and each exact runtime PATH origin and
digest were verified. Both fixture configs retained `pack_downloading.disabled` after launch.
The ten focused JUnit tests, three runner identity rejection cases, four existing FrozenLib
fixture-config helper cases, Python syntax check and scoped `git diff --check` passed.
Input identities, result/log hashes, root contracts, bytecode contracts and the comparison are
recorded in [`docs/evidence/boat-water-mask-3.3.11.json`](evidence/boat-water-mask-3.3.11.json).

`javap` confirmed EMF `3.3.11` retains `EMFModelPartRoot#getRoot`, the public custom-model,
custom-animation and directory fields, and `EMFDirectoryHandler#getFinalFileLocation`.
Wilder Wild `4.3` registers all ten audited `main` layers and constructs `BoatRenderer` with
the same two-argument descriptor for each of them. The production renderer hook and its
resource, hull and geometry conditions are unchanged.

Reproduce the current baseline and then test one built production JAR:

```powershell
./tools/boat-water-mask-probe/run.ps1 -Mode baseline -Strict `
  -InventoryPath build/pipeline-validation-2026-10-06/inventory.json
./tools/boat-water-mask-probe/run.ps1 -Mode patched -Strict `
  -InventoryPath build/pipeline-validation-2026-10-06/inventory.json `
  -OverrideArtifactPath build/libs/lampas2-overrides-1.0.0.jar
```

The runner launches installed Minecraft and Fabric Loader through plain `KnotClient`; it does
not run Gradle or include development class/resource directories. Patched mode requires exactly
one selected production overrides JAR and verifies its runtime origin and hash plus the boat
mixin's application log. Baseline mode rejects any overrides mod. Both modes require every
stage PASS, a result artifact and process exit `0`. The existing bounded shutdown guard remains
fixture-only. The runner disables FrozenLib pack downloading only in the fresh fixture.

The current structural probe does not launch a world, row provider boats, capture screenshots,
or activate a shader. Current gameplay, visual rendering, shader behavior and live installed-client
acceptance remain unverified. The older visual evidence below does not establish those results
for EMF `3.3.11` or Wilder Wild `4.3`.

## Verification — 2026-09-20

`gradlew.bat test --no-daemon` and `gradlew.bat build --no-daemon` passed;
that run reported 107 tests with no failures or errors. Independent source
review found no remaining production blockers.

The isolated strict probes used Minecraft 26.2, Fabric Loader 0.19.5, the installed boat
providers, ETF 7.2.1, and these resource packs in the client's order:

1. `FA+All_Extensions-v1.9.2.zip`
2. `FreshAnimations_v1.10.5.zip`
3. `FA+Player-v1.1.zip`

Both EMF `3.3.5` and `3.3.8` strict runs completed all four stages — ON-1, OFF, ON-2,
and OFF-2 — with `BOAT_PROBE_RESULT PASS` and process exit `0`. Of 134 inspected
renderers, the 108 supported boat masks matched vanilla geometry: Pyrite 52, Promenade 6,
Wilder Wild 10, BetterEnd 18, and BetterNether 22. Vanilla oak ordinary/chest boats
retained their EMF hulls and masks with the extensions enabled. BetterX rafts and
BloomingNature's custom renderers remained outside this repair. Exact result, log, input,
and override JAR hashes are recorded in
[`docs/evidence/emf-boat-water-mask-3.3.8.json`](evidence/emf-boat-water-mask-3.3.8.json).

The probe's temporary harness guard prevents an external GLFW close request until the
final result is written, then waits up to 75 seconds for the non-daemon pool threads
observed at completion before releasing the guard and stopping the client. The fixture
also disables FrozenLib `packDownloading` under its own `config/` directory. These are
harness-only controls and do not change the installed instance or production behavior.

The original runner used explicit artifact identity. The current inventory runner can select a
historical EMF artifact with all three explicit identity inputs, for example:

```powershell
./tools/boat-water-mask-probe/run.ps1 -Mode patched -Strict `
  -InventoryPath C:/path/historical-inventory.json `
  -OverrideArtifactPath C:/path/lampas2-overrides-1.0.0.jar `
  -EmfArtifactPath C:/path/entity_model_features-3.3.8-26.2-fabric.jar `
  -EmfVersion 3.3.8 `
  -EmfSha256 714686cefe56a7e46fa1e13ecdeddcb55ddfbb9715ae5b1ffd7573c1928d9fdd
```

The original runner verified the JAR hash and Fabric metadata before constructing the fixture,
discovered the installed Biolith JAR instead of assuming an alpha filename, and preserved
the previous fixture under `build/boat-mask-smoke-history/` before a reset. It also
required every stage and the result artifact when Gradle exited normally. That fixture was
confined to `build/boat-mask-smoke`. The fixture-only FrozenLib rewrite cases can be
checked independently with `powershell.exe -NoProfile -File
tools/boat-water-mask-probe/test-frozenlib-config.ps1`.

The accepted visual matrix contains `baseline-off-20260920-054052`,
`baseline-on-20260920-054325`, `patched-off-20260920-053557`, and
`patched-on-20260920-053836`. Every run completed with four 1280x720 screenshots, PASS,
and exit `0`. Manual review
of the catalogs shows the rectangular water transparency defect around provider boats in the
baseline images and continuous water around those boats in the patched images. Vanilla boats,
BetterX rafts, and BloomingNature custom renderers remain visible. Each fixture assertion
count was 1, and every runtime `BOAT_VISUAL_OVERRIDE` used one regular `PATH` origin with a
matching SHA-256. Mounted rowing and turning
covered vanilla oak only; provider boats were not covered. Both resource reload cycles
completed. A post-reload detached-oar artifact appears in both baseline and candidate and is
not proof of a regression. Shader-on logs confirm `lampas-photon-aria.zip` and the Iris
pipeline with no invalid-pack or fallback messages; nonfatal GTAO option warnings are present.
The shader artifact SHA-256 is recorded in the evidence metadata.

These results establish isolated client model, reload, and visual behavior. They do not
establish pack staging, deployment, or behavior in the live installed instance.
