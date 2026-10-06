package lampas2overrides.client.chatheads;

/** Exact Chatting bytecode boundaries inspected in the installed client artifact. */
public final class ChattingHeadContract {

	public static final String DRAW_METHOD = "draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
			+ "Lnet/minecraft/client/multiplayer/PlayerInfo;III)V";
	public static final String DRAW_FACE_METHOD = "drawFace(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
			+ "Lnet/minecraft/client/multiplayer/PlayerInfo;"
			+ "Lorg/polyfrost/chatting/chat/HeadTextures$Head;III)V";

	private ChattingHeadContract() {
	}
}
