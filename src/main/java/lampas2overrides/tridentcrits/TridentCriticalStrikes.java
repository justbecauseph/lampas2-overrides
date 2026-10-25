package lampas2overrides.tridentcrits;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gives thrown tridents Critical Strike crits. Critical Strike 1.0.6+26.2 rolls ranged crits in a wrap of the
 * {@code hurtOrSimulate} call in {@code AbstractArrow.onHitEntity}, but {@code ThrownTrident} overrides {@code onHitEntity}
 * without calling it, so a thrown trident never crits and the trident's crit affixes, gems and enchantments only count for
 * melee hits with it.
 *
 * <p>The thrower's crit chance and multiplier are taken when {@code TridentItem.releaseUsing} runs, while the trident is still
 * in hand and its modifiers are still on the player. Critical Strike's own arrows read the player at hit time instead, which
 * for a trident would be whatever the player holds after the throw. The hit then rolls and applies the crit the way Critical
 * Strike's {@code CritLogic.modifyDamage} and arrow wrap do: the damage source carries the multiplier, as Critical Strike's
 * own crits do, the damage is multiplied by it and by {@code balance_ranged_damage_multiplier}, and
 * {@code CritLogic.playFxAt} plays the crit sound and particles at {@code sound_ranged_crit_volume}. Each hit rolls once, from
 * the trident's random source; {@code enable_critical_strike_batching} is not honoured. No crit is rolled when
 * {@code enable_ranged_criticals} is off, for tridents thrown by a non-player, or for a trident that lost its snapshot by
 * being saved, unloaded or moved to another dimension.
 *
 * <p>Every Critical Strike member is resolved by name, once, on the first server-side throw, so Critical Strike stays off the
 * compile classpath. Without Critical Strike the bridge reports {@link State#MISSING}. The first failure to resolve or call a
 * member disables the bridge for the session, logs one warning naming it and reports {@link State#FAILED}; thrown tridents then
 * deal plain damage again. The client side also passes through {@link #beginThrow} and {@link #hurt}, but never takes a
 * snapshot, so the static state is only touched on the server thread.
 */
public final class TridentCriticalStrikes {
	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/tridentcrits");
	static final String CRITICAL_STRIKE_MOD_ID = "critical_strike";
	static final String MOD_CLASS = "net.critical_strike.CriticalStrikeMod";
	static final String CONFIG_MANAGER_CLASS = "net.tiny_config.ConfigManager";
	static final String CONFIG_CLASS = "net.critical_strike.internal.Config";
	static final String STRIKER_CLASS = "net.critical_strike.internal.CriticalStriker";
	static final String DAMAGE_SOURCE_CLASS = "net.critical_strike.api.CriticalDamageSource";
	static final String CRIT_LOGIC_CLASS = "net.critical_strike.internal.CritLogic";

	/** Whether the Critical Strike members are usable. */
	public enum State {
		/** Every member is resolved and none has failed. */
		READY,
		/** Critical Strike is not installed, so tridents never crit. */
		MISSING,
		/** Resolving or calling a member failed; the bridge is off for this session. */
		FAILED
	}

	/** The thrower's crit chance (1 is certain) and damage multiplier when the trident left their hand. */
	public record Snapshot(double chance, double multiplier) {
	}

	/** The original {@code hurtOrSimulate} call, with the damage source and amount to pass on. */
	@FunctionalInterface
	public interface Hurt {
		boolean apply(DamageSource source, float amount);
	}

	private record Throw(LivingEntity thrower, Snapshot snapshot) {
	}

	private record Settings(boolean rangedCriticals, float damageMultiplier, float soundVolume) {
	}

	/** The throw in progress on this thread, between the start and end of {@code TridentItem.releaseUsing}. */
	private static final ThreadLocal<Throw> THROWING = new ThreadLocal<>();

	private static boolean initialised;
	private static State state = State.MISSING;
	private static Class<?> striker;
	private static MethodHandle config;
	private static MethodHandle configValue;
	private static MethodHandle rangedCriticals;
	private static MethodHandle rangedDamageMultiplier;
	private static MethodHandle rangedSoundVolume;
	private static MethodHandle criticalChance;
	private static MethodHandle criticalDamageMultiplier;
	private static MethodHandle setCriticalDamageMultiplier;
	private static MethodHandle playFx;

	private TridentCriticalStrikes() {
	}

	/**
	 * Starts a trident throw by {@code user}, recording their crit stats for the {@code ThrownTrident} it creates. Returns the
	 * throw this replaces, for {@link #endThrow}.
	 */
	@Nullable
	public static Object beginThrow(LivingEntity user) {
		Throw previous = THROWING.get();
		Snapshot snapshot = user.level().isClientSide() ? null : snapshotOf(user);
		if (snapshot == null) {
			THROWING.remove();
		} else {
			THROWING.set(new Throw(user, snapshot));
		}
		return previous;
	}

	/** Ends the throw {@link #beginThrow} started, restoring the one it returned. */
	public static void endThrow(@Nullable Object previous) {
		if (previous == null) {
			THROWING.remove();
		} else {
			THROWING.set((Throw) previous);
		}
	}

	/** The snapshot for a trident created by {@code owner} during their throw, or null outside one. */
	@Nullable
	public static Snapshot snapshotFor(@Nullable Entity owner) {
		Throw current = THROWING.get();
		return current != null && current.thrower() == owner ? current.snapshot() : null;
	}

	/**
	 * Applies the trident's {@code hurtOrSimulate} call, rolling a crit from the snapshot first. Without a snapshot, with the
	 * bridge unavailable or with Critical Strike's ranged crits switched off, the hit is unchanged.
	 */
	public static boolean hurt(@Nullable Snapshot snapshot, RandomSource random, Entity target, DamageSource source, float amount,
							   Hurt hurt) {
		if (snapshot == null || state() != State.READY) {
			return hurt.apply(source, amount);
		}
		Settings settings = settings();
		if (settings == null || !settings.rangedCriticals() || !(random.nextFloat() < snapshot.chance())) {
			return hurt.apply(source, amount);
		}
		float multiplier = (float) snapshot.multiplier();
		try {
			setCriticalDamageMultiplier.invoke(source, multiplier);
		} catch (Throwable failure) {
			disable("CriticalDamageSource.rng_setCriticalDamageMultiplier", failure);
			return hurt.apply(source, amount);
		}
		boolean hit = hurt.apply(source, amount * multiplier * settings.damageMultiplier());
		if (hit) {
			try {
				playFx.invoke(target, settings.soundVolume());
			} catch (Throwable failure) {
				disable("CritLogic.playFxAt", failure);
			}
		}
		return hit;
	}

	/** Whether the bridge can give tridents crits, resolving it first if nothing has been thrown yet. */
	public static State state() {
		if (!initialised) {
			initialise();
		}
		return state;
	}

	@Nullable
	private static Snapshot snapshotOf(LivingEntity user) {
		if (state() != State.READY || !striker.isInstance(user)) {
			return null;
		}
		try {
			return new Snapshot((double) criticalChance.invoke(user), (double) criticalDamageMultiplier.invoke(user));
		} catch (Throwable failure) {
			disable("CriticalStriker crit chance and multiplier", failure);
			return null;
		}
	}

	@Nullable
	private static Settings settings() {
		try {
			Object manager = config.invoke();
			Object value = manager == null ? null : configValue.invoke(manager);
			if (value == null) {
				disable("CriticalStrikeMod.config.value", new IllegalStateException("Critical Strike's config is not loaded"));
				return null;
			}
			return new Settings((boolean) rangedCriticals.invoke(value), (float) rangedDamageMultiplier.invoke(value),
					(float) rangedSoundVolume.invoke(value));
		} catch (Throwable failure) {
			disable("Critical Strike config", failure);
			return null;
		}
	}

	private static void initialise() {
		initialised = true;
		if (!FabricLoader.getInstance().isModLoaded(CRITICAL_STRIKE_MOD_ID)) {
			state = State.MISSING;
			return;
		}
		String member = MOD_CLASS;
		try {
			ClassLoader loader = TridentCriticalStrikes.class.getClassLoader();
			Class<?> mod = Class.forName(MOD_CLASS, false, loader);
			Class<?> manager = Class.forName(member = CONFIG_MANAGER_CLASS, false, loader);
			Class<?> configClass = Class.forName(member = CONFIG_CLASS, false, loader);
			striker = Class.forName(member = STRIKER_CLASS, false, loader);
			Class<?> damageSource = Class.forName(member = DAMAGE_SOURCE_CLASS, false, loader);
			Class<?> critLogic = Class.forName(member = CRIT_LOGIC_CLASS, false, loader);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			config = lookup.findStaticGetter(mod, member = "config", manager);
			configValue = lookup.findGetter(manager, member = "value", Object.class);
			rangedCriticals = lookup.findGetter(configClass, member = "enable_ranged_criticals", boolean.class);
			rangedDamageMultiplier = lookup.findGetter(configClass, member = "balance_ranged_damage_multiplier", float.class);
			rangedSoundVolume = lookup.findGetter(configClass, member = "sound_ranged_crit_volume", float.class);
			criticalChance = lookup.findVirtual(striker, member = "rng_criticalChance", MethodType.methodType(double.class));
			criticalDamageMultiplier = lookup.findVirtual(striker, member = "rng_criticalDamageMultiplier",
					MethodType.methodType(double.class));
			setCriticalDamageMultiplier = lookup.findVirtual(damageSource, member = "rng_setCriticalDamageMultiplier",
					MethodType.methodType(void.class, float.class));
			playFx = lookup.findStatic(critLogic, member = "playFxAt", MethodType.methodType(void.class, Entity.class, float.class));
			state = State.READY;
		} catch (Throwable failure) {
			disable(member, failure);
		}
	}

	private static void disable(String member, Throwable failure) {
		if (state == State.FAILED) {
			return;
		}
		state = State.FAILED;
		LOGGER.warn("Critical Strike member {} unavailable; thrown tridents deal plain damage ({})", member, failure.toString());
	}
}
