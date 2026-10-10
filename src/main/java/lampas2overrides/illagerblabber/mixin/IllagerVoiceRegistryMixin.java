package lampas2overrides.illagerblabber.mixin;

import java.util.Map;
import java.util.UUID;

import com.leclowndu93150.illagerblabber.stuff.voice.IllagerType;
import com.leclowndu93150.illagerblabber.stuff.voice.IllagerVoiceManager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lampas2overrides.illagerblabber.IllagerBlabberProfile;
import lampas2overrides.illagerblabber.IllagerBlabberRegistryCleanup;

/**
 * Replaces IllagerBlabber's per-tick voice update with an identity-checked manager lookup.
 *
 * <p>The vendor path interned a lock string and wrote a tick marker for every illager on every tick,
 * and its manager map kept each entity alive after unload. Its "already processed" guard could never
 * fire because the tick counter advanced on every call. This takeover keeps the same update order
 * (manager update, then state update) without those costs, and replaces a manager that still holds a
 * previous same-UUID entity, such as the pre-portal copy. Ownership is released by
 * {@link IllagerBlabberRegistryCleanup} on entity unload and on server stop.
 */
@Pseudo
@Mixin(targets = IllagerBlabberProfile.REGISTRY_TARGET, remap = false)
public abstract class IllagerVoiceRegistryMixin {

	@Shadow
	private static void updateIllagerState(AbstractIllager illager) {
		throw new AssertionError("replaced by IllagerVoiceRegistry.updateIllagerState");
	}

	@Inject(
		method = "updateIllager(Lnet/minecraft/world/entity/monster/illager/AbstractIllager;Lcom/leclowndu93150/illagerblabber/stuff/voice/IllagerType;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void lampas2$updateWithoutRegistryLeak(AbstractIllager illager, IllagerType type, CallbackInfo ci) {
		Map<UUID, Object> managers = IllagerVoiceRegistryAccessor.lampas2$voiceManagers();
		UUID id = illager.getUUID();
		Object existing = managers.get(id);
		IllagerVoiceManager manager;
		if (existing != null && IllagerBlabberRegistryCleanup.managerEntity(existing) == illager) {
			manager = (IllagerVoiceManager) existing;
		} else {
			IllagerBlabberRegistryCleanup.forget(id, IllagerBlabberRegistryCleanup.perEntityMaps());
			manager = new IllagerVoiceManager(illager, type);
			managers.put(id, manager);
		}
		manager.update();
		updateIllagerState(illager);
		ci.cancel();
	}

	@Redirect(
		method = "updateIllagerState(Lnet/minecraft/world/entity/monster/illager/AbstractIllager;)V",
		at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V")
	)
	private static void lampas2$demoteStateLog(Logger logger, String message) {
		logger.debug(message);
	}
}
