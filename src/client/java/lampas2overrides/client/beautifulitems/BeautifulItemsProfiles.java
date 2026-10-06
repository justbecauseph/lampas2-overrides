package lampas2overrides.client.beautifulitems;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/** Exact optional-artifact profiles for the Beautiful item-model reload fix. */
public final class BeautifulItemsProfiles {

	public static final String ENCHANTED_BOOKS_TARGET = "com.cerbon.beb.fabric.BeautifulEnchantedBooksFabric";
	public static final String POTIONS_TARGET = "com.cerbon.beautiful_potions.fabric.BeautifulPotionsFabric";

	private static final Map<String, TargetProfile> TARGETS = Map.of(
		ENCHANTED_BOOKS_TARGET,
		new TargetProfile("beb", "6.0.0", "08819eb4b0773d389b850dad7cc0c2134a1fcb5d3693d28e03cc8c309181fa8f"),
		POTIONS_TARGET,
		new TargetProfile("beautiful_potions", "2.0.1", "53d132f68b9c97105699e3de3542ad9f3ef5ec4e4df5e147f00e5f8510e33a82")
	);

	private BeautifulItemsProfiles() {
	}

	public static Optional<TargetProfile> targetProfile(String targetClassName) {
		return Optional.ofNullable(TARGETS.get(targetClassName));
	}

	/** Checks all three immutable identifiers before consulting a mod's installed artifact. */
	public static boolean matchesProfile(String targetClassName, String modId, String version, String sha256) {
		TargetProfile profile = TARGETS.get(targetClassName);
		return profile != null
			&& profile.modId().equals(modId)
			&& profile.version().equals(version)
			&& sha256 != null
			&& profile.jarSha256().equalsIgnoreCase(sha256);
	}

	/** Resolves one configured target independently; an unavailable or unreadable origin disables only it. */
	public static boolean isInstalledTargetSupported(FabricLoader loader, String targetClassName) {
		TargetProfile profile = TARGETS.get(targetClassName);
		if (loader == null || profile == null) {
			return false;
		}
		try {
			Optional<ModContainer> container = loader.getModContainer(profile.modId());
			return container != null && container
				.map(mod -> matchesArtifact(targetClassName, mod))
				.orElse(false);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** Checks the metadata and SHA-256 of the sole path that supplied this mod. */
	public static boolean matchesArtifact(String targetClassName, ModContainer container) {
		TargetProfile profile = TARGETS.get(targetClassName);
		if (container == null || profile == null) {
			return false;
		}
		try {
			ModMetadata metadata = container.getMetadata();
			if (metadata == null) {
				return false;
			}
			String modId = metadata.getId();
			String version = metadata.getVersion().getFriendlyString();
			if (!profile.modId().equals(modId) || !profile.version().equals(version)) {
				return false;
			}
			String sha256 = artifactSha256(container);
			return matchesProfile(targetClassName, modId, version, sha256);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static String artifactSha256(ModContainer container) {
		try {
			Path artifact = artifactPath(container);
			if (artifact == null) {
				return null;
			}
			try (InputStream input = Files.newInputStream(artifact)) {
				MessageDigest digest = MessageDigest.getInstance("SHA-256");
				byte[] buffer = new byte[8192];
				int read;
				while ((read = input.read(buffer)) >= 0) {
					if (read > 0) {
						digest.update(buffer, 0, read);
					}
				}
				return HexFormat.of().formatHex(digest.digest());
			}
		} catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
			return null;
		}
	}

	private static Path artifactPath(ModContainer container) {
		ModOrigin origin = container.getOrigin();
		if (origin == null || origin.getKind() != ModOrigin.Kind.PATH) {
			return null;
		}
		List<Path> paths = origin.getPaths();
		if (paths == null || paths.size() != 1) {
			return null;
		}
		Path path = paths.get(0);
		return path != null && Files.isRegularFile(path) && Files.isReadable(path) ? path : null;
	}

	public record TargetProfile(String modId, String version, String jarSha256) {
	}
}
