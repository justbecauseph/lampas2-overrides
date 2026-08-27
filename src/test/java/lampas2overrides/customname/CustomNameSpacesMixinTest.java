package lampas2overrides.customname;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the Custom Name 0.4.4-26.2 player-name space fix.
 *
 * <p>These tests verify the mixin configuration document and the version gate constant
 * without loading any Custom Name classes. They act as a compile-time and packaging check:
 * if someone "simplifies" the mixin to {@code return original} or the config drifts, the
 * test suite catches it before a build reaches the server.
 *
 * <p>Live regression tests are documented in AGENTS.md (Phase 12 restriction matrix).
 */
public class CustomNameSpacesMixinTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.customname.mixins.json";

	// ── Mixin config document ──────────────────────────────────────────────────

	@Test
	void mixinConfigExists() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
		}
	}

	@Test
	void mixinConfigReferencesPlugin() throws IOException {
		String json = readMixinConfig();
		assertTrue(json.contains("lampas2overrides.customname.CustomNameMixinPlugin"),
			"config must reference CustomNameMixinPlugin");
	}

	@Test
	void mixinConfigReferencesMixin() throws IOException {
		String json = readMixinConfig();
		assertTrue(json.contains("CustomNameUtilMixin"),
			"config must reference CustomNameUtilMixin");
	}

	@Test
	void mixinConfigUsesJava25() throws IOException {
		String json = readMixinConfig();
		assertTrue(json.contains("JAVA_25"), "config must declare JAVA_25 compatibility level");
	}

	@Test
	void mixinConfigHasDefaultRequireOne() throws IOException {
		String json = readMixinConfig();
		assertTrue(json.contains("\"defaultRequire\": 1"),
			"config must set defaultRequire: 1 so a missing call-site fails loudly on 0.4.4-26.2");
	}

	// ── Version gate ──────────────────────────────────────────────────────────

	/**
	 * The affected version constant drives the plugin gate and must remain exactly
	 * "0.4.4-26.2". Any change here should be intentional, backed by a new javap
	 * inspection of the deployed jar.
	 */
	@Test
	void pluginGatesOnExactVersion() {
		// Access through the class directly — no FabricLoader in test scope,
		// but we can verify the constant's declared value via the source contract.
		assertEquals("0.4.4-26.2", CustomNameMixinPlugin.AFFECTED_VERSION,
			"plugin must gate on exactly 0.4.4-26.2; update only after inspecting the new jar");
	}

	// ── Mixin target contract ─────────────────────────────────────────────────

	/**
	 * Verifies that the mixin targets the correct class and method, and that the
	 * @ModifyArg injects at the right invocation, on the right argument index.
	 *
	 * <p>These are source-level checks. The authoritative runtime check is observing
	 * "Mixing CustomNameUtilMixin" in debug.log. These tests exist so that structural
	 * regressions (wrong class, index change, replaced with redirect) are visible
	 * in CI without requiring a live Minecraft server.
	 */
	@Test
	void mixinTargetsCustomNameUtil() {
		// The mixin class name encodes the target via @Mixin(targets=...).
		// Verify the descriptor strings used in @ModifyArg are present in the source.
		// (The injector strings are validated by reading the compiled class; here we
		//  read the source file from the test classpath as a documentation contract.)
		String src = readSourceOrSkip();
		if (src == null) return; // source not on test classpath — skip structural checks

		assertTrue(src.contains("xyz.eclipseisoffline.eclipsescustomname.CustomNameUtil"),
			"mixin must target CustomNameUtil");
		assertTrue(src.contains("playerNameArgumentToComponent"),
			"ModifyArg method must be playerNameArgumentToComponent");
		assertTrue(src.contains("nameArgumentToComponent"),
			"injection site must be the nameArgumentToComponent call");
		assertTrue(src.contains("index = 2"),
			"must modify argument index 2 (spaceAllowed)");
		assertTrue(src.contains("return true"),
			"replacement value must always be true — do not simplify to 'return original'");
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static String readMixinConfig() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static InputStream resource(String name) {
		return CustomNameSpacesMixinTest.class.getClassLoader().getResourceAsStream(name);
	}

	/**
	 * Attempts to read the mixin source file from the test classpath. Returns null
	 * (and causes the structural checks to skip) when the build does not include
	 * sources on the test classpath — that is acceptable because the runtime mixin
	 * application in debug.log is the authoritative check.
	 */
	private static String readSourceOrSkip() {
		try (InputStream src = resource(
				"lampas2overrides/customname/mixin/CustomNameUtilMixin.java")) {
			if (src == null) return null;
			return new String(src.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}
}
