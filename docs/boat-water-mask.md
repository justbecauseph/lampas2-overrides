# Boat water-mask compatibility

EMF `3.3.11` can replace Minecraft's shared boat water-patch layer with the selected
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
- Wilder Wild `4.3`: `boat/{baobab,willow,cypress,palm,maple}` and
  `chest_boat/{baobab,willow,cypress,palm,maple}`.
- BetterEnd `26.201.3` with `wover-item` `26.201.3`: `boat/<name>_boat` and
  `chest_boat/<name>_chest_boat` for `dragon_tree`, `helix_tree`, `jellyshroom`,
  `lacugrove`, `lucernia`, `mossy_glowshroom`, `pythadendron`, `tenanea`, and
  `umbrella_tree`.
- BetterNether `26.201.3` with `wover-item` `26.201.3`: `boat/<name>_boat` and
  `chest_boat/<name>_chest_boat` for `anchor_tree`, `crimson`, `gloomwood`,
  `mushroom_fir`, `nether_mushroom`, `nether_sakura`, `rubeus`, `stalagnate`,
  `warped`, `wart`, and `willow`.

These profiles cover 108 ordinary and chest-boat model-layer IDs.

The BetterEnd `end_lotus` and BetterNether `nether_reed` raft layers are excluded because
their providers construct `RaftRenderer`, which has a different rendering contract.
BloomingNature is also excluded from this phase because its installed renderer does not
construct the shared water patch; it needs a separate repair target.

Activation requires the immutable EMF version and artifact profile:

| EMF metadata version | EMF artifact SHA-256 |
|---|---|
| `3.3.11` | `26b13c2ee755932e74f80ecf6baf16a25582568ae7482cf472751abb98e7b14a` |

The mixin plugin accepts only that version with that artifact hash. It rejects unknown, absent,
unreadable, multi-path, and wrong-hash artifacts. Pyrite additionally
requires artifact SHA-256
`6c378632fcadd0501a41bd4af90aec58b8cf03a065f69cd9837160c97ed81831`. Wilder Wild `4.3`
additionally requires artifact SHA-256
`9569288654cee9becfb075a3379d9c8eb5f4a9b06180d7f1936d5bc530f5368c`. The artifact result
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
and the resulting renderer model state. No visual or shader run is recorded for EMF `3.3.11`.

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

Reproduce the baseline and then test one built production JAR against the current pack 2.3.29
inventory (BetterEnd and BetterNether `26.201.3`):

```powershell
./tools/boat-water-mask-probe/run.ps1 -Mode baseline -Strict `
  -InventoryPath build/pipeline-validation-2026-10-13/inventory.json
./tools/boat-water-mask-probe/run.ps1 -Mode patched -Strict `
  -InventoryPath build/pipeline-validation-2026-10-13/inventory.json `
  -OverrideArtifactPath build/libs/lampas2-overrides-1.0.1.jar
```

The runner launches installed Minecraft and Fabric Loader through plain `KnotClient`; it does
not run Gradle or include development class/resource directories. Patched mode requires exactly
one selected production overrides JAR and verifies its runtime origin and hash plus the boat
mixin's application log. Baseline mode rejects any overrides mod. Both modes require every
stage PASS, a result artifact and process exit `0`. The existing bounded shutdown guard remains
fixture-only. The runner disables FrozenLib pack downloading only in the fresh fixture.

The current structural probe does not launch a world, row provider boats, capture screenshots,
or activate a shader. Current gameplay, visual rendering, shader behavior and live installed-client
acceptance remain unverified for EMF `3.3.11` and Wilder Wild `4.3`.

## Update — 2026-10-13 BetterX 26.201.3

BetterEnd and BetterNether moved from `26.201.2` to `26.201.3`. The profiles above now name
`26.201.3` with `wover-item` `26.201.3`. Pipeline lock `2.3.29` records
`better-end-26.201.3.jar` (SHA-256 `4f43af115334e6353c9fe20a4eb2e7478ff69ef58580960548ccd88d028386c1`)
and `better-nether-26.201.3.jar` (SHA-256 `6b9811ea1ba6a266a0929656fcafa42b43e6a5044d0cc47d339914fff56023ab`).

Every archive entry whose path contains `boat` is byte-identical between the `26.201.2` and
`26.201.3` JARs: 112 entries in BetterEnd and 136 in BetterNether. In `wover-item-api`, only
`BoatRendererBuilder.class` and `fabric.mod.json` changed, and `javap -c` output for
`BoatRendererBuilder` is identical between the two versions.

Both strict probe modes then ran against a fresh 2.3.29 inventory
(`build/pipeline-validation-2026-10-13/inventory.json`), which also selects FrozenLib `3.0.3`.
The unpatched baseline (`20261013T061410Z-baseline-17af85d6`) completed ON-1, OFF, ON-2 and OFF-2
with PASS and exit `0`. With FA Extensions enabled, all 18 BetterEnd and 22 BetterNether hulls
stayed plain while their water patch was the selected `boat_patch.jem` EMF root, so the defect
persists on `26.201.3`. The patched run (`20261013T062425Z-patched-3c042eaf`) used the production JAR
with SHA-256 `53e278ad3914af9183eec1c87f78f3a82368138acc24065924b006d5568c5e4f`. `BoatRendererMixin` applied, all four
stages passed with exit `0`, and every supported mask matched vanilla geometry: Pyrite 52,
Promenade 6, Wilder Wild 10, BetterEnd 18 and BetterNether 22. The four raft layers,
BloomingNature, and vanilla boats with EMF hulls kept their masks unchanged. Gameplay, visual
rendering and shader behavior remain unverified.
