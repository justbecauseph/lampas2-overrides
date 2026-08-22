package lampas2overrides.client.chatheads;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
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
 * prefix. It returns a tri-state {@link Resolution}:
 * <ul>
 *   <li>{@link ResolutionType#MATCH}: An unambiguous custom TAB name match was found.</li>
 *   <li>{@link ResolutionType#AMBIGUOUS}: Multiple players matched the candidate display name;
 *       cancels detection to prevent Chatting from guessing the wrong player account.</li>
 *   <li>{@link ResolutionType#NO_MATCH}: No custom display name matched; allows Chatting's native
 *       username detector to run.</li>
 * </ul>
 */
public final class ChatPlayerResolver {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/chat-resolver");

	public enum ResolutionType {
		MATCH,
		NO_MATCH,
		AMBIGUOUS
	}

	public record Resolution(ResolutionType type, @Nullable PlayerInfo player) {
		public static Resolution match(PlayerInfo player) {
			return new Resolution(ResolutionType.MATCH, player);
		}

		public static Resolution noMatch() {
			return new Resolution(ResolutionType.NO_MATCH, null);
		}

		public static Resolution ambiguous() {
			return new Resolution(ResolutionType.AMBIGUOUS, null);
		}
	}

	private ChatPlayerResolver() {
	}

	/**
	 * Resolves the {@link Resolution} for the given chat message using currently online players.
	 *
	 * @param message the raw chat message content
	 * @return the {@link Resolution}
	 */
	public static Resolution resolve(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return Resolution.noMatch();
		}

		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return Resolution.noMatch();
		}

		return resolve(connection.getOnlinePlayers(), message);
	}

	/**
	 * Resolves the {@link Resolution} for the given chat message against a candidate collection of players.
	 *
	 * @param players the online players to check
	 * @param message the raw chat message content
	 * @return the {@link Resolution}
	 */
	public static Resolution resolve(Collection<PlayerInfo> players, String message) {
		if (players == null || players.isEmpty() || message == null || message.isEmpty()) {
			return Resolution.noMatch();
		}

		String prefix = extractPrefix(message);
		if (prefix == null || prefix.isEmpty()) {
			return Resolution.noMatch();
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
			return Resolution.noMatch();
		}

		// 1. Exact TAB display-name match
		List<PlayerInfo> exactMatches = new ArrayList<>();
		for (PlayerCandidate candidate : candidates) {
			if (prefix.equals(candidate.displayName())) {
				exactMatches.add(candidate.player());
			}
		}

		if (exactMatches.size() == 1) {
			return Resolution.match(exactMatches.get(0));
		} else if (exactMatches.size() > 1) {
			LOGGER.debug("Ambiguous Chatting display-name exact match \"{}\": {} players", prefix, exactMatches.size());
			return Resolution.ambiguous();
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
			return Resolution.match(bestCandidates.get(0));
		} else if (bestCandidates.size() > 1) {
			LOGGER.debug("Ambiguous Chatting display-name match \"{}\": {} players", bestDisplayName, bestCandidates.size());
			return Resolution.ambiguous();
		}

		return Resolution.noMatch();
	}

	/**
	 * Extracts the sender portion of a chat message.
	 *
	 * <p>Supports standard colon separators ({@code Sender: message}), bracketed tags (e.g. {@code [Rank: Admin] Sender: message}),
	 * angle brackets ({@code <Sender> message}), and common arrow prefixes ({@code Sender » message}, {@code Sender -> message}).
	 */
	public static String extractPrefix(String message) {
		if (message == null || message.isEmpty()) {
			return null;
		}

		String cleanMessage = ChatFormatting.stripFormatting(message);
		if (cleanMessage == null || cleanMessage.isEmpty()) {
			return null;
		}

		int colon = findTopLevelColon(cleanMessage);
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

	private static int findTopLevelColon(String text) {
		int bracketDepth = 0;
		int parenDepth = 0;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '[' || c == '{') {
				bracketDepth++;
			} else if (c == ']' || c == '}') {
				if (bracketDepth > 0) bracketDepth--;
			} else if (c == '(') {
				parenDepth++;
			} else if (c == ')') {
				if (parenDepth > 0) parenDepth--;
			} else if (c == ':' && bracketDepth == 0 && parenDepth == 0) {
				return i;
			}
		}

		// Fallback to first colon if all colons were nested inside unclosed brackets
		return text.indexOf(':');
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
