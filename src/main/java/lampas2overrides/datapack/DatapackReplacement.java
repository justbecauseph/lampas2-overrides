package lampas2overrides.datapack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers a built-in datapack that replaces functions of another mod, but only when that mod is present at an
 * exact version and every upstream resource the replacement depends on has the fingerprint recorded in the profile.
 * Unknown versions and modified resources leave the pack disabled (fail closed).
 */
public final class DatapackReplacement {

	/**
	 * @param version           exact friendly version string of the upstream mod (for {@link #registerAnyVersion}, the
	 *                          release the fingerprints were audited on)
	 * @param packId            built-in pack identifier under the lampas2-overrides namespace
	 * @param packName          display name shown by {@code /datapack list}
	 * @param expectedResources upstream resource path → SHA-256 the replacement was written against
	 */
	public record Profile(
			String version,
			Identifier packId,
			String packName,
			Map<String, String> expectedResources
	) {}

	private DatapackReplacement() {
	}

	/**
	 * @return true when the pack was registered; false when the mod is absent, unsupported, modified or the
	 *         registration failed (each case is logged)
	 */
	public static boolean register(String modId, String modName, Map<String, Profile> profiles, Logger logger) {
		FabricLoader loader = FabricLoader.getInstance();
		Optional<ModContainer> targetOptional = loader.getModContainer(modId);
		if (targetOptional.isEmpty()) {
			return false;
		}

		ModContainer target = targetOptional.get();
		String version = target.getMetadata().getVersion().getFriendlyString();
		Profile profile = profiles.get(version);
		if (profile == null) {
			logger.warn("{} {} is not supported; the performance datapack will remain disabled", modName, version);
			return false;
		}

		if (!matchesExpectedResources(target, modName, profile, logger)) {
			logger.warn("{} {} does not match the verified {} functions; the performance datapack will remain disabled",
					modName, version, profile.version());
			return false;
		}

		boolean registered = registerPack(profile);
		if (registered) {
			logger.info("Enabled the version-gated {} {} performance datapack", modName, profile.version());
		} else {
			logger.error("Failed to register the {} {} performance datapack", modName, profile.version());
		}
		return registered;
	}

	/**
	 * Registers the pack for any installed version of the mod, as long as every upstream resource in the profile still
	 * has its recorded fingerprint. {@link Profile#version()} names the release the fingerprints were audited on and
	 * is only reported. Use this when the replacement's correctness depends on resource content rather than on the
	 * release number.
	 *
	 * @return true when the pack was registered; false when the mod is absent, a resource differs or the
	 *         registration failed (each case is logged)
	 */
	public static boolean registerAnyVersion(String modId, String modName, Profile profile, Logger logger) {
		Optional<ModContainer> targetOptional = FabricLoader.getInstance().getModContainer(modId);
		if (targetOptional.isEmpty()) {
			return false;
		}

		ModContainer target = targetOptional.get();
		String version = target.getMetadata().getVersion().getFriendlyString();
		if (!matchesExpectedResources(target, modName, profile, logger)) {
			logger.warn("{} {} does not match the functions audited on {}; the performance datapack will remain disabled",
					modName, version, profile.version());
			return false;
		}

		boolean registered = registerPack(profile);
		if (registered) {
			logger.info("Enabled the {} performance datapack for {} (functions audited on {})", modName, version,
					profile.version());
		} else {
			logger.error("Failed to register the {} performance datapack for {}", modName, version);
		}
		return registered;
	}

	private static boolean registerPack(Profile profile) {
		ModContainer self = FabricLoader.getInstance().getModContainer("lampas2-overrides").orElseThrow(
				() -> new IllegalStateException("Unable to locate the lampas2-overrides mod container"));
		return ResourceLoader.registerBuiltinPack(
				profile.packId(),
				self,
				Component.literal(profile.packName()),
				PackActivationType.ALWAYS_ENABLED
		);
	}

	private static boolean matchesExpectedResources(ModContainer target, String modName, Profile profile, Logger logger) {
		for (Map.Entry<String, String> expected : profile.expectedResources().entrySet()) {
			Optional<Path> resource = target.findPath(expected.getKey());
			if (resource.isEmpty()) {
				logger.warn("{} resource {} is missing", modName, expected.getKey());
				return false;
			}

			try {
				if (!ResourceFingerprint.matches(resource.get(), expected.getValue())) {
					logger.warn("{} resource {} has an unexpected SHA-256 fingerprint", modName, expected.getKey());
					return false;
				}
			} catch (IOException exception) {
				logger.warn("Could not fingerprint {} resource {}", modName, expected.getKey(), exception);
				return false;
			}
		}

		return true;
	}
}
