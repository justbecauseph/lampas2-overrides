package lampas2overrides.client.beautifulitems.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.client.beautifulitems.BeautifulItemsProfiles;

/** Drops stale Fabric extra-model keys before Beautiful Potions registers this reload's keys. */
@Pseudo
@Mixin(targets = BeautifulItemsProfiles.POTIONS_TARGET, remap = false)
public abstract class BeautifulPotionsFabricMixin {

	@Shadow
	@Final
	private static Map<?, ?> REGISTERED_MODELS;

	@Inject(
		method = "initialize(Ljava/util/Set;Lnet/fabricmc/fabric/api/client/model/loading/v1/ModelLoadingPlugin$Context;)V",
		at = @At("HEAD")
	)
	private void lampas2overrides$clearRegisteredModelKeys(CallbackInfo ci) {
		REGISTERED_MODELS.clear();
	}
}
