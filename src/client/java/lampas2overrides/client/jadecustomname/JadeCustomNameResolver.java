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
 * <p>When the target entity is a player and Custom Name has synced an updated tab list display name
 * via {@link PlayerInfo#getTabListDisplayName()}, that formatted name is returned. In all other cases
 * or when packet/info data is missing, this falls back to {@link Entity#getDisplayName()}.
 */
public final class JadeCustomNameResolver {

	private JadeCustomNameResolver() {
	}

	public static Component resolvePlayerDisplayName(Entity entity) {
		if (entity instanceof Player player) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null) {
				ClientPacketListener connection = minecraft.getConnection();
				if (connection != null) {
					PlayerInfo info = connection.getPlayerInfo(player.getUUID());
					if (info != null) {
						Component displayName = info.getTabListDisplayName();
						if (displayName != null) {
							return displayName;
						}
					}
				}
			}
		}
		return entity != null ? entity.getDisplayName() : null;
	}

	public static Component resolvePlayerDisplayName(Entity entity, Function<Player, PlayerInfo> playerInfoLookup) {
		if (entity instanceof Player player && playerInfoLookup != null) {
			PlayerInfo info = playerInfoLookup.apply(player);
			if (info != null) {
				Component displayName = info.getTabListDisplayName();
				if (displayName != null) {
					return displayName;
				}
			}
		}
		return entity != null ? entity.getDisplayName() : null;
	}
}
