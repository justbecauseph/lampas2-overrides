package lampas2overrides.trinketsdatafix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class TrinketsDataFixMixinPluginTest {
	private static final String SHA = TrinketsDataFixMixinPlugin.CURRENT_CLASS_SHA256;
	// Class hash of the retired Trinkets 4.1.0+26.2 artifact, kept only as a negative literal.
	private static final String RETIRED_410_SHA = "089c59253b7b9ab9f7f0a54c1f0dc7912379ad5bfa335c4acfcfbb91e17914f9";

	@Test void acceptsCurrentArtifactOnlyWithItsOwnExactHash() {
		assertTrue(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.1+26.2",
			TrinketsDataFixMixinPlugin.CURRENT_CLASS_SHA256));
		assertEquals(TrinketsSchemaRepair.Profile.V4_1_1,
			TrinketsDataFixMixinPlugin.profileFor("26.2", "4.1.1+26.2",
				TrinketsDataFixMixinPlugin.CURRENT_CLASS_SHA256));
	}

	@Test void rejectsRetiredFourOneZeroArtifact() {
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.0+26.2", RETIRED_410_SHA));
		assertNull(TrinketsDataFixMixinPlugin.profileFor("26.2", "4.1.0+26.2", RETIRED_410_SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.0+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.1+26.2", RETIRED_410_SHA));
	}

	@Test void rejectsAbsentOrNullInputs() {
		assertFalse(TrinketsDataFixMixinPlugin.matches(null, "4.1.1+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", null, SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.1+26.2", null));
	}

	@Test void rejectsNearVersionsAndWrongHash() {
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.20", "4.1.1+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2-pre", "4.1.1+26.2", SHA));
		assertFalse(TrinketsDataFixMixinPlugin.matches("26.2", "4.1.1+26.2", "deadbeef"));
	}

	@Test void auditedConstantsMatchCompatibilityManifest() throws Exception {
		String manifest = new String(
			getClass().getResourceAsStream("/compatibility-targets.json").readAllBytes(),
			java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(manifest.contains("\"4.1.1+26.2\""));
		assertTrue(manifest.contains(TrinketsDataFixMixinPlugin.CURRENT_CLASS_SHA256));
	}
}
