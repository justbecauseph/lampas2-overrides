package lampas2overrides.trinketsdatafix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class TrinketsDataFixMixinPluginTest {
	private static final String SHA = TrinketsDataFixMixinPlugin.EXPECTED_CLASS_SHA256;

	@Test void acceptsAuditedVersionsAndHash() {
		assertTrue(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.0+26.2", SHA));
	}

	@Test void rejectsAbsentOrNullInputs() {
		assertFalse(TrinketsDataFixMixinPlugin.matches(null, "4.1.0+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", null, SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.0+26.2", null));
	}

	@Test void rejectsNearVersionsAndWrongHash() {
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.20", "4.1.0+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2-pre", "4.1.0+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.1+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.0+26.2", "deadbeef"));
	}

	@Test void auditedConstantsMatchCompatibilityManifest() throws Exception {
		String manifest = new String(
			getClass().getResourceAsStream("/compatibility-targets.json").readAllBytes(),
			java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(manifest.contains("\"4.1.0+26.2\""));
		assertTrue(manifest.contains(TrinketsDataFixMixinPlugin.EXPECTED_CLASS_SHA256));
	}
}
