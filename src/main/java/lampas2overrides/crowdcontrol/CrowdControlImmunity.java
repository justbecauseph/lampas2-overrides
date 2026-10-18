package lampas2overrides.crowdcontrol;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Decides which mobs RPG Series (Spell Engine) crowd control cannot land on: Apotheosis invaders and elites, the mounts
 * they ride, and mobs that staff tag {@value #IMMUNE_ENTITY_TAG}. Spell Engine's own {@code #spell_engine:bosses} stay
 * as Spell Engine handles them; they are reported here only for {@code /lampas ccimmunity check}.
 *
 * <p>Only non-player {@link Mob}s are protected. Spell Engine classifies some player self-buffs as movement-impairing,
 * so protecting players would change their own spells. Only mob passengers protect their mount: a player, armor stand or
 * display entity riding a mob does not, whatever its tags.
 */
public final class CrowdControlImmunity {
	/** Vanilla entity tag ({@code /tag <mob> add lampas.cc_immune}) that makes a mob immune, as an invader is. */
	public static final String IMMUNE_ENTITY_TAG = "lampas.cc_immune";
	/** Effects that count as crowd control on protected mobs in addition to Spell Engine's own classification. */
	public static final TagKey<MobEffect> EXTRA_CROWD_CONTROL =
			TagKey.create(Registries.MOB_EFFECT, Identifier.fromNamespaceAndPath("lampas", "spell_crowd_control"));
	/** Spell Engine's boss tag, which its own boss gate reads. */
	public static final TagKey<EntityType<?>> SPELL_ENGINE_BOSSES =
			TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("spell_engine", "bosses"));

	/** Why a mob is or is not immune, in the order {@link #reason} checks. */
	public enum Reason {
		INVADER(true),
		ELITE(true),
		ENTITY_TAG(true),
		RIDDEN_BY_PROTECTED(true),
		SPELL_ENGINE_BOSS(false),
		NONE(false);

		private final boolean protectedMob;

		Reason(boolean protectedMob) {
			this.protectedMob = protectedMob;
		}

		/** Whether this reason makes the mob protected. A Spell Engine boss is immune through Spell Engine, not through this mod. */
		public boolean isProtected() {
			return protectedMob;
		}
	}

	private CrowdControlImmunity() {
	}

	/** Whether the mob is an invader, an elite, tagged {@value #IMMUNE_ENTITY_TAG}, or ridden by such a mob. */
	public static boolean isProtected(Entity entity) {
		return entity instanceof Mob && entity.getSelfAndPassengers().anyMatch(CrowdControlImmunity::protectsItself);
	}

	/** The first reason that applies, checking the mob itself, then its passengers, then the Spell Engine boss tag. */
	public static Reason reason(Entity entity) {
		if (entity instanceof Mob) {
			Reason own = ownReason(entity);
			if (own != Reason.NONE) {
				return own;
			}
			if (entity.getSelfAndPassengers().anyMatch(passenger -> passenger != entity && protectsItself(passenger))) {
				return Reason.RIDDEN_BY_PROTECTED;
			}
		}
		if (entity.is(SPELL_ENGINE_BOSSES)) {
			return Reason.SPELL_ENGINE_BOSS;
		}
		return Reason.NONE;
	}

	private static boolean protectsItself(Entity entity) {
		return ownReason(entity) != Reason.NONE;
	}

	/** The mob's own reason. Players and entities that are not mobs (displays, armor stands) never have one. */
	private static Reason ownReason(Entity entity) {
		if (!(entity instanceof Mob)) {
			return Reason.NONE;
		}
		switch (ApothicBossMarkers.markerOf(entity)) {
			case INVADER:
				return Reason.INVADER;
			case ELITE:
				return Reason.ELITE;
			default:
				break;
		}
		return entity.entityTags().contains(IMMUNE_ENTITY_TAG) ? Reason.ENTITY_TAG : Reason.NONE;
	}
}
