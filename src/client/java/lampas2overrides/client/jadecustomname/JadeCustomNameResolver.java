package lampas2overrides.client.jadecustomname;

import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves the client display name for Jade when rendering entity titles.
 *
 * <p>When the target entity is a player and the server has synced an updated tab list display name
 * via {@link PlayerInfo#getTabListDisplayName()}, that formatted name is returned. Custom Name can
 * do this from the server without being installed on the client. Missing packet/info data falls
 * back to {@link Entity#getDisplayName()}.
 */
public final class JadeCustomNameResolver {

	private JadeCustomNameResolver() {
	}

	public static Component resolvePlayerDisplayName(Entity entity) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof Player player) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null) {
				ClientPacketListener connection = minecraft.getConnection();
				if (connection != null) {
					PlayerInfo info = connection.getPlayerInfo(player.getUUID());
					Component displayName = resolveTabListDisplayName(info, null);
					if (displayName != null) {
						return displayName;
					}
				}
			}
		}
		return entity.getDisplayName();
	}

	public static Component resolvePlayerDisplayName(Entity entity, Function<Player, PlayerInfo> playerInfoLookup) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof Player player && playerInfoLookup != null) {
			return resolveTabListDisplayName(playerInfoLookup.apply(player), entity.getDisplayName());
		}
		return entity.getDisplayName();
	}

	static Component resolveTabListDisplayName(PlayerInfo info, Component fallback) {
		if (info != null) {
			Component displayName = info.getTabListDisplayName();
			if (displayName != null) {
				return displayName;
			}
		}
		return fallback;
	}
}
