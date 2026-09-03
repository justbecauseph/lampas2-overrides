package lampas2overrides.incendium;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;

public final class IncendiumOptimization implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Incendium");

	@Override
	public void onInitialize() {
		FabricLoader loader = FabricLoader.getInstance();
		Optional<ModContainer> incendiumOptional = loader.getModContainer("incendium");
		if (incendiumOptional.isEmpty()) {
			return;
		}

		ModContainer incendium = incendiumOptional.get();
		String version = incendium.getMetadata().getVersion().getFriendlyString();
		Optional<IncendiumCompatibility.Profile> profileOptional = IncendiumCompatibility.getProfile(version);
		if (profileOptional.isEmpty()) {
			LOGGER.warn("Incendium {} is not supported; the performance datapack will remain disabled",
					version);
			return;
		}

		IncendiumCompatibility.Profile profile = profileOptional.get();
		if (!matchesExpectedResources(incendium, profile)) {
			LOGGER.warn("Incendium {} does not match the verified {} functions; the performance datapack will remain disabled",
					version, profile.version());
			return;
		}

		ModContainer self = loader.getModContainer("lampas2-overrides").orElseThrow(
				() -> new IllegalStateException("Unable to locate the lampas2-overrides mod container"));
		boolean registered = ResourceLoader.registerBuiltinPack(
				profile.packId(),
				self,
				Component.literal(profile.packName()),
				PackActivationType.ALWAYS_ENABLED
		);

		if (registered) {
			LOGGER.info("Enabled the version-gated Incendium {} performance datapack", profile.version());
		} else {
			LOGGER.error("Failed to register the Incendium {} performance datapack", profile.version());
		}
	}

	private static boolean matchesExpectedResources(ModContainer incendium, IncendiumCompatibility.Profile profile) {
		for (Map.Entry<String, String> expected : profile.expectedResources().entrySet()) {
			Optional<Path> resource = incendium.findPath(expected.getKey());
			if (resource.isEmpty()) {
				LOGGER.warn("Incendium resource {} is missing", expected.getKey());
				return false;
			}

			try {
				if (!IncendiumCompatibility.matches(resource.get(), expected.getValue())) {
					LOGGER.warn("Incendium resource {} has an unexpected SHA-256 fingerprint", expected.getKey());
					return false;
				}
			} catch (IOException exception) {
				LOGGER.warn("Could not fingerprint Incendium resource {}", expected.getKey(), exception);
				return false;
			}
		}

		return true;
	}
}
