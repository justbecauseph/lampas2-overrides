package lampas2overrides.illagerblabber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads the exact IllagerBlabber 1.1.0 classes from the test runtime classpath and checks every member
 * the takeover binds by name, descriptor, access, and call-site count.
 */
class IllagerBlabberVendorContractTest {

	private static final String REGISTRY = "com/leclowndu93150/illagerblabber/stuff/voice/IllagerVoiceRegistry.class";
	private static final String MANAGER = "com/leclowndu93150/illagerblabber/stuff/voice/IllagerVoiceManager.class";
	private static final String RAIDER = "com/leclowndu93150/illagerblabber/mixin/RaiderMixin.class";
	private static final String REGISTRY_OWNER = "com/leclowndu93150/illagerblabber/stuff/voice/IllagerVoiceRegistry";
	private static final String CONCURRENT_MAP = "Ljava/util/concurrent/ConcurrentHashMap;";
	private static final String ABSTRACT_ILLAGER = "Lnet/minecraft/world/entity/monster/illager/AbstractIllager;";
	private static final String ILLAGER_TYPE = "Lcom/leclowndu93150/illagerblabber/stuff/voice/IllagerType;";
	private static final String UPDATE_ILLAGER_DESC = "(" + ABSTRACT_ILLAGER + ILLAGER_TYPE + ")V";
	private static final String UPDATE_STATE_DESC = "(" + ABSTRACT_ILLAGER + ")V";
	private static final String CONSTRUCTOR_DESC = "(" + ABSTRACT_ILLAGER + ILLAGER_TYPE + ")V";
	private static final String SLF4J_LOGGER = "org/slf4j/Logger";

	private static final List<String> PER_ENTITY_MAPS = List.of(
		"voiceManagers", "hadTargetLastTick", "victoryTimers", "combatDebounceTimers",
		"lastProcessedTick", "lastPillagerTargets", "lastVindicatorTargets", "lastEvokerTargets");

	@Test
	void registryDeclaresEveryPerUuidMapTheCleanupBinds() throws IOException {
		ClassFacts registry = facts(REGISTRY);
		for (String name : PER_ENTITY_MAPS) {
			FieldFact field = registry.fields.get(name);
			assertNotNull(field, "missing vendor map " + name);
			assertEquals(CONCURRENT_MAP, field.descriptor, name + " descriptor");
			assertTrue((field.access & Opcodes.ACC_STATIC) != 0, name + " must be static");
			assertTrue((field.access & Opcodes.ACC_PRIVATE) != 0, name + " must be private");
		}
		FieldFact groupCooldowns = registry.fields.get("lastGroupSpottedSoundTime");
		assertNotNull(groupCooldowns, "missing vendor map lastGroupSpottedSoundTime");
		assertEquals(CONCURRENT_MAP, groupCooldowns.descriptor);
		assertTrue((groupCooldowns.access & Opcodes.ACC_STATIC) != 0);
	}

	@Test
	void updateIllagerIsPublicStaticAndUpdateStateIsPrivateStatic() throws IOException {
		ClassFacts registry = facts(REGISTRY);

		MethodFact update = registry.method("updateIllager", UPDATE_ILLAGER_DESC);
		assertTrue((update.access & Opcodes.ACC_PUBLIC) != 0 && (update.access & Opcodes.ACC_STATIC) != 0,
			"the HEAD inject targets a public static method");

		MethodFact state = registry.method("updateIllagerState", UPDATE_STATE_DESC);
		assertTrue((state.access & Opcodes.ACC_PRIVATE) != 0 && (state.access & Opcodes.ACC_STATIC) != 0,
			"the shadow requires a private static method");
	}

	@Test
	void updateIllagerStateHasExactlyOneInfoCallForTheRedirect() throws IOException {
		MethodFact state = facts(REGISTRY).method("updateIllagerState", UPDATE_STATE_DESC);
		assertEquals(1, state.count(SLF4J_LOGGER, "info", "(Ljava/lang/String;)V"),
			"the redirect must match exactly one Logger.info(String) call site");
	}

