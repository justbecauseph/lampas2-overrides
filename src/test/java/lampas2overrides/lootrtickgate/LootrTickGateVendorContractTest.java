package lampas2overrides.lootrtickgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads the pinned Lootr 1.24.41.124 classes from the test runtime classpath and checks the tick path that
 * {@link LootrTickGate} depends on: the static delegate, the interface default method, and the instructions that run
 * before Lootr's modulo branch returns.
 */
class LootrTickGateVendorContractTest {

	private static final String LOOTR_API = "noobanidus/mods/lootr/common/api/LootrAPI";
	private static final String ILOOTR_API = "noobanidus/mods/lootr/common/api/interfaces/lootr/ILootrAPI";
	private static final String DEFAULT_IMPL = "noobanidus/mods/lootr/common/impl/DefaultLootrAPIImpl";
	/** The class Lootr's Fabric entrypoint installs as {@code LootrAPI.INSTANCE}. */
	private static final String FABRIC_IMPL = "noobanidus/mods/lootr/fabric/impl/LootrAPIImpl";
	private static final String CONTAINER = "noobanidus/mods/lootr/common/api/data/ILootrContainerInstance";
	private static final String LEVEL = "net/minecraft/world/level/Level";
	private static final String HANDLE = "handleInstanceTick";
	private static final String HANDLE_DESC = "(L" + CONTAINER + ";)V";

	/** Owner.name of every method call before the modulo branch, in bytecode order. */
	private static final List<String> PRE_MODULO_CALLS = List.of(
		CONTAINER + ".getDataLevel",
		CONTAINER + ".getDataLevel",
		LEVEL + ".isClientSide",
		LOOTR_API + ".getData",
		LOOTR_API + ".getCurrentTicks",
		LOOTR_API + ".getTickDelay",
		CONTAINER + ".getRandomOffset");

	@Test
	void testClasspathCarriesTheDeployedFabricArtifact() throws IOException, URISyntaxException, NoSuchAlgorithmException {
		URL resource = getClass().getClassLoader().getResource(LOOTR_API + ".class");
		assertNotNull(resource, "Lootr must be on the test runtime classpath");
		Path jar = Path.of(((JarURLConnection) resource.openConnection()).getJarFileURL().toURI());
		String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
		assertEquals(LootrTickGateProfile.JAR_SHA256, sha256,
			"the bytecode checks must read the Lootr jar the gate accepts at runtime, not another loader's build of the same version");
	}

	@Test
	void staticDelegateForwardsEveryTickToTheInterfaceDefaultMethod() throws IOException {
		MethodFact delegate = facts(LOOTR_API).method(HANDLE, HANDLE_DESC);
		assertTrue((delegate.access & Opcodes.ACC_PUBLIC) != 0 && (delegate.access & Opcodes.ACC_STATIC) != 0,
			"the HEAD inject targets a public static LootrAPI#handleInstanceTick");

		List<Insn> calls = delegate.calls();
		assertEquals(1, calls.size(),
			"the gate relies on the static method doing nothing but forwarding to ILootrAPI#handleInstanceTick");
		Insn forward = calls.get(0);
		assertEquals(Opcodes.INVOKEINTERFACE, forward.opcode(), "the forward must be an interface call");
		assertEquals(ILOOTR_API, forward.owner(), "the forward must target ILootrAPI");
		assertEquals(HANDLE, forward.name(), "the forward must target handleInstanceTick");
		assertEquals(HANDLE_DESC, forward.descriptor(), "the forward must keep the container descriptor");
	}

	@Test
	void noClassOnTheFabricApiImplementationChainOverridesTheTickHook() throws IOException {
		List<String> chain = new ArrayList<>();
		for (String type = FABRIC_IMPL; type != null && !"java/lang/Object".equals(type); type = facts(type).superName) {
			chain.add(type);
			assertFalse(facts(type).declaresMethodNamed(HANDLE), type + " declares handleInstanceTick; the gate relies on "
				+ "the interface default body running for every container, and an override would bypass it");
		}
		assertTrue(chain.contains(DEFAULT_IMPL), "the Fabric LootrAPIImpl must extend DefaultLootrAPIImpl, got " + chain);
	}

	@Test
	void defaultTickHookReadsTheContainerBeforeTheModuloBranch() throws IOException {
		List<Insn> body = facts(ILOOTR_API).method(HANDLE, HANDLE_DESC).insns;
		int modulo = indexOfOpcode(body, Opcodes.IREM);
		assertTrue(modulo > 0, "the gate relies on a single IREM modulo branch in the tick hook");

		List<Insn> prefix = body.subList(0, modulo);
		List<String> calls = new ArrayList<>();
		for (Insn insn : prefix) {
			if (insn.isCall()) {
				calls.add(insn.owner() + "." + insn.name());
			}
		}
		assertEquals(PRE_MODULO_CALLS, calls,
			"the gate relies on Lootr fetching the container data and reading the schedule operands, in this order, "
				+ "and on nothing else before the modulo branch");
		List<Integer> operands = new ArrayList<>();
		for (Insn insn : prefix) {
			if (insn.opcode() != Insn.LABEL) {
				operands.add(insn.opcode());
			}
		}
		operands = operands.subList(Math.max(0, operands.size() - 5), operands.size());
		assertEquals(List.of(Opcodes.INVOKESTATIC, Opcodes.INVOKESTATIC, Opcodes.ALOAD, Opcodes.INVOKEINTERFACE, Opcodes.IADD),
			operands, "the gate relies on Lootr computing getCurrentTicks() % (getTickDelay() + instance.getRandomOffset())");

		for (Insn insn : prefix) {
			assertFalse(insn.opcode() == Opcodes.PUTFIELD || insn.opcode() == Opcodes.PUTSTATIC
					|| insn.opcode() == Opcodes.INVOKEDYNAMIC,
				"the gate relies on no field write or dynamic call before the modulo branch, so a skipped tick loses no state change");
		}

		Insn branch = nextInstruction(body, modulo + 1);
		assertEquals(Opcodes.IFNE, branch.opcode(),
			"the gate relies on the modulo result being tested with IFNE immediately after IREM");
		assertNotNull(branch.label(), "the IFNE must carry its jump target");

		int landing = indexOfLabel(body, branch.label());
		assertTrue(landing >= 0, "the IFNE target label must appear in the tick hook");
		assertEquals(Opcodes.RETURN, nextInstruction(body, landing + 1).opcode(),
			"the gate relies on the IFNE skipping straight to a RETURN, so an off-schedule tick ends without further work");
	}

