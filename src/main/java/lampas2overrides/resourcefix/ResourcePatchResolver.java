package lampas2overrides.resourcefix;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Resolves virtual resource patches at runtime with version gating and SHA-256 fingerprint verification.
 */
public final class ResourcePatchResolver {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/resource-fix");
	private static final Set<String> LOGGED_KEYS = ConcurrentHashMap.newKeySet();
	private static final Map<String, byte[]> REPLACEMENT_CACHE = new ConcurrentHashMap<>();

	/**
	 * Attempts to resolve a virtual resource patch for a mod resource.
	 *
	 * @param modContainer The owning mod container (may be null).
	 * @param rawPath The resource path (e.g. "pack.mcmeta" or "data/minecraft/tags/...").
	 * @param originalSupplier The supplier for the original resource (may be null).
	 * @return A patched IoSupplier if a patch applies, or null if no patch applies.
	 */
	public static IoSupplier<InputStream> resolve(
		ModContainer modContainer,
		String rawPath,
		IoSupplier<InputStream> originalSupplier
	) {
		if (modContainer == null || rawPath == null) {
			return null;
		}

		String modId = modContainer.getMetadata().getId();
		String normalizedPath = normalizePath(rawPath);

		ResourcePatch patch = ResourcePatchRegistry.findPatch(modId, normalizedPath);
		if (patch == null) {
			return null;
		}

		String actualVersion = modContainer.getMetadata().getVersion().getFriendlyString();
		if (!patch.expectedVersion().equals(actualVersion)) {
			String logKey = "ver_mismatch:" + modId + ":" + normalizedPath + ":" + actualVersion;
			if (LOGGED_KEYS.add(logKey)) {
				LOGGER.info("Skipping {} resource patch for {}: expected {}, found {}",
					modId, normalizedPath, patch.expectedVersion(), actualVersion);
			}
			return null;
		}

		// Read and verify original bytes if supplier is present
		byte[] originalBytes = null;
		if (originalSupplier != null) {
			try (InputStream is = originalSupplier.get()) {
				if (is != null) {
					originalBytes = is.readAllBytes();
				}
			} catch (IOException e) {
				LOGGER.warn("Failed to read original resource {} for mod {} v{}: {}",
					normalizedPath, modId, actualVersion, e.getMessage());
			}
		}

		if (originalBytes != null) {
			String actualSha256 = sha256Hex(originalBytes);
			if (!patch.expectedSha256().equalsIgnoreCase(actualSha256)) {
				String logKey = "hash_mismatch:" + modId + ":" + normalizedPath + ":" + actualSha256;
				if (LOGGED_KEYS.add(logKey)) {
					LOGGER.warn("Skipping {} {} resource patch for {}: SHA-256 mismatch (expected {}, found {})",
						modId, actualVersion, normalizedPath, patch.expectedSha256(), actualSha256);
				}
				final byte[] fallbackBytes = originalBytes;
				return () -> new ByteArrayInputStream(fallbackBytes);
			}
		}

		// Load replacement bytes
		byte[] replacementBytes = REPLACEMENT_CACHE.computeIfAbsent(patch.replacementPath(), path -> {
			try (InputStream is = ResourcePatchResolver.class.getClassLoader().getResourceAsStream(path)) {
				if (is == null) {
					LOGGER.error("Missing replacement resource on classpath: {}", path);
					return null;
				}
				return is.readAllBytes();
			} catch (IOException e) {
				LOGGER.error("Failed to load replacement resource {}: {}", path, e.getMessage());
				return null;
			}
		});

		if (replacementBytes == null) {
			return null;
		}

		String logKey = "applied:" + modId + ":" + normalizedPath;
		if (LOGGED_KEYS.add(logKey)) {
			LOGGER.info("Applied {} {} compatibility resource: {}",
				modId, actualVersion, normalizedPath);
		}

		return () -> new ByteArrayInputStream(replacementBytes);
	}

	public static String normalizePath(String path) {
		String normalized = path.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	public static String sha256Hex(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(data);
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 algorithm not found", e);
		}
	}

	public static void clearLogHistoryForTests() {
		LOGGED_KEYS.clear();
	}

	private ResourcePatchResolver() {
	}
}
