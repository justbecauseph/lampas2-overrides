# Trinkets V1460 structure DFU repair

## Scope and artifact gate

The common-side V1460 hook supports one exact `trinkets_updated` artifact on Minecraft 26.2. It
selects the repair profile only when both the metadata version and the SHA-256 of
`eu/pb4/trinkets/mixin/datafixer/V1460Mixin.class` match:

| Version | V1460 class SHA-256 | Artifact SHA-256 | Schema contract |
|---|---|---|---|
| 4.1.1+26.2 | `07f35c1ac7a56705c9671b7703b3c58dddbdc419c0fb00edf89b13e757810265` | `2365e8a19b5f62c812e95ac5fc23ee163bc753a5d9ec38f517f72da269eb0c4b` | Current flat slot and Cardinal schema |

A different version, or this version with a different class hash, leaves the hook disabled. The
repair matches the exact `DSL.and(cardinal, trinkets, vanillaTail)` template emitted by that class.
It reuses the vanilla tail, and unknown template shapes remain unchanged.

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
current data and unknown values survive unchanged.

After building the production JAR, write the client compile classpath and run the isolated helper
probe with the exact audit artifact:

```powershell
./gradlew -I tools/trinkets-dfu-probe/classpath.init.gradle trinketsProbeClasspath
.\tools\trinkets-dfu-probe\run.ps1 `
  -ProductionJar build\libs\lampas2-overrides-1.0.0.jar `
  -TrinketsJar C:\path\to\trinkets-4.1.1+26.2.jar
```

The init script writes `build/trinkets-dfu-probe/compile-classpath.txt`, which `run.ps1` reads by
default; pass `-RuntimeClasspathFile` to use another file.

The probe verifies both JARs, invokes the real Trinkets attachment method, runs a DataFixer
migration through the resulting V1460 schema, checks the production helper, and does not launch a
game or modify a world. The helper probe alone does not prove Mixin application; the separate
headless runtime probe below checks the actual Minecraft fixer. Dedicated-server structure loading,
saved-world migration and deployed-pack checks remain separate acceptance steps.

The focused Trinkets tests passed (16 tests, run before the 4.1.0 cases were removed), the coordinated
Gradle build passed, and the probe passed on Java 25.0.4 against production JAR SHA-256
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
[the 4.1.1 evidence](evidence/trinkets-4.1.1-dfu.json). That file predates the 2026-10-13 removal
of the 4.1.0 profile; its 4.1.0 entries are historical.

On 2026-10-13, after the 4.1.0 profile was removed, the helper probe passed again against production
JAR SHA-256 `53e278ad3914af9183eec1c87f78f3a82368138acc24065924b006d5568c5e4f` and the installed
`trinkets-4.1.1+26.2.jar`. The 4.1.1 gate, `currentVanillaTail` and `repair411` are unchanged; the
headless `SERVER` probe above was not re-run on this JAR.
