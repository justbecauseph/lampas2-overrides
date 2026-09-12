package lampas2overrides.client.boatmask;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

/** Pure version, artifact, companion-mod, and model-layer policy for the boat mask shim. */
public final class BoatWaterMaskProfiles {

	public static final String EMF_MOD_ID = "entity_model_features";
	public static final String EXPECTED_EMF_VERSION = "3.3.5";
	public static final String EXPECTED_EMF_ARTIFACT_SHA256 =
		"72b2d489d03bf2ea07b5693ef26cd03572a42dda181e095aed85b3ab39cce549";
	public static final String EXPECTED_PYRITE_ARTIFACT_SHA256 =
		"6c378632fcadd0501a41bd4af90aec58b8cf03a065f69cd9837160c97ed81831";

	private static final Map<String, ProviderProfile> PROVIDERS = providers();
	private static final Map<ModContainer, ArtifactResult> ARTIFACT_RESULTS =
		Collections.synchronizedMap(new IdentityHashMap<>());

	private BoatWaterMaskProfiles() {
	}

	public static boolean supportedEmfVersion(String version) {
		return EXPECTED_EMF_VERSION.equals(version);
	}

	public static boolean matchesArtifact(ModContainer container, String expectedSha256) {
		if (container == null || expectedSha256 == null) {
			return false;
		}
		synchronized (ARTIFACT_RESULTS) {
			ArtifactResult cached = ARTIFACT_RESULTS.get(container);
			if (cached != null && cached.expectedSha256().equalsIgnoreCase(expectedSha256)) {
				return cached.matches();
			}

			boolean matches = false;
			try {
				Path artifact = artifactPath(container);
				if (artifact != null) {
					try (InputStream input = Files.newInputStream(artifact)) {
						matches = expectedSha256.equalsIgnoreCase(sha256Hex(input));
					}
				}
			} catch (IOException | RuntimeException exception) {
				matches = false;
			}
			ARTIFACT_RESULTS.put(container, new ArtifactResult(expectedSha256, matches));
			return matches;
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
		return path != null && Files.isRegularFile(path) ? path : null;
	}

	public static boolean supportedProviderVersion(String modId, String version) {
		ProviderProfile profile = PROVIDERS.get(modId);
		return profile != null && profile.expectedVersion().equals(version);
	}

	public static boolean hasSupportedProvider(FabricLoader loader) {
		for (ProviderProfile profile : PROVIDERS.values()) {
			if (installedProviderMatches(loader, profile.modId()) && requiredModsMatch(loader, profile)) {
				return true;
			}
		}
		return false;
	}

	public static boolean installedProviderMatches(FabricLoader loader, String modId) {
		ProviderProfile profile = PROVIDERS.get(modId);
		return profile != null && loader.getModContainer(modId)
			.map(container -> profile.expectedVersion().equals(container.getMetadata().getVersion().getFriendlyString())
				&& (profile.artifactSha256() == null || matchesArtifact(container, profile.artifactSha256())))
			.orElse(false);
	}

	public static boolean isAuditedLayer(String modId, String version, String layer, String modelNamespace, String modelPath) {
		ProviderProfile profile = PROVIDERS.get(modId);
		return profile != null
			&& profile.expectedVersion().equals(version)
			&& "main".equals(layer)
			&& modId.equals(modelNamespace)
			&& profile.layerPaths().contains(modelPath);
	}

	public static boolean requiredModsMatch(FabricLoader loader, String modId) {
		ProviderProfile profile = PROVIDERS.get(modId);
		return profile != null && requiredModsMatch(loader, profile);
	}

	private static boolean requiredModsMatch(FabricLoader loader, ProviderProfile profile) {
		for (Map.Entry<String, String> required : profile.requiredMods().entrySet()) {
			if (!loader.getModContainer(required.getKey())
				.map(container -> required.getValue().equals(container.getMetadata().getVersion().getFriendlyString()))
				.orElse(false)) {
				return false;
			}
		}
		return true;
	}

	private static String sha256Hex(InputStream input) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("SHA-256 is required by the Java runtime", exception);
		}
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) >= 0) {
			if (read > 0) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static Map<String, ProviderProfile> providers() {
		Map<String, ProviderProfile> profiles = new LinkedHashMap<>();
		profiles.put("pyrite", new ProviderProfile("pyrite", "0.18.3+26.2", Map.of(), EXPECTED_PYRITE_ARTIFACT_SHA256, Set.of(
			"boat/azalea", "boat/black_stained", "boat/blue_stained", "boat/brown_mushroom",
			"boat/brown_stained", "boat/cyan_stained", "boat/dragon_stained", "boat/glow_stained",
			"boat/gray_stained", "boat/green_stained", "boat/honey_stained", "boat/light_blue_stained",
			"boat/light_gray_stained", "boat/lime_stained", "boat/magenta_stained", "boat/nostalgia_stained",
			"boat/orange_stained", "boat/pink_stained", "boat/poisonous_stained", "boat/purple_stained",
			"boat/red_mushroom", "boat/red_stained", "boat/rose_stained", "boat/star_stained",
			"boat/white_stained", "boat/yellow_stained", "chest_boat/azalea", "chest_boat/black_stained",
			"chest_boat/blue_stained", "chest_boat/brown_mushroom", "chest_boat/brown_stained",
			"chest_boat/cyan_stained", "chest_boat/dragon_stained", "chest_boat/glow_stained",
			"chest_boat/gray_stained", "chest_boat/green_stained", "chest_boat/honey_stained",
			"chest_boat/light_blue_stained", "chest_boat/light_gray_stained", "chest_boat/lime_stained",
			"chest_boat/magenta_stained", "chest_boat/nostalgia_stained", "chest_boat/orange_stained",
			"chest_boat/pink_stained", "chest_boat/poisonous_stained", "chest_boat/purple_stained",
			"chest_boat/red_mushroom", "chest_boat/red_stained", "chest_boat/rose_stained",
			"chest_boat/star_stained", "chest_boat/white_stained", "chest_boat/yellow_stained")));
		profiles.put("promenade", new ProviderProfile("promenade", "5.6.0", Map.of(), null, boatPairPaths("sakura", "maple", "palm")));
		profiles.put("wilderwild", new ProviderProfile("wilderwild", "4.2.11-mc26.2", Map.of(), null,
			boatPairPaths("baobab", "willow", "cypress", "palm", "maple")));
		profiles.put("betterend", new ProviderProfile("betterend", "26.201.2",
			Map.of("wover-item", "26.201.2"), null, fullItemBoatPairPaths(
				"dragon_tree", "helix_tree", "jellyshroom", "lacugrove", "lucernia", "mossy_glowshroom",
				"pythadendron", "tenanea", "umbrella_tree")));
		profiles.put("betternether", new ProviderProfile("betternether", "26.201.2",
			Map.of("wover-item", "26.201.2"), null, fullItemBoatPairPaths(
				"anchor_tree", "crimson", "gloomwood", "mushroom_fir", "nether_mushroom", "nether_sakura",
				"rubeus", "stalagnate", "warped", "wart", "willow")));
		return Map.copyOf(profiles);
	}

	private static Set<String> boatPairPaths(String... names) {
		Set<String> paths = new java.util.LinkedHashSet<>();
		for (String name : names) {
			paths.add("boat/" + name);
			paths.add("chest_boat/" + name);
		}
		return Set.copyOf(paths);
	}

	private static Set<String> fullItemBoatPairPaths(String... names) {
		Set<String> paths = new java.util.LinkedHashSet<>();
		for (String name : names) {
			paths.add("boat/" + name + "_boat");
			paths.add("chest_boat/" + name + "_chest_boat");
		}
		return Set.copyOf(paths);
	}

	private record ProviderProfile(
		String modId,
		String expectedVersion,
		Map<String, String> requiredMods,
		String artifactSha256,
		Set<String> layerPaths
	) {
	}

	private record ArtifactResult(String expectedSha256, boolean matches) {
	}
}
