package lampas2overrides.resourcefix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of known-defective mod resources targeted for virtual patching.
 * Each entry is strictly version-gated and SHA-256 fingerprinted against upstream releases.
 */
public final class ResourcePatchRegistry {

	private static final Map<ResourcePatchKey, ResourcePatch> PATCHES = new HashMap<>();
	/** Declared versions per normalized "modId|path"; the resolver asks for these on every unpatched resource. */
	private static final Map<String, List<String>> VERSIONS = new HashMap<>();

	static {
		// 1. Formations Overworld 1.0.5+c - The loot tables are already 26.2-compatible; only pack.mcmeta needs the format range.
		register(new ResourcePatch(
			"formationsoverworld",
			"1.0.5+c",
			"pack.mcmeta",
			"ffa966eb7835cc4de1273945333236331eff33116e918869e4e29c881b39f940",
			"lampas2-overrides/resource-patches/formationsoverworld/1.0.5+c/pack.mcmeta"
		));

		// 2. Grim Kingdoms Lost Structures Ruins 2.0.3 - Missing supported_formats in pack.mcmeta
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins",
			"2.0.3",
			"pack.mcmeta",
			"a43a389339936bfb6171776a5d41dba3acf4aab2087ad8ad1eb84c11d09018c4",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/pack.mcmeta"
		));

		// Grim Kingdoms 2.0.3 - zero-level enchantments in audited structure items.
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/plains/glimmervault_fortress_plains.nbt",
			"ceaad2f0e6d5b1075872c4a9ea117e51d8cd12542b6a913b67df486ab5795651",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/plains/glimmervault_fortress_plains.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/plains/sandfell_outpost.nbt",
			"cbdeabe0a9b552f4b605c2686c3f52cd76d12844eb376627ecc10f769939396f",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/plains/sandfell_outpost.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/snowy/blackridge_watch.nbt",
			"008469e97c9ef31779adc77bfec7e0c2c14d92559092f102650a3a62c0f82462",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/snowy/blackridge_watch.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/snowy/frozen_onion_fort2.nbt",
			"26314ba4c97bb852b50867c8f215e101714a9168314663c984b2523fc1e90475",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/snowy/frozen_onion_fort2.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/snowy/glimmervault_fortress.nbt",
			"dec9c010ddf102cde7a6f2188e248705b11776422cec5e149d98954b882c47f4",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/snowy/glimmervault_fortress.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/snowy/winterhold_citadel.nbt",
			"8c6674cf35602fa52886a2725b5aca69451c0fc28d57fd8221b7a9ab67d1ef7e",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/snowy/winterhold_citadel.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/taiga/blackridge_watch_taiga.nbt",
			"46ef7b657cec178141ecf98d00fc8e3f8f65bf0528cca492f7253507e6a20249",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/taiga/blackridge_watch_taiga.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/taiga/winterhold_citadel_taiga.nbt",
			"2dc471af463c7df181e0851cfe65f8c85bbc698a7c240a4e6207e0edfe564617",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/taiga/winterhold_citadel_taiga.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/water/ship3.nbt",
			"241a5a5172d04610627f889395668f456b350f4884d29078b50e80a30fb0afa7",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/water/ship3.nbt"
		));
		register(new ResourcePatch(
			"mr_grim_kingdomsloststructuresruins", "2.0.3",
			"data/grim_kingdoms/structure/water/ship9.nbt",
			"d5e0b3fd913cc5cfc48c272346b9c43fb0e64d684f007fff5034023e1fab85aa",
			"lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/data/grim_kingdoms/structure/water/ship9.nbt"
		));

		// 3. Pyrite 0.18.3+26.2 - Builtin datapack pack.mcmeta files with max format 81 instead of 107
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

		// 4. Easter's Delight 1.3.1 - Builtin recipe override pack.mcmeta missing supported_formats
		register(new ResourcePatch(
			"eastersdelight",
			"1.3.1",
			"resourcepacks/farmersdelight_overrides/pack.mcmeta",
			"f9a0ce7a98f832eb2f246c521c39c937a1ddef167b56718312584f340b14ed82",
			"lampas2-overrides/resource-patches/eastersdelight/1.3.1/resourcepacks/farmersdelight_overrides/pack.mcmeta"
		));

		// 5. Wilder Wild 4.3.3 - stone pool's radius 15 reaches chunk distance 2
		// under FrozenLib's +1 sampling and four-neighbor exposure checks with C2ME's write radius 1.
		register(new ResourcePatch(
			"wilderwild",
			"4.3.3",
			"data/wilderwild/worldgen/configured_feature/stone_pool.json",
			"59cf59a1c1e86f9627361e1dcec3250b81d535c6e84ff7c3b2950d9b90f2efe4",
			"lampas2-overrides/resource-patches/wilderwild/4.3.3/data/wilderwild/worldgen/configured_feature/stone_pool.json"
		));

		// 6. Rogues 3.1.3.003+26.2 - Sharpness rewritten as +8% per level of base attack damage plus flat
		// attack-damage modifiers (weapon damage, Strength). Restores flat Sharpness (Apothic Enchanting's
		// definition) and keeps the damage exclusive set so Sharpness cannot stack with Smite or Bane.
		register(new ResourcePatch(
			"rogues",
			"3.1.3.003+26.2",
			"data/minecraft/enchantment/sharpness.json",
			"fe176357898633059d7e37643e19115d25352725a0015630f92f39b2f96f620e",
			"lampas2-overrides/resource-patches/rogues/3.1.3.003+26.2/data/minecraft/enchantment/sharpness.json"
		));

		// 7. RPG Series 26.2 spells - martial cooldowns stop scaling with haste (D3 P1), twelve crowd-control
		// apply limits reach the End top band at Pinnacle (D9), and Fire Wall and the Meteor burning ground deal
		// 0.4 x their upstream damage. 66 patches across seven jars.
		RpgSeriesSpellPatches.registerAll(ResourcePatchRegistry::register);

		VERSIONS.replaceAll((resource, versions) -> versions.stream().sorted().toList());
	}

	private static void register(ResourcePatch patch) {
		ResourcePatchKey key = ResourcePatchKey.of(patch.modId(), patch.expectedVersion(), patch.resourcePath());
		// Keys are normalized, so two spellings of one version would otherwise replace each other silently.
		if (PATCHES.put(key, patch) != null) {
			throw new IllegalStateException("Duplicate resource patch " + key);
		}
		VERSIONS.computeIfAbsent(resourceKey(key.modId(), key.resourcePath()), resource -> new ArrayList<>())
			.add(patch.expectedVersion());
	}

	private static String resourceKey(String normalizedModId, String normalizedPath) {
		return normalizedModId + "|" + normalizedPath;
	}

	public static ResourcePatch findPatch(String modId, String version, String resourcePath) {
		return PATCHES.get(ResourcePatchKey.of(modId, version, resourcePath));
	}

	public static ResourcePatch findPatch(String modId, String resourcePath) {
		String normMod = modId.toLowerCase(Locale.ROOT);
		String normPath = ResourcePatchResolver.normalizePath(resourcePath).toLowerCase(Locale.ROOT);
		for (Map.Entry<ResourcePatchKey, ResourcePatch> entry : PATCHES.entrySet()) {
			if (entry.getKey().modId().equals(normMod) && entry.getKey().resourcePath().equals(normPath)) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** Registered versions, as declared, of every patch for this mod resource. */
	public static List<String> registeredVersions(String modId, String resourcePath) {
		String normMod = modId.toLowerCase(Locale.ROOT);
		String normPath = ResourcePatchResolver.normalizePath(resourcePath).toLowerCase(Locale.ROOT);
		return VERSIONS.getOrDefault(resourceKey(normMod, normPath), List.of());
	}

	public static Map<ResourcePatchKey, ResourcePatch> getAllPatches() {
		return Collections.unmodifiableMap(PATCHES);
	}

	private ResourcePatchRegistry() {
	}
}
