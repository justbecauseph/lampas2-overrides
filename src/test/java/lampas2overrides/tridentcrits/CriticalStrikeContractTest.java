package lampas2overrides.tridentcrits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bytecode contract test, not a behaviour test. It reads, with ASM and without loading them, the Critical Strike members
 * {@link TridentCriticalStrikes} resolves by name from the pinned Critical Strike jar on the test runtime classpath (Tiny Config's
 * {@code ConfigManager} from the jar it nests), and the vanilla 26.2 trident code the mixins rely on. It also pins the inspected
 * jar, so a change to {@code critical_strike_version} has to be re-inspected. The plugin itself applies to any installed
 * release; this is the contract that was checked. The facts were first read with javap on the dev-server jar
 * (SHA-256 69872634…1234), the same bytes as Modrinth version eozhJvBr. The jar is {@code testRuntimeOnly}.
 */
class CriticalStrikeContractTest {

	private static final String JAR_SHA256 = "69872634bcd03e8cb0c8d39d5371a83693b9444a5305f63be972f63de3791234";
	private static final String TINY_CONFIG_JAR = "META-INF/jars/tiny-config-4.0.1-fabric.jar";

	@Test
	void jarIsTheInspectedOne() throws Exception {
		assertEquals(JAR_SHA256, sha256(Files.readAllBytes(criticalStrikeJar())), "Critical Strike jar on the test classpath");
	}

	@Test
	void configIsReachableThroughPublicFields() throws Exception {
		ClassNode mod = read(TridentCriticalStrikes.MOD_CLASS);
		assertField(mod, "config", "Lnet/tiny_config/ConfigManager;", true);

		ClassNode manager = readNested(TridentCriticalStrikes.CONFIG_MANAGER_CLASS);
		assertField(manager, "value", "Ljava/lang/Object;", false);

		ClassNode config = read(TridentCriticalStrikes.CONFIG_CLASS);
		assertField(config, "enable_ranged_criticals", "Z", false);
		assertField(config, "balance_ranged_damage_multiplier", "F", false);
		assertField(config, "sound_ranged_crit_volume", "F", false);
	}

	@Test
	void critMembersHaveTheResolvedShapes() throws Exception {
		ClassNode striker = read(TridentCriticalStrikes.STRIKER_CLASS);
		assertTrue((striker.access & Opcodes.ACC_INTERFACE) != 0, "CriticalStriker is an interface");
		assertMethod(striker, "rng_criticalChance", "()D", false);
		assertMethod(striker, "rng_criticalDamageMultiplier", "()D", false);

		ClassNode damageSource = read(TridentCriticalStrikes.DAMAGE_SOURCE_CLASS);
		assertMethod(damageSource, "rng_setCriticalDamageMultiplier", "(F)V", false);

		ClassNode critLogic = read(TridentCriticalStrikes.CRIT_LOGIC_CLASS);
		assertMethod(critLogic, "playFxAt", "(Lnet/minecraft/world/entity/Entity;F)V", true);
	}

	@Test
	void thrownTridentHurtsOnceWithoutCallingTheArrowHitCriticalStrikeWraps() throws Exception {
		ClassNode trident = read("net.minecraft.world.entity.projectile.arrow.ThrownTrident");
		MethodNode onHitEntity = method(trident, "onHitEntity", "(Lnet/minecraft/world/phys/EntityHitResult;)V");
		int hurts = 0;
		for (AbstractInsnNode insn : onHitEntity.instructions) {
			if (insn instanceof MethodInsnNode call) {
				if (call.name.equals("hurtOrSimulate")) {
					assertEquals("net/minecraft/world/entity/Entity", call.owner, "hurtOrSimulate owner the wrap targets");
					assertEquals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z", call.desc, "hurtOrSimulate descriptor");
					hurts++;
				}
				assertTrue(!(call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("onHitEntity")),
						"ThrownTrident.onHitEntity calls super.onHitEntity, where Critical Strike already rolls crits");
			}
		}
		assertEquals(1, hurts, "hurtOrSimulate calls in ThrownTrident.onHitEntity");

		method(trident, "<init>",
				"(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V");
		ClassNode tridentItem = read("net.minecraft.world.item.TridentItem");
		method(tridentItem, "releaseUsing",
				"(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z");
	}

	private static void assertField(ClassNode owner, String name, String descriptor, boolean isStatic) {
		List<FieldNode> found = owner.fields.stream().filter(field -> field.name.equals(name)).toList();
		assertEquals(1, found.size(), owner.name + "." + name);
		FieldNode field = found.get(0);
		assertEquals(descriptor, field.desc, owner.name + "." + name + " type");
		assertTrue((field.access & Opcodes.ACC_PUBLIC) != 0, owner.name + "." + name + " is public");
		assertEquals(isStatic, (field.access & Opcodes.ACC_STATIC) != 0, owner.name + "." + name + " static");
	}

	private static void assertMethod(ClassNode owner, String name, String descriptor, boolean isStatic) {
		MethodNode method = method(owner, name, descriptor);
		assertTrue((method.access & Opcodes.ACC_PUBLIC) != 0, owner.name + "." + name + " is public");
		assertEquals(isStatic, (method.access & Opcodes.ACC_STATIC) != 0, owner.name + "." + name + " static");
	}

	private static MethodNode method(ClassNode owner, String name, String descriptor) {
		List<MethodNode> found = owner.methods.stream()
				.filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
				.toList();
		assertEquals(1, found.size(), owner.name + "." + name + descriptor);
		return found.get(0);
	}

	private static ClassNode read(String className) throws Exception {
		try (InputStream input = CriticalStrikeContractTest.class.getClassLoader().getResourceAsStream(resource(className))) {
			assertNotNull(input, className + " is not on the test classpath");
			return node(input.readAllBytes());
		}
	}

	/** Reads a class from the Tiny Config jar Critical Strike nests, which is not on the plain test classpath. */
	private static ClassNode readNested(String className) throws Exception {
		try (ZipFile outer = new ZipFile(criticalStrikeJar().toFile())) {
			ZipEntry nested = outer.getEntry(TINY_CONFIG_JAR);
			assertNotNull(nested, TINY_CONFIG_JAR + " is not nested in the Critical Strike jar");
			try (ZipInputStream inner = new ZipInputStream(new ByteArrayInputStream(outer.getInputStream(nested).readAllBytes()))) {
				for (ZipEntry entry = inner.getNextEntry(); entry != null; entry = inner.getNextEntry()) {
					if (entry.getName().equals(resource(className))) {
						return node(inner.readAllBytes());
					}
				}
			}
		}
		throw new AssertionError(className + " is not in " + TINY_CONFIG_JAR);
	}

	private static Path criticalStrikeJar() throws Exception {
		URL url = CriticalStrikeContractTest.class.getClassLoader().getResource(resource(TridentCriticalStrikes.MOD_CLASS));
		assertNotNull(url, "Critical Strike is not on the test classpath");
		return Path.of(((JarURLConnection) url.openConnection()).getJarFileURL().toURI());
	}

	private static String resource(String className) {
		return className.replace('.', '/') + ".class";
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return node;
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
