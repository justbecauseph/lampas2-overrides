package lampas2overrides.datapack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ResourceFingerprintTest {

	@Test
	void fingerprintsResources(@TempDir Path temporaryDirectory) throws IOException {
		Path resource = temporaryDirectory.resolve("resource.mcfunction");
		Files.writeString(resource, "function incendium:test\n", StandardCharsets.UTF_8);

		assertEquals("79e8c6df7eaf2fe744d688608de570354677e9497e61c957dc2639e437fc3752",
				ResourceFingerprint.sha256(resource));
		assertTrue(ResourceFingerprint.matches(resource,
				"79e8c6df7eaf2fe744d688608de570354677e9497e61c957dc2639e437fc3752"));
		assertFalse(ResourceFingerprint.matches(resource,
				"0000000000000000000000000000000000000000000000000000000000000000"));
	}
}
