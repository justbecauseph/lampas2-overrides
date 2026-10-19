package lampas2overrides.client.voxyreversez.mixin;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import lampas2overrides.client.voxyreversez.VoxyReverseZ;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.iris.VoxyUniforms;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;

/**
 * Pack code reads Voxy depth as forward-Z after Iris's transform, so it must unproject with forward-Z matrices. The
 * inverse and previous-frame uniforms call these getters, so they convert too. {@code vxNearPlane} and
 * {@code vxFarPlane} are backported from upstream's {@code 263} branch for packs written against it.
 */
@Mixin(value = VoxyUniforms.class, remap = false)
abstract class VoxyUniformsMixin {

	@ModifyReturnValue(method = "getViewProjection", at = @At("RETURN"))
	private static Matrix4f lampas2$forwardZViewProjection(Matrix4f matrix) {
		return lampas2$toForwardZ(matrix);
	}

	@ModifyReturnValue(method = "getProjection", at = @At("RETURN"))
	private static Matrix4f lampas2$forwardZProjection(Matrix4f matrix) {
		return lampas2$toForwardZ(matrix);
	}

	@Inject(method = "addUniforms", at = @At("TAIL"))
	private static void lampas2$addPlaneUniforms(UniformHolder uniforms, CallbackInfo ci) {
		uniforms
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "vxNearPlane", VoxyReverseZ::nearPlane)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "vxFarPlane", VoxyReverseZ::farPlane);
	}

	/**
	 * The getters return an identity placeholder without a render system, and a fresh viewport's matrices are identity
	 * until its first frame; JOML's copy keeps the identity flag, so those stay unconverted.
	 */
	private static Matrix4f lampas2$toForwardZ(Matrix4f matrix) {
		if ((matrix.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0) {
			return matrix;
		}
		VoxyRenderSystem renderSystem = IVoxyRenderSystemHolder.getNullable();
		Viewport<?> viewport = renderSystem == null ? null : renderSystem.getViewport();
		if (viewport == null) {
			return matrix;
		}
		RenderProperties properties = ((ViewportAccessor) viewport).lampas2$getProperties();
		if (properties == null || !properties.isReverseZ()) {
			return matrix;
		}
		return VoxyReverseZ.toForwardZ(matrix, properties.isZero2One());
	}
}
