# Trinkets V1460 structure DFU repair

## Scope and artifact gates

The common-side V1460 hook supports two exact `trinkets_updated` artifacts on Minecraft 26.2. It
selects a repair profile only when both the metadata version and the SHA-256 of
`eu/pb4/trinkets/mixin/datafixer/V1460Mixin.class` match:

| Version | V1460 class SHA-256 | Artifact SHA-256 | Schema contract |
|---|---|---|---|
| 4.1.0+26.2 | `089c59253b7b9ab9f7f0a54c1f0dc7912379ad5bfa335c4acfcfbb91e17914f9` | `e1b49012e3fe25e63812ef73dfd0f2e0891fd6aa88c7ef4e73d781fddc714f39` | Old duplicate root remainder |
| 4.1.1+26.2 | `07f35c1ac7a56705c9671b7703b3c58dddbdc419c0fb00edf89b13e757810265` | `2365e8a19b5f62c812e95ac5fc23ee163bc753a5d9ec38f517f72da269eb0c4b` | Current flat slot and Cardinal schema |

Each pair is independent. A version with the other artifact's class hash remains disabled. The 4.1.1
repair matches the exact `DSL.and(cardinal, trinkets, vanillaTail)` template emitted by that class;
the 4.1.0 repair retains its separate `allWithRemainder` shape check. Both reuse the vanilla tail,
and unknown template shapes remain unchanged.

## 4.1.1 data loss

Trinkets 4.1.1 removed the duplicate outer remainder, but typed read/write of the emitted schema
still drops a current root such as
`trinkets:{__version:1,"hand/ring":{Items:[...],cosmetic:[...],hidden_slots:[]}}` to
`trinkets:{}`. A legacy `cardinal_components.trinkets:trinkets` value can also consume the enclosing
`cardinal_components` object and discard other components.

The repair types `Items` and `cosmetic` at both flat and legacy grouped slot paths. Its per-entry
remainder branches retain the version scalar, slot metadata, unknown values, empty values, and
Cardinal siblings. The original vanilla template remains the owner of the root remainder. Minecraft
NBT compounds cannot contain JSON-style null values, so the fixtures use valid NBT scalar,
compound, and list values rather than JSON nulls.

## Verification

The unit regression reflects the test-runtime-only pinned 4.1.1 artifact, verifies its metadata and
class hash, and invokes its private `attachTrinketFixer` method to build the exact upstream schema.
A marker `DataFix` then checks that the unpatched template reproduces current-root and Cardinal
sibling loss, while the repaired template migrates markers in vanilla inventory, flat `Items`,
`cosmetic`, and legacy grouped Trinkets stacks. A no-rewrite typed read/write checks that supported
current data and unknown values survive unchanged. The previous 4.1.0 migration fixture remains.

After building the production JAR, run the isolated helper probe with the exact audit artifact and
the existing runtime classpath:

```powershell
.\tools\trinkets-dfu-probe\run.ps1 `
  -ProductionJar build\libs\lampas2-overrides-1.0.0.jar `
  -TrinketsJar C:\path\to\trinkets-4.1.1+26.2.jar `
  -RuntimeClasspathFile build\wind-state-probe\frozen3-wilder43\compile-classpath.txt
```

The probe verifies both JARs, invokes the real Trinkets attachment method, runs a DataFixer
migration through the resulting V1460 schema, checks the production helper, and does not launch a
game or modify a world. The helper probe alone does not prove Mixin application; the separate
headless runtime probe below checks the actual Minecraft fixer. Dedicated-server structure loading,
saved-world migration and deployed-pack checks remain separate acceptance steps.

The focused Trinkets tests passed (16 tests), the coordinated Gradle build passed, and the probe
passed on Java 25.0.4 against production JAR SHA-256
`7efacfbb125d93ab9eb17de2d796c61feaf3d04547cd62294f1ba0f5ad319a71` and the audited Trinkets JAR
SHA-256 above. It verified the reproduced upstream loss, current flat and legacy grouped item
migrations, Cardinal sibling retention, unknown scalar/empty data, and no-rewrite round-trip.

The separate headless Fabric `SERVER` probe loaded the exact production JAR through `KnotServer`
and exercised Minecraft's real V1460 schemas and full DFU chain from DataVersion 1460 to 4903.
The baseline reproduced flat inventory and Cardinal sibling loss for both `PLAYER` and `ENTITY`.
The patched run preserved each complete fixture in typed read/write and migrated vanilla,
flat `Items`/`cosmetic`, and legacy Cardinal items against independent vanilla-field and
`ITEM_STACK` controls. Exported bytecode confirms Trinkets's return hook runs before the Lampas
repair in both target lambdas. Both processes exited `0` from a fixture-only `preLaunch` entrypoint
before server Main, mod initializers, world creation or a listening server. This verifies woven
schema behavior on controlled fixtures; it does not establish live saved-world migration.
Exact origins, hashes, results and hook order are in
[the 4.1.1 evidence](evidence/trinkets-4.1.1-dfu.json).

Historical 4.1.0 validation loaded the exact templates `grim_kingdoms:plains/zweiberg_plains` at
`200,80,0` and `grim_kingdoms:water/blackwater` at `300,80,0` with zero decode failures and zero
schema mismatches. The matching earlier production JAR was
`e2f05414e9fdc5c16f7860670c14cef05e7b50f78789917afbc11d01b36ebbc8`; that result applies to the
4.1.0 deployment only.
