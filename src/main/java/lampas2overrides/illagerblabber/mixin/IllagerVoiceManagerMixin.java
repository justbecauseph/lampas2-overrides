package lampas2overrides.illagerblabber.mixin;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import lampas2overrides.illagerblabber.IllagerBlabberProfile;

/** Demotes IllagerBlabber's per-illager construction and victory logs from INFO to DEBUG. */
@Pseudo
@Mixin(targets = IllagerBlabberProfile.MANAGER_TARGET, remap = false)
public abstract class IllagerVoiceManagerMixin {

	@Redirect(
		method = "<init>(Lnet/minecraft/world/entity/monster/illager/AbstractIllager;Lcom/leclowndu93150/illagerblabber/stuff/voice/IllagerType;)V",
		at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V")
	)
	private static void lampas2$demoteConstructionLog(Logger logger, String message, Object[] arguments) {
		logger.debug(message, arguments);
	}

	@Redirect(
		method = "update()V",
		at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V")
	)
	private static void lampas2$demoteVictoryLog(Logger logger, String message, Object argument) {
		logger.debug(message, argument);
	}
}
