# Beautiful Items production-JAR client probe

This probe launches a fresh, isolated Fabric client fixture to verify actual loader mixin application and model lookup behavior for the installed Beautiful Enchanted Books 6.0.0 and BeautifulPotions 2.0.1 JARs. It uses the installed Minecraft 26.2 client JAR, Fabric Loader 0.19.5, Java 25, and installed Fabric API 0.161.0+26.2. It does not use the Gradle development runtime.

Run from the repository root:

```powershell
python tools/beautiful-items-probe/run.py baseline
python tools/beautiful-items-probe/run.py patched --override-jar build/libs/lampas2-overrides-1.0.0.jar
```

`--minecraft-root` can select another installed `.minecraft-lampas` root. By default the runner uses `%APPDATA%\.minecraft-lampas`. `--timeout-seconds` defaults to 180. Java 25 and Python must be available on `PATH`.

Every invocation creates a new, non-reused fixture under ignored `build/beautiful-items-probe/<RunId>`. The runner verifies pinned mod IDs, versions, and SHA-256 values before copying the vendor JARs and root Fabric API JAR. It extracts Fabric API's nested modules only into that run's compile tree. The runtime mods directory contains those three production dependencies, the compiled probe, and—only in patched mode—exactly one selected production override JAR. Fabric Loader's runtime mod origins and SHA-256 values are checked against those exact fixture paths. Patched mode also requires both target Mixin application lines in the process log. Baseline mode fails if an override is loaded.

The client waits for its initial resource load, then checks all 695 BEB and 828 potion `ExtraModelKey` values through `FabricModelManager.getModel`. It performs two real asynchronous resource reloads, filters one audited model from each vendor, blocks all item model resources, and restores the resource packs. Expected cache hits are derived from the pinned baseline and the two audited resource removals. The result file records every stage, actual counts, missing-key samples, artifact origins and hashes, particle sprite selections, and runner validation. A successful run must exit normally with code zero and report `PASS`.

The resolver check uses synthetic stacks in GUI context. It gives Sharpness a standalone synthetic holder and binds an empty component map only if the two fixture item holders are unbound; this menu-only client has no world registry synchronization from which to obtain those item prototype maps. Each synthetic stack explicitly supplies its `ITEM_MODEL` plus stored Sharpness or Swiftness potion contents. The probe calls `ItemModelResolver.updateForTopItem` and checks the selected particle sprite. It skips sprite selection during the empty-resource stage because that stage intentionally removes every baked item model. The fixture has no world, player, server, screenshot, or gameplay interaction; this is controlled model-selection evidence, not visual or gameplay acceptance.

Logs, inputs, fixture JARs, compiled probe output, and result files are retained under each RunId for review. The runner does not install, stage, publish, or copy anything into a real client mods directory.
