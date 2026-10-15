package lampas2overrides.resourcefix;

import java.util.Locale;
import java.util.Objects;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * Composite key identifying a resource patch by mod ID, exact version, and normalized resource path.
 */
public record ResourcePatchKey(String modId, String version, String resourcePath) {

	public ResourcePatchKey {
		Objects.requireNonNull(modId, "modId");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(resourcePath, "resourcePath");
		modId = modId.toLowerCase(Locale.ROOT);
		version = loaderVersion(version).toLowerCase(Locale.ROOT);
		resourcePath = ResourcePatchResolver.normalizePath(resourcePath).toLowerCase(Locale.ROOT);
	}

	public static ResourcePatchKey of(String modId, String version, String resourcePath) {
		return new ResourcePatchKey(modId, version, resourcePath);
	}

	/**
	 * Returns the version as Fabric Loader reports it. Loader rebuilds semantic versions from their
	 * numeric components, so a fabric.mod.json version of {@code 3.1.3.003+26.2} reaches the resolver
	 * as {@code 3.1.3.3+26.2}; registered versions must be compared in the same form.
	 */
	static String loaderVersion(String version) {
		try {
			return Version.parse(version).getFriendlyString();
		} catch (VersionParsingException e) {
			return version;
		}
	}
}
