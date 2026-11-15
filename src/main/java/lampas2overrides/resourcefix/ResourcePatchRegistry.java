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

		// 7. Paladins and Rogues 3.1.3.003+26.2 aeternium weapons (BetterEnd-gated) - the handle key is the list
		// ["#c:wood_sticks", "minecraft:stick"]; a 26.2 ingredient list holds item ids only, so the tag entry fails to
		// parse and the recipe is dropped. The replacement uses "minecraft:stick", as upstream 3.1.4.1+26.3 does.
		aeterniumRecipe("paladins", "claymore", "fa5e637b0bc9a4c9a44d38e42cd1fa846d9430e76e835a3c8c283975b431c82c");
		aeterniumRecipe("paladins", "great_hammer", "820b2782dbf37a61ddf3722eb1a40354fad5ef75a015cc08b50bb0dbcff33622");
		aeterniumRecipe("paladins", "mace", "67265d2248c2c53c82217fed21f3f75b3bf449a936fa5f1ad423629cf8dd07fa");
		aeterniumRecipe("rogues", "dagger", "50d78f25dda5b29943529d6ca91225e4fceb403cdb387cce6d3e85353420e1e8");
		aeterniumRecipe("rogues", "double_axe", "25abbc0b23f2aa5485351370763990f008d4af0bb424d0b30ed8ea675824a648");
		aeterniumRecipe("rogues", "glaive", "b2da2ec235865b2f1e9613f764199904db61d00718bc5ef1c4a7e02ecef89fdf");
		aeterniumRecipe("rogues", "sickle", "2277d4e7cb73b661c964dad281e116c86c9dbeb08e7abe0abc6784fa38613483");

		// 8. RPG Series 26.2 spells - martial cooldowns stop scaling with haste (D3 P1), twelve crowd-control
		// apply limits reach the End top band at Pinnacle (D9), and Fire Wall and the Meteor burning ground deal
		// 0.4 x their upstream damage. 66 patches across seven jars.
		RpgSeriesSpellPatches.registerAll(ResourcePatchRegistry::register);

		// 9. Waystone template pools. These pools are the only places Structory: Towers 1.0.17, Dungeons and Taverns 5.3.2
		// and Trek B0.6.2 place Waystones blocks (the four Structory *fwaystone pools place Fabric Waystones blocks).
		// The Structory and Dungeons pools are emptied: each replacement is the upstream pool with its single element
		// replaced by minecraft:empty_pool_element, which JigsawPlacement skips without the "Empty or non-existent pool"
		// warning, leaving the jigsaw's final_state. Trek's eight pools place Waystones' 6x6x6 village plaza
		// (waystones:village/*), so each is repointed to lampas:village/waystone_plaza/common or
		// lampas:village/waystone_plaza/desert, copies of those plaza templates without the two waystone blocks.
		String structoryDir = "data/structory_towers/worldgen/template_pool/waystones/";
		waystonePool("structory_towers", "1.0.17", structoryDir + "deepslate_brick_fwaystone.json",
			"414c87b5791dc3024701f32bbacb671e3d283eac8d9746c0f48edc4a7e349dc5");
		waystonePool("structory_towers", "1.0.17", structoryDir + "desert_fwaystone.json",
			"df3c673cacc178d663608c4d2486ecedadf951933449f3aeb0838514749fa831");
		waystonePool("structory_towers", "1.0.17", structoryDir + "fwaystone.json",
			"3d6286a4feabb69a1d732adeca90c6596e61e8b1db2016d7b187c830cd3740be");
		waystonePool("structory_towers", "1.0.17", structoryDir + "mossy_waystone.json",
			"b19f8302dd87c95ef7d68d5e73a3023fbc39b536b6dfaa968f7cd83d281bcdf6");
		waystonePool("structory_towers", "1.0.17", structoryDir + "sandy_waystone.json",
			"05d6a77429eccaa0a0b47e4ead47de8da5070443d32d05a64d887345ee32062f");
		waystonePool("structory_towers", "1.0.17", structoryDir + "stone_brick_fwaystone.json",
			"a2954c63280174b461cee4759de15c807cdfa2ba442a7344ede8640c8bcac9a4");
		waystonePool("structory_towers", "1.0.17", structoryDir + "waystone.json",
			"4894004faeaa002d735c334777ec72227758e7f5a5adfddf8d8b429c9db402ee");

		String dungeonsDir = "data/nova_structures/worldgen/template_pool/shrine/waystone/";
		waystonePool("mr_dungeons_andtaverns", "5.3.2", dungeonsDir + "waystone.json",
			"b103d4054cde7d17c49cc65bdf2b328997368f9323f7d4dac2a366e2f2186df1");

		String trekDir = "data/trek/worldgen/template_pool/village/waystone/";
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "cherry.json",
			"850017bd0e275709407cda7e963c645c959a295de194512ce6b1b70647f61f4a");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "desert.json",
			"f7ae2a4357695fcf59a65a172173472dfc2d00e9cdbe6d59e341a28074e80eb5");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "plains.json",
			"0331ef5ca14d7ea87eeb07d391d0b796e81f839b00b5577bdd47fd997a760e92");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "savanna.json",
			"1296c2e4f8c2e60780f6c2d0b5d78e2f6b54a8265741e7b13bb4e41311ec3cbf");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "snowy.json",
			"b26d176c1195cc1878ad8aed82738533a54550cb033d5298d4224ec13def068a");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "swamp.json",
			"08cd294d0e03f6aa3d9752eca5e372ecc4f3924bbba9fefb2c641d7887804529");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "swamp_vanilla.json",
			"7bcb0ac8c4a5155371faf811fc6eb720892b4c3f12eb1f3f731f9a38eefa0585");
		waystonePool("mr_trek", "1-B0.6.2", trekDir + "taiga.json",
			"272f6f0e894f8fe61fa811856cb3af93baa6e33a2ad4db25c022cbe3b021a8c6");

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

	private static void aeterniumRecipe(String modId, String weapon, String upstreamSha256) {
		String path = "data/" + modId + "/recipe/aeternium_" + weapon + ".json";
		register(new ResourcePatch(modId, "3.1.3.003+26.2", path, upstreamSha256,
			"lampas2-overrides/resource-patches/" + modId + "/3.1.3.003+26.2/" + path));
	}

	private static void waystonePool(String modId, String version, String path, String upstreamSha256) {
		register(new ResourcePatch(modId, version, path, upstreamSha256,
			"lampas2-overrides/resource-patches/" + modId + "/" + version + "/" + path));
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