	private static int indexOfOpcode(List<Insn> body, int opcode) {
		for (int i = 0; i < body.size(); i++) {
			if (body.get(i).opcode() == opcode) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfLabel(List<Insn> body, Label label) {
		for (int i = 0; i < body.size(); i++) {
			Insn insn = body.get(i);
			if (insn.opcode() == Insn.LABEL && insn.label() == label) {
				return i;
			}
		}
		return -1;
	}

	private static Insn nextInstruction(List<Insn> body, int from) {
		for (int i = from; i < body.size(); i++) {
			if (body.get(i).opcode() != Insn.LABEL) {
				return body.get(i);
			}
		}
		throw new AssertionError("no instruction follows index " + from);
	}

	private static ClassFacts facts(String internalName) throws IOException {
		String resource = internalName + ".class";
		try (InputStream input = LootrTickGateVendorContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, "missing pinned Lootr class " + resource + "; testRuntimeOnly lootr must be on the test classpath");
			ClassFacts facts = new ClassFacts();
			new ClassReader(input).accept(new FactsVisitor(facts), ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
			return facts;
		}
	}

	/** One instruction, method call, field access, jump or label event, in bytecode order. */
	private record Insn(int opcode, String owner, String name, String descriptor, Label label) {

		/** Opcode value for a label event, which is not an instruction. */
		static final int LABEL = -1;

		static Insn plain(int opcode) {
			return new Insn(opcode, null, null, null, null);
		}

		static Insn member(int opcode, String owner, String name, String descriptor) {
			return new Insn(opcode, owner, name, descriptor, null);
		}

		static Insn jump(int opcode, Label target) {
			return new Insn(opcode, null, null, null, target);
		}

		static Insn labelEvent(Label label) {
			return new Insn(LABEL, null, null, null, label);
		}

		boolean isCall() {
			return opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL
				|| opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKEINTERFACE;
		}
	}

	private static final class ClassFacts {
		String superName;
		final Map<String, MethodFact> methods = new HashMap<>();
		final Set<String> methodNames = new HashSet<>();

		MethodFact method(String name, String descriptor) {
			MethodFact method = methods.get(name + descriptor);
			assertNotNull(method, "missing pinned Lootr method " + name + descriptor);
			return method;
		}

		boolean declaresMethodNamed(String name) {
			return methodNames.contains(name);
		}
	}

	private static final class MethodFact {
		final int access;
		final List<Insn> insns = new ArrayList<>();

		MethodFact(int access) {
			this.access = access;
		}

		List<Insn> calls() {
			List<Insn> calls = new ArrayList<>();
			for (Insn insn : insns) {
				if (insn.isCall()) {
					calls.add(insn);
				}
			}
			return calls;
		}
	}

	private static final class FactsVisitor extends ClassVisitor {
		private final ClassFacts facts;

		FactsVisitor(ClassFacts facts) {
			super(Opcodes.ASM9);
			this.facts = facts;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			facts.superName = superName;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodFact method = new MethodFact(access);
			facts.methods.put(name + descriptor, method);
			facts.methodNames.add(name);
			return new InstructionRecorder(method);
		}
	}

	/** Records every instruction kind in order, so the "next instruction" checks see the whole body. */
	private static final class InstructionRecorder extends MethodVisitor {
		private final MethodFact method;

		InstructionRecorder(MethodFact method) {
			super(Opcodes.ASM9);
			this.method = method;
		}

		@Override
		public void visitInsn(int opcode) {
			method.insns.add(Insn.plain(opcode));
		}

		@Override
		public void visitIntInsn(int opcode, int operand) {
			method.insns.add(Insn.plain(opcode));
		}

		@Override
		public void visitVarInsn(int opcode, int varIndex) {
			method.insns.add(Insn.plain(opcode));
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			method.insns.add(Insn.plain(opcode));
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			method.insns.add(Insn.member(opcode, owner, name, descriptor));
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			method.insns.add(Insn.member(opcode, owner, name, descriptor));
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			method.insns.add(Insn.plain(Opcodes.INVOKEDYNAMIC));
		}

		@Override
		public void visitJumpInsn(int opcode, Label label) {
			method.insns.add(Insn.jump(opcode, label));
		}

		@Override
		public void visitLabel(Label label) {
			method.insns.add(Insn.labelEvent(label));
		}

		@Override
		public void visitLdcInsn(Object value) {
			method.insns.add(Insn.plain(Opcodes.LDC));
		}

		@Override
		public void visitIincInsn(int varIndex, int increment) {
			method.insns.add(Insn.plain(Opcodes.IINC));
		}

		@Override
		public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
			method.insns.add(Insn.plain(Opcodes.TABLESWITCH));
		}

		@Override
		public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
			method.insns.add(Insn.plain(Opcodes.LOOKUPSWITCH));
		}

		@Override
		public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
			method.insns.add(Insn.plain(Opcodes.MULTIANEWARRAY));
		}
	}
}
