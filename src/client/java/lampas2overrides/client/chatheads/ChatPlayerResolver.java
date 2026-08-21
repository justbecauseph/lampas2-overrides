package lampas2overrides.client.chatheads;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Resolves a chat sender to their {@link PlayerInfo} by matching custom/TAB display names.
 *
 * <p>Chatting's built-in player detector splits the text before {@code :} on non-word characters
 * ({@code \W}) and matches tokens against player usernames or single-token display names. That
 * breaks when a player has a multi-word or custom TAB display name (e.g. {@code The Administrator},
 * {@code [Admin] The Administrator}, {@code Lord Bucket of Chicken}).
 *
 * <p>This resolver gives priority to matching complete TAB display names against the chat sender
 * prefix. If no confident match is found (or if multiple players match ambiguously), this returns
 * {@code null} and allows Chatting's standard detector to handle single-token usernames.
 */
public final class ChatPlayerResolver {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/chat-resolver");

	private ChatPlayerResolver() {
	}

	/**
	 * Resolves the {@link PlayerInfo} for the given chat message using currently online players.
	 *
	 * @param message the raw chat message content
	 * @return the matching {@link PlayerInfo}, or {@code null} if no unambiguous match was found
	 */
	public static PlayerInfo resolve(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return null;
		}

		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return null;
		}

		return resolve(connection.getOnlinePlayers(), message);
	}

	/**
	 * Resolves the {@link PlayerInfo} for the given chat message against a candidate collection of players.
	 *
	 * @param players the online players to check
	 * @param message the raw chat message content
	 * @return the matching {@link PlayerInfo}, or {@code null} if no unambiguous match was found
	 */
	public static PlayerInfo resolve(Collection<PlayerInfo> players, String message) {
		if (players == null || players.isEmpty() || message == null || message.isEmpty()) {
			return null;
		}

		String prefix = extractPrefix(message);
		if (prefix == null || prefix.isEmpty()) {
			return null;
		}

		// Candidate records with a configured, non-empty TAB display name
		List<PlayerCandidate> candidates = new ArrayList<>();
		for (PlayerInfo player : players) {
			if (player == null) {
				continue;
			}
			Component tabComponent = player.getTabListDisplayName();
			if (tabComponent == null) {
				continue;
			}
			String rawName = tabComponent.getString();
			String displayName = ChatFormatting.stripFormatting(rawName);
			if (displayName == null) {
				continue;
			}
			displayName = displayName.trim();
			if (displayName.isEmpty()) {
				continue;
			}
			candidates.add(new PlayerCandidate(player, displayName));
		}

		if (candidates.isEmpty()) {
			return null;
		}

		// 1. Exact TAB display-name match
		List<PlayerInfo> exactMatches = new ArrayList<>();
		for (PlayerCandidate candidate : candidates) {
			if (prefix.equals(candidate.displayName())) {
				exactMatches.add(candidate.player());
			}
		}

		if (exactMatches.size() == 1) {
			return exactMatches.get(0);
		} else if (exactMatches.size() > 1) {
			LOGGER.debug("Ambiguous Chatting display-name exact match \"{}\": {} players", prefix, exactMatches.size());
			return null;
		}

		// 2. Longest boundary-safe contained TAB display-name match
		int bestLength = -1;
		List<PlayerInfo> bestCandidates = new ArrayList<>();
		String bestDisplayName = null;

		for (PlayerCandidate candidate : candidates) {
			String displayName = candidate.displayName();
			if (containsDisplayName(prefix, displayName)) {
				int len = displayName.length();
				if (len > bestLength) {
					bestLength = len;
					bestCandidates.clear();
					bestCandidates.add(candidate.player());
					bestDisplayName = displayName;
				} else if (len == bestLength) {
					bestCandidates.add(candidate.player());
				}
			}
		}

		if (bestCandidates.size() == 1) {
			return bestCandidates.get(0);
		} else if (bestCandidates.size() > 1) {
			LOGGER.debug("Ambiguous Chatting display-name match \"{}\": {} players", bestDisplayName, bestCandidates.size());
			return null;
		}

		return null;
	}

	/**
	 * Extracts the sender portion of a chat message.
	 *
	 * <p>Supports standard colon separators ({@code Sender: message}), angle brackets ({@code <Sender> message}),
	 * and common arrow prefixes ({@code Sender » message}, {@code Sender -> message}).
	 */
	public static String extractPrefix(String message) {
		if (message == null || message.isEmpty()) {
			return null;
		}

		String cleanMessage = ChatFormatting.stripFormatting(message);
		if (cleanMessage == null || cleanMessage.isEmpty()) {
			return null;
		}

		int colon = cleanMessage.indexOf(':');
		if (colon >= 0) {
			String prefix = cleanMessage.substring(0, colon).trim();
			return prefix.isEmpty() ? null : prefix;
		}

		int arrow = cleanMessage.indexOf('»');
		if (arrow >= 0) {
			String prefix = cleanMessage.substring(0, arrow).trim();
			return prefix.isEmpty() ? null : prefix;
		}

		int arrow2 = cleanMessage.indexOf("->");
		if (arrow2 >= 0) {
			String prefix = cleanMessage.substring(0, arrow2).trim();
			return prefix.isEmpty() ? null : prefix;
		}

		int open = cleanMessage.indexOf('<');
		int close = cleanMessage.indexOf('>');
		if (open >= 0 && close > open) {
			String prefix = cleanMessage.substring(0, close + 1).trim();
			return prefix.isEmpty() ? null : prefix;
		}

		return null;
	}

	/**
	 * Checks whether {@code haystack} contains {@code needle} surrounded by word boundaries (whitespace,
	 * start/end of string, or non-alphanumeric punctuation).
	 */
	public static boolean containsDisplayName(String haystack, String needle) {
		if (haystack == null || needle == null || needle.isEmpty() || haystack.length() < needle.length()) {
			return false;
		}

		int index = 0;
		while ((index = haystack.indexOf(needle, index)) != -1) {
			int start = index;
			int end = index + needle.length();

			boolean validBefore = (start == 0) || isBoundaryChar(haystack.charAt(start - 1));
			boolean validAfter = (end == haystack.length()) || isBoundaryChar(haystack.charAt(end));

			if (validBefore && validAfter) {
				return true;
			}
			index++;
		}

		return false;
	}

	private static boolean isBoundaryChar(char c) {
		return !(Character.isLetterOrDigit(c) || c == '_');
	}

	private record PlayerCandidate(PlayerInfo player, String displayName) {
	}
}
