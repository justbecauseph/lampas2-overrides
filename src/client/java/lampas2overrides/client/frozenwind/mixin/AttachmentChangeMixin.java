package lampas2overrides.client.frozenwind.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import lampas2overrides.client.frozenwind.WindManagerDecoded;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.frozenblock.lib.wind.WindManager;

/** Installs decoded wind state where Fabric has validated the attachment target. */
@Mixin(targets = "net.fabricmc.fabric.impl.attachment.sync.AttachmentChange", remap = false)
public abstract class AttachmentChangeMixin {

	@WrapOperation(
		method = "tryApply(Lnet/minecraft/world/level/Level;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/fabricmc/fabric/api/attachment/v1/AttachmentTarget;setAttached(Lnet/fabricmc/fabric/api/attachment/v1/AttachmentType;Ljava/lang/Object;)Ljava/lang/Object;"
		),
		require = 1
	)
	private Object lampas2$applyDecodedWind(AttachmentTarget target, AttachmentType<?> type, Object value,
			Operation<Object> original) {
		if (type == WindManager.ATTACHMENT_TYPE && value instanceof WindManagerDecoded decoded) {
			value = decoded.lampas2$applyDecodedToSingleton();
		}
		return original.call(target, type, value);
	}
}
