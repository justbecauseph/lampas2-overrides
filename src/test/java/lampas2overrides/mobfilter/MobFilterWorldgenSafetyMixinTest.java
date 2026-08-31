package lampas2overrides.mobfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.minecraft.resources.Identifier;

/** Structural, bytecode contract, and concurrency tests for the Mob Filter worldgen fixes. */
public final class MobFilterWorldgenSafetyMixinTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.mobfilter.mixins.json";
	private static final String MIXIN_SERVICE_CLASS_RESOURCE =
		"lampas2overrides/mobfilter/mixin/MixinServiceMixin.class";
	private static final String SPAWN_ATTEMPT_MIXIN_CLASS_RESOURCE =
		"lampas2overrides/mobfilter/mixin/WorldgenThreadSpawnAttemptMixin.class";
	private static final String UPSTREAM_SERVICE_CLASS_RESOURCE =
		"net/pcal/mobfilter/MixinService.class";
	private static final String UPSTREAM_WORLDGEN_ATTEMPT_CLASS_RESOURCE =
		"net/pcal/mobfilter/SpawnAttempt$WorldgenThreadSpawnAttempt.class";
	private static final String UPSTREAM_DIMENSION_CHECK_CLASS_RESOURCE =
		"net/pcal/mobfilter/RuleCheck$DimensionCheck.class";
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
		assertTrue(json.contains("WorldgenThreadSpawnAttemptMixin"));
		assertTrue(json.contains("\"compatibilityLevel\": \"JAVA_25\""));
		assertTrue(json.contains("\"defaultRequire\": 1"));
	}

	@Test
	void pluginGatesOnExactAffectedVersion() {
		assertEquals("0.28.0+26.2", MobFilterMixinPlugin.AFFECTED_VERSION);
	}

	@Test
	void mixinServiceTargetsExpectedCallSites() throws IOException {
		MixinServiceInfo info = readMixinServiceClass();

		assertEquals("net.pcal.mobfilter.MixinService", info.mixinTarget);
		assertEquals("lampas2$skipWorldgenDiscard", info.redirectMethod);
		assertEquals(List.of("WorldGenRegion_addFreshEntity"), info.redirectMethods);
		assertEquals("INVOKE", info.redirectAtValue);
		assertEquals(
			"Lnet/minecraft/world/entity/Entity;remove"
				+ "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
			info.redirectAtTarget);
		assertEquals(1, info.redirectRequire);
		assertEquals(
			"(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
			info.redirectDescriptor);
		assertFalse(info.redirectStatic,
			"redirect handler must be non-static because the target method is an instance method");
		assertEquals(List.of(Opcodes.RETURN), info.redirectInstructions);

		assertEquals("lampas2$scopeWorldgenDimension", info.wrapMethod);
		assertEquals(List.of("WorldGenRegion_addFreshEntity"), info.wrapMethods);
		assertEquals(
			"(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/entity/Entity;"
				+ "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"
				+ "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
			info.wrapDescriptor);
		assertFalse(info.wrapStatic,
			"wrap handler must be non-static because the target method is an instance method");
	}

	@Test
	void worldgenSpawnAttemptMixinTargetsExpectedCallSite() throws IOException {
		SpawnAttemptMixinInfo info = readSpawnAttemptMixinClass();

		assertEquals("net.pcal.mobfilter.SpawnAttempt$WorldgenThreadSpawnAttempt", info.mixinTarget);
		assertEquals("lampas2$provideWorldgenDimension", info.injectMethod);
		assertEquals(List.of("getDimensionId"), info.injectMethods);
		assertEquals("HEAD", info.injectAtValue);
		assertTrue(info.injectCancellable, "inject handler must be cancellable to return dimension");
		assertEquals(1, info.injectRequire);
		assertEquals(
			"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",
			info.injectDescriptor);
		assertFalse(info.injectStatic,
			"inject handler must be non-static because getDimensionId is an instance method");
	}

	@Test
	void pinnedMobFilterWorldgenSpawnAttemptReturnsNullDimension() throws IOException {
		try (InputStream input = resource(UPSTREAM_WORLDGEN_ATTEMPT_CLASS_RESOURCE)) {
			assertNotNull(input,
				UPSTREAM_WORLDGEN_ATTEMPT_CLASS_RESOURCE + " must be supplied by the pinned Mob Filter artifact");
			WorldgenAttemptClassInfo info = new WorldgenAttemptClassInfo();
			new ClassReader(input).accept(new WorldgenAttemptClassVisitor(info), 0);

			assertTrue(info.getDimensionIdFound, "getDimensionId method must exist on WorldgenThreadSpawnAttempt");
			assertTrue(info.hasAconstNull, "getDimensionId must load null (aconst_null)");
			assertTrue(info.hasAreturn, "getDimensionId must return a reference (areturn)");
		}
	}

	@Test
	void pinnedMobFilterDimensionCheckTreatsNullAsMatch() throws IOException {
		try (InputStream input = resource(UPSTREAM_DIMENSION_CHECK_CLASS_RESOURCE)) {
			assertNotNull(input,
				UPSTREAM_DIMENSION_CHECK_CLASS_RESOURCE + " must be supplied by the pinned Mob Filter artifact");
			DimensionCheckClassInfo info = new DimensionCheckClassInfo();
			new ClassReader(input).accept(new DimensionCheckClassVisitor(info), 0);

			assertTrue(info.isMatchFound, "isMatch method must exist on DimensionCheck");
			assertTrue(info.callsGetDimensionId, "isMatch must call SpawnAttempt.getDimensionId");
			assertTrue(info.returnsTrueOnNull, "isMatch must contain iconst_1 return path for null dimensionId");
		}
	}

	@Test
	void pinnedMobFilterStillOwnsBothVetoCallbacks() throws IOException {
		try (InputStream input = resource(UPSTREAM_SERVICE_CLASS_RESOURCE)) {
			assertNotNull(input,
				UPSTREAM_SERVICE_CLASS_RESOURCE + " must be supplied by the pinned Mob Filter test artifact");
			UpstreamServiceClassInfo info = readUpstreamServiceClass(input);

			assertEquals(1, info.worldgenRemoveCalls);
			assertEquals(1, info.worldgenSetReturnValueCalls);
			assertFalse(info.worldgenMethodStatic,
				"WorldGenRegion_addFreshEntity must be an instance method");
			assertEquals(1, info.serverRemoveCalls);
			assertEquals(1, info.serverSetReturnValueCalls);
		}
	}

	@Test
	void worldgenDimensionContextPreservesAndRestoresStateAcrossNesting() {
		Identifier overworld = Identifier.fromNamespaceAndPath("minecraft", "overworld");
		Identifier aria = Identifier.fromNamespaceAndPath("lampas", "aria");

		assertNull(WorldgenDimensionContext.get(), "initial context must be null");

		WorldgenDimensionContext.set(overworld);
		assertEquals(overworld, WorldgenDimensionContext.get());

		Identifier prev = WorldgenDimensionContext.get();
		try {
			WorldgenDimensionContext.set(aria);
			assertEquals(aria, WorldgenDimensionContext.get());
		} finally {
			WorldgenDimensionContext.set(prev);
		}

		assertEquals(overworld, WorldgenDimensionContext.get(), "outer dimension must be restored");
		WorldgenDimensionContext.clear();
		assertNull(WorldgenDimensionContext.get(), "context must be null after clear");
	}

	@Test
	void worldgenDimensionContextRestoresPreviousStateOnException() {
		Identifier overworld = Identifier.fromNamespaceAndPath("minecraft", "overworld");
		Identifier aria = Identifier.fromNamespaceAndPath("lampas", "aria");

		WorldgenDimensionContext.set(overworld);
		assertEquals(overworld, WorldgenDimensionContext.get());

		try {
			Identifier prev = WorldgenDimensionContext.get();
			try {
				WorldgenDimensionContext.set(aria);
				assertEquals(aria, WorldgenDimensionContext.get());
				throw new IllegalStateException("simulated worldgen failure");
			} finally {
				WorldgenDimensionContext.set(prev);
			}
		} catch (IllegalStateException expected) {
			assertEquals("simulated worldgen failure", expected.getMessage());
		}

		assertEquals(overworld, WorldgenDimensionContext.get(), "outer dimension must be restored despite exception");
		WorldgenDimensionContext.clear();
		assertNull(WorldgenDimensionContext.get());
	}

	@Test
	void worldgenDimensionContextIsThreadLocalAndIsolatedAcrossWorkers() throws Exception {
		int threadCount = 4;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

		Identifier[] dimensions = {
			Identifier.fromNamespaceAndPath("lampas", "aria"),
			Identifier.fromNamespaceAndPath("minecraft", "overworld"),
			Identifier.fromNamespaceAndPath("minecraft", "the_nether"),
			Identifier.fromNamespaceAndPath("minecraft", "the_end")
		};

		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			final Identifier threadDim = dimensions[i];
			futures.add(executor.submit(() -> {
				try {
					startLatch.await();
					for (int iter = 0; iter < 100; iter++) {
						assertNull(WorldgenDimensionContext.get(), "thread context should initially be null");
						WorldgenDimensionContext.set(threadDim);
						assertEquals(threadDim, WorldgenDimensionContext.get());
						Thread.sleep(1);
						assertEquals(threadDim, WorldgenDimensionContext.get());
						WorldgenDimensionContext.clear();
						assertNull(WorldgenDimensionContext.get());
					}
				} catch (Throwable t) {
					failures.add(t);
				} finally {
					doneLatch.countDown();
				}
			}));
		}

		startLatch.countDown();
		assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "all worker threads must complete within timeout");
		executor.shutdown();

		for (Future<?> f : futures) {
			f.get();
		}
		assertTrue(failures.isEmpty(), "no worker thread should fail or encounter dimension leakage: " + failures);
	}

	private static MixinServiceInfo readMixinServiceClass() throws IOException {
		try (InputStream input = resource(MIXIN_SERVICE_CLASS_RESOURCE)) {
			assertNotNull(input, MIXIN_SERVICE_CLASS_RESOURCE + " must exist on the test classpath");
			MixinServiceInfo info = new MixinServiceInfo();
			new ClassReader(input).accept(new MixinServiceVisitor(info), 0);
			return info;
		}
	}

	private static SpawnAttemptMixinInfo readSpawnAttemptMixinClass() throws IOException {
		try (InputStream input = resource(SPAWN_ATTEMPT_MIXIN_CLASS_RESOURCE)) {
			assertNotNull(input, SPAWN_ATTEMPT_MIXIN_CLASS_RESOURCE + " must exist on the test classpath");
			SpawnAttemptMixinInfo info = new SpawnAttemptMixinInfo();
			new ClassReader(input).accept(new SpawnAttemptMixinVisitor(info), 0);
			return info;
		}
	}

	private static UpstreamServiceClassInfo readUpstreamServiceClass(InputStream input) throws IOException {
		UpstreamServiceClassInfo info = new UpstreamServiceClassInfo();
		new ClassReader(input).accept(new UpstreamServiceVisitor(info), 0);
		return info;
	}

	// ── MixinServiceMixin Info and Visitor ────────────────────────────────────

	private static final class MixinServiceInfo {
		String mixinTarget;
		String redirectMethod;
		String redirectDescriptor;
		String redirectAtValue;
		String redirectAtTarget;
		int redirectRequire = -1;
		boolean redirectStatic;
		final List<String> redirectMethods = new ArrayList<>();
		final List<Integer> redirectInstructions = new ArrayList<>();

		String wrapMethod;
		String wrapDescriptor;
		boolean wrapStatic;
		final List<String> wrapMethods = new ArrayList<>();
	}

	private static final class MixinServiceVisitor extends ClassVisitor {
		private final MixinServiceInfo info;

		MixinServiceVisitor(MixinServiceInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
				return new TargetAnnotationVisitor(target -> info.mixinTarget = target);
			}
			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			boolean isRedirectHandler = name.equals("lampas2$skipWorldgenDiscard");
			if (isRedirectHandler) {
				info.redirectDescriptor = descriptor;
				info.redirectStatic = (access & Opcodes.ACC_STATIC) != 0;
			}
			boolean isWrapHandler = name.equals("lampas2$scopeWorldgenDimension");
			if (isWrapHandler) {
				info.wrapDescriptor = descriptor;
				info.wrapStatic = (access & Opcodes.ACC_STATIC) != 0;
			}
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitInsn(int opcode) {
					if (isRedirectHandler) info.redirectInstructions.add(opcode);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
					if (annotationDescriptor.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) {
						info.redirectMethod = name;
						return new RedirectAnnotationVisitor(info);
					}
					if (annotationDescriptor.equals("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")) {
						info.wrapMethod = name;
						return new WrapMethodAnnotationVisitor(info);
					}
					return super.visitAnnotation(annotationDescriptor, visible);
				}
			};
		}
	}

	private static final class RedirectAnnotationVisitor extends AnnotationVisitor {
		private final MixinServiceInfo info;

		RedirectAnnotationVisitor(MixinServiceInfo info) {
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
			if (name.equals("method")) info.redirectMethods.add((String) value);
			if (name.equals("require")) info.redirectRequire = (Integer) value;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			if (name.equals("at")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String nestedName, Object value) {
						if (nestedName.equals("value")) info.redirectAtValue = (String) value;
						if (nestedName.equals("target")) info.redirectAtTarget = (String) value;
					}
				};
			}
			return super.visitAnnotation(name, descriptor);
		}
	}

	private static final class WrapMethodAnnotationVisitor extends AnnotationVisitor {
		private final MixinServiceInfo info;

		WrapMethodAnnotationVisitor(MixinServiceInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if (name.equals("method")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String ignored, Object value) {
						info.wrapMethods.add((String) value);
					}
				};
			}
			return super.visitArray(name);
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals("method")) info.wrapMethods.add((String) value);
		}
	}

	// ── WorldgenThreadSpawnAttemptMixin Info and Visitor ─────────────────────

	private static final class SpawnAttemptMixinInfo {
		String mixinTarget;
		String injectMethod;
		String injectDescriptor;
		String injectAtValue;
		boolean injectCancellable;
		int injectRequire = -1;
		boolean injectStatic;
		final List<String> injectMethods = new ArrayList<>();
	}

	private static final class SpawnAttemptMixinVisitor extends ClassVisitor {
		private final SpawnAttemptMixinInfo info;

		SpawnAttemptMixinVisitor(SpawnAttemptMixinInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
				return new TargetAnnotationVisitor(target -> info.mixinTarget = target);
			}
			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			boolean isInjectHandler = name.equals("lampas2$provideWorldgenDimension");
			if (isInjectHandler) {
				info.injectDescriptor = descriptor;
				info.injectStatic = (access & Opcodes.ACC_STATIC) != 0;
			}
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
					if (annotationDescriptor.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
						info.injectMethod = name;
						return new InjectAnnotationVisitor(info);
					}
					return super.visitAnnotation(annotationDescriptor, visible);
				}
			};
		}
	}

	private static final class InjectAnnotationVisitor extends AnnotationVisitor {
		private final SpawnAttemptMixinInfo info;

		InjectAnnotationVisitor(SpawnAttemptMixinInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if (name.equals("method")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String ignored, Object value) {
						info.injectMethods.add((String) value);
					}
				};
			}
			if (name.equals("at")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitAnnotation(String name, String descriptor) {
						return new AnnotationVisitor(Opcodes.ASM9) {
							@Override
							public void visit(String nestedName, Object value) {
								if (nestedName.equals("value")) info.injectAtValue = (String) value;
							}
						};
					}
				};
			}
			return super.visitArray(name);
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals("method")) info.injectMethods.add((String) value);
			if (name.equals("cancellable")) info.injectCancellable = (Boolean) value;
			if (name.equals("require")) info.injectRequire = (Integer) value;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			if (name.equals("at")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String nestedName, Object value) {
						if (nestedName.equals("value")) info.injectAtValue = (String) value;
					}
				};
			}
			return super.visitAnnotation(name, descriptor);
		}
	}

	// ── Upstream Bytecode Visitors ────────────────────────────────────────────

	private static final class WorldgenAttemptClassInfo {
		boolean getDimensionIdFound;
		boolean hasAconstNull;
		boolean hasAreturn;
	}

	private static final class WorldgenAttemptClassVisitor extends ClassVisitor {
		private final WorldgenAttemptClassInfo info;

		WorldgenAttemptClassVisitor(WorldgenAttemptClassInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			if (name.equals("getDimensionId")) {
				info.getDimensionIdFound = true;
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitInsn(int opcode) {
						if (opcode == Opcodes.ACONST_NULL) info.hasAconstNull = true;
						if (opcode == Opcodes.ARETURN) info.hasAreturn = true;
					}
				};
			}
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	private static final class DimensionCheckClassInfo {
		boolean isMatchFound;
		boolean callsGetDimensionId;
		boolean returnsTrueOnNull;
	}

	private static final class DimensionCheckClassVisitor extends ClassVisitor {
		private final DimensionCheckClassInfo info;

		DimensionCheckClassVisitor(DimensionCheckClassInfo info) {
			super(Opcodes.ASM9);
			this.info = info;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			if (name.equals("isMatch")) {
				info.isMatchFound = true;
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String methodName,
							String methodDescriptor, boolean isInterface) {
						if (owner.equals("net/pcal/mobfilter/SpawnAttempt") && methodName.equals("getDimensionId")) {
							info.callsGetDimensionId = true;
						}
					}

					@Override
					public void visitInsn(int opcode) {
						if (opcode == Opcodes.ICONST_1) {
							info.returnsTrueOnNull = true;
						}
					}
				};
			}
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	private static final class UpstreamServiceClassInfo {
		int worldgenRemoveCalls;
		int worldgenSetReturnValueCalls;
		boolean worldgenMethodStatic;
		int serverRemoveCalls;
		int serverSetReturnValueCalls;
	}

	private static final class UpstreamServiceVisitor extends ClassVisitor {
		private final UpstreamServiceClassInfo info;

		UpstreamServiceVisitor(UpstreamServiceClassInfo info) {
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

	private static final class TargetAnnotationVisitor extends AnnotationVisitor {
		private final java.util.function.Consumer<String> targetConsumer;

		TargetAnnotationVisitor(java.util.function.Consumer<String> targetConsumer) {
			super(Opcodes.ASM9);
			this.targetConsumer = targetConsumer;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			if (name.equals("targets")) {
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public void visit(String ignored, Object value) {
						targetConsumer.accept((String) value);
					}
				};
			}
			return super.visitArray(name);
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

