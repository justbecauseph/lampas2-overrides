package lampas2overrides.client.frozenwind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;

/** Contract tests for the exact client-only wind compatibility feature. */
public final class FrozenWindCompatibilityTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.frozenwind.mixins.json";

	@Test
	void exactArtifactGateConstantsMatchTheInspectedEvidence() {
		assertEquals("26.2", FrozenWindCompatibility.MINECRAFT_VERSION);
		assertEquals("2.5.3-mc26.2", FrozenWindCompatibility.FROZENLIB_VERSION);
		assertEquals("4.2.11-mc26.2", FrozenWindCompatibility.WILDERWILD_VERSION);
		assertEquals("2.2.19+515ac5339e", FrozenWindCompatibility.ATTACHMENT_API_MODULE_VERSION);
		assertEquals(
			"a8845939d7dbfb3f9fa1d102fe77c1236c9d63b226e3b655dba9295c681c26d3",
			FrozenWindCompatibility.WIND_MANAGER_SHA256);
		assertEquals(
			"1e66dd8d15651c50dea4f1016f9de4ce614d2280e23d6babcc59df2d4df15d6b",
			FrozenWindCompatibility.WILDERWILD_EXTENSION_SHA256);
		assertEquals(
			"f68661b4382a423b470cefe90967087bf96d6de701e6d120b3122133d9012d37",
			FrozenWindCompatibility.ATTACHMENT_CHANGE_SHA256);
	}

	@Test
	void exactVersionGateRejectsMissingAndNearbyVersions() {
		assertTrue(FrozenWindCompatibility.exactVersion("2.5.3-mc26.2", "2.5.3-mc26.2"));
		for (String actual : new String[] {null, "2.5.2-mc26.2", "2.5.3-mc26.1", "2.5.3-mc26.2+local"}) {
			assertFalse(FrozenWindCompatibility.exactVersion(actual, "2.5.3-mc26.2"));
		}
		assertFalse(FrozenWindCompatibility.exactVersion("2.5.3-mc26.2", null));
	}

	@Test
	void evaluateRejectsMissingAttachmentModule(@TempDir Path tempDirectory) {
		Map<String, ModContainer> mods = fixture(tempDirectory);
		mods.remove("fabric-data-attachment-api-v1");

		FrozenWindCompatibility.GateResult result = FrozenWindCompatibility.evaluate(loader(mods));

		assertFalse(result.enabled());
		assertTrue(result.reason().contains("Fabric Data Attachment API is absent"));
	}

	@Test
	void evaluateRejectsAttachmentModuleVersionMismatch(@TempDir Path tempDirectory) {
		Map<String, ModContainer> mods = fixture(tempDirectory);
		mods.put("fabric-data-attachment-api-v1", mod(
			"fabric-data-attachment-api-v1", "2.2.18+515ac5339e", Map.of()));

		FrozenWindCompatibility.GateResult result = FrozenWindCompatibility.evaluate(loader(mods));

		assertFalse(result.enabled());
		assertTrue(result.reason().contains("Fabric Data Attachment API metadata"));
	}

	@Test
	void evaluateRejectsWindClassHashMismatch(@TempDir Path tempDirectory) {
		FrozenWindCompatibility.GateResult result = FrozenWindCompatibility.evaluate(loader(fixture(tempDirectory)));

		assertFalse(result.enabled());
		assertTrue(result.reason().contains("WindManager SHA-256"));
	}

	@Test
	void hashHelperIsDeterministicAndFailClosedForMissingInput() throws IOException {
		assertEquals(
			"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			FrozenWindCompatibility.sha256(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
		assertEquals(null, FrozenWindCompatibility.sha256((InputStream) null));
	}

	@Test
	void mixinConfigIsClientOnlyAndRequiresEveryResolvedCallSite() throws IOException {
		String config = readResource(MIXIN_CONFIG);
		assertTrue(config.contains("lampas2overrides.client.frozenwind.FrozenWindMixinPlugin"));
		assertTrue(config.contains("WindManagerMixin"));
		assertTrue(config.contains("AttachmentChangeMixin"));
		assertTrue(config.contains("\"client\""));
		assertTrue(config.contains("\"defaultRequire\": 1"));
	}

	private static String readResource(String resource) throws IOException {
		try (InputStream input = FrozenWindCompatibilityTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, resource + " must exist on the test classpath");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Map<String, ModContainer> fixture(Path temporaryDirectory) {
		Path wrongClass = temporaryDirectory.resolve("target.class");
		try {
			java.nio.file.Files.writeString(wrongClass, "fixture mismatch", StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
		Map<String, ModContainer> mods = new HashMap<>();
		mods.put("minecraft", mod("minecraft", "26.2", Map.of()));
		mods.put("fabric-data-attachment-api-v1", mod("fabric-data-attachment-api-v1",
			FrozenWindCompatibility.ATTACHMENT_API_MODULE_VERSION,
			Map.of("net/fabricmc/fabric/impl/attachment/sync/AttachmentChange.class", wrongClass)));
		mods.put("frozenlib", mod("frozenlib", FrozenWindCompatibility.FROZENLIB_VERSION,
			Map.of("net/frozenblock/lib/wind/WindManager.class", wrongClass)));
		mods.put("wilderwild", mod("wilderwild", FrozenWindCompatibility.WILDERWILD_VERSION,
			Map.of("net/frozenblock/wilderwild/wind/WWWindManagerExtension.class", wrongClass)));
		return mods;
	}

	private static FabricLoader loader(Map<String, ModContainer> mods) {
		return proxy(FabricLoader.class, (instance, method, args) -> {
			if (method.getName().equals("getModContainer")) {
				return Optional.ofNullable(mods.get(args[0]));
			}
			return defaultValue(method.getReturnType());
		});
	}

	private static ModContainer mod(String id, String version, Map<String, Path> paths) {
		ModMetadata metadata = proxy(ModMetadata.class, (instance, method, args) -> {
			if (method.getName().equals("getId")) return id;
			if (method.getName().equals("getVersion")) return Version.parse(version);
			if (method.getName().equals("getName")) return id;
			return defaultValue(method.getReturnType());
		});
		return proxy(ModContainer.class, (instance, method, args) -> {
			if (method.getName().equals("getMetadata")) return metadata;
			if (method.getName().equals("findPath")) return Optional.ofNullable(paths.get(args[0]));
			if (method.getName().equals("getRootPaths")) return List.of();
			return defaultValue(method.getReturnType());
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}
}
