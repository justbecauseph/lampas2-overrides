package lampas2overrides.illagerblabber;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

class IllagerBlabberProfileTest {

	private static final String MOD_ID = "illagerblabber";
	private static final String VERSION = "1.1.0";
	private static final String SHA256 = "ceecc1248d29786d4b1b595e50dd61f9be49b647f015d30b4373c544fcd9b0e5";

	@Test
	void acceptsOnlyTheExactIdVersionAndHash() {
		assertTrue(IllagerBlabberProfile.matchesProfile(MOD_ID, VERSION, SHA256));
		assertTrue(IllagerBlabberProfile.matchesProfile(MOD_ID, VERSION, SHA256.toUpperCase(Locale.ROOT)));
		assertFalse(IllagerBlabberProfile.matchesProfile("lootr", VERSION, SHA256));
		assertFalse(IllagerBlabberProfile.matchesProfile(MOD_ID, "1.1.1", SHA256));
		assertFalse(IllagerBlabberProfile.matchesProfile(MOD_ID, "1.0.0", SHA256));
		assertFalse(IllagerBlabberProfile.matchesProfile(MOD_ID, VERSION, SHA256.replace('c', 'd')));
		assertFalse(IllagerBlabberProfile.matchesProfile(MOD_ID, VERSION, null));
		assertFalse(IllagerBlabberProfile.matchesProfile(null, VERSION, SHA256));
		assertFalse(IllagerBlabberProfile.matchesProfile(MOD_ID, null, SHA256));
	}

	@Test
	void rejectsAbsentModsAndNullLoaders() {
		assertFalse(IllagerBlabberProfile.isInstalledSupported(null));
		assertFalse(IllagerBlabberProfile.isInstalledSupported(loader(Map.of())));
	}

	@Test
	void acceptsTheExactArtifactOnTheTestClasspathAndRejectsAnyOtherFile(@TempDir Path tempDirectory) throws Exception {
		Path vendorJar = vendorJar();
		ModContainer vendor = container(MOD_ID, VERSION, origin(List.of(vendorJar), ModOrigin.Kind.PATH));
		assertTrue(IllagerBlabberProfile.matchesArtifact(vendor));
		assertTrue(IllagerBlabberProfile.isInstalledSupported(loader(Map.of(MOD_ID, vendor))));

		Path impostor = tempDirectory.resolve("illagerblabber-1.1.0.jar");
		Files.writeString(impostor, "not the audited jar");
		assertFalse(IllagerBlabberProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(impostor), ModOrigin.Kind.PATH))));
	}

	@Test
	void failsClosedForWrongMetadataUnsupportedOriginsAndDirectories(@TempDir Path tempDirectory) throws Exception {
		Path vendorJar = vendorJar();
		ModOrigin path = origin(List.of(vendorJar), ModOrigin.Kind.PATH);

		assertFalse(IllagerBlabberProfile.matchesArtifact(container(MOD_ID, "1.1.1", path)));
		assertFalse(IllagerBlabberProfile.matchesArtifact(container("illagerblabber-fork", VERSION, path)));
		assertFalse(IllagerBlabberProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(vendorJar), ModOrigin.Kind.UNKNOWN))));
		assertFalse(IllagerBlabberProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(vendorJar, vendorJar), ModOrigin.Kind.PATH))));
		assertFalse(IllagerBlabberProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(tempDirectory), ModOrigin.Kind.PATH))));
		assertFalse(IllagerBlabberProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(tempDirectory.resolve("missing.jar")), ModOrigin.Kind.PATH))));
		assertFalse(IllagerBlabberProfile.matchesArtifact(container(MOD_ID, VERSION, null)));
		assertFalse(IllagerBlabberProfile.matchesArtifact(null));
	}

	@Test
	void rejectsOriginReadFailuresWithoutThrowing() {
		ModOrigin unreadable = proxy(ModOrigin.class, (proxy, method, args) -> switch (method.getName()) {
			case "getKind" -> ModOrigin.Kind.PATH;
			case "getPaths" -> throw new SecurityException("origin denied");
			default -> null;
		});
		assertFalse(IllagerBlabberProfile.matchesArtifact(container(MOD_ID, VERSION, unreadable)));
	}

	private static Path vendorJar() throws Exception {
		Class<?> registry = Class.forName(IllagerBlabberProfile.REGISTRY_TARGET, false,
			IllagerBlabberProfileTest.class.getClassLoader());
		return Path.of(registry.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private static ModOrigin origin(List<Path> paths, ModOrigin.Kind kind) {
		return proxy(ModOrigin.class, (proxy, method, args) -> switch (method.getName()) {
			case "getKind" -> kind;
			case "getPaths" -> paths;
			default -> null;
		});
	}

	private static ModContainer container(String modId, String version, ModOrigin origin) {
		ModMetadata metadata = proxy(ModMetadata.class, (proxy, method, args) -> switch (method.getName()) {
			case "getId" -> modId;
			case "getVersion" -> Version.parse(version);
			default -> defaultValue(method.getReturnType());
		});
		return proxy(ModContainer.class, (proxy, method, args) -> switch (method.getName()) {
			case "getOrigin" -> origin;
			case "getMetadata" -> metadata;
			default -> defaultValue(method.getReturnType());
		});
	}

	private static FabricLoader loader(Map<String, ModContainer> mods) {
		return proxy(FabricLoader.class, (proxy, method, args) -> {
			if ("getModContainer".equals(method.getName())) {
				return Optional.ofNullable(mods.get(args[0]));
			}
			return defaultValue(method.getReturnType());
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == char.class) {
			return '\0';
		}
		return (byte) 0;
	}
}
