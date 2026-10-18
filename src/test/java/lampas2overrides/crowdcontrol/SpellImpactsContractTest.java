package lampas2overrides.crowdcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Bytecode contract test, not a behaviour test. It reads {@code SpellImpacts} from the pinned Spell Engine jar on the test
 * runtime classpath with ASM and checks the call sites {@code SpellImpactsCrowdControlMixin} wraps: that each exists exactly once
 * in {@code performImpact}, in the order and context the mixin relies on. It also pins the inspected test jar and class bytes, so
 * a change to {@code spell_engine_version} has to be re-inspected. The plugin itself applies to any installed release; this is
 * the contract that was checked. The facts were first read with javap on the
 * dev-server jar (SHA-256 bfc25887…ab2a), the same bytes as Modrinth version QBsRwj9A. The jar is {@code testRuntimeOnly}.
 */
class SpellImpactsContractTest {

	private static final String SPELL_IMPACTS = "net/spell_engine/internals/impact/SpellImpacts";
	private static final String PERFORM_IMPACT_DESCRIPTOR = "(Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;"
			+ "Lnet/spell_engine/api/spell/Spell$Impact;Lnet/spell_engine/internals/SpellExecution$ImpactContext;"
			+ "Ljava/util/Collection;)Z";
	private static final String JAR_SHA256 = "bfc258875662071d76c448e603f05f0f519bf3e8a0c32207d232d820cab0ab2a";
	private static final String CLASS_SHA256 = "80478b3197388de7fed5ac852292dbb686ea2c090c8eb5bc384a925622170139";

	private static final String ENTITY_IS = "net/minecraft/world/entity/Entity.is(Lnet/minecraft/tags/TagKey;)Z";
	private static final String IS_MOVEMENT_IMPAIRING =
			"net/spell_engine/api/effect/StatusEffectClassification.isMovementImpairing(Lnet/minecraft/core/Holder;)Z";
	private static final String DISABLES_MOB_AI =
			"net/spell_engine/api/effect/StatusEffectClassification.disablesMobAI(Lnet/minecraft/core/Holder;)Z";
	private static final String GET_ATTRIBUTE_VALUE =
			"net/minecraft/world/entity/LivingEntity.getAttributeValue(Lnet/minecraft/core/Holder;)D";
	private static final String ADD_EFFECT = "net/minecraft/world/entity/LivingEntity.addEffect"
			+ "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z";
	private static final String PUSH = "net/minecraft/world/entity/Entity.push(DDD)V";

	private static byte[] classBytes;
	private static Path jar;
	private static List<AbstractInsnNode> code;

	@BeforeAll
	static void readSpellImpacts() throws Exception {
		try (InputStream input = SpellImpactsContractTest.class.getClassLoader().getResourceAsStream(SPELL_IMPACTS + ".class")) {
			assertNotNull(input, "SpellImpacts is not on the test classpath");
			classBytes = input.readAllBytes();
		}
		// jar:file:/…/spell_engine-fabric-1.10.9+26.2.jar!/net/…/SpellImpacts.class, read without loading the class.
		URL resource = SpellImpactsContractTest.class.getClassLoader().getResource(SPELL_IMPACTS + ".class");
		JarURLConnection connection = (JarURLConnection) resource.openConnection();
		jar = Path.of(connection.getJarFileURL().toURI());

		ClassNode node = new ClassNode();
		new ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		List<MethodNode> performImpact = node.methods.stream()
				.filter(method -> method.name.equals("performImpact"))
				.toList();
		assertEquals(1, performImpact.size(), "exactly one performImpact overload");
		MethodNode method = performImpact.get(0);
		assertEquals(PERFORM_IMPACT_DESCRIPTOR, method.desc, "performImpact descriptor the mixin selects");
		assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC),
				"performImpact must be private static, as the mixin handlers are");
		code = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn.getOpcode() >= 0) {
				code.add(insn);
			}
		}
	}

	@Test
	void jarAndClassBytesAreTheInspectedOnes() throws Exception {
		assertEquals(JAR_SHA256, sha256(Files.readAllBytes(jar)), "Spell Engine jar on the test classpath");
		assertEquals(CLASS_SHA256, sha256(classBytes), "SpellImpacts.class bytes");
	}

	@Test
	void bossGateTestsTheSpellEngineBossTagOnTheTargetThenRefusesCrowdControl() {
		int is = onlyCall(ENTITY_IS);
		assertTrue(code.get(is - 2) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 2,
				"the boss gate must test the target argument (local 2)");
		assertTrue(code.get(is - 1) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
						&& field.owner.equals("net/spell_engine/api/tags/SpellEngineEntityTags") && field.name.equals("bosses"),
				"Entity.is must read SpellEngineEntityTags.bosses");

		int impairing = onlyCall(IS_MOVEMENT_IMPAIRING);
		int disablesAi = onlyCall(DISABLES_MOB_AI);
		assertTrue(is < impairing && impairing < disablesAi, "boss tag, then isMovementImpairing, then disablesMobAI");
		assertEquals(Opcodes.ICONST_0, code.get(disablesAi + 2).getOpcode(), "the gate refuses with false");
		assertEquals(Opcodes.IRETURN, code.get(disablesAi + 3).getOpcode(), "the gate returns");
		assertTrue(onlyCall(ADD_EFFECT) > disablesAi, "addEffect comes after the gate");
	}

	@Test
	void harmfulVelocityReadsKnockbackResistanceOnceBeforeThePush() {
		int read = onlyCall(GET_ATTRIBUTE_VALUE);
		assertTrue(code.get(read - 1) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
						&& field.owner.equals("net/minecraft/world/entity/ai/attributes/Attributes")
						&& field.name.equals("KNOCKBACK_RESISTANCE"),
				"getAttributeValue must read Attributes.KNOCKBACK_RESISTANCE");
		assertTrue(onlyCall(PUSH) > read, "the push comes after the knockback resistance read");
	}

	/** The index of the only call to {@code ownerAndMethod} in performImpact. */
	private static int onlyCall(String ownerAndMethod) {
		int found = -1;
		int count = 0;
		for (int i = 0; i < code.size(); i++) {
			if (code.get(i) instanceof MethodInsnNode call && (call.owner + "." + call.name + call.desc).equals(ownerAndMethod)) {
				found = i;
				count++;
			}
		}
		assertEquals(1, count, "calls to " + ownerAndMethod + " in performImpact");
		return found;
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
