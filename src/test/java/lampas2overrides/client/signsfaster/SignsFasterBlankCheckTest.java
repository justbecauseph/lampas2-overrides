package lampas2overrides.client.signsfaster;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

class SignsFasterBlankCheckTest {

	private static Component[] lines(String... texts) {
		Component[] lines = new Component[texts.length];
		for (int i = 0; i < texts.length; i++) {
			lines[i] = Component.literal(texts[i]);
		}
		return lines;
	}

	@Test
	void signWithNoTextOnAnyLineIsBlank() {
		assertTrue(SignsFasterBlankCheck.allLinesEmpty(lines("", "", "", "")));
	}

	@Test
	void textOnAnyLineMakesTheSignNotBlank() {
		assertFalse(SignsFasterBlankCheck.allLinesEmpty(lines("", "Market", "", "")));
		assertFalse(SignsFasterBlankCheck.allLinesEmpty(lines("meow", "", "", "")));
		assertFalse(SignsFasterBlankCheck.allLinesEmpty(lines("", "", "", "x")));
	}
}
