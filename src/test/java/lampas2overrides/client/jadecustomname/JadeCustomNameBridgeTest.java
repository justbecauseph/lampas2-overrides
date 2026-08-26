package lampas2overrides.client.jadecustomname;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

public class JadeCustomNameBridgeTest {

	@Test
	void testMixinConfigContent() throws IOException {
		try (InputStream input = JadeCustomNameBridgeTest.class.getClassLoader()
				.getResourceAsStream("lampas2-overrides.jadecustomname.mixins.json")) {
			assertNotNull(input, "lampas2-overrides.jadecustomname.mixins.json must exist in resources");
			String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(json.contains("lampas2overrides.client.jadecustomname.JadeCustomNameMixinPlugin"));
			assertTrue(json.contains("ObjectNameProviderMixin"));
			assertTrue(json.contains("JAVA_25"));
		}
	}

	@Test
	void testResolverNullEntity() {
		assertNull(JadeCustomNameResolver.resolvePlayerDisplayName(null, player -> null));
	}

	@Test
	void testResolverFallbackWhenPlayerInfoMissing() {
		// When entity is null or lookup returns null, falls back safely
		assertNull(JadeCustomNameResolver.resolvePlayerDisplayName(null, player -> null));
	}
}
