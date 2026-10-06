package lampas2overrides.client.chatheads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatHeadsBridgeContractTest {

	private static final UUID OWNER = UUID.fromString("6f9a930e-3c89-4d87-a8c9-2873d2334753");
	private static final UUID OTHER = UUID.fromString("df53cfa0-370d-49e5-9a30-8e0c012d7b8c");

	@Test
	void gateRequiresFiguraAndChattingPresenceOnly() {
		assertTrue(ChatHeadsMixinPlugin.shouldApply(true, true));
		assertFalse(ChatHeadsMixinPlugin.shouldApply(false, true));
		assertFalse(ChatHeadsMixinPlugin.shouldApply(true, false));
		assertFalse(ChatHeadsMixinPlugin.shouldApply(false, false));
	}

	@Test
	void targetsThePlayerInfoDrawBoundaryAndRequiredFaceBoundary() throws IOException {
		assertEquals(
				"draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/multiplayer/PlayerInfo;III)V",
				ChattingHeadContract.DRAW_METHOD);
		assertEquals(
				"drawFace(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/multiplayer/PlayerInfo;"
						+ "Lorg/polyfrost/chatting/chat/HeadTextures$Head;III)V",
				ChattingHeadContract.DRAW_FACE_METHOD);

		try (InputStream input = getClass().getClassLoader()
				.getResourceAsStream("lampas2-overrides.chatheads.mixins.json")) {
			assertTrue(input != null);
			String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(config.contains("ChatHeadDrawMixin"));
			assertTrue(config.contains("\"defaultRequire\": 1"));
			assertFalse(config.contains("PlayerFaceExtractorMixin"));
		}
	}

	@Test
	void firstShadowCallDrawsAvatarAtMainCoordinatesAndCancelsSecondFace() {
		ChatHeadDrawContext context = new ChatHeadDrawContext(OWNER, 20, new Object());

		ChatHeadDrawContext.FacePlan shadow = context.plan(OWNER, 21, 34);
		assertEquals(ChatHeadDrawContext.FaceAction.DRAW_AVATAR, shadow.action());
		assertEquals(20, shadow.x());
		assertEquals(33, shadow.y());

		context.markAvatarDrawn();
		ChatHeadDrawContext.FacePlan main = context.plan(OWNER, 20, 33);
		assertEquals(ChatHeadDrawContext.FaceAction.SUPPRESS_DUPLICATE, main.action());
	}

	@Test
	void directMainCallUsesItsCoordinatesAndOtherOwnersKeepTheirSkin() {
		ChatHeadDrawContext context = new ChatHeadDrawContext(OWNER, 20, new Object());

		ChatHeadDrawContext.FacePlan main = context.plan(OWNER, 20, 33);
		assertEquals(ChatHeadDrawContext.FaceAction.DRAW_AVATAR, main.action());
		assertEquals(20, main.x());
		assertEquals(33, main.y());

		assertEquals(ChatHeadDrawContext.FaceAction.PASS_THROUGH, context.plan(OTHER, 20, 33).action());
	}

	@Test
	void missingAvatarLeavesBothNativeFaceCallsAlone() {
		ChatHeadDrawContext context = new ChatHeadDrawContext(OWNER, 20, null);
		assertEquals(ChatHeadDrawContext.FaceAction.PASS_THROUGH, context.plan(OWNER, 21, 34).action());
		assertEquals(ChatHeadDrawContext.FaceAction.PASS_THROUGH, context.plan(OWNER, 20, 33).action());
	}

	@Test
	void nextHeadStartsWithFreshShadowState() {
		ChatHeadDrawContext first = new ChatHeadDrawContext(OWNER, 20, new Object());
		first.plan(OWNER, 20, 33);
		first.markAvatarDrawn();

		ChatHeadDrawContext next = new ChatHeadDrawContext(OWNER, 40, new Object());
		assertEquals(ChatHeadDrawContext.FaceAction.DRAW_AVATAR, next.plan(OWNER, 40, 33).action());
	}
}
