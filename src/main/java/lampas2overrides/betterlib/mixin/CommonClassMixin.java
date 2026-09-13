package lampas2overrides.betterlib.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.Lampas2Overrides;

/** Suppresses Better Lib's demo villager registration to maintain clean vanilla registries and prevent client RemapException. */
@Mixin(targets = "com.reggarf.mods.better_lib.CommonClass", remap = false)
abstract class CommonClassMixin {

	@Unique
	private static final Logger LAMPAS2_LOGGER =
		LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/better-lib");

	@Inject(method = "registerJsonVillagers", at = @At("HEAD"), cancellable = true, remap = false)
	private static void lampas2$suppressDemoVillagers(CallbackInfo ci) {
		LAMPAS2_LOGGER.info("Suppressed Better Lib demo villager registration to maintain clean vanilla registries");
		ci.cancel();
	}
}
