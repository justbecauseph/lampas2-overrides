package lampas2overrides.resourcefix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Waystone template pools in Structory: Towers 1.0.17, Dungeons and Taverns 5.3.2 and Trek B0.6.2. Each upstream pool has
 * one {@code single_pool_element} that places a Waystones structure (Fabric Waystones for Structory's four
 * {@code *fwaystone} pools). Structory and Dungeons replacements swap that element for
 * {@code minecraft:empty_pool_element} and change nothing else. Trek's eight pools place Waystones' 6x6x6 village plaza, so
 * their replacements change only the element's location, to {@code lampas:village/waystone_plaza/common} or
 * {@code lampas:village/waystone_plaza/desert}. Those two templates ship in this mod as Waystones 26.2.0.13's plaza with the
 * two waystone blocks set to air; the NBT test checks them against the upstream fixtures.
 */
public class WaystonePoolPatchTest {

	private static final String STRUCTORY_DIR = "data/structory_towers/worldgen/template_pool/waystones/";
	private static final String DUNGEONS_DIR = "data/nova_structures/worldgen/template_pool/shrine/waystone/";
	private static final String TREK_DIR = "data/trek/worldgen/template_pool/village/waystone/";
	private static final String EMPTY_ELEMENTS = "[{\"weight\":1,\"element\":{\"element_type\":\"minecraft:empty_pool_element\"}}]";
	private static final String TREK_MOD_ID = "mr_trek";
	private static final String WAYSTONE_PREFIX = "waystones:";
	private static final String AIR = "minecraft:air";
	/** The Waystones plaza location in each upstream Trek pool, and the lampas copy of that plaza that replaces it. */
	private static final Map<String, String> PLAZA_FOR_UPSTREAM = Map.of(
		"waystones:village/common/waystone", "lampas:village/waystone_plaza/common",
		"waystones:village/desert/waystone", "lampas:village/waystone_plaza/desert"
	);

	/** One patched pool: mod ID, declared version, resource path, upstream SHA-256, and the prefix of the location it places. */
	private record Pool(String modId, String version, String path, String sha256, String locationPrefix) {
	}

	/** A Waystones 26.2.0.13 plaza template (upstream fixture) and the copy this mod ships under lampas:village/waystone_plaza. */
	private record PlazaTemplate(String name, String upstreamSha256, String shippedSha256) {
		String upstreamFixture() {
			return "upstream/waystones/26.2.0.13/data/waystones/structure/village/" + name + "/waystone.nbt";
		}

		String shippedResource() {
			return "data/lampas/structure/village/waystone_plaza/" + name + ".nbt";
		}
	}

	/** Result of {@code withoutWaystoneBlocks}: the transformed template and how many palette entries and blocks it changed. */
	private record Removal(CompoundTag template, int paletteEntries, int blocks) {
	}

