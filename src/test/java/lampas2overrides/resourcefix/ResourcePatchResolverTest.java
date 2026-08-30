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
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.server.packs.resources.IoSupplier;

public class ResourcePatchResolverTest {

	@BeforeEach
	void setUp() {
		ResourcePatchResolver.clearLogHistoryForTests();
	}

	@Test
	void registryContainsAllFiveTargetPatches() {
		assertEquals(10, ResourcePatchRegistry.getAllPatches().size());

		assertNotNull(ResourcePatchRegistry.findPatch("better_lib",
			"data/minecraft/tags/point_of_interest_type/acquirable_job_site.json"));
		assertNotNull(ResourcePatchRegistry.findPatch("mns", "pack.mcmeta"));
		assertNotNull(ResourcePatchRegistry.findPatch("mvs", "pack.mcmeta"));
		assertNotNull(ResourcePatchRegistry.findPatch("formationsoverworld", "pack.mcmeta"));
		assertNotNull(ResourcePatchRegistry.findPatch("mr_grim_kingdomsloststructuresruins", "pack.mcmeta"));
	}

	@Test
	void allReplacementResourcesExistAndParseCleanly() throws IOException {
		for (ResourcePatch patch : ResourcePatchRegistry.getAllPatches().values()) {
			try (InputStream is = getClass().getClassLoader().getResourceAsStream(patch.replacementPath())) {
				assertNotNull(is, "Missing replacement resource file on classpath: " + patch.replacementPath());
				byte[] bytes = is.readAllBytes();
				assertTrue(bytes.length > 0, "Replacement file is empty: " + patch.replacementPath());

				String content = new String(bytes, StandardCharsets.UTF_8);
				JsonObject json = JsonParser.parseString(content).getAsJsonObject();
				assertNotNull(json, "Parsed JSON is null: " + patch.replacementPath());

				if (patch.resourcePath().endsWith("pack.mcmeta")) {
					assertTrue(json.has("pack"), "pack.mcmeta missing 'pack' root: " + patch.replacementPath());
					JsonObject pack = json.getAsJsonObject("pack");
					assertTrue(pack.get("pack_format").getAsInt() >= 48);
					assertTrue(pack.has("supported_formats"), "Missing supported_formats in " + patch.replacementPath());
					JsonArray sf = pack.getAsJsonArray("supported_formats");
					assertEquals(2, sf.size());
					assertTrue(sf.get(0).getAsInt() <= 48);
					assertEquals(107, sf.get(1).getAsInt());
					assertTrue(pack.has("min_format"), "Missing min_format in " + patch.replacementPath());
					assertTrue(pack.has("max_format"), "Missing max_format in " + patch.replacementPath());
				} else if (patch.resourcePath().endsWith("acquirable_job_site.json")) {
					assertFalse(content.contains("//"), "acquirable_job_site.json replacement must not contain comments");
					assertTrue(json.has("replace"), "Missing 'replace' property");
					assertFalse(json.get("replace").getAsBoolean());
					assertTrue(json.has("values"), "Missing 'values' array");
					assertEquals(0, json.getAsJsonArray("values").size(), "values array must be empty");
				}
			}
		}
	}

	@Test
	void appliesPatchWhenVersionAndHashMatch() throws IOException {
		ModContainer mod = createMockModContainer("better_lib", "2.1.1");
		byte[] originalMalformedBytes = ("//{\n" +
			"//  \"values\": [\n" +
			"//    \"better_lib:andesite_worker\",\n" +
			"//    \"better_lib:ore_trader\"\n" +
			"//  ]\n" +
			"//}").getBytes(StandardCharsets.UTF_8);

		IoSupplier<InputStream> originalSupplier = () -> new ByteArrayInputStream(originalMalformedBytes);
		IoSupplier<InputStream> patchedSupplier = ResourcePatchResolver.resolve(
			mod,
			"data/minecraft/tags/point_of_interest_type/acquirable_job_site.json",
			originalSupplier
		);

		assertNotNull(patchedSupplier, "Expected patched supplier to be returned");
		try (InputStream in = patchedSupplier.get()) {
			String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(result).getAsJsonObject();
			assertFalse(result.contains("//"));
			assertTrue(json.has("values"));
			assertEquals(0, json.getAsJsonArray("values").size());
		}
	}

	@Test
	void skipsPatchWhenVersionDoesNotMatch() throws IOException {
		ModContainer modNewVersion = createMockModContainer("better_lib", "2.1.2");
		byte[] originalMalformedBytes = "//{}".getBytes(StandardCharsets.UTF_8);

		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			modNewVersion,
			"data/minecraft/tags/point_of_interest_type/acquirable_job_site.json",
			() -> new ByteArrayInputStream(originalMalformedBytes)
		);

