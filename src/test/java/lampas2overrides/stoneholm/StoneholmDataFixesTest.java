package lampas2overrides.stoneholm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

public class StoneholmDataFixesTest {

	private static final String PACK_ROOT = "resourcepacks/stoneholm_2_1_1_fixes/";

	@Test
	void supportsOnlyVerifiedUndergroundVillageVersion() {
		assertTrue(StoneholmCompatibility.supportsVersion("2.1.1"));
		assertFalse(StoneholmCompatibility.supportsVersion("2.1.0"));
		assertFalse(StoneholmCompatibility.supportsVersion("2.1.2"));
		assertFalse(StoneholmCompatibility.supportsVersion("2.1.1+modified"));
	}

	@Test
	void fingerprintsResources(@TempDir Path temporaryDirectory) throws IOException {
		Path resource = temporaryDirectory.resolve("dummy.nbt");
		Files.writeString(resource, "stoneholm_test_content\n", StandardCharsets.UTF_8);

		String sha = StoneholmCompatibility.sha256(resource);
		assertNotNull(sha);
		assertEquals(64, sha.length());
		assertTrue(StoneholmCompatibility.matches(resource, sha));
		assertFalse(StoneholmCompatibility.matches(resource,
				"0000000000000000000000000000000000000000000000000000000000000000"));
	}

