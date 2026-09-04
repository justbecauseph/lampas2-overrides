package lampas2overrides.resourcefix;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of known-defective mod resources targeted for virtual patching.
 * Each entry is strictly version-gated and SHA-256 fingerprinted against upstream releases.
 */
public final class ResourcePatchRegistry {

	private static final Map<ResourcePatchKey, ResourcePatch> PATCHES = new HashMap<>();

	static {
		// 1. Better Lib 2.1.1 - Malformed POI tags with comment prefix '//{'
		register(new ResourcePatch(
			"better_lib",
			"2.1.1",
			"data/minecraft/tags/point_of_interest_type/acquirable_job_site.json",
			"2f605b74d0e3e5a0044e809b0d01bce9f25012800080527d2229c2576942f06d",
			"lampas2-overrides/resource-patches/better_lib/2.1.1/data/minecraft/tags/point_of_interest_type/acquirable_job_site.json"
		));

		// 2. Moogs Nether Structures 3.0.0 - Missing supported_formats in pack.mcmeta
		register(new ResourcePatch(
			"mns",
			"3.0.0",
			"pack.mcmeta",
			"2f42633d021798b55c2c51ba36364ce59080c0025e75bcb24fdc32f47243e90b",
			"lampas2-overrides/resource-patches/mns/3.0.0/pack.mcmeta"
		));

		// 3. Moogs Voyager Structures 5.0.11 - Missing supported_formats in pack.mcmeta
		register(new ResourcePatch(
			"mvs",
			"5.0.11",
			"pack.mcmeta",
			"60b4eafe19cd5b676b457256fa3548a650830553efbc6945b4b6178fe7e627b1",
			"lampas2-overrides/resource-patches/mvs/5.0.11/pack.mcmeta"
		));

		// 4. Moogs Voyager Structures 5.0.14 - Missing supported_formats in pack.mcmeta
		register(new ResourcePatch(
			"mvs",
			"5.0.14",
			"pack.mcmeta",
			"63edcfe8244ea844e2c98d429330e112b92a5b7ca906aac4fc61421458036dc1",
			"lampas2-overrides/resource-patches/mvs/5.0.14/pack.mcmeta"
		));

		// 5. Formations Overworld 1.0.5+a - Missing supported_formats in pack.mcmeta & 26.2 chain renaming
		register(new ResourcePatch(
			"formationsoverworld",
			"1.0.5+a",
			"pack.mcmeta",
			"ffa966eb7835cc4de1273945333236331eff33116e918869e4e29c881b39f940",
			"lampas2-overrides/resource-patches/formationsoverworld/1.0.5+a/pack.mcmeta"
		));
		register(new ResourcePatch(
			"formationsoverworld",
			"1.0.5+a",
			"data/formationsoverworld/loot_table/stone_tower/smithing.json",
			"380118821ec580c348da35a5983283499950e976099e96123e015cbe508346c0",
			"lampas2-overrides/resource-patches/formationsoverworld/1.0.5+a/data/formationsoverworld/loot_table/stone_tower/smithing.json"
		));
		register(new ResourcePatch(
			"formationsoverworld",
			"1.0.5+a",
			"data/formationsoverworld/loot_table/witch_tower/smithing.json",
			"32c4bd2ea2c860a13ebde2ac05fe964bc2334914584cb68c14f4a994d9630d46",
			"lampas2-overrides/resource-patches/formationsoverworld/1.0.5+a/data/formationsoverworld/loot_table/witch_tower/smithing.json"
		));

		// 6. Grim Kingdoms Lost Structures Ruins 2.0.3 - Missing supported_formats in pack.mcmeta
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins",
			"2.0.3",
			"pack.mcmeta",
			"a43a389339936bfb6171776a5d41dba3acf4aab2087ad8ad1eb84c11d09018c4",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/pack.mcmeta"
		));

		// 7. Pyrite 0.18.3+26.2 - Builtin datapack pack.mcmeta files with max format 81 instead of 107
		register(new ResourcePatch(
			"pyrite",
			"0.18.3+26.2",
			"resourcepacks/pyrite_azalea/pack.mcmeta",
			"c8fb5ec87aece1041b99e9910d6f7e07b6dc96f3027b0ec8eae27b3463f90d0d",
			"lampas2-overrides/resource-patches/pyrite/0.18.3+26.2/resourcepacks/pyrite_azalea/pack.mcmeta"
		));
		register(new ResourcePatch(
			"pyrite",
			"0.18.3+26.2",
			"resourcepacks/pyrite_crafting_tables/pack.mcmeta",
			"31ce98a55a3b820c736e344379ca8e11cd778b6fb614cfdd1ba20bd6eff1bba0",
			"lampas2-overrides/resource-patches/pyrite/0.18.3+26.2/resourcepacks/pyrite_crafting_tables/pack.mcmeta"
		));
		register(new ResourcePatch(
			"pyrite",
			"0.18.3+26.2",
			"resourcepacks/pyrite_mushrooms/pack.mcmeta",
			"a1dff9a4b637842f2df0c680dd15e765a2ba99f41aa722b1b8f88201314104d5",
			"lampas2-overrides/resource-patches/pyrite/0.18.3+26.2/resourcepacks/pyrite_mushrooms/pack.mcmeta"
		));
		register(new ResourcePatch(
			"pyrite",
			"0.18.3+26.2",
			"resourcepacks/pyrite_oddities/pack.mcmeta",
			"8876a1008cf065aeb81b74e3fae0a7439f098b22cc6dbc11058395f4f1558188",
			"lampas2-overrides/resource-patches/pyrite/0.18.3+26.2/resourcepacks/pyrite_oddities/pack.mcmeta"
		));

		// 8. Easter's Delight 1.3.1 - Builtin recipe override pack.mcmeta missing supported_formats
		register(new ResourcePatch(
			"eastersdelight",
			"1.3.1",
			"resourcepacks/farmersdelight_overrides/pack.mcmeta",
			"f9a0ce7a98f832eb2f246c521c39c937a1ddef167b56718312584f340b14ed82",
			"lampas2-overrides/resource-patches/eastersdelight/1.3.1/resourcepacks/farmersdelight_overrides/pack.mcmeta"
		));
	}

	private static void register(ResourcePatch patch) {
		ResourcePatchKey key = ResourcePatchKey.of(patch.modId(), patch.expectedVersion(), patch.resourcePath());
		PATCHES.put(key, patch);
	}

	public static ResourcePatch findPatch(String modId, String version, String resourcePath) {
		return PATCHES.get(ResourcePatchKey.of(modId, version, resourcePath));
	}

	public static ResourcePatch findPatch(String modId, String resourcePath) {
		String normMod = modId.toLowerCase();
		String normPath = ResourcePatchResolver.normalizePath(resourcePath).toLowerCase();
		for (Map.Entry<ResourcePatchKey, ResourcePatch> entry : PATCHES.entrySet()) {
			if (entry.getKey().modId().equals(normMod) && entry.getKey().resourcePath().equals(normPath)) {
				return entry.getValue();
			}
		}
		return null;
	}

	public static boolean hasPatchForResource(String modId, String resourcePath) {
		return findPatch(modId, resourcePath) != null;
	}

	public static Map<ResourcePatchKey, ResourcePatch> getAllPatches() {
		return Collections.unmodifiableMap(PATCHES);
	}

	private ResourcePatchRegistry() {
	}
}