		assertNull(patched, "Patch must be skipped when mod version is different");
	}

	@Test
	void skipsPatchWhenHashDoesNotMatch() throws IOException {
		ModContainer mod = createMockModContainer("better_lib", "2.1.1");
		byte[] alreadyFixedUpstreamBytes = "{\"replace\":false,\"values\":[\"foo:bar\"]}".getBytes(StandardCharsets.UTF_8);

		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			mod,
			"data/minecraft/tags/point_of_interest_type/acquirable_job_site.json",
			() -> new ByteArrayInputStream(alreadyFixedUpstreamBytes)
		);

		assertNotNull(patched, "Fallback supplier with original bytes should be returned");
		try (InputStream in = patched.get()) {
			byte[] read = in.readAllBytes();
			assertArrayEquals(alreadyFixedUpstreamBytes, read, "Original bytes must be preserved when hash mismatch occurs");
		}
	}

	@Test
	void leavesUnrelatedModUntouched() {
		ModContainer unrelatedMod = createMockModContainer("unrelated_mod", "1.0.0");
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			unrelatedMod,
			"pack.mcmeta",
			() -> new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))
		);

		assertNull(patched, "Unrelated mod must not be patched");
	}

	@Test
	void leavesUnrelatedPathInAffectedModUntouched() {
		ModContainer mod = createMockModContainer("better_lib", "2.1.1");
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			mod,
			"data/better_lib/tags/villager_trade/ore_trader/level_1.json",
			() -> new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))
		);

		assertNull(patched, "Unrelated path in affected mod must not be patched");
	}

	@Test
	void verifiesAgainstActualServerInstalledJarsIfPresent() throws IOException {
		Path serverMods = Path.of("../lampas-server-fabric/mods");
		if (!Files.isDirectory(serverMods)) {
			return;
		}

		String[][] jarChecks = {
			{"better_lib-fabric-26.1-2.1.1.jar", "better_lib", "2.1.1", "data/minecraft/tags/point_of_interest_type/acquirable_job_site.json"},
			{"MoogsNetherStructures-1.21-3.0.0.jar", "mns", "3.0.0", "pack.mcmeta"},
			{"MoogsVoyagerStructures-1.21-5.0.11.jar", "mvs", "5.0.11", "pack.mcmeta"},
			{"formationsoverworld-1.0.5a-mc1.21+.jar", "formationsoverworld", "1.0.5+a", "pack.mcmeta"},
			{"grim-kingdoms-lost-structures-ruins-2.0.3.jar", "mr_grim_kingdomsloststructuresruins", "2.0.3", "pack.mcmeta"}
		};

		for (String[] check : jarChecks) {
			Path jarPath = serverMods.resolve(check[0]);
			if (!Files.exists(jarPath)) {
				continue;
			}

			ResourcePatch patch = ResourcePatchRegistry.findPatch(check[1], check[3]);
			assertNotNull(patch, "Patch rule missing for " + check[1] + " : " + check[3]);
			assertEquals(check[2], patch.expectedVersion());

			try (ZipFile zf = new ZipFile(jarPath.toFile())) {
				ZipEntry entry = zf.getEntry(check[3]);
				assertNotNull(entry, "Entry " + check[3] + " not found in " + check[0]);
				try (InputStream is = zf.getInputStream(entry)) {
					byte[] raw = is.readAllBytes();
					String sha256 = ResourcePatchResolver.sha256Hex(raw);
					assertEquals(patch.expectedSha256().toLowerCase(), sha256.toLowerCase(),
						"Fingerprint mismatch against installed jar: " + check[0]);
				}
			}
		}
	}

	private static ModContainer createMockModContainer(String modId, String versionString) {
		Version version;
		try {
			version = Version.parse(versionString);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		ModMetadata metadata = (ModMetadata) Proxy.newProxyInstance(
			ResourcePatchResolverTest.class.getClassLoader(),
			new Class<?>[] { ModMetadata.class },
			(proxy, method, args) -> {
				if ("getId".equals(method.getName())) {
					return modId;
				}
				if ("getVersion".equals(method.getName())) {
					return version;
				}
				if ("getName".equals(method.getName())) {
					return modId;
				}
				return null;
			}
		);

		return (ModContainer) Proxy.newProxyInstance(
			ResourcePatchResolverTest.class.getClassLoader(),
			new Class<?>[] { ModContainer.class },
			(proxy, method, args) -> {
				if ("getMetadata".equals(method.getName())) {
					return metadata;
				}
				return null;
			}
		);
	}
}
