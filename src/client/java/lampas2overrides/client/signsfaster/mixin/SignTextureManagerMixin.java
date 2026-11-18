package lampas2overrides.client.signsfaster.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import lampas2overrides.client.signsfaster.SignsFasterBlankCheck;
import net.minecraft.world.level.block.entity.SignText;

/**
 * Signs Faster 1.0.3's {@code SignTextureManager.getOrCreateRegion} begins by returning null when the first line is
 * empty. A null region makes the caller draw nothing, and the mod has already cancelled vanilla's sign text, so a sign
 * with a blank first line and text below it shows nothing. The texture generator already skips each empty line on its
 * own, so the first-line check is the only defect. This wraps that one {@code String.isEmpty} call and answers for
 * every line: the region is skipped only when all of {@code getMessages(false)} is empty.
 */
@Mixin(targets = "org.lolip_p.signfasterlegacy.util.SignTextureManager", remap = false)
abstract class SignTextureManagerMixin {

	@WrapOperation(method = "getOrCreateRegion",
		at = @At(value = "INVOKE", target = "Ljava/lang/String;isEmpty()Z"))
	private static boolean lampas2$blankOnlyWhenAllLinesEmpty(String firstLine, Operation<Boolean> original,
			@Local(argsOnly = true) SignText text) {
		// A non-empty first line already means "has text"; only a blank first line needs the other lines checked.
		return original.call(firstLine) && SignsFasterBlankCheck.allLinesEmpty(text.getMessages(false));
	}
}
