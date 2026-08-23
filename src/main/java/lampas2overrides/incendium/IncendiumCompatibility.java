package lampas2overrides.incendium;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

final class IncendiumCompatibility {

	static final String SUPPORTED_VERSION = "5.5.0";

	static final Map<String, String> EXPECTED_RESOURCES = Map.of(
			"data/incendium/function/clocks/main.mcfunction",
			"7f72f3a8df4688dc993a03a0860a2e51d226041a5f9886ab4745e7343780d5a0",
			"data/incendium/function/technical/entity_id/check.mcfunction",
			"dbdbf31acb6d9274f63f218f2616e1f4883e0963909d40d362f00bf15f308c17",
			"data/incendium/function/technical/entity_id/reset.mcfunction",
			"340591a2b5a74330f04c9c0dfc07aa497302ef16c0f498cb07d6d0abe8dd92d5"
	);

	private IncendiumCompatibility() {
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
