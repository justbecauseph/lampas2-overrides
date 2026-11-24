package lampas2overrides.client.defaultpacks;

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
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contract tests for the default-selected built-in resource packs. Fabric API's resource loader and the three mods that
 * own the packs are read with ASM from the test runtime classpath, pinned to the versions the pack ships, and never
 * loaded.
 */
class DefaultPacksContractTest {

	private static final String MIXIN = "lampas2overrides/client/defaultpacks/mixin/ResourceLoaderImplMixin";
	private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";

	private static final String LOADER_IMPL = "net/fabricmc/fabric/impl/resource/ResourceLoaderImpl";
	private static final String RESOURCE_LOADER = "net/fabricmc/fabric/api/resource/v1/ResourceLoader";
	private static final String RESOURCE_MANAGER_HELPER = "net/fabricmc/fabric/api/resource/ResourceManagerHelper";
	private static final String MOD_NIO_PACK = "net/fabricmc/fabric/impl/resource/pack/ModNioPackResources";
	private static final String DEFAULT_STORAGE = "net/fabricmc/fabric/impl/resource/client/DefaultResourcePackStorage";
	private static final String OPTIONS_MIXIN = "net/fabricmc/fabric/mixin/resource/client/OptionsMixin";
	private static final String PACK_TYPE = "net/minecraft/server/packs/PackType";
	private static final String ACTIVATION_TYPE = "net/fabricmc/fabric/api/resource/v1/pack/PackActivationType";
	private static final String LEGACY_ACTIVATION_TYPE = "net/fabricmc/fabric/api/resource/ResourcePackActivationType";
	private static final String IDENTIFIER = "net/minecraft/resources/Identifier";

	private static final String REGISTER = "registerBuiltinPack";
	private static final String LEGACY_REGISTER = "registerBuiltinResourcePack";
	private static final String REGISTER_DESC = "(Lnet/minecraft/resources/Identifier;Ljava/lang/String;"
		+ "Lnet/fabricmc/loader/api/ModContainer;Lnet/minecraft/network/chat/Component;"
		+ "Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;)Z";
	private static final String REGISTER_NO_NAME_DESC = "(Lnet/minecraft/resources/Identifier;Ljava/lang/String;"
		+ "Lnet/fabricmc/loader/api/ModContainer;Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;)Z";
	/** Slot of {@code activationType} in the static five-argument {@code registerBuiltinPack}. */
	private static final int ACTIVATION_TYPE_SLOT = 4;
	private static final String CREATE = "create";
	private static final String CREATE_DESC = "(Ljava/lang/String;Lnet/fabricmc/loader/api/ModContainer;Ljava/lang/String;"
		+ "Lnet/minecraft/server/packs/PackType;Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;Z)"
		+ "Lnet/fabricmc/fabric/impl/resource/pack/ModNioPackResources;";

	/** Each pack ID with the client initializer class that registers it. */
	private static final Map<String, String> REGISTERING_CLASS = Map.of(
		"continuity:default", "me/pepperbell/continuity/client/ContinuityClient",
		"continuity:glass_pane_culling_fix", "me/pepperbell/continuity/client/ContinuityClient",
		"elytratrails:allaytrails", "dbrighthd/elytratrails/ElytraTrailsClient",
		"elytratrails:arrowtrails", "dbrighthd/elytratrails/ElytraTrailsClient",
		"quad-lolmbdv:more-pillowed-connected-bed-variants", "de/pnku/mbdv/MoreBedVariantsClient");

	@Test
	void mixinModifiesTheClientPackCreateCall() throws IOException {
		ClassNode mixin = read(MIXIN);
		List<AnnotationNode> handlers = new ArrayList<>();
		for (MethodNode method : mixin.methods) {
			AnnotationNode annotation = find(method.visibleAnnotations, MODIFY_ARG);
			if (annotation == null) {
				annotation = find(method.invisibleAnnotations, MODIFY_ARG);
			}
			if (annotation != null) {
				handlers.add(annotation);
			}
		}
		assertEquals(1, handlers.size());
		AnnotationNode handler = handlers.getFirst();
		assertEquals(List.of(REGISTER + REGISTER_DESC), strings(value(handler, "method")));
		assertEquals(4, value(handler, "index"), "index 4 is create's activationType argument");
		Object atValue = value(handler, "at");
		if (atValue instanceof List<?> ats) {
			assertEquals(1, ats.size());
			atValue = ats.getFirst();
		}
		AnnotationNode at = (AnnotationNode) atValue;
		assertEquals("INVOKE", value(at, "value"));
		assertEquals("L" + MOD_NIO_PACK + ";" + CREATE + CREATE_DESC, value(at, "target"));
		assertEquals(0, value(at, "ordinal"));

		MethodNode target = method(read(LOADER_IMPL), REGISTER, REGISTER_DESC);
		assertTrue((target.access & Opcodes.ACC_STATIC) != 0, "registerBuiltinPack must stay static");
	}

