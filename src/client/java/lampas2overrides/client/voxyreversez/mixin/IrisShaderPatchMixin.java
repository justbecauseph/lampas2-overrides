package lampas2overrides.client.voxyreversez.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import lampas2overrides.client.voxyreversez.VoxyReverseZ;
import me.cortex.voxy.client.iris.IrisShaderPatch;

/**
 * Iris 1.11.4 rewrites every pack program so it sees forward-Z depth, but Voxy appends the pack's Voxy patch to its
 * own shader without Iris's transform. Each getter returns the stored untransformed source, and
 * {@code IrisVoxyRenderPipelineData} reads each once per pipeline build, so the transform runs once per source.
 */
@Mixin(value = IrisShaderPatch.class, remap = false)
abstract class IrisShaderPatchMixin {

	@ModifyReturnValue(method = "getPatchOpaqueSource", at = @At("RETURN"))
	private String lampas2$transformOpaquePatch(String source) {
		return VoxyReverseZ.patchForReverseZ(source);
	}

	@ModifyReturnValue(method = "getPatchTranslucentSource", at = @At("RETURN"))
	private String lampas2$transformTranslucentPatch(String source) {
		return VoxyReverseZ.patchForReverseZ(source);
	}
}
