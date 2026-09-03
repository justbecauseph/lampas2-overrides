package lampas2overrides.incendium;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

public final class IncendiumCompatibility {

	public record Profile(
			String version,
			Identifier packId,
			String packName,
			Map<String, String> expectedResources
	) {}

	private static final Profile PROFILE_5_5_0 = new Profile(
			"5.5.0",
			Identifier.fromNamespaceAndPath("lampas2-overrides", "incendium_5_5_0_optimizations"),
			"Lampas2 Incendium 5.5.0 optimizations",
			Map.of(
					"data/incendium/function/clocks/main.mcfunction",
					"7f72f3a8df4688dc993a03a0860a2e51d226041a5f9886ab4745e7343780d5a0",
					"data/incendium/function/technical/entity_id/check.mcfunction",
					"dbdbf31acb6d9274f63f218f2616e1f4883e0963909d40d362f00bf15f308c17",
					"data/incendium/function/technical/entity_id/reset.mcfunction",
					"340591a2b5a74330f04c9c0dfc07aa497302ef16c0f498cb07d6d0abe8dd92d5"
			)
	);

	private static final Profile PROFILE_5_5_1 = new Profile(
			"5.5.1",
			Identifier.fromNamespaceAndPath("lampas2-overrides", "incendium_5_5_1_optimizations"),
			"Lampas2 Incendium 5.5.1 optimizations",
			Map.of(
					"data/incendium/function/clocks/main.mcfunction",
					"fe026903d2fae586c11320e263308c62b18eb9c0ae9fa11dcb1b7cabeba091dc",
					"data/incendium/function/technical/entity_id/check.mcfunction",
					"dbdbf31acb6d9274f63f218f2616e1f4883e0963909d40d362f00bf15f308c17",
					"data/incendium/function/technical/entity_id/reset.mcfunction",
					"340591a2b5a74330f04c9c0dfc07aa497302ef16c0f498cb07d6d0abe8dd92d5"
			)
	);

	private static final Map<String, Profile> PROFILES = Map.of(
			"5.5.0", PROFILE_5_5_0,
			"5.5.1", PROFILE_5_5_1
	);

	private IncendiumCompatibility() {
	}

	public static boolean supportsVersion(String version) {
		return PROFILES.containsKey(version);
	}

	public static Optional<Profile> getProfile(String version) {
		return Optional.ofNullable(PROFILES.get(version));
	}

	public static Map<String, Profile> getProfiles() {
		return PROFILES;
	}

	public static boolean matches(Path resource, String expectedSha256) throws IOException {
		return expectedSha256.equals(sha256(resource));
	}

	public static String sha256(Path resource) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}

		try (InputStream input = Files.newInputStream(resource)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}

		return HexFormat.of().formatHex(digest.digest());
	}
}
