package lampas2overrides.client.jadecustomname;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.PlayerInfo;
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
	void gateRequiresJadeOnly() {
		assertTrue(JadeCustomNameMixinPlugin.shouldApply(true));
		assertFalse(JadeCustomNameMixinPlugin.shouldApply(false));
	}

	@Test
	void testResolverNullEntity() {
		assertNull(JadeCustomNameResolver.resolvePlayerDisplayName(null, player -> null));
	}

	@Test
	void testResolverFallbackWhenPlayerInfoMissing() {
		Component fallback = Component.literal("Vanilla Player Name");
		assertSame(fallback, JadeCustomNameResolver.resolveTabListDisplayName(null, fallback));
		assertNull(JadeCustomNameResolver.resolvePlayerDisplayName(null, player -> null));
	}

	@Test
	void resolverUsesServerSyncedTabNameWithoutClientCustomName() {
		PlayerInfo info = new PlayerInfo(new GameProfile(UUID.randomUUID(), "ActualPlayer"), false);
		Component synced = Component.literal("Server Custom Name");
		Component fallback = Component.literal("ActualPlayer");
		info.setTabListDisplayName(synced);

		assertSame(synced, JadeCustomNameResolver.resolveTabListDisplayName(info, fallback));
	}

	@Test
	void resolverFallsBackWhenSyncedTabNameIsNull() {
		PlayerInfo info = new PlayerInfo(new GameProfile(UUID.randomUUID(), "ActualPlayer"), false);
		Component fallback = Component.literal("ActualPlayer");

		assertSame(fallback, JadeCustomNameResolver.resolveTabListDisplayName(info, fallback));
	}
}
