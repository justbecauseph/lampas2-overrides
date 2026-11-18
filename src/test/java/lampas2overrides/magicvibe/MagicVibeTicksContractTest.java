package lampas2overrides.magicvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks, against the Magic Vibe Decorations 3.0.1 jar the pack runs, what the {@code magicvibe} mixin relies on: the crystal
 * effects run only from the blocks' scheduled {@code tick}, the mod has no other periodic hook, and cancelling a scheduled
 * tick skips nothing but the mod's own code.
 */
class MagicVibeTicksContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.magicvibe.mixins.json";
	private static final String MOD_CLASS = "lillouarts/magicvibedecorations/MagicVibeDecorationsMod.class";
	private static final String BLOCK = "net/minecraft/world/level/block/Block";
	private static final String TICK_DESC =
		"(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
			+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V";
	/** Calls that act on entities near a crystal: the finding is that these appear only in {@code tick}. */
	private static final Set<String> ENTITY_EFFECTS = Set.of("getEntitiesOfClass", "discard", "hurt", "hurtServer",
		"setRemainingFireTicks", "igniteForSeconds", "addEffect", "kill", "remove");

	private static final List<ClassNode> MOD_CLASSES = new ArrayList<>();

	@BeforeAll
	static void readModJar() throws IOException {
		URL url = MagicVibeTicksContractTest.class.getClassLoader().getResource(MOD_CLASS);
		assertNotNull(url, "Magic Vibe Decorations must be on the test classpath");
		try (JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile()) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.getName().startsWith("lillouarts/") && entry.getName().endsWith(".class")) {
					try (InputStream input = jar.getInputStream(entry)) {
						ClassNode node = new ClassNode();
						new ClassReader(input).accept(node, 0);
						MOD_CLASSES.add(node);
					}
				}
			}
		}
		assertTrue(MOD_CLASSES.size() > 100, "read only " + MOD_CLASSES.size() + " Magic Vibe Decorations classes");
	}

	@Test
	void registersTheFeatureAsCommonAndRequired() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.magicvibe.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.magicvibe.MagicVibeTicksMixinPlugin", config.get("plugin").getAsString());
		assertEquals(1, config.getAsJsonArray("mixins").size());
		assertEquals("BlockStateTickMixin", config.getAsJsonArray("mixins").get(0).getAsString());
		assertFalse(config.has("client"));
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject metadata = readJson("fabric.mod.json");
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			if (MIXIN_CONFIG.equals(entry.getAsJsonObject().get("config").getAsString())) {
				registered = entry.getAsJsonObject();
			}
		}
		assertNotNull(registered);
		assertFalse(registered.has("environment"), "the tick cancel must also run on a dedicated server");
		assertFalse(metadata.getAsJsonObject("depends").has(MagicVibeTicks.MOD_ID));
	}

	/** Cancelling {@code BlockStateBase.tick} for a block that inherits vanilla's tick must skip nothing. */
	@Test
	void vanillaBlockTickIsEmpty() throws IOException {
		ClassNode behaviour = readClass("net/minecraft/world/level/block/state/BlockBehaviour.class");
		MethodNode tick = method(behaviour, "tick", TICK_DESC);
		List<Integer> opcodes = new ArrayList<>();
		for (AbstractInsnNode insn : tick.instructions) {
			if (insn.getOpcode() >= 0) {
				opcodes.add(insn.getOpcode());
			}
		}
		assertEquals(List.of(Opcodes.RETURN), opcodes);
		ClassNode block = readClass(BLOCK + ".class");
		assertTrue(block.methods.stream().noneMatch(m -> m.name.equals("tick") && m.desc.equals(TICK_DESC)));
	}

	/** No mod block inherits a vanilla block's tick (a button, a falling block), so a cancelled tick only skips mod code. */
	@Test
	void everyBlockExtendsVanillaBlockDirectly() {
		Set<String> vanillaBlockSupers = new TreeSet<>();
		int blocks = 0;
		for (ClassNode node : MOD_CLASSES) {
			if (node.superName.startsWith("net/minecraft/world/level/block/")) {
				vanillaBlockSupers.add(node.superName);
				blocks++;
			}
		}
		assertEquals(Set.of(BLOCK), vanillaBlockSupers);
		assertTrue(blocks > 100, "found only " + blocks + " blocks");
	}

	@Test
	void entityEffectsRunOnlyFromScheduledTicks() {
		Set<String> outside = new TreeSet<>();
		Set<String> inTick = new TreeSet<>();
		for (ClassNode node : MOD_CLASSES) {
			for (MethodNode method : node.methods) {
				for (AbstractInsnNode insn : method.instructions) {
					for (String[] call : calls(insn)) {
						if (!ENTITY_EFFECTS.contains(call[1]) || !call[0].startsWith("net/minecraft/")) {
							continue;
						}
						String where = node.name + "." + method.name + " -> " + call[0] + "." + call[1];
						if (method.name.equals("tick") && method.desc.equals(TICK_DESC)) {
							inTick.add(node.name);
						} else {
							outside.add(where);
						}
					}
				}
			}
		}
		assertEquals(Set.of(), outside);
		// The 3.0.1 crystal, druze, columns and crystallised blocks of the Ender Amethyst, Nether Fire and Overworld Secrets lines.
		assertEquals(12, inTick.size(), inTick.toString());
	}

	/** Scheduled ticks are the mod's only periodic hook: no random ticks, block entities or tick events. */
	@Test
	void noOtherPeriodicHook() {
		Set<String> found = new TreeSet<>();
		for (ClassNode node : MOD_CLASSES) {
			for (MethodNode method : node.methods) {
				if (method.name.equals("randomTick") || method.name.equals("isRandomlyTicking") || method.name.equals("getTicker")
					|| method.name.equals("newBlockEntity")) {
					found.add(node.name + "." + method.name);
				}
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof MethodInsnNode call && (call.owner.contains("ServerTickEvents")
						|| call.owner.contains("ServerLevelEvents") || call.owner.endsWith("BlockEntityType$Builder"))) {
						found.add(node.name + "." + method.name + " -> " + call.owner);
					}
				}
			}
		}
		assertEquals(Set.of(), found);
	}

	/** Owner and name of a direct call, or of each method handle an {@code invokedynamic} passes (a method reference). */
	private static List<String[]> calls(AbstractInsnNode insn) {
		List<String[]> calls = new ArrayList<>();
		if (insn instanceof MethodInsnNode call) {
			calls.add(new String[] { call.owner, call.name });
		} else if (insn instanceof InvokeDynamicInsnNode indy) {
			for (Object arg : indy.bsmArgs) {
				if (arg instanceof Handle handle) {
					calls.add(new String[] { handle.getOwner(), handle.getName() });
				}
			}
		}
		return calls;
	}

	private static ClassNode readClass(String resource) throws IOException {
		try (InputStream input = MagicVibeTicksContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, resource + " must be on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input).accept(node, 0);
			return node;
		}
	}

	private static MethodNode method(ClassNode owner, String name, String desc) {
		return owner.methods.stream()
			.filter(m -> m.name.equals(name) && m.desc.equals(desc))
			.findFirst()
			.orElseThrow(() -> new AssertionError(owner.name + " has no " + name + desc));
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream input = MagicVibeTicksContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, resource);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
