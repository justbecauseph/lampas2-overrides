package lampas2overrides.resourcefix;

import java.util.Objects;

/**
 * Immutable rule for virtually patching a known-defective resource inside a mod archive.
 *
 * @param modId The Fabric mod ID owning the target resource.
 * @param expectedVersion The exact version of the mod for which this patch is valid.
 * @param resourcePath The normalized relative archive path of the resource (e.g. "pack.mcmeta" or "data/foo/bar.json").
 * @param expectedSha256 The exact lowercase SHA-256 hex digest of the original unpatched resource bytes.
 * @param replacementPath The classloader path to the replacement resource in lampas2-overrides.
 */
public record ResourcePatch(
	String modId,
	String expectedVersion,
	String resourcePath,
	String expectedSha256,
	String replacementPath
) {
	public ResourcePatch {
		Objects.requireNonNull(modId, "modId");
		Objects.requireNonNull(expectedVersion, "expectedVersion");
		Objects.requireNonNull(resourcePath, "resourcePath");
		Objects.requireNonNull(expectedSha256, "expectedSha256");
		Objects.requireNonNull(replacementPath, "replacementPath");
	}
}
