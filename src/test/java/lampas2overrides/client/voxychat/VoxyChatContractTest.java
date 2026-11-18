package lampas2overrides.client.voxychat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class VoxyChatContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.voxychat.mixins.json";
	private static final String LOGGER = "me/cortex/voxy/common/Logger";

	@Test
	void registersTheFeatureAsClientOnlyAndRequired() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.client.voxychat.mixin", config.get("package").getAsString());
		assertEquals("lampas2overrides.client.voxychat.VoxyChatMixinPlugin", config.get("plugin").getAsString());
		assertEquals(1, config.getAsJsonArray("client").size());
		assertEquals("VoxyLoggerMixin", config.getAsJsonArray("client").get(0).getAsString());
		assertFalse(config.has("mixins"), "the Voxy chat mixin must not be common");
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject metadata = readJson("fabric.mod.json");
		JsonObject registered = null;
		for (JsonElement entry : metadata.getAsJsonArray("mixins")) {
			if (MIXIN_CONFIG.equals(entry.getAsJsonObject().get("config").getAsString())) {
				registered = entry.getAsJsonObject();
			}
		}
		assertNotNull(registered);
		assertEquals("client", registered.get("environment").getAsString());
		assertFalse(metadata.getAsJsonObject("depends").has("voxy"));
	}

	/** The wrapped call must be the only chat path in {@code error}, and {@code warn} must not post to chat at all. */
	@Test
	void voxyLoggerPostsErrorsToChatOnlyThroughOneShowInHudCall() throws IOException {
		ClassNode logger = new ClassNode();
		try (InputStream input = VoxyChatContractTest.class.getClassLoader().getResourceAsStream(LOGGER + ".class")) {
			assertNotNull(input, "Voxy must be on the test classpath");
			new ClassReader(input).accept(logger, 0);
		}

		MethodNode showInHud = method(logger, "showInHUD", "(Ljava/lang/String;)V");
		assertTrue((showInHud.access & Opcodes.ACC_STATIC) != 0);
		assertEquals(1, showInHudCalls(method(logger, "error", "([Ljava/lang/Object;)V")).size());
		assertEquals(0, showInHudCalls(method(logger, "warn", "([Ljava/lang/Object;)V")).size());
	}

	private static List<MethodInsnNode> showInHudCalls(MethodNode method) {
		List<MethodInsnNode> calls = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
				&& call.owner.equals(LOGGER) && call.name.equals("showInHUD")
				&& call.desc.equals("(Ljava/lang/String;)V")) {
				calls.add(call);
			}
		}
		return calls;
	}

	private static MethodNode method(ClassNode owner, String name, String descriptor) {
		return owner.methods.stream()
			.filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
			.findFirst()
			.orElseThrow(() -> new AssertionError(owner.name + "." + name + descriptor + " is missing"));
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream input = VoxyChatContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, resource);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
