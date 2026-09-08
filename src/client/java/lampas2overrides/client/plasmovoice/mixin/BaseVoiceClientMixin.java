package lampas2overrides.client.plasmovoice.mixin;

import lampas2overrides.client.plasmovoice.PlasmoVoiceShutdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.voice.api.client.PlasmoVoiceClient;

/** 2.1.16 omits capture/UDP cleanup from its CLIENT_STOPPING callback. */
@Pseudo
@Mixin(targets = "su.plo.voice.client.BaseVoiceClient", remap = false)
public abstract class BaseVoiceClientMixin {
    @Inject(method = "onShutdown()V", at = @At("HEAD"))
    private void lampas2overrides$closeVoiceResources(CallbackInfo ci) {
        PlasmoVoiceShutdown.cleanup((PlasmoVoiceClient) this);
    }
}
