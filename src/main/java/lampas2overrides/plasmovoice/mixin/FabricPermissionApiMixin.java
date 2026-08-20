package lampas2overrides.plasmovoice.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;

/** Prevents SLIB from converting wildcard permission strings into invalid Minecraft identifiers. */
@Mixin(targets = "su.plo.slib.mod.permission.FabricPermissionApi", remap = false)
abstract class FabricPermissionApiMixin {

	@Unique
	private static final Logger LAMPAS2_LOGGER =
		LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/plasmo-voice");

	@Inject(method = "createNode", at = @At("HEAD"), cancellable = true, remap = false)
	private void lampas2$declineWildcardNode(
		String permission,
		CallbackInfoReturnable<PermissionNode<Boolean>> callback
	) {
		if (permission.indexOf('*') >= 0) {
			LAMPAS2_LOGGER.info(
				"Leaving wildcard permission {} to Plasmo Voice's fallback permission handling",
				permission
			);
			callback.setReturnValue(null);
		}
	}
}
