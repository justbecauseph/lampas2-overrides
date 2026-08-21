package lampas2overrides.client.chatheads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public class ChatPlayerResolverTest {

	private static PlayerInfo createPlayer(String ign, String tabDisplayName) {
		PlayerInfo info = new PlayerInfo(new GameProfile(UUID.randomUUID(), ign), false);
		if (tabDisplayName != null) {
			info.setTabListDisplayName(Component.literal(tabDisplayName));
		}
		return info;
	}

	@Test
	void testExtractPrefix() {
		assertEquals("The Administrator", ChatPlayerResolver.extractPrefix("The Administrator: hello world"));
		assertEquals("[Admin] The Administrator", ChatPlayerResolver.extractPrefix("[Admin] The Administrator: hello"));
		assertEquals("<The Administrator>", ChatPlayerResolver.extractPrefix("<The Administrator> hello"));
		assertEquals("[World] <The Administrator>", ChatPlayerResolver.extractPrefix("[World] <The Administrator> hello"));
		assertEquals("The Administrator", ChatPlayerResolver.extractPrefix("The Administrator » hello"));
		assertEquals("The Administrator", ChatPlayerResolver.extractPrefix("The Administrator -> hello"));
		assertEquals("The Administrator", ChatPlayerResolver.extractPrefix("§6The Administrator§r: hello"));
		assertNull(ChatPlayerResolver.extractPrefix("The Administrator was slain by Zombie"));
		assertNull(ChatPlayerResolver.extractPrefix(""));
		assertNull(ChatPlayerResolver.extractPrefix(null));
	}

	@Test
	void testContainsDisplayNameBoundary() {
		assertTrue(ChatPlayerResolver.containsDisplayName("The Administrator", "The Administrator"));
		assertTrue(ChatPlayerResolver.containsDisplayName("[Admin] The Administrator", "The Administrator"));
		assertTrue(ChatPlayerResolver.containsDisplayName("<The Administrator>", "The Administrator"));
		assertTrue(ChatPlayerResolver.containsDisplayName("[World] [Admin] The Administrator", "The Administrator"));
		assertTrue(ChatPlayerResolver.containsDisplayName("Mary-Ann", "Mary-Ann"));
		assertTrue(ChatPlayerResolver.containsDisplayName("Mary-Ann", "Ann"));

		// Must not match substrings inside longer alphanumeric words
		assertFalse(ChatPlayerResolver.containsDisplayName("Annie", "Ann"));
		assertFalse(ChatPlayerResolver.containsDisplayName("Annabelle", "Ann"));
		assertFalse(ChatPlayerResolver.containsDisplayName("[Admin] Annie", "Ann"));
		assertFalse(ChatPlayerResolver.containsDisplayName("MaryAnn", "Ann"));
		assertFalse(ChatPlayerResolver.containsDisplayName("Administrator", "Admin"));
	}

	@Test
	void testExactDisplayNameMatch() {
		PlayerInfo p1 = createPlayer("ActualIGN", "The Administrator");
		List<PlayerInfo> players = List.of(p1);

		PlayerInfo resolved = ChatPlayerResolver.resolve(players, "The Administrator: hello");
		assertSame(p1, resolved);
	}

	@Test
	void testContainedDisplayNameMatch() {
		PlayerInfo p1 = createPlayer("ActualIGN", "The Administrator");
		List<PlayerInfo> players = List.of(p1);

		assertSame(p1, ChatPlayerResolver.resolve(players, "[Admin] The Administrator: hello"));
		assertSame(p1, ChatPlayerResolver.resolve(players, "<The Administrator> hello"));
		assertSame(p1, ChatPlayerResolver.resolve(players, "[World] <The Administrator> hello"));
	}

	@Test
	void testLongestMatchPrecedence() {
		PlayerInfo pAdmin = createPlayer("User1", "Admin");
		PlayerInfo pFull = createPlayer("User2", "The Administrator");
		List<PlayerInfo> players = List.of(pAdmin, pFull);

		// "[Staff] The Administrator: hi" -> longest match is "The Administrator"
		assertSame(pFull, ChatPlayerResolver.resolve(players, "[Staff] The Administrator: hi"));

		// "[Admin] The Administrator: hi" -> both "Admin" and "The Administrator" match boundaries,
		// but "The Administrator" is longer (17 > 5)
		assertSame(pFull, ChatPlayerResolver.resolve(players, "[Admin] The Administrator: hi"));

		// "Admin: hi" -> exact match for Admin
		assertSame(pAdmin, ChatPlayerResolver.resolve(players, "Admin: hi"));
	}

	@Test
	void testSubstringCollisionDoesNotMatch() {
		PlayerInfo pAnn = createPlayer("AnnIGN", "Ann");
		List<PlayerInfo> players = List.of(pAnn);

		// "Annie: hi" should not match "Ann"
		assertNull(ChatPlayerResolver.resolve(players, "Annie: hi"));
	}

	@Test
	void testNullTabListDisplayNameFallsThrough() {
		PlayerInfo pSteve = createPlayer("Steve", null);
		List<PlayerInfo> players = List.of(pSteve);

		// Normal usernames without custom TAB display name should return null so Chatting handles them
		assertNull(ChatPlayerResolver.resolve(players, "Steve: hello"));
	}

	@Test
	void testFormattedTabListDisplayName() {
		PlayerInfo p1 = createPlayer("ActualIGN", "§c[Admin] §6The Administrator§r");
		List<PlayerInfo> players = List.of(p1);

		assertSame(p1, ChatPlayerResolver.resolve(players, "[Admin] The Administrator: hello"));
		assertSame(p1, ChatPlayerResolver.resolve(players, "§c[Admin] §6The Administrator§r: hello"));
	}

	@Test
	void testDuplicateExactDisplayNameAmbiguity() {
		PlayerInfo p1 = createPlayer("User1", "The Administrator");
		PlayerInfo p2 = createPlayer("User2", "The Administrator");
		List<PlayerInfo> players = List.of(p1, p2);

		// Ambiguous exact match -> must return null
		assertNull(ChatPlayerResolver.resolve(players, "The Administrator: hello"));
	}

	@Test
	void testDuplicateContainedDisplayNameAmbiguity() {
		PlayerInfo p1 = createPlayer("User1", "The Administrator");
		PlayerInfo p2 = createPlayer("User2", "The Administrator");
		List<PlayerInfo> players = List.of(p1, p2);

		// Ambiguous contained match -> must return null
		assertNull(ChatPlayerResolver.resolve(players, "[Admin] The Administrator: hello"));
	}

	@Test
	void testEqualLengthCandidatesAmbiguity() {
		PlayerInfo p1 = createPlayer("User1", "Mark");
		PlayerInfo p2 = createPlayer("User2", "John");
		List<PlayerInfo> players = List.of(p1, p2);

		// Both "Mark" and "John" have length 4 and are contained in "[Mark and John]"
		assertNull(ChatPlayerResolver.resolve(players, "[Mark and John]: hello"));
	}
}
