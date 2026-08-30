package lampas2overrides.stoneholm;

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
import net.minecraft.resources.Identifier;

public final class StoneholmDataFixes implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("Lampas2 Overrides/Stoneholm");
	private static final Identifier PACK_ID = Identifier.fromNamespaceAndPath(
			"lampas2-overrides", "stoneholm_2_1_1_fixes");

	@Override
	public void onInitialize() {
		FabricLoader loader = FabricLoader.getInstance();
		Optional<ModContainer> stoneholmOptional = loader.getModContainer("underground_village");
		if (stoneholmOptional.isEmpty()) {
			return;
		}

		ModContainer stoneholm = stoneholmOptional.get();
		String version = stoneholm.getMetadata().getVersion().getFriendlyString();
		if (!StoneholmCompatibility.supportsVersion(version)) {
			LOGGER.warn("Underground Village {} is not supported; worldgen fixes will remain disabled",
					version);
			return;
		}

		if (!matchesExpectedResources(stoneholm)) {
			LOGGER.warn("Underground Village {} does not match the verified 2.1.1 resources; worldgen fixes will remain disabled",
					version);
			return;
		}

		ModContainer self = loader.getModContainer("lampas2-overrides").orElseThrow(
				() -> new IllegalStateException("Unable to locate the lampas2-overrides mod container"));
		boolean registered = ResourceLoader.registerBuiltinPack(
				PACK_ID,
				self,
				Component.literal("Lampas2 Underground Village 2.1.1 worldgen fixes"),
				PackActivationType.ALWAYS_ENABLED
		);

		if (registered) {
			LOGGER.info("Enabled version-gated Underground Village 2.1.1 worldgen fixes");
		} else {
			LOGGER.error("Failed to register the Underground Village 2.1.1 worldgen fixes datapack");
		}
	}

	private static boolean matchesExpectedResources(ModContainer stoneholm) {
		for (Map.Entry<String, String> expected : StoneholmCompatibility.EXPECTED_RESOURCES.entrySet()) {
			Optional<Path> resource = stoneholm.findPath(expected.getKey());
			if (resource.isEmpty()) {
				LOGGER.warn("Underground Village resource {} is missing", expected.getKey());
				return false;
			}

			try {
				if (!StoneholmCompatibility.matches(resource.get(), expected.getValue())) {
					LOGGER.warn("Underground Village resource {} has an unexpected SHA-256 fingerprint", expected.getKey());
					return false;
				}
			} catch (IOException exception) {
				LOGGER.warn("Could not fingerprint Underground Village resource {}", expected.getKey(), exception);
				return false;
			}
		}

		return true;
	}
}
