package lampas2overrides.resourcefix;

import java.util.Locale;
import java.util.Objects;

/**
 * Composite key identifying a resource patch by mod ID, exact version, and normalized resource path.
 */
public record ResourcePatchKey(String modId, String version, String resourcePath) {

	public ResourcePatchKey {
		Objects.requireNonNull(modId, "modId");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(resourcePath, "resourcePath");
		modId = modId.toLowerCase(Locale.ROOT);
		version = version.toLowerCase(Locale.ROOT);
		resourcePath = ResourcePatchResolver.normalizePath(resourcePath).toLowerCase(Locale.ROOT);
	}

	public static ResourcePatchKey of(String modId, String version, String resourcePath) {
		return new ResourcePatchKey(modId, version, resourcePath);
	}
}
