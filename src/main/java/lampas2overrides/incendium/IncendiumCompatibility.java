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

	private static final Profile PROFILE_5_5_1 = new Profile(
			"5.5.1",
			Identifier.fromNamespaceAndPath("lampas2-overrides", "incendium_5_5_1_optimizations"),
			"Lampas2 Incendium 5.5.1 optimizations",
			Map.ofEntries(
					Map.entry("data/incendium/function/clocks/main.mcfunction",
							"fe026903d2fae586c11320e263308c62b18eb9c0ae9fa11dcb1b7cabeba091dc"),
					Map.entry("data/incendium/function/player/main.mcfunction",
							"298e91e1b53a906c89fd326b3d8b6e1e64cc1c2b18ee914d28b1c0bd5e525518"),
					Map.entry("data/incendium/function/item/chilling_blade/on_hit.mcfunction",
							"dce837a919050bd97fe8b6f21fa10b21dc8bbaf42f4a864ecb232d02f896fca9"),
					// Not replaced: clocks/lampas_frozen relies on it counting in.frozen down.
					Map.entry("data/incendium/function/entity/chilling.mcfunction",
							"d171b72e50e7737db20e14267f55ffcba0c14cdc16dac7c8cc8f171f525e337f"),
					Map.entry("data/incendium/function/technical/entity_id/check.mcfunction",
							"dbdbf31acb6d9274f63f218f2616e1f4883e0963909d40d362f00bf15f308c17"),
					Map.entry("data/incendium/function/technical/entity_id/reset.mcfunction",
							"340591a2b5a74330f04c9c0dfc07aa497302ef16c0f498cb07d6d0abe8dd92d5"),
					Map.entry("data/incendium/function/technical/summon_helper.mcfunction",
							"5ed3539c83a33c6c90fd7402fff2caac89c5277cc8f251dd3b99b5419e77b5f5"),
					// Not replaced: the held-item gate in player/main is sound only while each of the seven gated lines
					// needs an incendium custom-data item in the main hand, off hand or head through these predicates.
					Map.entry("data/incendium/predicate/holding/voltaic_trident.json",
							"b2f765e09111ac713efafaa0f60e4dada2b2061aafe6a502b9908b3c7c0028a4"),
					Map.entry("data/incendium/predicate/holding/hefty_pickaxe.json",
							"b698e2e3d22a069c171ddb1b084f536cd7b2bcfd9ae2dde505e1ca936a1bc3dc"),
					Map.entry("data/incendium/predicate/wearing/scouts_battle_helm.json",
							"4544c23ccd67190c011ac484de86f6142bfb9e04f6c1d48d9c394463d8c54746"),
					Map.entry("data/incendium/predicate/holding/firestorm.json",
							"098539b8ba4a68c34f8135f3865b46593f2d55427a8abc3e16e7ba5fd863b457"),
					Map.entry("data/incendium/predicate/holding/firestorm/mainhand.json",
							"c0cf1ca8f6bdb60308b92141fcb001624a0b9c82559528ba0aba74c5dafcadfd"),
					Map.entry("data/incendium/predicate/holding/firestorm/offhand.json",
							"9ba0547a8b607cf7f8612aade7f3af1912760ac1c74962ca8e6c193ee763e8aa"),
					Map.entry("data/incendium/predicate/holding/holy_wrath.json",
							"26beaa6693b5c3c8bd01347be05b6d1399fea3a90e1e4d2ef43ae0b529eba4d2"),
					Map.entry("data/incendium/predicate/holding/sentrys_wrath.json",
							"45900f555d462e00d6ae8edf68db5b17a368d1e60adf780d3050856e4643da65"),
					Map.entry("data/incendium/predicate/holding/sentrys_wrath/mainhand.json",
							"eaa73249d97749db007ec78b36ce3717ba7ec047d61a1a22618245501133d8a3"),
					Map.entry("data/incendium/predicate/holding/sentrys_wrath/offhand.json",
							"d196ee4b53ad2601323c7f8104f7d0a1a6dbb67c57d41126536937de946d2e22")
			)
	);

	private static final Map<String, Profile> PROFILES = Map.of(
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
