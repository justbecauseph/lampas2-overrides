package lampas2overrides.client.jadecustomname.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import lampas2overrides.client.jadecustomname.JadeCustomNameResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Directs Jade's player name resolution to Custom Name's synced PlayerInfo display name.
 */
@Mixin(
	targets = "snownee.jade.addon.core.ObjectNameProvider",
	remap = false
)
public abstract class ObjectNameProviderMixin {

	@Redirect(
		method = "getEntityName(Lnet/minecraft/world/entity/Entity;Z)Lnet/minecraft/network/chat/Component;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getDisplayName()Lnet/minecraft/network/chat/Component;"
		)
	)
	private static Component lampas2$useCustomPlayerName(Entity entity) {
		return JadeCustomNameResolver.resolvePlayerDisplayName(entity);
	}
}
