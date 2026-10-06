package lampas2overrides.client.beautifulitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

class BeautifulItemsProfilesTest {

	private static final String BOOKS_SHA256 =
		"08819eb4b0773d389b850dad7cc0c2134a1fcb5d3693d28e03cc8c309181fa8f";
	private static final String POTIONS_SHA256 =
		"53d132f68b9c97105699e3de3542ad9f3ef5ec4e4df5e147f00e5f8510e33a82";

	@Test
	void keepsExactVersionAndArtifactProfilesIndependent() {
		BeautifulItemsProfiles.TargetProfile books =
			BeautifulItemsProfiles.targetProfile(BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET).orElseThrow();
		BeautifulItemsProfiles.TargetProfile potions =
			BeautifulItemsProfiles.targetProfile(BeautifulItemsProfiles.POTIONS_TARGET).orElseThrow();

		assertEquals("beb", books.modId());
		assertEquals("6.0.0", books.version());
		assertEquals(BOOKS_SHA256, books.jarSha256());
		assertEquals("beautiful_potions", potions.modId());
		assertEquals("2.0.1", potions.version());
		assertEquals(POTIONS_SHA256, potions.jarSha256());

		assertTrue(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET, "beb", "6.0.0", BOOKS_SHA256));
		assertTrue(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.POTIONS_TARGET, "beautiful_potions", "2.0.1", POTIONS_SHA256));
		assertFalse(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET, "beb", "6.0.1", BOOKS_SHA256));
		assertFalse(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET, "beb", "6.0.0", POTIONS_SHA256));
		assertFalse(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET, "beautiful_potions", "2.0.1", POTIONS_SHA256));
		assertFalse(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.POTIONS_TARGET, "beautiful_potions", "2.0.2", POTIONS_SHA256));
		assertFalse(BeautifulItemsProfiles.matchesProfile(
			BeautifulItemsProfiles.POTIONS_TARGET, "beautiful_potions", "2.0.1", BOOKS_SHA256));
		assertFalse(BeautifulItemsProfiles.matchesProfile("unknown.Target", "beb", "6.0.0", BOOKS_SHA256));
	}

	@Test
	void rejectsAbsentModsAndUnknownTargets() {
		FabricLoader loader = loader(Map.of());
		assertFalse(BeautifulItemsProfiles.isInstalledTargetSupported(loader, BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET));
		assertFalse(BeautifulItemsProfiles.isInstalledTargetSupported(loader, BeautifulItemsProfiles.POTIONS_TARGET));
		assertFalse(BeautifulItemsProfiles.isInstalledTargetSupported(loader, "unknown.Target"));
		assertFalse(BeautifulItemsProfiles.isInstalledTargetSupported(null, BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET));
		assertTrue(BeautifulItemsProfiles.targetProfile("unknown.Target").isEmpty());
	}

	@Test
	void failsClosedForBadMetadataAndUnverifiedOrigins(@TempDir Path tempDirectory) throws Exception {
		Path artifact = tempDirectory.resolve("candidate.jar");
		Files.writeString(artifact, "not the audited jar");
		ModOrigin pathOrigin = origin(List.of(artifact), ModOrigin.Kind.PATH);

		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("beb", "6.0.0", pathOrigin)));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("wrong-id", "6.0.0", pathOrigin)));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("beb", "6.0.1", pathOrigin)));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("beb", "6.0.0", origin(List.of(artifact), ModOrigin.Kind.UNKNOWN))));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("beb", "6.0.0", origin(List.of(artifact, artifact), ModOrigin.Kind.PATH))));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("beb", "6.0.0", origin(List.of(tempDirectory), ModOrigin.Kind.PATH))));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET,
			container("beb", "6.0.0", origin(List.of(tempDirectory.resolve("missing.jar")), ModOrigin.Kind.PATH))));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(
			BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET, container("beb", "6.0.0", null)));
		assertFalse(BeautifulItemsProfiles.matchesArtifact(BeautifulItemsProfiles.ENCHANTED_BOOKS_TARGET, null));
		assertFalse(BeautifulItemsProfiles.matchesArtifact("unknown.Target", container("beb", "6.0.0", pathOrigin)));
	}

	@Test
	void rejectsOriginReadFailuresWithoutDisablingProfileLookup() {
		ModOrigin unreadableOrigin = proxy(ModOrigin.class, (proxy, method, args) -> {
			if ("getKind".equals(method.getName())) {
				return ModOrigin.Kind.PATH;
			}
			if ("getPaths".equals(method.getName())) {
				throw new SecurityException("origin denied");
			}
			return null;
		});
		ModContainer container = container("beautiful_potions", "2.0.1", unreadableOrigin);

		assertFalse(BeautifulItemsProfiles.matchesArtifact(BeautifulItemsProfiles.POTIONS_TARGET, container));
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