	@Test
	void verifiesAgainstReferenceJarIfPresent() throws IOException {
		Path jarPath = Path.of("../lampas-server-fabric/mods/underground_village-fabric-26.1-2.1.1.jar");
		if (!Files.exists(jarPath)) {
			return;
		}

		try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
			for (var entry : StoneholmCompatibility.EXPECTED_RESOURCES.entrySet()) {
				ZipEntry zipEntry = zipFile.getEntry(entry.getKey());
				assertNotNull(zipEntry, "Missing resource in reference jar: " + entry.getKey());
				try (InputStream in = zipFile.getInputStream(zipEntry)) {
					byte[] bytes = in.readAllBytes();
					String sha256 = bytesToSha256(bytes);
					assertEquals(entry.getValue(), sha256,
							"Fingerprint mismatch for reference resource " + entry.getKey());
				}
			}
		}
	}

	@Test
	void allReplacementNbtFilesDecompressAndDeserialize() throws IOException {
		String[] nbtPaths = {
				"data/stoneholm/structure/poi/v4/founten.nbt",
				"data/stoneholm/structure/poi/v4/sidebed_bedroom.nbt",
				"data/stoneholm/structure/abandoned_poi/v4/sidebed_bedroom.nbt",
				"data/stoneholm/structure/poi/v4/tall_bedroom.nbt"
		};

		for (String nbtPath : nbtPaths) {
			byte[] rawBytes = readPackResourceBytes(nbtPath);
			assertTrue(rawBytes.length >= 18, "NBT file too small: " + nbtPath);

			// Check GZIP magic bytes (0x1f, 0x8b)
			assertEquals((byte) 0x1f, rawBytes[0], "Invalid GZIP magic header: " + nbtPath);
			assertEquals((byte) 0x8b, rawBytes[1], "Invalid GZIP magic header: " + nbtPath);

			// Read footer CRC32 and ISIZE
			int len = rawBytes.length;
			ByteBuffer buffer = ByteBuffer.wrap(rawBytes, len - 8, 8).order(ByteOrder.LITTLE_ENDIAN);
			long expectedCrc = buffer.getInt() & 0xFFFFFFFFL;
			long expectedIsize = buffer.getInt() & 0xFFFFFFFFL;

			// Decompress fully and compute CRC32
			byte[] decompressed;
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(rawBytes))) {
				decompressed = gzip.readAllBytes();
			}

			CRC32 crc = new CRC32();
			crc.update(decompressed);
			assertEquals(expectedCrc, crc.getValue(), "GZIP CRC32 mismatch on " + nbtPath);
			assertEquals(expectedIsize, decompressed.length & 0xFFFFFFFFL, "GZIP ISIZE mismatch on " + nbtPath);

			// Deserialize with Minecraft NbtIo
			CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(rawBytes), NbtAccounter.unlimitedHeap());
			assertNotNull(root, "Deserialized NBT is null: " + nbtPath);
			assertTrue(root.contains("size"), "NBT root missing 'size': " + nbtPath);
			assertTrue(root.contains("blocks"), "NBT root missing 'blocks': " + nbtPath);
			assertTrue(root.contains("palette"), "NBT root missing 'palette': " + nbtPath);
		}
	}

	@Test
	void repairedIronGolemStructures() throws IOException {
		String[] ironGolemStructures = {
				"data/stoneholm/structure/poi/v4/sidebed_bedroom.nbt",
				"data/stoneholm/structure/abandoned_poi/v4/sidebed_bedroom.nbt"
		};

		for (String path : ironGolemStructures) {
			byte[] rawBytes = readPackResourceBytes(path);
			CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(rawBytes), NbtAccounter.unlimitedHeap());

			List<String> jigsawPools = extractJigsawPools(root);
			assertFalse(jigsawPools.contains("stoneholm:iron_golm"),
					"Typo 'stoneholm:iron_golm' still present in " + path);
			assertTrue(jigsawPools.contains("stoneholm:iron_golem"),
					"Expected 'stoneholm:iron_golem' pool in " + path);

			String rawString = new String(rawBytes, StandardCharsets.ISO_8859_1);
			assertFalse(rawString.contains("iron_golm"), "Raw binary contains iron_golm in " + path);
		}
	}

	@Test
	void repairedTallBedroomStructure() throws IOException {
		String path = "data/stoneholm/structure/poi/v4/tall_bedroom.nbt";
		byte[] rawBytes = readPackResourceBytes(path);
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(rawBytes), NbtAccounter.unlimitedHeap());

		List<String> jigsawPools = extractJigsawPools(root);
		assertFalse(jigsawPools.contains("stoneholm:villager"),
				"Typo 'stoneholm:villager' still present in " + path);
		assertTrue(jigsawPools.contains("stoneholm:villagers"),
				"Expected 'stoneholm:villagers' pool in " + path);

		String rawString = new String(rawBytes, StandardCharsets.ISO_8859_1);
		assertFalse(rawString.contains("stoneholm:villager\0"),
				"Raw binary contains singular villager in " + path);
	}

	@Test
	void repairedFountainStructure() throws IOException {
		String path = "data/stoneholm/structure/poi/v4/founten.nbt";
		byte[] rawBytes = readPackResourceBytes(path);
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(rawBytes), NbtAccounter.unlimitedHeap());

		ListTag size = root.getListOrEmpty("size");
		assertEquals(3, size.size());
		assertEquals(15, size.getIntOr(0, 0));
		assertEquals(6, size.getIntOr(1, 0));
		assertEquals(15, size.getIntOr(2, 0));

		ListTag blocks = root.getListOrEmpty("blocks");
		assertEquals(1350, blocks.size());
	}

	@Test
	void repairedBetterVillagersTemplatePools() throws IOException {
		String[] poolPaths = {
				"data/stoneholm/worldgen/template_pool/addons/better_villagers/better_villagers_point_of_interest.json",
				"data/stoneholm/worldgen/template_pool/addons/better_villagers/better_villagers_abandoned_point_of_interest.json"
		};

		for (String poolPath : poolPaths) {
			String jsonContent = readPackResourceString(poolPath);
			assertFalse(jsonContent.contains("stoneholm:addons/better_villager/"),
					"Singular 'better_villager' typo found in " + poolPath);

			JsonObject parsed = JsonParser.parseString(jsonContent).getAsJsonObject();
			assertTrue(parsed.has("name"), "Missing 'name' in " + poolPath);
			assertTrue(parsed.has("elements"), "Missing 'elements' in " + poolPath);

			JsonArray elements = parsed.getAsJsonArray("elements");
			assertEquals(3, elements.size(), "Expected 3 elements in " + poolPath);

			List<String> locations = new ArrayList<>();
			for (JsonElement el : elements) {
				JsonObject element = el.getAsJsonObject().getAsJsonObject("element");
				String location = element.get("location").getAsString();
				locations.add(location);
				assertTrue(location.startsWith("stoneholm:addons/better_villagers/poi/"),
						"Unexpected location prefix: " + location);
			}

			assertTrue(locations.contains("stoneholm:addons/better_villagers/poi/andesite_worker"));
			assertTrue(locations.contains("stoneholm:addons/better_villagers/poi/brass_worker"));
			assertTrue(locations.contains("stoneholm:addons/better_villagers/poi/copper_worker"));
		}
	}

	private static List<String> extractJigsawPools(CompoundTag root) {
		List<String> pools = new ArrayList<>();
		ListTag blocks = root.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag block = blocks.getCompound(i).orElse(null);
			if (block == null) {
				continue;
			}
			block.getCompound("nbt").ifPresent(tileNbt -> {
				if ("minecraft:jigsaw".equals(tileNbt.getString("id").orElse(null))) {
					tileNbt.getString("pool").ifPresent(pools::add);
				}
			});
		}
		return pools;
	}

	private static byte[] readPackResourceBytes(String path) throws IOException {
		try (InputStream input = StoneholmDataFixesTest.class.getClassLoader()
				.getResourceAsStream(PACK_ROOT + path)) {
			if (input == null) {
				throw new IOException("Missing test pack resource: " + path);
			}
			return input.readAllBytes();
		}
	}

	private static String readPackResourceString(String path) throws IOException {
		return new String(readPackResourceBytes(path), StandardCharsets.UTF_8);
	}

	private static String bytesToSha256(byte[] data) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(data);
			return java.util.HexFormat.of().formatHex(hash);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