	private static final Pool STRUCTORY_WAYSTONE = new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "waystone.json",
		"4894004faeaa002d735c334777ec72227758e7f5a5adfddf8d8b429c9db402ee", "structory_towers:waystones/");
	private static final Pool DUNGEONS_SHRINE = new Pool("mr_dungeons_andtaverns", "5.3.2", DUNGEONS_DIR + "waystone.json",
		"b103d4054cde7d17c49cc65bdf2b328997368f9323f7d4dac2a366e2f2186df1", "nova_structures:shrine/shrine_waystone/");
	private static final Pool TREK_TAIGA = new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "taiga.json",
		"272f6f0e894f8fe61fa811856cb3af93baa6e33a2ad4db25c022cbe3b021a8c6", "waystones:village/");

	private static final List<Pool> POOLS = List.of(
		new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "deepslate_brick_fwaystone.json",
			"414c87b5791dc3024701f32bbacb671e3d283eac8d9746c0f48edc4a7e349dc5", "structory_towers:waystones/"),
		new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "desert_fwaystone.json",
			"df3c673cacc178d663608c4d2486ecedadf951933449f3aeb0838514749fa831", "structory_towers:waystones/"),
		new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "fwaystone.json",
			"3d6286a4feabb69a1d732adeca90c6596e61e8b1db2016d7b187c830cd3740be", "structory_towers:waystones/"),
		new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "mossy_waystone.json",
			"b19f8302dd87c95ef7d68d5e73a3023fbc39b536b6dfaa968f7cd83d281bcdf6", "structory_towers:waystones/"),
		new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "sandy_waystone.json",
			"05d6a77429eccaa0a0b47e4ead47de8da5070443d32d05a64d887345ee32062f", "structory_towers:waystones/"),
		new Pool("structory_towers", "1.0.17", STRUCTORY_DIR + "stone_brick_fwaystone.json",
			"a2954c63280174b461cee4759de15c807cdfa2ba442a7344ede8640c8bcac9a4", "structory_towers:waystones/"),
		STRUCTORY_WAYSTONE,
		DUNGEONS_SHRINE,
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "cherry.json",
			"850017bd0e275709407cda7e963c645c959a295de194512ce6b1b70647f61f4a", "waystones:village/"),
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "desert.json",
			"f7ae2a4357695fcf59a65a172173472dfc2d00e9cdbe6d59e341a28074e80eb5", "waystones:village/"),
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "plains.json",
			"0331ef5ca14d7ea87eeb07d391d0b796e81f839b00b5577bdd47fd997a760e92", "waystones:village/"),
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "savanna.json",
			"1296c2e4f8c2e60780f6c2d0b5d78e2f6b54a8265741e7b13bb4e41311ec3cbf", "waystones:village/"),
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "snowy.json",
			"b26d176c1195cc1878ad8aed82738533a54550cb033d5298d4224ec13def068a", "waystones:village/"),
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "swamp.json",
			"08cd294d0e03f6aa3d9752eca5e372ecc4f3924bbba9fefb2c641d7887804529", "waystones:village/"),
		new Pool("mr_trek", "1-B0.6.2", TREK_DIR + "swamp_vanilla.json",
			"7bcb0ac8c4a5155371faf811fc6eb720892b4c3f12eb1f3f731f9a38eefa0585", "waystones:village/"),
		TREK_TAIGA
	);

	private static final List<PlazaTemplate> PLAZAS = List.of(
		new PlazaTemplate("common", "8018c9c611c0222e81b8a511c2719c2188d11b4c95bb01a0ad5112f4ec95bef5",
			"2ca1824afd8149c034e73e51258653b66eb36d27483aada0e886252e02fc7985"),
		new PlazaTemplate("desert", "88488c210fe34985fca3d55213f4aac39291b88b82f2d36a1c064638bb64a907",
			"82ebc40a9c3aaa9b32d91ee16c37c246914932ebe70342670bfbac53107b6e37")
	);

	@BeforeEach
	void setUp() {
		ResourcePatchResolver.clearLogHistoryForTests();
	}

	@Test
	void fixturesMatchPinnedHashesAndRegisteredPatches() throws IOException {
		assertEquals(16, POOLS.size());
		for (Pool pool : POOLS) {
			byte[] upstream = readRaw(fixture(pool));
			assertEquals(pool.sha256(), ResourcePatchResolver.sha256Hex(upstream), "Fixture is not the deployed jar entry: " + pool.path());

			ResourcePatch patch = ResourcePatchRegistry.findPatch(pool.modId(), pool.version(), pool.path());
			assertNotNull(patch, pool.path());
			assertEquals(pool.version(), patch.expectedVersion(), pool.path());
			assertEquals(pool.sha256(), patch.expectedSha256(), pool.path());
		}
	}

	@Test
	void upstreamPoolsPlaceOneWaystoneElement() throws IOException {
		for (Pool pool : POOLS) {
			JsonArray elements = parse(readRaw(fixture(pool))).getAsJsonArray("elements");
			assertEquals(1, elements.size(), pool.path());
			JsonObject element = elements.get(0).getAsJsonObject().getAsJsonObject("element");
			assertEquals("minecraft:single_pool_element", element.get("element_type").getAsString(), pool.path());
			assertTrue(element.get("location").getAsString().startsWith(pool.locationPrefix()), pool.path());
		}
	}

	@Test
	void replacementChangesOnlyTheElements() throws IOException {
		for (Pool pool : POOLS) {
			JsonObject expected = expectedReplacement(pool, parse(readRaw(fixture(pool))));

			ResourcePatch patch = ResourcePatchRegistry.findPatch(pool.modId(), pool.version(), pool.path());
			assertNotNull(patch, pool.path());
			JsonObject replacement = parse(readRaw(patch.replacementPath()));
			assertEquals(expected, replacement, "Replacement must equal upstream with only the elements changed: " + pool.path());

			String elements = replacement.get("elements").toString();
			if (pool.modId().equals(TREK_MOD_ID)) {
				assertTrue(elements.contains("lampas:village/waystone_plaza/"), pool.path());
				assertFalse(replacement.toString().contains(WAYSTONE_PREFIX), pool.path());
			} else {
				assertFalse(elements.contains("single_pool_element"), pool.path());
				assertFalse(elements.contains("location"), pool.path());
			}
		}
	}

	/**
	 * A structure id {@code ns:path} loads from {@code data/ns/structure/path.nbt}. A location with no file behind it
	 * loads as an empty template without a warning, so every Trek town center would silently lose its plaza.
	 */
	@Test
	void trekReplacementLocationsResolveToShippedPlazaTemplates() throws IOException {
		List<String> shipped = PLAZAS.stream().map(PlazaTemplate::shippedResource).toList();
		int checked = 0;
		for (Pool pool : POOLS) {
			if (!pool.modId().equals(TREK_MOD_ID)) {
				continue;
			}
			ResourcePatch patch = ResourcePatchRegistry.findPatch(pool.modId(), pool.version(), pool.path());
			assertNotNull(patch, pool.path());
			String location = parse(readRaw(patch.replacementPath())).getAsJsonArray("elements").get(0).getAsJsonObject()
				.getAsJsonObject("element").get("location").getAsString();
			int colon = location.indexOf(':');
			assertTrue(colon > 0, pool.path() + " location " + location);
			String resource = "data/" + location.substring(0, colon) + "/structure/" + location.substring(colon + 1) + ".nbt";
			assertTrue(shipped.contains(resource), pool.path() + " points at " + resource + ", which is not a shipped plaza template");
			assertTrue(readRaw(resource).length > 0, resource);
			checked++;
		}
		assertEquals(8, checked);
	}

	@Test
	void appliesEachPatchForDeclaredVersionAndHash() throws IOException {
		for (Pool pool : POOLS) {
			byte[] upstream = readRaw(fixture(pool));
			try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
				IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
					ResourcePatchResolverTest.createMockModContainer(pool.modId(), pool.version()),
					pool.path(),
					() -> new ByteArrayInputStream(upstream)
				);
				assertNotNull(patched, pool.path());
				try (InputStream in = patched.get()) {
					JsonObject applied = parse(in.readAllBytes());
					assertEquals(expectedReplacement(pool, parse(upstream)).get("elements"), applied.get("elements"), pool.path());
					if (pool.modId().equals(TREK_MOD_ID)) {
						String location = applied.getAsJsonArray("elements").get(0).getAsJsonObject()
							.getAsJsonObject("element").get("location").getAsString();
						assertTrue(location.startsWith("lampas:village/waystone_plaza/"), pool.path());
					}
				}
				assertTrue(logs.has(Level.INFO, "Applied " + pool.modId() + " " + pool.version()
					+ " compatibility resource: " + pool.path()), "Expected the applied line: " + logs.messages());
			}
		}
	}

	@Test
	void warnsAndKeepsUpstreamForOtherVersions() throws IOException {
		assertKeepsUpstreamForOtherVersion(STRUCTORY_WAYSTONE, "1.0.18");
		assertKeepsUpstreamForOtherVersion(DUNGEONS_SHRINE, "5.3.3");
		assertKeepsUpstreamForOtherVersion(TREK_TAIGA, "1-B0.6.3");
	}

	@Test
	void passesChangedFileThroughWithSha256Warning() throws IOException {
		for (Pool pool : List.of(STRUCTORY_WAYSTONE, DUNGEONS_SHRINE, TREK_TAIGA)) {
			byte[] upstream = readRaw(fixture(pool));
			byte[] changed = Arrays.copyOf(upstream, upstream.length + 1);
			changed[upstream.length] = ' ';
			try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
				IoSupplier<InputStream> resolved = ResourcePatchResolver.resolve(
					ResourcePatchResolverTest.createMockModContainer(pool.modId(), pool.version()),
					pool.path(),
					() -> new ByteArrayInputStream(changed)
				);
				assertNotNull(resolved, pool.path());
				try (InputStream in = resolved.get()) {
					assertArrayEquals(changed, in.readAllBytes(), "A changed upstream file must pass through unchanged");
				}
				assertTrue(logs.has(Level.WARN, "SHA-256 mismatch (expected " + pool.sha256()), logs.messages().toString());
			}
		}
	}

	@Test
	void plazaTemplatesAreWaystonesPlazaWithTheWaystoneBlocksRemoved() throws IOException {
		for (PlazaTemplate plaza : PLAZAS) {
			byte[] upstreamBytes = readRaw(plaza.upstreamFixture());
			byte[] shippedBytes = readRaw(plaza.shippedResource());
			assertEquals(plaza.upstreamSha256(), ResourcePatchResolver.sha256Hex(upstreamBytes), plaza.upstreamFixture());
			assertEquals(plaza.shippedSha256(), ResourcePatchResolver.sha256Hex(shippedBytes), plaza.shippedResource());

			CompoundTag upstream = NbtIo.readCompressed(new ByteArrayInputStream(upstreamBytes), NbtAccounter.unlimitedHeap());
			CompoundTag shipped = NbtIo.readCompressed(new ByteArrayInputStream(shippedBytes), NbtAccounter.unlimitedHeap());

			ListTag size = shipped.getListOrEmpty("size");
			assertEquals(List.of(6, 6, 6), List.of(size.getIntOr(0, -1), size.getIntOr(1, -1), size.getIntOr(2, -1)),
				plaza.shippedResource());
			assertTrue(paletteNames(shipped).stream().noneMatch(name -> name.startsWith(WAYSTONE_PREFIX)), plaza.shippedResource());

			Removal removal = withoutWaystoneBlocks(upstream);
			assertEquals(2, removal.paletteEntries(), "Waystone palette entries in " + plaza.upstreamFixture());
			assertEquals(2, removal.blocks(), "Waystone blocks in " + plaza.upstreamFixture());
			assertEquals(removal.template(), shipped,
				"Shipped template must equal upstream with the waystone blocks set to air: " + plaza.shippedResource());
		}
	}

	/** The replacement the registry must ship for this pool, derived by mutating its upstream fixture. */
	private static JsonObject expectedReplacement(Pool pool, JsonObject upstream) {
		JsonObject expected = upstream.deepCopy();
		if (!pool.modId().equals(TREK_MOD_ID)) {
			expected.add("elements", JsonParser.parseString(EMPTY_ELEMENTS));
			return expected;
		}
		JsonObject element = expected.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("element");
		String upstreamLocation = element.get("location").getAsString();
		String plaza = PLAZA_FOR_UPSTREAM.get(upstreamLocation);
		assertNotNull(plaza, pool.path() + " places " + upstreamLocation + ", which is not a Waystones plaza template");
		element.addProperty("location", plaza);
		return expected;
	}

	/** The upstream template with each Waystones palette entry set to minecraft:air and the nbt removed from the blocks that use one. */
	private static Removal withoutWaystoneBlocks(CompoundTag upstream) {
		CompoundTag template = upstream.copy();
		ListTag palette = template.getListOrEmpty("palette");
		Set<Integer> waystoneStates = new HashSet<>();
		for (int index = 0; index < palette.size(); index++) {
			if (palette.getCompoundOrEmpty(index).getStringOr("Name", "").startsWith(WAYSTONE_PREFIX)) {
				CompoundTag air = new CompoundTag();
				air.putString("Name", AIR);
				palette.set(index, air);
				waystoneStates.add(index);
			}
		}
		int blocksChanged = 0;
		ListTag blocks = template.getListOrEmpty("blocks");
		for (int index = 0; index < blocks.size(); index++) {
			CompoundTag block = blocks.getCompoundOrEmpty(index);
			if (waystoneStates.contains(block.getIntOr("state", -1)) && block.remove("nbt") != null) {
				blocksChanged++;
			}
		}
		return new Removal(template, waystoneStates.size(), blocksChanged);
	}

	private static List<String> paletteNames(CompoundTag template) {
		return template.getListOrEmpty("palette").compoundStream().map(entry -> entry.getStringOr("Name", "")).toList();
	}

	private static void assertKeepsUpstreamForOtherVersion(Pool pool, String otherVersion) throws IOException {
		byte[] upstream = readRaw(fixture(pool));
		try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
			assertNull(ResourcePatchResolver.resolve(
				ResourcePatchResolverTest.createMockModContainer(pool.modId(), otherVersion),
				pool.path(),
				() -> new ByteArrayInputStream(upstream)
			), "Unregistered version must keep upstream: " + otherVersion);
			assertTrue(logs.has(Level.WARN, "Skipping " + pool.modId() + " resource patch for " + pool.path()
				+ ": version " + otherVersion + " is not registered (patch targets " + pool.version() + ")"),
				logs.messages().toString());
		}
	}

	private static String fixture(Pool pool) {
		return "upstream/" + pool.modId() + "/" + pool.version() + "/" + pool.path();
	}

	/** Raw classpath bytes, so the CRLF line endings of the Dungeons and Trek fixtures reach the resolver unchanged. */
	private static byte[] readRaw(String resource) throws IOException {
		try (InputStream input = WaystonePoolPatchTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, "Missing test resource: " + resource);
			return input.readAllBytes();
		}
	}

	/** Parses a pool or replacement, skipping a UTF-8 byte order mark if one is present. */
	private static JsonObject parse(byte[] bytes) {
		String text = new String(bytes, StandardCharsets.UTF_8);
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		return JsonParser.parseString(text).getAsJsonObject();
	}
}
