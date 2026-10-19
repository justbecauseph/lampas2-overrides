package lampas2overrides.client.voxyreversez.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import me.cortex.voxy.client.core.RenderProperties;

/**
 * Voxy 0.2.19-beta returns forward-Z whenever a shader pack is on, because Iris up to 1.11.2 switched the game to
 * forward-Z for packs. Iris 1.11.4 leaves the depth buffer reverse-Z, so Voxy must follow vanilla's depth direction
 * with or without a pack, as upstream's {@code 263} branch does.
 */
@Mixin(value = RenderProperties.class, remap = false)
abstract class RenderPropertiesMixin {

	@ModifyExpressionValue(
		method = "useReverseZ",
		at = @At(value = "INVOKE", target = "Lme/cortex/voxy/client/core/util/IrisUtil;irisShaderPackEnabled()Z")
	)
	private static boolean lampas2$followVanillaDepthWithShaders(boolean shaderPackEnabled) {
		return false;
	}
}
