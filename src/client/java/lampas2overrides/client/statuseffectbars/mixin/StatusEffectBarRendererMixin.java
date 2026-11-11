package lampas2overrides.client.statuseffectbars.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import lampas2overrides.client.statuseffectbars.EffectIconPosition;

/**
 * Replaces the x and y that Status Effect Bars draws its bar at with the position {@link EffectIconPosition} recorded.
 *
 * <p>Status Effect Bars calls {@code render} from {@code HudMixin} at {@code getMobEffectSprite}, with the unshifted
 * locals. The integer arguments are counted in order: ordinal 0 is x and ordinal 1 is y. When nothing was recorded, as
 * on the inventory screen where Status Effect Bars also calls {@code render}, both values pass through unchanged.
 * The target is named by string because Status Effect Bars is not a compile dependency.
 */
@Mixin(targets = "io.github.a5b84.statuseffectbars.StatusEffectBarRenderer", remap = false)
public class StatusEffectBarRendererMixin {

	private static final String RENDER =
		"render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;"
			+ "Lnet/minecraft/world/effect/MobEffectInstance;IIIILio/github/a5b84/statuseffectbars/config/StatusEffectBarsConfig$LayoutConfig;)V";

	@ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static int lampas2$effectX(int x) {
		return EffectIconPosition.x(x);
	}

	@ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private static int lampas2$effectY(int y) {
		return EffectIconPosition.y(y);
	}
}
