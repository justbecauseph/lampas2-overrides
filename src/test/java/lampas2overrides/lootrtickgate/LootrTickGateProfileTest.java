package lampas2overrides.lootrtickgate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.URL;
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

class LootrTickGateProfileTest {

	private static final String MOD_ID = "lootr";
	private static final String VERSION = "1.24.41.124";
	private static final String SHA256 = "6669f8415aa4280eae0959d6e8cac0bb0dab02dbfd3efd6a153c1fad02e80a6b";

	@Test
	void acceptsOnlyTheExactIdVersionAndHash() {
		assertTrue(LootrTickGateProfile.matchesProfile(MOD_ID, VERSION, SHA256));
		assertTrue(LootrTickGateProfile.matchesProfile(MOD_ID, VERSION, SHA256.toUpperCase(Locale.ROOT)));
		assertFalse(LootrTickGateProfile.matchesProfile("illagerblabber", VERSION, SHA256));
		assertFalse(LootrTickGateProfile.matchesProfile(MOD_ID, "1.24.41.125", SHA256));
		assertFalse(LootrTickGateProfile.matchesProfile(MOD_ID, "1.24.39.121", SHA256));
		assertFalse(LootrTickGateProfile.matchesProfile(MOD_ID, VERSION, SHA256.replace('6', '7')));
		assertFalse(LootrTickGateProfile.matchesProfile(MOD_ID, VERSION, null));
		assertFalse(LootrTickGateProfile.matchesProfile(null, VERSION, SHA256));
		assertFalse(LootrTickGateProfile.matchesProfile(MOD_ID, null, SHA256));
	}

	@Test
	void rejectsAbsentModsAndNullLoaders() {
		assertFalse(LootrTickGateProfile.isInstalledSupported(null));
		assertFalse(LootrTickGateProfile.isInstalledSupported(loader(Map.of())));
	}

	@Test
	void acceptsTheExactArtifactOnTheTestClasspathAndRejectsAnyOtherFile(@TempDir Path tempDirectory) throws Exception {
		Path vendorJar = vendorJar();
		ModContainer vendor = container(MOD_ID, VERSION, origin(List.of(vendorJar), ModOrigin.Kind.PATH));
		assertTrue(LootrTickGateProfile.matchesArtifact(vendor));
		assertTrue(LootrTickGateProfile.isInstalledSupported(loader(Map.of(MOD_ID, vendor))));

		Path impostor = tempDirectory.resolve("lootr-fabric-26.2-1.24.41.124.jar");
		Files.writeString(impostor, "not the audited jar");
		assertFalse(LootrTickGateProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(impostor), ModOrigin.Kind.PATH))));
	}

	@Test
	void failsClosedForWrongMetadataUnsupportedOriginsAndDirectories(@TempDir Path tempDirectory) throws Exception {
		Path vendorJar = vendorJar();
		ModOrigin path = origin(List.of(vendorJar), ModOrigin.Kind.PATH);

		assertFalse(LootrTickGateProfile.matchesArtifact(container(MOD_ID, "1.24.41.125", path)));
		assertFalse(LootrTickGateProfile.matchesArtifact(container("lootr-fork", VERSION, path)));
		assertFalse(LootrTickGateProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(vendorJar), ModOrigin.Kind.UNKNOWN))));
		assertFalse(LootrTickGateProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(vendorJar, vendorJar), ModOrigin.Kind.PATH))));
		assertFalse(LootrTickGateProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(tempDirectory), ModOrigin.Kind.PATH))));
		assertFalse(LootrTickGateProfile.matchesArtifact(
			container(MOD_ID, VERSION, origin(List.of(tempDirectory.resolve("missing.jar")), ModOrigin.Kind.PATH))));
		assertFalse(LootrTickGateProfile.matchesArtifact(container(MOD_ID, VERSION, null)));
		assertFalse(LootrTickGateProfile.matchesArtifact(null));
	}

	@Test
	void rejectsOriginReadFailuresWithoutThrowing() {
		ModOrigin unreadable = proxy(ModOrigin.class, (proxy, method, args) -> switch (method.getName()) {
			case "getKind" -> ModOrigin.Kind.PATH;
			case "getPaths" -> throw new SecurityException("origin denied");
			default -> null;
		});
		assertFalse(LootrTickGateProfile.matchesArtifact(container(MOD_ID, VERSION, unreadable)));
	}

	/** The jar holding {@link LootrTickGateProfile#TARGET}, found as a resource so no Lootr class is loaded. */
	private static Path vendorJar() throws Exception {
		URL resource = LootrTickGateProfileTest.class.getClassLoader()
			.getResource(LootrTickGateProfile.TARGET.replace('.', '/') + ".class");
		assertNotNull(resource, "Lootr must be on the test runtime classpath");
		return Path.of(((JarURLConnection) resource.openConnection()).getJarFileURL().toURI());
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
