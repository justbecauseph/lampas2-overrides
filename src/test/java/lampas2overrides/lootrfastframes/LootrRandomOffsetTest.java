package lampas2overrides.lootrfastframes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class LootrRandomOffsetTest {

	@Test
	void mixinProvidesConcreteLootrRandomOffsetDelegate() throws Exception {
		boolean[] found = new boolean[2];
		ClassReader reader = new ClassReader(
				"lampas2overrides.lootrfastframes.mixin.ItemFrameBlockEntityMixin");
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
				if ("getRandomOffset".equals(name) && "()I".equals(descriptor)) {
					found[0] = (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT))
							== Opcodes.ACC_PUBLIC;
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String name,
								String descriptor, boolean isInterface) {
							if (opcode == Opcodes.INVOKEVIRTUAL
									&& "lampas2overrides/lootrfastframes/FixedLootrInstance".equals(owner)
									&& "getRandomOffset".equals(name) && "()I".equals(descriptor)) {
								found[1] = true;
							}
						}
					};
				}
				return null;
			}
		}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		assertTrue(found[0], "ItemFrameBlockEntityMixin must concretely implement getRandomOffset()I");
		assertTrue(found[1], "getRandomOffset()I must delegate to FixedLootrInstance");
	}
}
