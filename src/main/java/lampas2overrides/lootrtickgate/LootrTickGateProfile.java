package lampas2overrides.lootrtickgate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * The one audited Lootr artifact whose tick path the gate was checked against.
 *
 * <p>Vendor classes are named only by string, so the plugin and this profile can run before any
 * Lootr type is loaded.
 */
public final class LootrTickGateProfile {

	public static final String MOD_ID = "lootr";
	public static final String VERSION = "1.24.41.124";
	public static final String JAR_SHA256 = "6669f8415aa4280eae0959d6e8cac0bb0dab02dbfd3efd6a153c1fad02e80a6b";
	public static final String TARGET = "noobanidus.mods.lootr.common.api.LootrAPI";

	private LootrTickGateProfile() {
	}

	/** Checks all three immutable identifiers; the SHA-256 comparison is case-insensitive. */
	public static boolean matchesProfile(String modId, String version, String sha256) {
		return MOD_ID.equals(modId)
			&& VERSION.equals(version)
			&& sha256 != null
			&& JAR_SHA256.equalsIgnoreCase(sha256);
	}

	/** Checks metadata first, then the sole PATH origin's SHA-256. Any failure disables the gate. */
	public static boolean isInstalledSupported(FabricLoader loader) {
		if (loader == null) {
			return false;
		}
		try {
			return loader.getModContainer(MOD_ID)
				.map(LootrTickGateProfile::matchesArtifact)
				.orElse(false);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public static boolean matchesArtifact(ModContainer container) {
		if (container == null) {
			return false;
		}
		try {
			ModMetadata metadata = container.getMetadata();
			if (metadata == null) {
				return false;
			}
			String modId = metadata.getId();
			String version = metadata.getVersion().getFriendlyString();
			if (!MOD_ID.equals(modId) || !VERSION.equals(version)) {
				return false;
			}
			return matchesProfile(modId, version, artifactSha256(container));
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** Version text for diagnostics only; it is never an acceptance input. */
	public static String installedVersion(ModContainer container) {
		try {
			return container.getMetadata().getVersion().getFriendlyString();
		} catch (RuntimeException exception) {
			return "unknown";
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
}
