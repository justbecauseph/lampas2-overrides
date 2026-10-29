package lampas2overrides.resourcefix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

public class ResourcePatchResolverTest {

	private static final String ROGUES = "rogues";
	private static final String ROGUES_VERSION = "3.1.3.003+26.2";
	private static final String SHARPNESS = "data/minecraft/enchantment/sharpness.json";

	@BeforeEach
	void setUp() {
		ResourcePatchResolver.clearLogHistoryForTests();
	}

	@Test
	void registryContainsAllTargetPatches() {
		// 19 pack metadata, worldgen and Sharpness patches plus 66 RPG Series spell patches.
		assertEquals(85, ResourcePatchRegistry.getAllPatches().size());

		assertNotNull(ResourcePatchRegistry.findPatch("formationsoverworld", "1.0.5+c", "pack.mcmeta"));
		assertNull(ResourcePatchRegistry.findPatch("formationsoverworld", "1.0.5+c", "data/formationsoverworld/loot_table/stone_tower/smithing.json"));
		assertNull(ResourcePatchRegistry.findPatch("formationsoverworld", "1.0.5+c", "data/formationsoverworld/loot_table/witch_tower/smithing.json"));
		assertNotNull(ResourcePatchRegistry.findPatch("mr_grim_kingdomsloststructuresruins", "pack.mcmeta"));
		assertNotNull(ResourcePatchRegistry.findPatch("wilderwild",
			"data/wilderwild/worldgen/configured_feature/stone_pool.json"));
		assertNotNull(ResourcePatchRegistry.findPatch("wilderwild", "4.3.3",
			"data/wilderwild/worldgen/configured_feature/stone_pool.json"));
		assertNull(ResourcePatchRegistry.findPatch("wilderwild", "4.3.3-mc26.2",
			"data/wilderwild/worldgen/configured_feature/stone_pool.json"));
		assertNotNull(ResourcePatchRegistry.findPatch(ROGUES, ROGUES_VERSION, SHARPNESS));
		assertNull(ResourcePatchRegistry.findPatch(ROGUES, "3.1.3.004+26.2", SHARPNESS));
		assertNull(ResourcePatchRegistry.findPatch("apothic_enchanting", "3.0.1", SHARPNESS));
	}

