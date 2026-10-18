package lampas2overrides.client.hotbarsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class HotbarSyncMixinContractTest {

	private static final String MIXIN_CONFIG = "lampas2-overrides.hotbarsync.mixins.json";
	private static final String GAME_MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
	private static final String CREATE_PLAYER_SHORT =
		"(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/stats/StatsCounter;Lnet/minecraft/client/ClientRecipeBook;)Lnet/minecraft/client/player/LocalPlayer;";
	private static final String CREATE_PLAYER_FULL =
		"(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/stats/StatsCounter;Lnet/minecraft/client/ClientRecipeBook;Lnet/minecraft/world/entity/player/Input;Z)Lnet/minecraft/client/player/LocalPlayer;";

	@Test
	void registersAnUngatedClientOnlyConfig() throws IOException {
		JsonObject config = readJson(MIXIN_CONFIG);
		assertTrue(config.get("required").getAsBoolean());
		assertEquals("lampas2overrides.client.hotbarsync.mixin", config.get("package").getAsString());
		assertFalse(config.has("plugin"), "the vanilla fix must not be gated on a mod or version");
		assertFalse(config.has("mixins"), "the fix must not apply on dedicated servers");
		assertEquals(1, config.getAsJsonArray("client").size());
		assertEquals("MultiPlayerGameModeMixin", config.getAsJsonArray("client").get(0).getAsString());
		assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());

		JsonObject registered = null;
		for (JsonElement entry : readJson("fabric.mod.json").getAsJsonArray("mixins")) {
			JsonObject mixin = entry.getAsJsonObject();
			if (MIXIN_CONFIG.equals(mixin.get("config").getAsString())) {
				registered = mixin;
			}
		}
		assertNotNull(registered, "fabric.mod.json must register the hotbar sync config");
		assertEquals("client", registered.get("environment").getAsString());
	}

	/**
	 * The fix rests on these vanilla facts: {@code carriedIndex} exists, only
	 * {@code ensureHasSentCarriedItem} reads or writes it (so -1 is never sent or used), and the short {@code createPlayer} overload (used by
	 * login and respawn) delegates to the full one the mixin targets.
	 */
	@Test
	void vanillaGameModeStillMatchesTheFixPremise() throws IOException {
		GameModeShape shape = readGameMode();
		assertTrue(shape.fields.contains("carriedIndex:I"));
		assertEquals(List.of("ensureHasSentCarriedItem"), shape.carriedIndexWriters);
		assertEquals(Set.of("ensureHasSentCarriedItem"), shape.carriedIndexReaders);
		assertTrue(shape.methods.contains("createPlayer" + CREATE_PLAYER_FULL));
		assertTrue(shape.shortOverloadDelegatesToFull,
			"createPlayer" + CREATE_PLAYER_SHORT + " no longer delegates to the full overload");
	}

	@Test
	void mixinResetsTheShadowedIndexAtTheFullOverloadReturn() throws IOException {
		List<String> injectMethods = new ArrayList<>();
		List<String> injectAt = new ArrayList<>();
		Set<String> shadowFields = new HashSet<>();
		try (InputStream in = HotbarSyncMixinContractTest.class.getClassLoader()
				.getResourceAsStream("lampas2overrides/client/hotbarsync/mixin/MultiPlayerGameModeMixin.class")) {
			assertNotNull(in, "Missing compiled MultiPlayerGameModeMixin");
			new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					return new FieldVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
							if (annotation.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
								shadowFields.add(name + ":" + descriptor);
							}
							return null;
						}
					};
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
						String[] exceptions) {
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
							if (!annotation.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
								return null;
							}
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public AnnotationVisitor visitArray(String key) {
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public void visit(String unused, Object value) {
											if (key.equals("method")) {
												injectMethods.add((String) value);
											}
										}

										@Override
										public AnnotationVisitor visitAnnotation(String unused, String at) {
											return new AnnotationVisitor(Opcodes.ASM9) {
												@Override
												public void visit(String atKey, Object value) {
													if (atKey.equals("value")) {
														injectAt.add((String) value);
													}
												}
											};
										}
									};
								}
							};
						}
					};
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		assertEquals(List.of("createPlayer" + CREATE_PLAYER_FULL), injectMethods);
		assertEquals(List.of("RETURN"), injectAt);
		assertEquals(Set.of("carriedIndex:I"), shadowFields);
	}

	private static GameModeShape readGameMode() throws IOException {
		GameModeShape shape = new GameModeShape();
		try (InputStream in = HotbarSyncMixinContractTest.class.getClassLoader()
				.getResourceAsStream(GAME_MODE + ".class")) {
			assertNotNull(in, "Minecraft client classes are not on the test classpath");
			new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					shape.fields.add(name + ":" + descriptor);
					return null;
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
						String[] exceptions) {
					shape.methods.add(name + descriptor);
					boolean shortOverload = name.equals("createPlayer") && descriptor.equals(CREATE_PLAYER_SHORT);
					return new MethodVisitor(Opcodes.ASM9) {
						@Override
						public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
							if (owner.equals(GAME_MODE) && field.equals("carriedIndex")) {
								if (opcode == Opcodes.PUTFIELD) {
									shape.carriedIndexWriters.add(name);
								} else if (opcode == Opcodes.GETFIELD) {
									shape.carriedIndexReaders.add(name);
								}
							}
						}

						@Override
						public void visitMethodInsn(int opcode, String owner, String method, String methodDescriptor,
								boolean isInterface) {
							if (shortOverload && owner.equals(GAME_MODE) && method.equals("createPlayer")
									&& methodDescriptor.equals(CREATE_PLAYER_FULL)) {
								shape.shortOverloadDelegatesToFull = true;
							}
						}
					};
				}
			}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}
		return shape;
	}

	private static JsonObject readJson(String resource) throws IOException {
		try (InputStream in = HotbarSyncMixinContractTest.class.getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(in, "Missing " + resource + " on the test classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static final class GameModeShape {
		final Set<String> fields = new HashSet<>();
		final Set<String> methods = new HashSet<>();
		final List<String> carriedIndexWriters = new ArrayList<>();
		final Set<String> carriedIndexReaders = new HashSet<>();
		boolean shortOverloadDelegatesToFull;
	}
}
