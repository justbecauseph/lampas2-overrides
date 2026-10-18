package dev.shadowsoffire.placebo.util;

import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * TEST DOUBLE of Placebo 11.0.1's {@code PersistentData}, for the GameTest server only. It has the same class name and the same
 * public static {@code of(Entity)} and {@code peek(Entity)} signatures, so {@code ApothicBossMarkers} resolves and calls it
 * through its real reflective path. The storage is not Placebo's: real Placebo keeps the tag in the Fabric attachment
 * {@code placebo:persistent_data}, while this keeps it in memory for the life of the entity object.
 *
 * <p>Real Placebo is not used here because it pulls in Architectury 21.1.12 and, for the markers, Apotheosis; the double keeps
 * the GameTest server to Spell Engine, Spell Power and Rogues.
 */
public final class PersistentData {
	private static final Map<Entity, CompoundTag> DATA = new WeakHashMap<>();

	private PersistentData() {
	}

	/** The entity's data tag, created empty on first use, as Placebo's {@code of} does. */
	public static CompoundTag of(Entity entity) {
		return DATA.computeIfAbsent(entity, ignored -> new CompoundTag());
	}

	/** The entity's data tag, or null when none was created, as Placebo's {@code peek} does. */
	public static @Nullable CompoundTag peek(Entity entity) {
		return DATA.get(entity);
	}
}
