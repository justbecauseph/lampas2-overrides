package lampas2overrides.customname;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Contract tests for the Custom Name 0.4.4-26.2 player-name space fix.
 *
 * <p>Config-document tests verify the mixin JSON is present and well-formed.
 * Structural tests use ASM to read the <em>compiled</em> class file — bypassing
 * the {@code RetentionPolicy.CLASS} limitation that makes Mixin annotations
 * invisible to {@code Class#getAnnotation()} at runtime.
 * The handler invocation test uses standard reflection, which works for the
 * handler body regardless of annotation retention.
 *
 * <p>There is no silent-skip path in any of these tests.
 *
 * <p>Live regression tests are documented in AGENTS.md (restriction matrix).
 */
public class CustomNameSpacesMixinTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.customname.mixins.json";
	private static final String MIXIN_CLASS_RESOURCE =
		"lampas2overrides/customname/mixin/CustomNameUtilMixin.class";

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
		assertEquals("0.4.4-26.2", CustomNameMixinPlugin.AFFECTED_VERSION,
			"plugin must gate on exactly 0.4.4-26.2; update only after inspecting the new jar");
	}

	// ── Compiled mixin structural contract (ASM) ──────────────────────────────

	/**
	 * Reads the compiled mixin class with ASM to inspect its {@code @Mixin} target,
	 * {@code @ModifyArg} method/index, and {@code @At} descriptor — all of which carry
	 * {@code RetentionPolicy.CLASS} and are therefore invisible to
	 * {@code Class#getAnnotation()} at runtime.
	 *
	 * <p>If someone shifts the argument index, changes the descriptor, or swaps the
	 * {@code @ModifyArg} for a {@code @Redirect}, this test fails immediately.
	 */
	@Test
	void mixinTargetsExpectedCallSite() throws IOException {
		MixinClassInfo info = readMixinClass();

		assertEquals(
			"xyz.eclipseisoffline.eclipsescustomname.CustomNameUtil",
			info.mixinTarget,
			"@Mixin must target CustomNameUtil"
		);
		assertNotNull(info.modifyArgMethod,
			"handler must carry @ModifyArg; was it renamed or replaced with @Redirect?");
		assertEquals(
			"lampas2$allowSpacesInPlayerNames",
			info.modifyArgMethod,
			"@ModifyArg handler method name must be lampas2$allowSpacesInPlayerNames"
		);
		assertTrue(
			info.modifyArgMethodDescriptors.contains(
				"playerNameArgumentToComponent(Ljava/lang/String;Z)"
					+ "Lnet/minecraft/network/chat/Component;"),
			"@ModifyArg method value must be playerNameArgumentToComponent with the two-argument descriptor; got: "
				+ info.modifyArgMethodDescriptors
		);
		assertEquals(2, info.modifyArgIndex,
			"@ModifyArg must modify argument index 2 (spaceAllowed)");
		assertEquals("INVOKE", info.atValue, "@At value must be INVOKE");
		assertEquals(
			"Lxyz/eclipseisoffline/eclipsescustomname/CustomNameUtil;"
				+ "nameArgumentToComponent(Ljava/lang/String;ZZZ)"
				+ "Lnet/minecraft/network/chat/Component;",
			info.atTarget,
			"@At target must be nameArgumentToComponent with the four-argument descriptor"
		);
	}

	/**
	 * Invokes the handler directly via reflection to confirm it always returns {@code true},
	 * regardless of the original argument value. The patch unconditionally allows spaces;
	 * it must never be simplified to {@code return original}.
	 */
	@Test
	void alwaysAllowsSpaces() throws Exception {
		Class<?> mixinClass = Class.forName(
			"lampas2overrides.customname.mixin.CustomNameUtilMixin");
		Method handler = mixinClass.getDeclaredMethod(
			"lampas2$allowSpacesInPlayerNames", boolean.class);
		handler.setAccessible(true);

		assertEquals(Boolean.TRUE, handler.invoke(null, false),
			"handler must return true when original is false (the bug case: restrictions not bypassed)");
		assertEquals(Boolean.TRUE, handler.invoke(null, true),
			"handler must return true when original is true (no regression when bypass is active)");
	}

	// ── ASM reader ────────────────────────────────────────────────────────────

	private static MixinClassInfo readMixinClass() throws IOException {
		try (InputStream input = resource(MIXIN_CLASS_RESOURCE)) {
			assertNotNull(input, MIXIN_CLASS_RESOURCE + " must exist on the test classpath");
			MixinClassInfo info = new MixinClassInfo();
			new ClassReader(input).accept(new MixinClassVisitor(info), 0);
			return info;
		}
	}

	private static final class MixinClassInfo {
		String mixinTarget;
		String modifyArgMethod;
		List<String> modifyArgMethodDescriptors = new ArrayList<>();
		int modifyArgIndex = -1;
		String atValue;
		String atTarget;
	}

	private static final class MixinClassVisitor extends ClassVisitor {
		private final MixinClassInfo info;

		MixinClassVisitor(MixinClassInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			// @Mixin is RetentionPolicy.CLASS → visible=false
			if (descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitArray(String name) {
						if ("targets".equals(name)) {
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public void visit(String name, Object value) {
									info.mixinTarget = (String) value;
								}
							};
						}
						return super.visitArray(name);
					}
				};
			}
			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
					if (annDesc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")) {
						info.modifyArgMethod = name;
						return new AnnotationVisitor(Opcodes.ASM9) {
							@Override
							public AnnotationVisitor visitArray(String annName) {
								if ("method".equals(annName)) {
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public void visit(String n, Object value) {
											info.modifyArgMethodDescriptors.add((String) value);
										}
									};
								}
								return super.visitArray(annName);
							}

							@Override
							public void visit(String annName, Object value) {
								if ("index".equals(annName)) {
									info.modifyArgIndex = (int) value;
								}
							}

							@Override
							public AnnotationVisitor visitAnnotation(String annName, String annDesc2) {
								if ("at".equals(annName)) {
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public void visit(String n, Object value) {
											if ("value".equals(n)) info.atValue = (String) value;
											if ("target".equals(n)) info.atTarget = (String) value;
										}
									};
								}
								return super.visitAnnotation(annName, annDesc2);
							}
						};
					}
					return super.visitAnnotation(annDesc, visible);
				}
			};
		}
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
}
