package lampas2overrides.betterlib;

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Contract tests for the Better Lib demo villager suppression mixin.
 */
public class BetterLibMixinTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.betterlib.mixins.json";
	private static final String MIXIN_CLASS_RESOURCE =
		"lampas2overrides/betterlib/mixin/CommonClassMixin.class";

	@Test
	void mixinConfigExists() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
		}
	}

	@Test
	void mixinConfigReferencesPlugin() throws IOException {
		String json = readMixinConfig();
		assertTrue(json.contains("lampas2overrides.betterlib.BetterLibMixinPlugin"),
			"config must reference BetterLibMixinPlugin");
	}

	@Test
	void mixinConfigReferencesMixin() throws IOException {
		String json = readMixinConfig();
		assertTrue(json.contains("CommonClassMixin"),
			"config must reference CommonClassMixin");
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
			"config must set defaultRequire: 1");
	}

	@Test
	void mixinTargetsExpectedCallSite() throws IOException {
		MixinClassInfo info = readMixinClass();

		assertEquals(
			"com.reggarf.mods.better_lib.CommonClass",
			info.mixinTarget,
			"@Mixin must target com.reggarf.mods.better_lib.CommonClass"
		);
		assertNotNull(info.injectMethod,
			"handler must carry @Inject; was it renamed or removed?");
		assertEquals(
			"lampas2$suppressDemoVillagers",
			info.injectMethod,
			"@Inject handler method name must be lampas2$suppressDemoVillagers"
		);
		assertTrue(
			info.injectTargetMethods.contains("registerJsonVillagers"),
			"@Inject method value must include registerJsonVillagers; got: " + info.injectTargetMethods
		);
		assertEquals("HEAD", info.atValue, "@At value must be HEAD");
		assertTrue(info.cancellable, "@Inject must declare cancellable = true");
	}

	@Test
	void handlerCancelsRegistration() throws Exception {
		Class<?> mixinClass = Class.forName(
			"lampas2overrides.betterlib.mixin.CommonClassMixin");
		Method handler = mixinClass.getDeclaredMethod(
			"lampas2$suppressDemoVillagers", CallbackInfo.class);
		handler.setAccessible(true);

		CallbackInfo ci = new CallbackInfo("registerJsonVillagers", true);
		handler.invoke(null, ci);

		assertTrue(ci.isCancelled(), "handler must cancel CallbackInfo to abort villager registration");
	}

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
		String injectMethod;
		List<String> injectTargetMethods = new ArrayList<>();
		String atValue;
		boolean cancellable;
	}

	private static final class MixinClassVisitor extends ClassVisitor {
		private final MixinClassInfo info;

		MixinClassVisitor(MixinClassInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
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
					if (annDesc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
						info.injectMethod = name;
						return new AnnotationVisitor(Opcodes.ASM9) {
							@Override
							public void visit(String n, Object value) {
								if ("cancellable".equals(n) && value instanceof Boolean b) {
									info.cancellable = b;
								}
							}

							@Override
							public AnnotationVisitor visitArray(String annName) {
								if ("method".equals(annName)) {
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public void visit(String n, Object value) {
											info.injectTargetMethods.add((String) value);
										}
									};
								}
								if ("at".equals(annName)) {
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public AnnotationVisitor visitAnnotation(String name, String descriptor) {
											return new AnnotationVisitor(Opcodes.ASM9) {
												@Override
												public void visit(String n, Object value) {
													if ("value".equals(n)) {
														info.atValue = (String) value;
													}
												}
											};
										}
									};
								}
								return super.visitArray(annName);
							}
						};
					}
					return super.visitAnnotation(annDesc, visible);
				}
			};
		}
	}

	private static String readMixinConfig() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static InputStream resource(String name) {
		return BetterLibMixinTest.class.getClassLoader().getResourceAsStream(name);
	}
}
