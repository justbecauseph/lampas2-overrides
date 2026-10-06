package lampas2overrides.client.chatheads;

import java.util.UUID;

/** Per-call state for Chatting's shadow and main face submissions. */
final class ChatHeadDrawContext {

	enum FaceAction {
		PASS_THROUGH,
		DRAW_AVATAR,
		SUPPRESS_DUPLICATE
	}

	record FacePlan(FaceAction action, int x, int y) {
		static FacePlan passThrough(int x, int y) {
			return new FacePlan(FaceAction.PASS_THROUGH, x, y);
		}

		static FacePlan drawAvatar(int x, int y) {
			return new FacePlan(FaceAction.DRAW_AVATAR, x, y);
		}

		static FacePlan suppressDuplicate(int x, int y) {
			return new FacePlan(FaceAction.SUPPRESS_DUPLICATE, x, y);
		}
	}

	private final UUID owner;
	private final int left;
	private final Object avatar;
	private boolean avatarDrawn;

	ChatHeadDrawContext(UUID owner, int left, Object avatar) {
		this.owner = owner;
		this.left = left;
		this.avatar = avatar;
	}

	Object avatar() {
		return avatar;
	}

	FacePlan plan(UUID faceOwner, int x, int y) {
		if (!owner.equals(faceOwner) || avatar == null) {
			return FacePlan.passThrough(x, y);
		}
		if (avatarDrawn) {
			return FacePlan.suppressDuplicate(x, y);
		}

		// Chatting submits the normal shadow at (left + 1, top + 1) before its main face.
		// Render the portrait at the main-face coordinates from that first call so the avatar is
		// submitted once, and cancel the subsequent skin/cached-texture call.
		if (x == left + 1) {
			return FacePlan.drawAvatar(x - 1, y - 1);
		}
		return FacePlan.drawAvatar(x, y);
	}

	void markAvatarDrawn() {
		avatarDrawn = true;
	}
}
