package lampas2overrides.stoneholm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

final class StoneholmCompatibility {

	static final String SUPPORTED_VERSION = "2.1.1";

	static final Map<String, String> EXPECTED_RESOURCES = Map.of(
			"data/stoneholm/structure/poi/v4/founten.nbt",
			"49946ba3e75b6530fd9ed1929bc46bf26dfe15e03e2975acc72bc701cb0db668",
			"data/stoneholm/structure/poi/v4/sidebed_bedroom.nbt",
			"77916843f777b802fafb0a997b2fae2c3809957a4e03f8a851923a8be6c08965",
			"data/stoneholm/structure/abandoned_poi/v4/sidebed_bedroom.nbt",
			"45c8063a2b1380a667895e057b1d8c93d4c7cc62caa315e559afe5f3513ed595",
			"data/stoneholm/structure/poi/v4/tall_bedroom.nbt",
			"78503d5f4716aab42a3b41ebd8de30c5c5dd621c4d0523f05610c0cf04f26421",
			"data/stoneholm/worldgen/template_pool/addons/better_villagers/better_villagers_point_of_interest.json",
			"736305800e2115aeca4466703e986980ea27138767ff284c5f4ed8846fa25326",
			"data/stoneholm/worldgen/template_pool/addons/better_villagers/better_villagers_abandoned_point_of_interest.json",
			"ccc17c83ee1353b4acaef31f5a99b21eda872e689ffdf654496910a66ae8d17a"
	);

	private StoneholmCompatibility() {
	}

	static boolean supportsVersion(String version) {
		return SUPPORTED_VERSION.equals(version);
	}

	static boolean matches(Path resource, String expectedSha256) throws IOException {
		return expectedSha256.equals(sha256(resource));
	}

	static String sha256(Path resource) throws IOException {
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
