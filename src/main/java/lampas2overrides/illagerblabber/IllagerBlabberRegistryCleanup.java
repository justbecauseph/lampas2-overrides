package lampas2overrides.illagerblabber;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

import lampas2overrides.illagerblabber.mixin.IllagerVoiceManagerAccessor;
import lampas2overrides.illagerblabber.mixin.IllagerVoiceRegistryAccessor;

/**
 * Ownership-checked cleanup for IllagerBlabber's per-UUID registry maps.
 *
 * <p>The static methods that take plain maps are the testable core and reference no vendor type.
 * The adapters below reach the vendor maps through the accessor mixins, which exist only when the
 * artifact gate has applied them; callers must therefore run only after that gate has passed.
 */
public final class IllagerBlabberRegistryCleanup {

	private IllagerBlabberRegistryCleanup() {
	}

	/**
	 * Forgets {@code id} only when no manager exists or the manager belongs to {@code entity}. A
	 * manager that belongs to a newer instance with the same UUID is left untouched.
	 *
	 * @return whether the UUID's state was removed
	 */
	public static boolean forgetIfOwned(UUID id, Object entity, Map<UUID, ?> managers,
			Function<Object, Object> managerEntity, Collection<? extends Map<UUID, ?>> perEntityMaps) {
		Object manager = managers.get(id);
		if (manager != null && managerEntity.apply(manager) != entity) {
			return false;
		}
		forget(id, perEntityMaps);
		return true;
	}

	public static void forget(UUID id, Collection<? extends Map<UUID, ?>> perEntityMaps) {
		for (Map<UUID, ?> map : perEntityMaps) {
			map.remove(id);
		}
	}

	public static void clearAll(Collection<? extends Map<?, ?>> maps) {
		for (Map<?, ?> map : maps) {
			map.clear();
		}
	}

	/** Every vendor map keyed by illager UUID, {@code voiceManagers} included. */
	public static List<Map<UUID, ?>> perEntityMaps() {
		return List.of(
			IllagerVoiceRegistryAccessor.lampas2$voiceManagers(),
			IllagerVoiceRegistryAccessor.lampas2$hadTargetLastTick(),
			IllagerVoiceRegistryAccessor.lampas2$victoryTimers(),
			IllagerVoiceRegistryAccessor.lampas2$combatDebounceTimers(),
			IllagerVoiceRegistryAccessor.lampas2$lastProcessedTick(),
			IllagerVoiceRegistryAccessor.lampas2$lastPillagerTargets(),
			IllagerVoiceRegistryAccessor.lampas2$lastVindicatorTargets(),
			IllagerVoiceRegistryAccessor.lampas2$lastEvokerTargets());
	}

	/** Forgets an illager's state on unload, but only while that entity still owns the UUID. */
	public static void onEntityUnload(Entity entity) {
		if (!(entity instanceof AbstractIllager illager)) {
			return;
		}
		forgetIfOwned(illager.getUUID(), illager, IllagerVoiceRegistryAccessor.lampas2$voiceManagers(),
			IllagerBlabberRegistryCleanup::managerEntity, perEntityMaps());
	}

	/** Releases every entry, including the per-type group cooldowns, when a server stops. */
	public static void clearAllState() {
		clearAll(perEntityMaps());
		IllagerVoiceRegistryAccessor.lampas2$lastGroupSpottedSoundTime().clear();
	}

	public static Object managerEntity(Object manager) {
		return ((IllagerVoiceManagerAccessor) manager).getIllager();
	}
}
