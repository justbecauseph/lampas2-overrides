package lampas2overrides.mobfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Structural and bytecode contract tests for the Mob Filter worldgen safety fix. */
public final class MobFilterWorldgenSafetyMixinTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.mobfilter.mixins.json";
	private static final String MIXIN_CLASS_RESOURCE =
		"lampas2overrides/mobfilter/mixin/MixinServiceMixin.class";
	private static final String UPSTREAM_CLASS_RESOURCE =
		"net/pcal/mobfilter/MixinService.class";
	private static final String REMOVE_DESCRIPTOR =
		"(Lnet/minecraft/world/entity/Entity$RemovalReason;)V";

	@Test
	void mixinConfigExists() throws IOException {
		try (InputStream input = resource(MIXIN_CONFIG)) {
			assertNotNull(input, MIXIN_CONFIG + " must exist on the classpath");
		}
	}

	@Test
	void mixinConfigDeclaresTheCommonHardContract() throws IOException {
		String json = readResource(MIXIN_CONFIG);
		assertTrue(json.contains("lampas2overrides.mobfilter.MobFilterMixinPlugin"));
		assertTrue(json.contains("MixinServiceMixin"));
		assertTrue(json.contains("\"compatibilityLevel\": \"JAVA_25\""));
		assertTrue(json.contains("\"defaultRequire\": 1"));
	}

	@Test
	void pluginGatesOnExactAffectedVersion() {
		assertEquals("0.28.0+26.2", MobFilterMixinPlugin.AFFECTED_VERSION);
	}

	@Test
	void mixinRedirectsOnlyTheWorldgenDiscardCall() throws IOException {
		MixinClassInfo info = readMixinClass();

		assertEquals("net.pcal.mobfilter.MixinService", info.mixinTarget);
		assertEquals("lampas2$skipWorldgenDiscard", info.redirectMethod);
		assertEquals(List.of("WorldGenRegion_addFreshEntity"), info.redirectMethods);
		assertEquals("INVOKE", info.atValue);
		assertEquals(
			"Lnet/minecraft/world/entity/Entity;remove"
				+ "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
			info.atTarget);
		assertEquals(1, info.require);
		assertEquals(
			"(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
			info.handlerDescriptor);
		assertFalse(info.handlerStatic,
			"redirect handler must be non-static because the target method is an instance method");
		assertEquals(List.of(Opcodes.RETURN), info.handlerInstructions);
	}

	@Test
	void pinnedMobFilterStillOwnsBothVetoCallbacks() throws IOException {
		try (InputStream input = resource(UPSTREAM_CLASS_RESOURCE)) {
			assertNotNull(input,
				UPSTREAM_CLASS_RESOURCE + " must be supplied by the pinned Mob Filter test artifact");
			UpstreamClassInfo info = readUpstreamClass(input);

			assertEquals(1, info.worldgenRemoveCalls);
			assertEquals(1, info.worldgenSetReturnValueCalls);
			assertFalse(info.worldgenMethodStatic,
				"WorldGenRegion_addFreshEntity must be an instance method");
			assertEquals(1, info.serverRemoveCalls);
			assertEquals(1, info.serverSetReturnValueCalls);
		}
	}

	private static MixinClassInfo readMixinClass() throws IOException {
		try (InputStream input = resource(MIXIN_CLASS_RESOURCE)) {
			assertNotNull(input, MIXIN_CLASS_RESOURCE + " must exist on the test classpath");
			MixinClassInfo info = new MixinClassInfo();
			new ClassReader(input).accept(new MixinClassVisitor(info), 0);
			return info;
		}
	}

	private static UpstreamClassInfo readUpstreamClass(InputStream input) throws IOException {
		UpstreamClassInfo info = new UpstreamClassInfo();
		new ClassReader(input).accept(new UpstreamClassVisitor(info), 0);
		return info;
	}

	private static final class MixinClassInfo {
		String mixinTarget;
		String redirectMethod;
		String handlerDescriptor;
		String atValue;
		String atTarget;
		int require = -1;
		boolean handlerStatic;
		final List<String> redirectMethods = new ArrayList<>();
		final List<Integer> handlerInstructions = new ArrayList<>();
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
						if (name.equals("targets")) {
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public void visit(String ignored, Object value) {
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
			boolean handler = name.equals("lampas2$skipWorldgenDiscard");
			if (handler) {
				info.handlerDescriptor = descriptor;
				info.handlerStatic = (access & Opcodes.ACC_STATIC) != 0;
			}
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitInsn(int opcode) {
					if (handler) info.handlerInstructions.add(opcode);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
					if (!annotationDescriptor.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) {
						return super.visitAnnotation(annotationDescriptor, visible);
					}
					info.redirectMethod = name;
					return new RedirectAnnotationVisitor(info);
				}
			};
		}
	}

	private static final class RedirectAnnotationVisitor extends AnnotationVisitor {
		private final MixinClassInfo info;

		RedirectAnnotationVisitor(MixinClassInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if (name.equals("method")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String ignored, Object value) {
						info.redirectMethods.add((String) value);
					}
				};
			}
			return super.visitArray(name);
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals("require")) info.require = (Integer) value;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			if (name.equals("at")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String nestedName, Object value) {
						if (nestedName.equals("value")) info.atValue = (String) value;
						if (nestedName.equals("target")) info.atTarget = (String) value;
					}
				};
			}
			return super.visitAnnotation(name, descriptor);
		}
	}

	private static final class UpstreamClassInfo {
		int worldgenRemoveCalls;
		int worldgenSetReturnValueCalls;
		boolean worldgenMethodStatic;
		int serverRemoveCalls;
		int serverSetReturnValueCalls;
	}

	private static final class UpstreamClassVisitor extends ClassVisitor {
		private final UpstreamClassInfo info;

		UpstreamClassVisitor(UpstreamClassInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			boolean worldgen = name.equals("WorldGenRegion_addFreshEntity");
			boolean server = name.equals("ServerLevel_addFreshEntity");
			if (worldgen) info.worldgenMethodStatic = (access & Opcodes.ACC_STATIC) != 0;
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName,
						String methodDescriptor, boolean isInterface) {
					if (owner.equals("net/minecraft/world/entity/Entity")
							&& methodName.equals("remove")
							&& methodDescriptor.equals(REMOVE_DESCRIPTOR)) {
						if (worldgen) info.worldgenRemoveCalls++;
						if (server) info.serverRemoveCalls++;
					}
					if (owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable")
							&& methodName.equals("setReturnValue")) {
						if (worldgen) info.worldgenSetReturnValueCalls++;
						if (server) info.serverSetReturnValueCalls++;
					}
				}
			};
		}
	}

	private static String readResource(String name) throws IOException {
		try (InputStream input = resource(name)) {
			assertNotNull(input, name + " must exist on the classpath");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static InputStream resource(String name) {
		return MobFilterWorldgenSafetyMixinTest.class.getClassLoader().getResourceAsStream(name);
	}
}