	@Test
	void firstCreateCallIsTheClientPackWithTheRegisteredActivationType() throws IOException {
		MethodNode register = method(read(LOADER_IMPL), REGISTER, REGISTER_DESC);
		List<MethodInsnNode> creates = calls(register, MOD_NIO_PACK, CREATE, CREATE_DESC);
		assertEquals(2, creates.size(), "one client and one data pack");
		List<String> packTypes = new ArrayList<>();
		for (MethodInsnNode create : creates) {
			AbstractInsnNode modBundled = previous(create);
			assertEquals(Opcodes.ICONST_0, modBundled.getOpcode());
			AbstractInsnNode activationType = previous(modBundled);
			assertTrue(activationType instanceof VarInsnNode var
				&& var.getOpcode() == Opcodes.ALOAD && var.var == ACTIVATION_TYPE_SLOT,
				"create must receive registerBuiltinPack's activationType argument");
			AbstractInsnNode packType = previous(activationType);
			assertTrue(packType instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
				&& field.owner.equals(PACK_TYPE), "create's PackType must be a PackType constant");
			packTypes.add(((FieldInsnNode) packType).name);
		}
		assertEquals(List.of("CLIENT_RESOURCES", "SERVER_DATA"), packTypes);
	}

	@Test
	void everyRegistrationOverloadEndsInTheFiveArgumentMethod() throws IOException {
		ClassNode impl = read(LOADER_IMPL);
		assertFalse(calls(method(impl, REGISTER, REGISTER_NO_NAME_DESC), LOADER_IMPL, REGISTER, REGISTER_DESC).isEmpty());

		ClassNode loader = read(RESOURCE_LOADER);
		int loaderOverloads = 0;
		for (MethodNode method : loader.methods) {
			if (method.name.equals(REGISTER)) {
				loaderOverloads++;
				assertFalse(calls(method, LOADER_IMPL, REGISTER, null).isEmpty(), method.name + method.desc);
			}
		}
		assertEquals(2, loaderOverloads);

		ClassNode helper = read(RESOURCE_MANAGER_HELPER);
		int helperOverloads = 0;
		for (MethodNode method : helper.methods) {
			if (method.name.equals(LEGACY_REGISTER)) {
				helperOverloads++;
				boolean delegates = !calls(method, RESOURCE_LOADER, REGISTER, null).isEmpty()
					|| !calls(method, LOADER_IMPL, REGISTER, null).isEmpty();
				assertTrue(delegates, method.name + method.desc);
			}
		}
		assertEquals(4, helperOverloads);
	}

	@Test
	void optionsLoadRunsFabricDefaultPackSelection() throws IOException {
		ClassNode storage = read(DEFAULT_STORAGE);
		boolean readsDefault = false;
		for (MethodNode method : storage.methods) {
			readsDefault |= !calls(method, ACTIVATION_TYPE, "isEnabledByDefault", "()Z").isEmpty();
		}
		assertTrue(readsDefault, "DefaultResourcePackStorage must select packs by PackActivationType.isEnabledByDefault");

		ClassNode optionsMixin = read(OPTIONS_MIXIN);
		boolean callsProcess = false;
		for (MethodNode method : optionsMixin.methods) {
			callsProcess |= !calls(method, DEFAULT_STORAGE, "process", null).isEmpty();
		}
		assertTrue(callsProcess, "Fabric API's OptionsMixin must run DefaultResourcePackStorage.process");
	}

	@Test
	void eachModRegistersItsPackAsNormal() throws IOException {
		assertEquals(DefaultEnabledPacks.PACK_IDS, REGISTERING_CLASS.keySet());
		for (Map.Entry<String, String> entry : REGISTERING_CLASS.entrySet()) {
			String packId = entry.getKey();
			String namespace = packId.substring(0, packId.indexOf(':'));
			String path = packId.substring(packId.indexOf(':') + 1);
			String owner = entry.getValue();

			try (JarFile jar = jarOf(owner)) {
				String root = "resourcepacks/" + path + "/";
				ZipEntry mcmeta = jar.getEntry(root + "pack.mcmeta");
				assertNotNull(mcmeta, packId + " has no pack.mcmeta");
				// Fabric API only default-selects a pack whose opened resources are a ModNioPackResources; a pack with
				// overlays opens as a CompositePackResources and would be skipped.
				try (InputStream in = jar.getInputStream(mcmeta)) {
					JsonObject meta = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					assertFalse(meta.has("overlays"), packId + " declares overlays");
				}
				boolean shipsData = jar.stream().anyMatch(e -> e.getName().startsWith(root + "data/"));
				assertFalse(shipsData, packId + " ships a data pack, which the mixin leaves at the mod's activation type");
			}

			ClassNode registering = read(owner);
			assertTrue(registersAsNormal(registering, namespace, path), packId + " is not registered as NORMAL in " + owner);
		}
	}

