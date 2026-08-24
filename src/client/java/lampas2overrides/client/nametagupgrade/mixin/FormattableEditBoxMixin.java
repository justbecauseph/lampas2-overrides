package lampas2overrides.client.nametagupgrade.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Prevents StringIndexOutOfBoundsException when dragging text selection past the left edge
 * of a horizontally scrolled FormattableEditBox.
 */
@Mixin(
	targets = "fuzs.nametagupgrade.common.client.gui.components.FormattableEditBox",
	remap = false
)
public abstract class FormattableEditBoxMixin {

	@ModifyArg(
		method = "findClickedPositionInText(Lnet/minecraft/client/input/MouseButtonEvent;)I",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/Math;min(II)I"
		),
		index = 0
	)
	private int lampas2$clampMouseOffset(int mouseOffset) {
		return Math.max(0, mouseOffset);
	}
}
