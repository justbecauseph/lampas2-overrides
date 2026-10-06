package lampas2overrides.client.chatheads;

import java.lang.reflect.Method;

import lampas2overrides.client.compat.Reflection;

/** Binds the cached-head destination size without linking Chatting into the build. */
final class ChattingHeadTextures {

	private static final String HEAD = "org.polyfrost.chatting.chat.HeadTextures$Head";

	private static Method getSize;

	private ChattingHeadTextures() {
	}

	/** @return {@code null} when the member used by cached-head replacement is available */
	static String bind() {
		Class<?> head = Reflection.findClass(HEAD);
		if (head == null) {
			return HEAD;
		}

		getSize = Reflection.findMethod(head, "getSize");
		if (getSize == null || getSize.getReturnType() != int.class) {
			return HEAD + "#getSize()I";
		}
		return null;
	}

	static int getSize(Object head) {
		return (Integer) Reflection.invoke(getSize, head);
	}
}