	/**
	 * Finds an {@code ldc path} whose identifier is built with {@code namespace}, either from an {@code ldc namespace}
	 * right before it or through a same-class static helper that loads {@code namespace} and calls
	 * {@code Identifier.fromNamespaceAndPath}, and checks that the next built-in pack registration after it, with no
	 * jump in between, takes {@code NORMAL} loaded directly before the call as its last argument.
	 */
	private static boolean registersAsNormal(ClassNode owner, String namespace, String path) {
		for (MethodNode method : owner.methods) {
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof LdcInsnNode ldc) || !path.equals(ldc.cst)) {
					continue;
				}
				if (!buildsIdentifier(owner, insn, namespace)) {
					continue;
				}
				for (AbstractInsnNode next = insn.getNext(); next != null; next = next.getNext()) {
					if (next instanceof JumpInsnNode) {
						break;
					}
					if (next instanceof MethodInsnNode call
						&& (call.name.equals(REGISTER) || call.name.equals(LEGACY_REGISTER))) {
						AbstractInsnNode last = previous(call);
						if (last instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
							&& (field.owner.equals(ACTIVATION_TYPE) || field.owner.equals(LEGACY_ACTIVATION_TYPE))
							&& field.name.equals("NORMAL")) {
							return true;
						}
						break;
					}
				}
			}
		}
		return false;
	}

	private static boolean buildsIdentifier(ClassNode owner, AbstractInsnNode pathLdc, String namespace) {
		AbstractInsnNode before = previous(pathLdc);
		AbstractInsnNode after = next(pathLdc);
		if (before instanceof LdcInsnNode ldc && namespace.equals(ldc.cst)
			&& after instanceof MethodInsnNode call && call.owner.equals(IDENTIFIER) && call.name.equals("fromNamespaceAndPath")) {
			return true;
		}
		if (after instanceof MethodInsnNode helper && helper.getOpcode() == Opcodes.INVOKESTATIC && helper.owner.equals(owner.name)) {
			MethodNode body = method(owner, helper.name, helper.desc);
			AbstractInsnNode first = body.instructions.getFirst();
			if (first.getOpcode() < 0) {
				first = next(first);
			}
			return first instanceof LdcInsnNode ldc && namespace.equals(ldc.cst)
				&& !calls(body, IDENTIFIER, "fromNamespaceAndPath", null).isEmpty();
		}
		return false;
	}

	private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String descriptor) {
		List<MethodInsnNode> found = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
				&& (descriptor == null || call.desc.equals(descriptor))) {
				found.add(call);
			}
		}
		return found;
	}

	private static AbstractInsnNode previous(AbstractInsnNode insn) {
		AbstractInsnNode current = insn.getPrevious();
		while (current != null && current.getOpcode() < 0) {
			current = current.getPrevious();
		}
		return current;
	}

	private static AbstractInsnNode next(AbstractInsnNode insn) {
		AbstractInsnNode current = insn.getNext();
		while (current != null && current.getOpcode() < 0) {
			current = current.getNext();
		}
		return current;
	}

	private static MethodNode method(ClassNode owner, String name, String descriptor) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name) && method.desc.equals(descriptor)) {
				return method;
			}
		}
		throw new AssertionError(owner.name + " has no " + name + descriptor);
	}

	private static AnnotationNode find(List<AnnotationNode> annotations, String descriptor) {
		if (annotations == null) {
			return null;
		}
		for (AnnotationNode annotation : annotations) {
			if (annotation.desc.equals(descriptor)) {
				return annotation;
			}
		}
		return null;
	}

	private static Object value(AnnotationNode annotation, String name) {
		for (int i = 0; i < annotation.values.size(); i += 2) {
			if (annotation.values.get(i).equals(name)) {
				return annotation.values.get(i + 1);
			}
		}
		throw new AssertionError(annotation.desc + " has no " + name);
	}

	private static List<String> strings(Object value) {
		List<String> result = new ArrayList<>();
		for (Object item : (List<?>) value) {
			result.add((String) item);
		}
		return result;
	}

	private static JarFile jarOf(String internalName) throws IOException {
		URL url = DefaultPacksContractTest.class.getClassLoader().getResource(internalName + ".class");
		assertNotNull(url, internalName + " is not on the test classpath");
		JarURLConnection connection = (JarURLConnection) url.openConnection();
		connection.setUseCaches(false);
		return connection.getJarFile();
	}

	private static ClassNode read(String internalName) throws IOException {
		try (InputStream input = DefaultPacksContractTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
			assertNotNull(input, internalName + " is not on the test classpath");
			ClassNode node = new ClassNode();
			new ClassReader(input).accept(node, 0);
			return node;
		}
	}
}