	@Test
	void allReplacementResourcesExistAndParseCleanly() throws IOException {
		for (ResourcePatch patch : ResourcePatchRegistry.getAllPatches().values()) {
			try (InputStream is = getClass().getClassLoader().getResourceAsStream(patch.replacementPath())) {
				assertNotNull(is, "Missing replacement resource file on classpath: " + patch.replacementPath());
				byte[] bytes = is.readAllBytes();
				assertTrue(bytes.length > 0, "Replacement file is empty: " + patch.replacementPath());

				if (patch.resourcePath().endsWith(".nbt")) {
					assertTrue(NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap()).contains("blocks"));
					continue;
				}

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
				} else if (patch.resourcePath().endsWith("wilderwild/worldgen/configured_feature/stone_pool.json")) {
					assertEquals("frozenlib:circular_waterlogged_vegetation_patch_less_borders",
						json.get("type").getAsString());
					JsonObject config = json.getAsJsonObject("config");
					assertEquals(12, config.getAsJsonObject("xz_radius").get("min_inclusive").getAsInt());
					assertEquals(14, config.getAsJsonObject("xz_radius").get("max_inclusive").getAsInt());
					assertEquals(4, config.get("depth").getAsInt());
					assertEquals(2, config.get("vertical_range").getAsInt());
				} else if (patch.resourcePath().equals(SHARPNESS)) {
					assertFlatExclusiveSharpness(json);
				} else if (patch.resourcePath().contains("/spell/")) {
					String school = json.get("school").getAsString();
					if (school.startsWith("spell_power:physical_")) {
						assertFalse(json.getAsJsonObject("cost").getAsJsonObject("cooldown").get("haste_affected").getAsBoolean(),
							"Martial cooldown still hasted: " + patch.replacementPath());
					}
				}
			}
		}
	}

	@Test
	void appliesFormationsOverworldMetadataPatchForVersion1_0_5c() throws IOException {
		byte[] original = formationsOverworldOriginalPackMetadata();
		assertEquals("ffa966eb7835cc4de1273945333236331eff33116e918869e4e29c881b39f940",
			ResourcePatchResolver.sha256Hex(original));

		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			createMockModContainer("formationsoverworld", "1.0.5+c"),
			"pack.mcmeta",
			() -> new ByteArrayInputStream(original)
		);
		assertNotNull(patched, "Expected metadata patch for Formations Overworld 1.0.5+c");
		try (InputStream in = patched.get()) {
			JsonObject pack = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
				.getAsJsonObject().getAsJsonObject("pack");
			assertEquals(48, pack.get("pack_format").getAsInt());
			assertEquals(48, pack.getAsJsonArray("supported_formats").get(0).getAsInt());
			assertEquals(107, pack.getAsJsonArray("supported_formats").get(1).getAsInt());
			assertEquals("Formations Overworld's resources", pack.get("description").getAsString());
		}
	}

	@Test
	void appliesWilderWild433StonePoolPatchWithExactVersionAndHash() throws IOException {
		String path = "data/wilderwild/worldgen/configured_feature/stone_pool.json";
		byte[] original = readFixtureResource("upstream/wilderwild/4.3.3/" + path);
		assertEquals("59cf59a1c1e86f9627361e1dcec3250b81d535c6e84ff7c3b2950d9b90f2efe4",
			ResourcePatchResolver.sha256Hex(original));

		ResourcePatch patch = ResourcePatchRegistry.findPatch("wilderwild", "4.3.3", path);
		assertNotNull(patch);
		assertEquals("4.3.3", patch.expectedVersion());
		assertEquals("lampas2-overrides/resource-patches/wilderwild/4.3.3/data/wilderwild/worldgen/configured_feature/stone_pool.json",
			patch.replacementPath());
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			createMockModContainer("wilderwild", "4.3.3"),
			path,
			() -> new ByteArrayInputStream(original)
		);
		assertNotNull(patched);
		try (InputStream in = patched.get()) {
			String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(14, JsonParser.parseString(result).getAsJsonObject()
				.getAsJsonObject("config").getAsJsonObject("xz_radius")
				.get("max_inclusive").getAsInt());
		}
	}

	@Test
	void leavesWilderWild433StonePoolUntouchedForUnknownVersionOrHash() throws IOException {
		String path = "data/wilderwild/worldgen/configured_feature/stone_pool.json";
		byte[] original = readFixtureResource("upstream/wilderwild/4.3.3/" + path);
		for (String unknownVersion : new String[] {"4.2.11-mc26.2", "4.3", "4.3.1", "4.3.3-mc26.2"}) {
			assertNull(ResourcePatchResolver.resolve(
				createMockModContainer("wilderwild", unknownVersion),
				path,
				() -> new ByteArrayInputStream(original)
			), "Unregistered Wilder Wild version must remain unpatched: " + unknownVersion);
		}

		byte[] tampered = original.clone();
		tampered[0] = (byte) (tampered[0] ^ 1);
		IoSupplier<InputStream> wrongHash = ResourcePatchResolver.resolve(
			createMockModContainer("wilderwild", "4.3.3"),
			path,
			() -> new ByteArrayInputStream(tampered)
		);
		assertNotNull(wrongHash);
		try (InputStream in = wrongHash.get()) {
			assertArrayEquals(tampered, in.readAllBytes(), "A changed resource must pass through unchanged");
		}
	}

	@Test
	void appliesRoguesSharpnessPatchWithExactVersionAndHash() throws IOException {
		byte[] original = readFixtureResource("upstream/rogues/" + ROGUES_VERSION + "/" + SHARPNESS);
		assertEquals("fe176357898633059d7e37643e19115d25352725a0015630f92f39b2f96f620e",
			ResourcePatchResolver.sha256Hex(original));
		JsonObject upstream = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject upstreamModifier = upstream.getAsJsonObject("effects")
			.getAsJsonArray("minecraft:attributes").get(0).getAsJsonObject();
		assertEquals("add_multiplied_base", upstreamModifier.get("operation").getAsString(),
			"Fixture must be Rogues' percentage Sharpness");
		assertEquals(0.08, upstreamModifier.getAsJsonObject("amount").get("per_level_above_first").getAsDouble());

		ResourcePatch patch = ResourcePatchRegistry.findPatch(ROGUES, ROGUES_VERSION, SHARPNESS);
		assertNotNull(patch);
		assertEquals("lampas2-overrides/resource-patches/rogues/3.1.3.003+26.2/data/minecraft/enchantment/sharpness.json",
			patch.replacementPath());
		// Fabric Loader drops leading zeros from numeric components: the live server lists "rogues 3.1.3.3+26.2".
		ModContainer rogues = createMockModContainer(ROGUES, ROGUES_VERSION);
		assertEquals("3.1.3.3+26.2", rogues.getMetadata().getVersion().getFriendlyString());
		try (LogCapture logs = LogCapture.attach()) {
			IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
				rogues,
				SHARPNESS,
				() -> new ByteArrayInputStream(original)
			);
			assertNotNull(patched);
			try (InputStream in = patched.get()) {
				assertFlatExclusiveSharpness(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
					.getAsJsonObject());
			}
			assertTrue(logs.has(Level.INFO,
				"Applied rogues 3.1.3.003+26.2 compatibility resource: data/minecraft/enchantment/sharpness.json"),
				"Expected the applied line: " + logs.messages());
		}
	}

	@Test
	void warnsAndKeepsUpstreamSharpnessForUnregisteredRoguesVersion() throws IOException {
		byte[] original = readFixtureResource("upstream/rogues/" + ROGUES_VERSION + "/" + SHARPNESS);
		// Declared fabric.mod.json version, then the form Fabric Loader reports for it.
		String[][] unknownVersions = {
			{"3.1.3.004+26.2", "3.1.3.4+26.2"},
			{"3.1.3.003+26.1", "3.1.3.3+26.1"},
			{"3.1.3.003", "3.1.3.3"},
			{"3.1.3.003-beta+26.2", "3.1.3.3-beta+26.2"}
		};
		for (String[] unknown : unknownVersions) {
			String unknownVersion = unknown[0];
			try (LogCapture logs = LogCapture.attach()) {
				assertNull(ResourcePatchResolver.resolve(
					createMockModContainer(ROGUES, unknownVersion),
					SHARPNESS,
					() -> new ByteArrayInputStream(original)
				), "Unregistered Rogues version must remain unpatched: " + unknownVersion);
				assertTrue(logs.has(Level.WARN, "Skipping rogues resource patch for " + SHARPNESS
						+ ": version " + unknown[1] + " is not registered (patch targets " + ROGUES_VERSION + ")"),
					"Expected a WARN naming the unregistered version: " + logs.messages());
				assertFalse(logs.has(Level.INFO, "is not registered"), "Version mismatch must not log at INFO");
			}
		}
	}

	@Test
	void warnsAndKeepsUpstreamSharpnessWhenRoguesResourceChanges() throws IOException {
		byte[] tampered = readFixtureResource("upstream/rogues/" + ROGUES_VERSION + "/" + SHARPNESS).clone();
		tampered[tampered.length - 1] = (byte) ' ';
		try (LogCapture logs = LogCapture.attach()) {
			IoSupplier<InputStream> resolved = ResourcePatchResolver.resolve(
				createMockModContainer(ROGUES, ROGUES_VERSION),
				SHARPNESS,
				() -> new ByteArrayInputStream(tampered)
			);
			assertNotNull(resolved);
			try (InputStream in = resolved.get()) {
				assertArrayEquals(tampered, in.readAllBytes(), "A changed resource must pass through unchanged");
			}
			assertTrue(logs.has(Level.WARN, "SHA-256 mismatch (expected fe176357898633059d7e37643e19115d25352725a0015630f92f39b2f96f620e"),
				"Expected the SHA-256 mismatch WARN: " + logs.messages());
		}
	}

	@Test
	void roguesSharpnessReplacementIsApothicEnchantingDefinitionWithDamageExclusiveSet() throws IOException {
		Path aeJar = Path.of("../lampas-server-fabric/mods/ApothicEnchanting-26.2-3.0.1.jar");
		assumeTrue(Files.exists(aeJar), "Apothic Enchanting 3.0.1 jar not present at " + aeJar);
		JsonObject expected;
		try (ZipFile zf = new ZipFile(aeJar.toFile());
				InputStream in = zf.getInputStream(zf.getEntry(SHARPNESS))) {
			expected = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		assertFalse(expected.has("exclusive_set"), "Apothic Enchanting 3.0.1 Sharpness is expected to omit exclusive_set");
		expected.addProperty("exclusive_set", "#minecraft:exclusive_set/damage");

		ResourcePatch patch = ResourcePatchRegistry.findPatch(ROGUES, ROGUES_VERSION, SHARPNESS);
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(patch.replacementPath())) {
			assertEquals(expected, JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
		}
	}

	@Test
	void roguesSharpnessReplacementEqualsVanillaSharpness() throws IOException {
		List<java.net.URL> providers = java.util.Collections.list(
			getClass().getClassLoader().getResources(SHARPNESS));
		assertEquals(1, providers.size(), "Expected only the Minecraft jar to provide " + SHARPNESS + ": " + providers);
		JsonObject vanilla;
		try (InputStream in = providers.get(0).openStream()) {
			vanilla = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		assertFlatExclusiveSharpness(vanilla);

		ResourcePatch patch = ResourcePatchRegistry.findPatch(ROGUES, ROGUES_VERSION, SHARPNESS);
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(patch.replacementPath())) {
			assertEquals(vanilla, JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
		}
	}

	@Test
	void skipsPatchWhenHashDoesNotMatch() throws IOException {
		ModContainer mod = createMockModContainer("formationsoverworld", "1.0.5+c");
		byte[] unexpectedUpstreamBytes = "{\"pack\":{\"pack_format\":48}}".getBytes(StandardCharsets.UTF_8);

		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			mod,
			"pack.mcmeta",
			() -> new ByteArrayInputStream(unexpectedUpstreamBytes)
		);

		assertNotNull(patched, "Fallback supplier with original bytes should be returned");
		try (InputStream in = patched.get()) {
			byte[] read = in.readAllBytes();
			assertArrayEquals(unexpectedUpstreamBytes, read, "Original bytes must be preserved when hash mismatch occurs");
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
		ModContainer mod = createMockModContainer("formationsoverworld", "1.0.5+c");
		IoSupplier<InputStream> patched = ResourcePatchResolver.resolve(
			mod,
			"data/formationsoverworld/worldgen/structure/unrelated.json",
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
			{"formationsoverworld-1.0.5c-mc1.21+.jar", "formationsoverworld", "1.0.5+c", "pack.mcmeta"},
			{"grim-kingdoms-lost-structures-ruins-2.0.3.jar", "mr_grim_kingdomsloststructuresruins", "2.0.3", "pack.mcmeta"},
			{"WilderWild-4.3.3-mc26.2-fabric.jar", "wilderwild", "4.3.3", "data/wilderwild/worldgen/configured_feature/stone_pool.json",
				"dfcc128df21715b20fe86bba18c5b9f731a66d3854269044ead125f6f9c20204"},
			{"rogues-fabric-3.1.3.003+26.2.jar", ROGUES, ROGUES_VERSION, SHARPNESS,
				"77bdff881c01011bff6e6e722a1f47dc01ca7d377a1f08231d305fc11b08f113"}
		};

		for (String[] check : jarChecks) {
			Path jarPath = serverMods.resolve(check[0]);
			if (!Files.exists(jarPath)) {
				continue;
			}

			ResourcePatch patch = ResourcePatchRegistry.findPatch(check[1], check[2], check[3]);
			assertNotNull(patch, "Patch rule missing for " + check[1] + " : " + check[3]);
			assertEquals(check[2], patch.expectedVersion());
			if (check.length > 4) {
				assertEquals(check[4], ResourcePatchResolver.sha256Hex(Files.readAllBytes(jarPath)),
					"JAR fingerprint mismatch against installed artifact: " + check[0]);
			}

			try (ZipFile zf = new ZipFile(jarPath.toFile())) {
				if (check.length > 4) {
					ZipEntry metadataEntry = zf.getEntry("fabric.mod.json");
					assertNotNull(metadataEntry, "fabric.mod.json not found in " + check[0]);
					try (InputStream metadataStream = zf.getInputStream(metadataEntry)) {
						JsonObject metadata = JsonParser.parseString(
							new String(metadataStream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
						assertEquals(check[1], metadata.get("id").getAsString());
						assertEquals(check[2], metadata.get("version").getAsString());
					}
				}
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


	@Test
	void grimStructuresPreserveAllNbtExceptAuditedZeroLevels() throws IOException {
		JsonObject evidence = JsonParser.parseString(Files.readString(Path.of("docs/evidence/grim-zero-enchantments.json"))).getAsJsonObject();
		String modId = evidence.get("mod").getAsString();
		for (var entry : evidence.getAsJsonArray("resources")) {
			JsonObject resource = entry.getAsJsonObject();
			String path = resource.get("path").getAsString();
			ResourcePatch patch = ResourcePatchRegistry.findPatch(modId, "2.0.3", path);
			assertNotNull(patch, path);
			byte[] original = Files.readAllBytes(Path.of("src/test/resources/grim-originals").resolve(path));
			assertEquals(resource.get("sha256").getAsString(), ResourcePatchResolver.sha256Hex(original));
			assertEquals(patch.expectedSha256(), ResourcePatchResolver.sha256Hex(original));
			CompoundTag expected = NbtIo.readCompressed(new ByteArrayInputStream(original), NbtAccounter.unlimitedHeap());
			for (var change : resource.getAsJsonArray("removed")) {
				String[] segments = change.getAsJsonObject().get("path").getAsString().substring(1).split("/");
				Tag cursor = expected;
				for (int i = 0; i < segments.length - 1; i++) {
					cursor = cursor instanceof CompoundTag compound ? compound.get(segments[i]) : ((ListTag) cursor).get(Integer.parseInt(segments[i]));
				}
				CompoundTag levels = (CompoundTag) cursor;
				String key = segments[segments.length - 1];
				assertEquals(0, levels.getIntOr(key, -999));
				levels.remove(key);
			}
			var mod = createMockModContainer(modId, "2.0.3");
			var resolved = ResourcePatchResolver.resolve(mod, path, () -> new ByteArrayInputStream(original));
			assertNotNull(resolved);
			try (InputStream in = resolved.get()) {
				assertEquals(expected, NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()), path);
			}
			assertNull(ResourcePatchResolver.resolve(createMockModContainer(modId, "2.0.4"), path, () -> new ByteArrayInputStream(original)));
			assertNull(ResourcePatchResolver.resolve(createMockModContainer("unrelated", "2.0.3"), path, () -> new ByteArrayInputStream(original)));
			assertNull(ResourcePatchResolver.resolve(mod, path, null));
			assertNull(ResourcePatchResolver.resolve(mod, path, () -> null));
			assertNull(ResourcePatchResolver.resolve(mod, path, () -> { throw new IOException("fixture read failure"); }));
			byte[] mismatch = original.clone();
			mismatch[mismatch.length - 1] ^= 1;
			try (InputStream in = ResourcePatchResolver.resolve(mod, path, () -> new ByteArrayInputStream(mismatch)).get()) {
				assertArrayEquals(mismatch, in.readAllBytes());
			}
		}
	}

	/** Vanilla-style Sharpness: flat +1 damage, +0.5 per level above the first, exclusive with the damage set. */
	private static void assertFlatExclusiveSharpness(JsonObject json) {
		JsonObject effects = json.getAsJsonObject("effects");
		assertEquals(1, effects.size(), "Only the flat damage effect may remain: " + effects);
		assertFalse(effects.has("minecraft:attributes"), "The +8% attack damage modifier must be gone");
		JsonArray damage = effects.getAsJsonArray("minecraft:damage");
		assertEquals(1, damage.size());
		JsonObject effect = damage.get(0).getAsJsonObject().getAsJsonObject("effect");
		assertEquals("minecraft:add", effect.get("type").getAsString());
		JsonObject value = effect.getAsJsonObject("value");
		assertEquals("minecraft:linear", value.get("type").getAsString());
		assertEquals(1.0, value.get("base").getAsDouble());
		assertEquals(0.5, value.get("per_level_above_first").getAsDouble());
		assertEquals("#minecraft:exclusive_set/damage", json.get("exclusive_set").getAsString());
		assertEquals(5, json.get("max_level").getAsInt());
		assertEquals("#minecraft:enchantable/sharp_weapon", json.get("supported_items").getAsString());
	}

	/** Captures events from the resolver's Log4j logger, the SLF4J binding Minecraft ships. */
	static final class LogCapture extends AbstractAppender implements AutoCloseable {
		private static final String LOGGER_NAME = "lampas2-overrides/resource-fix";
		private final List<LogEvent> events = new CopyOnWriteArrayList<>();
		private final org.apache.logging.log4j.core.Logger logger;
		private final Level previousLevel;

		private LogCapture() {
			super("lampas2-resource-fix-capture", null, null, true, Property.EMPTY_ARRAY);
			this.logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LOGGER_NAME);
			this.previousLevel = logger.getLevel();
		}

		static LogCapture attach() {
			LogCapture capture = new LogCapture();
			capture.start();
			capture.logger.addAppender(capture);
			capture.logger.setLevel(Level.ALL);
			return capture;
		}

		@Override
		public void append(LogEvent event) {
			events.add(event.toImmutable());
		}

		boolean has(Level level, String fragment) {
			return events.stream().anyMatch(event -> event.getLevel() == level
				&& event.getMessage().getFormattedMessage().contains(fragment));
		}

		List<String> messages() {
			return events.stream().map(event -> event.getLevel() + " " + event.getMessage().getFormattedMessage()).toList();
		}

		@Override
		public void close() {
			logger.removeAppender(this);
			logger.setLevel(previousLevel);
			stop();
		}
	}

	static ModContainer createMockModContainer(String modId, String versionString) {
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

	static byte[] readFixtureResource(String resource) throws IOException {
		try (InputStream input = ResourcePatchResolverTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, "Missing test fixture: " + resource);
			byte[] bytes = input.readAllBytes();
			// The text fixture includes a trailing LF, while the upstream jar entry does not.
			if (bytes.length > 0 && bytes[bytes.length - 1] == '\n') {
				return java.util.Arrays.copyOf(bytes, bytes.length - 1);
			}
			return bytes;
		}
	}

	private static byte[] formationsOverworldOriginalPackMetadata() {
		return ("{\n" +
			"    \"pack\": {\n" +
			"        \"description\": \"Formations Overworld's resources\",\n" +
			"        \"pack_format\": 48\n" +
			"    }\n" +
			"}\n").getBytes(StandardCharsets.UTF_8);
	}

}
