package lampas2overrides.illagerblabber.mixin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import lampas2overrides.illagerblabber.IllagerBlabberProfile;

/**
 * Exposes IllagerBlabber's private static registry maps so that unload and server stop can release
 * them. Every map except {@code lastGroupSpottedSoundTime} is keyed by illager UUID.
 */
@Pseudo
@Mixin(targets = IllagerBlabberProfile.REGISTRY_TARGET, remap = false)
public interface IllagerVoiceRegistryAccessor {

	@Accessor("voiceManagers")
	static ConcurrentHashMap<UUID, Object> lampas2$voiceManagers() {
		throw new AssertionError();
	}

	@Accessor("hadTargetLastTick")
	static ConcurrentHashMap<UUID, Boolean> lampas2$hadTargetLastTick() {
		throw new AssertionError();
	}

	@Accessor("victoryTimers")
	static ConcurrentHashMap<UUID, Integer> lampas2$victoryTimers() {
		throw new AssertionError();
	}

	@Accessor("combatDebounceTimers")
	static ConcurrentHashMap<UUID, Integer> lampas2$combatDebounceTimers() {
		throw new AssertionError();
	}

	@Accessor("lastProcessedTick")
	static ConcurrentHashMap<UUID, Long> lampas2$lastProcessedTick() {
		throw new AssertionError();
	}

	@Accessor("lastPillagerTargets")
	static ConcurrentHashMap<UUID, UUID> lampas2$lastPillagerTargets() {
		throw new AssertionError();
	}

	@Accessor("lastVindicatorTargets")
	static ConcurrentHashMap<UUID, UUID> lampas2$lastVindicatorTargets() {
		throw new AssertionError();
	}

	@Accessor("lastEvokerTargets")
	static ConcurrentHashMap<UUID, UUID> lampas2$lastEvokerTargets() {
		throw new AssertionError();
	}

	@Accessor("lastGroupSpottedSoundTime")
	static ConcurrentHashMap<Object, Long> lampas2$lastGroupSpottedSoundTime() {
		throw new AssertionError();
	}
}