	@Test
	void managerConstructorAndVictoryUpdateHaveOneInfoCallEach() throws IOException {
		ClassFacts manager = facts(MANAGER);
		assertEquals(1, manager.method("<init>", CONSTRUCTOR_DESC)
			.count(SLF4J_LOGGER, "info", "(Ljava/lang/String;[Ljava/lang/Object;)V"),
			"the constructor redirect must match exactly one Logger.info(String, Object[]) call site");
		assertEquals(1, manager.method("update", "()V")
			.count(SLF4J_LOGGER, "info", "(Ljava/lang/String;Ljava/lang/Object;)V"),
			"the update redirect must match exactly one Logger.info(String, Object) call site");
	}

	@Test
	void managerKeepsTheEntityFieldTheAccessorReads() throws IOException {
		ClassFacts manager = facts(MANAGER);
		FieldFact illager = manager.fields.get("illager");
		assertNotNull(illager, "the accessor binds the manager's illager field");
		assertEquals(ABSTRACT_ILLAGER, illager.descriptor);
		assertTrue((illager.access & Opcodes.ACC_PRIVATE) != 0 && (illager.access & Opcodes.ACC_FINAL) != 0);

		MethodFact constructor = manager.method("<init>", CONSTRUCTOR_DESC);
		assertTrue((constructor.access & Opcodes.ACC_PUBLIC) != 0);
		MethodFact update = manager.method("update", "()V");
		assertTrue((update.access & Opcodes.ACC_PUBLIC) != 0);
	}

	@Test
	void raiderTailIsTheRealPerTickCallerOfTheRegistryUpdate() throws IOException {
		int calls = 0;
		for (MethodFact method : facts(RAIDER).methods.values()) {
			calls += method.count(REGISTRY_OWNER, "updateIllager", UPDATE_ILLAGER_DESC);
		}
		assertTrue(calls > 0, "RaiderMixin must call updateIllager so the HEAD inject is on the live path");
	}

	@Test
	void pinnedJarOnTheTestClasspathMatchesTheProfileHash() throws Exception {
		Class<?> registry = Class.forName(IllagerBlabberProfile.REGISTRY_TARGET, false,
			IllagerBlabberVendorContractTest.class.getClassLoader());
		Path jar = Path.of(registry.getProtectionDomain().getCodeSource().getLocation().toURI());
		String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
		assertEquals(IllagerBlabberProfile.JAR_SHA256, digest, "vendor jar " + jar + " is not the audited artifact");
	}

	private static ClassFacts facts(String resource) throws IOException {
		try (InputStream input = IllagerBlabberVendorContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, "missing vendor class " + resource);
			ClassFacts facts = new ClassFacts();
			new ClassReader(input).accept(new FactsVisitor(facts), 0);
			return facts;
		}
	}

	private static final class ClassFacts {
		final Map<String, FieldFact> fields = new HashMap<>();
		final Map<String, MethodFact> methods = new HashMap<>();

		MethodFact method(String name, String descriptor) {
			MethodFact method = methods.get(name + descriptor);
			assertNotNull(method, "missing vendor method " + name + descriptor);
			return method;
		}
	}

	private static final class FieldFact {
		final int access;
		final String descriptor;

		FieldFact(int access, String descriptor) {
			this.access = access;
			this.descriptor = descriptor;
		}
	}

	private static final class MethodFact {
		final int access;
		final List<String> invocations = new ArrayList<>();

		MethodFact(int access) {
			this.access = access;
		}

		int count(String owner, String name, String descriptor) {
			String wanted = owner + " " + name + " " + descriptor;
			int total = 0;
			for (String invocation : invocations) {
				if (invocation.equals(wanted)) {
					total++;
				}
			}
			return total;
		}
	}

	private static final class FactsVisitor extends ClassVisitor {
		private final ClassFacts facts;

		FactsVisitor(ClassFacts facts) {
			super(Opcodes.ASM9);
			this.facts = facts;
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			facts.fields.put(name, new FieldFact(access, descriptor));
			return super.visitField(access, name, descriptor, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodFact method = new MethodFact(access);
			facts.methods.put(name + descriptor, method);
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
					method.invocations.add(owner + " " + methodName + " " + methodDescriptor);
				}
			};
		}
	}
}
