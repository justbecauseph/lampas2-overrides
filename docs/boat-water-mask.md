# Boat water-mask compatibility

EMF `3.3.5` and `3.3.8` use the same compatibility repair. Either exact artifact can replace Minecraft's shared boat water-patch layer with the selected
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
- Wilder Wild `4.2.11-mc26.2`: `boat/{baobab,willow,cypress,palm,maple}` and
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

The mixin plugin selects the hash from the metadata version and rejects swapped, unknown, absent,
unreadable, multi-path, and wrong-hash artifacts. Pyrite additionally
requires artifact SHA-256
`6c378632fcadd0501a41bd4af90aec58b8cf03a065f69cd9837160c97ed81831`. The artifact result
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
and the resulting renderer model state. The accepted visual matrix below covers the selected
shader-off and shader-on client views.

## Verification — 2026-09-20

`gradlew.bat test --no-daemon` and `gradlew.bat build --no-daemon` passed;
the current checkout reports 107 tests with no failures or errors. Independent source
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

Reproduce with explicit artifact identity, for example:

```powershell
./tools/boat-water-mask-probe/run.ps1 -Strict `
  -EmfArtifactPath C:/path/entity_model_features-3.3.8-26.2-fabric.jar `
  -EmfVersion 3.3.8 `
  -EmfSha256 714686cefe56a7e46fa1e13ecdeddcb55ddfbb9715ae5b1ffd7573c1928d9fdd
```

The runner verifies the JAR hash and Fabric metadata before constructing the fixture,
discovers the installed Biolith JAR instead of assuming an alpha filename, and preserves
the previous fixture under `build/boat-mask-smoke-history/` before a reset. It also
requires every stage and the result artifact when Gradle exits normally. The fixture is
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
