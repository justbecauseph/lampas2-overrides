package lampas2overrides.crowdcontrol;

import java.util.ArrayList;
import java.util.List;

import dev.shadowsoffire.placebo.util.PersistentData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_power.api.SpellPower;

/**
 * Live coverage of spell crowd-control immunity on a GameTest server running real Spell Engine 1.10.9+26.2, Spell Power 1.6.2
 * and Rogues 3.1.3.003 (for {@code rogues:demoralize} and {@code rogues:shatter}). Each test casts a test spell from
 * {@code data/lampas_gametest/spell/} through {@link SpellImpacts#performImpacts}, the entry point every spell delivery uses, so
 * Spell Engine's own apply limit, boss gate and velocity code run with the mixin applied.
 *
 * <p>Protection comes from two sources. Most tests tag the mob {@code lampas.cc_immune}. The Shout and Throw test marks invaders
 * and elites through {@code dev.shadowsoffire.placebo.util.PersistentData}, which on the hermetic GameTest server is a TEST
 * DOUBLE of Placebo's class (see its javadoc): the reflective bridge and the Apotheosis marker keys are real, Placebo's storage
 * is not.
 *
 * <p>Every blocked case is paired with an unprotected control that receives the effect in the same test, so a hook that never
 * ran, or a Spell Engine classification that never loaded, fails the test instead of passing it. Zombies are created without
 * {@code finalizeSpawn}, so their knockback resistance is 0. Expected values come from the test spells and Spell Engine's
 * documented rules: a SET effect of d seconds lasts d × 20 ticks, and an apply limit passes when
 * {@code health_base + power × spell_power_multiplier >= max health}.
 */
public final class CrowdControlImmunityGameTests {
	private static final String NAMESPACE = "lampas_gametest";
	private static final double VELOCITY_TOLERANCE = 1.0e-6;

	@GameTest(maxTicks = 20)
	public void stunIsBlockedOnATaggedZombie(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob control = fixture.zombie(1, 1);
			Mob tagged = fixture.tagged(fixture.zombie(3, 1));
			Holder<Spell> stun = fixture.spell("cc_stun");

			helper.assertTrue(fixture.cast(stun, control, 0), "the stun did not land on the control zombie");
			helper.assertValueEqual(duration(control, "spell_engine:stun"), 40, "stun ticks on the control zombie");
			helper.assertFalse(fixture.cast(stun, tagged, 0), "the stun impact was performed on the tagged zombie");
			helper.assertFalse(has(tagged, "spell_engine:stun"), "the tagged zombie was stunned");
			helper.assertValueEqual(SpellEngineMixinPlugin.hookState(), SpellEngineMixinPlugin.HookState.APPLIED,
					"the Spell Engine hook state");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void movementImpairingEffectsAreBlockedOnATaggedZombie(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob control = fixture.zombie(1, 1);
			Mob tagged = fixture.tagged(fixture.zombie(3, 1));
			for (String[] effect : new String[][] {
					{"cc_immobilize", "spell_engine:immobilize", "60"},
					{"cc_slowness", "minecraft:slowness", "60"}}) {
				Holder<Spell> spell = fixture.spell(effect[0]);
				fixture.cast(spell, control, 0);
				fixture.cast(spell, tagged, 0);
				helper.assertValueEqual(duration(control, effect[1]), Integer.parseInt(effect[2]),
						effect[1] + " ticks on the control zombie");
				helper.assertFalse(has(tagged, effect[1]), "the tagged zombie received " + effect[1]);
			}
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void knockUpLeavesATaggedZombieStill(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob control = fixture.zombie(1, 1);
			Mob tagged = fixture.tagged(fixture.zombie(3, 1));
			Holder<Spell> knockUp = fixture.spell("cc_knockup");

			helper.assertTrue(fixture.cast(knockUp, control, 0), "the knock-up was not performed on the control zombie");
			// Reset to zero, then push (0, 0.8, 0) × (1 + 0 × power) × (1 − knockback resistance 0).
			assertMotion(helper, control, new Vec3(0, 0.8f, 0), "the control zombie's motion");
			helper.assertFalse(fixture.cast(knockUp, tagged, 0), "the knock-up was performed on the tagged zombie");
			assertMotion(helper, tagged, Vec3.ZERO, "the tagged zombie's motion");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void debuffsAndLevitationStillLandOnATaggedZombie(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob tagged = fixture.tagged(fixture.zombie(1, 1));
			fixture.cast(fixture.spell("debuff_bleed"), tagged, 0);
			fixture.cast(fixture.spell("debuff_weakness"), tagged, 0);
			// Levitation is not crowd control here (Q14): it is not in #lampas:spell_crowd_control.
			fixture.cast(fixture.spell("cc_levitation"), tagged, 0);

			helper.assertValueEqual(duration(tagged, "spell_engine:bleed"), 120, "bleed ticks on the tagged zombie");
			helper.assertValueEqual(duration(tagged, "minecraft:weakness"), 80, "weakness ticks on the tagged zombie");
			helper.assertValueEqual(duration(tagged, "minecraft:levitation"), 40, "levitation ticks on the tagged zombie");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void taggedRiderProtectsItsMount(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob loneSpider = fixture.mob(EntityTypes.SPIDER, 1, 1);
			Mob mount = fixture.mob(EntityTypes.SPIDER, 4, 1);
			Mob rider = fixture.tagged(fixture.mob(EntityTypes.SKELETON, 4, 1));
			helper.assertTrue(rider.startRiding(mount, true, false), "the skeleton could not mount the spider");
			Holder<Spell> stun = fixture.spell("cc_stun");

			fixture.cast(stun, loneSpider, 0);
			fixture.cast(stun, mount, 0);

			helper.assertValueEqual(duration(loneSpider, "spell_engine:stun"), 40, "stun ticks on the lone spider");
			helper.assertFalse(has(mount, "spell_engine:stun"), "the tagged skeleton's spider was stunned");
			helper.assertValueEqual(CrowdControlImmunity.reason(mount), CrowdControlImmunity.Reason.RIDDEN_BY_PROTECTED,
					"the mount's reason");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	/**
	 * Shout (Demoralize) and Throw (Shatter) at the limits lampas2-overrides 1.0.2 sets: 50 + 9.5P and 100 + 8.5P. At power 40
	 * those are 430 and 440, while the upstream 50 + 2P and 100 + 2P are 130 and 180. Every zombie has 200 max health, so only
	 * the raised limits let the effects through, and the protected zombies sit under them.
	 */
	@GameTest(maxTicks = 20)
	public void shoutAndThrowSkipInvadersAndElitesUnderTheRaisedLimits(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob plain = fixture.healthy(fixture.zombie(1, 1));
			Mob pending = fixture.healthy(fixture.zombie(3, 1));
			Mob invader = fixture.healthy(fixture.zombie(5, 1));
			Mob elite = fixture.healthy(fixture.zombie(1, 4));
			Mob upstreamControl = fixture.healthy(fixture.zombie(3, 4));
			PersistentData.of(pending).putString(ApothicBossMarkers.ELITE_KEY, "apotheosis:overworld/craig");
			PersistentData.of(invader).putBoolean(ApothicBossMarkers.INVADER_KEY, true);
			PersistentData.of(elite).putBoolean(ApothicBossMarkers.ELITE_KEY, true);
			helper.assertValueEqual(ApothicBossMarkers.state(), ApothicBossMarkers.State.READY, "the Placebo marker bridge");
			helper.assertValueEqual(CrowdControlImmunity.reason(invader), CrowdControlImmunity.Reason.INVADER, "the invader's reason");
			helper.assertValueEqual(CrowdControlImmunity.reason(elite), CrowdControlImmunity.Reason.ELITE, "the elite's reason");
			helper.assertValueEqual(CrowdControlImmunity.reason(pending), CrowdControlImmunity.Reason.NONE,
					"the pending elite's reason");

			Holder<Spell> shout = fixture.spell("shout_raised");
			Holder<Spell> throwSpell = fixture.spell("throw_raised");
			for (Mob target : List.of(plain, pending, invader, elite)) {
				fixture.cast(shout, target, 40);
				fixture.cast(throwSpell, target, 40);
			}
			fixture.cast(fixture.spell("shout_upstream"), upstreamControl, 40);
			fixture.cast(fixture.spell("throw_upstream"), upstreamControl, 40);

			for (Mob target : List.of(plain, pending)) {
				helper.assertTrue(has(target, "rogues:demoralize"), "Demoralize missing on an unprotected zombie");
				helper.assertTrue(has(target, "rogues:shatter"), "Shatter missing on an unprotected zombie");
			}
			for (Mob target : List.of(invader, elite)) {
				helper.assertFalse(has(target, "rogues:demoralize"), "Demoralize landed on a protected zombie");
				helper.assertFalse(has(target, "rogues:shatter"), "Shatter landed on a protected zombie");
			}
			helper.assertFalse(has(upstreamControl, "rogues:demoralize"), "the upstream Shout limit let Demoralize through");
			helper.assertFalse(has(upstreamControl, "rogues:shatter"), "the upstream Throw limit let Shatter through");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	/**
	 * Bosses stay as Spell Engine handles them. The GameTest datapack adds the husk to {@code #spell_engine:bosses}: Spell Engine's
	 * own gate still refuses its stun, while Shatter and Demoralize, which Spell Engine does not classify as crowd control, and the
	 * knock-up still land (Q7 and Q13 defaults).
	 */
	@GameTest(maxTicks = 20)
	public void spellEngineBossesKeepTheirOwnRules(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob boss = fixture.healthy(fixture.mob(EntityTypes.HUSK, 1, 1));
			helper.assertValueEqual(CrowdControlImmunity.reason(boss), CrowdControlImmunity.Reason.SPELL_ENGINE_BOSS,
					"the husk's reason");

			fixture.cast(fixture.spell("cc_stun"), boss, 0);
			fixture.cast(fixture.spell("shout_raised"), boss, 40);
			fixture.cast(fixture.spell("throw_raised"), boss, 40);
			helper.assertTrue(fixture.cast(fixture.spell("cc_knockup"), boss, 0), "the knock-up was not performed on the boss");

			helper.assertFalse(has(boss, "spell_engine:stun"), "Spell Engine's boss gate let the stun through");
			helper.assertTrue(has(boss, "rogues:demoralize"), "Demoralize missing on the boss");
			helper.assertTrue(has(boss, "rogues:shatter"), "Shatter missing on the boss");
			assertMotion(helper, boss, new Vec3(0, 0.8f, 0), "the boss's motion");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void playersAndNonMobRidersAreNeverProtected(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			ServerPlayer player = fixture.player();
			player.addTag(CrowdControlImmunity.IMMUNE_ENTITY_TAG);
			PersistentData.of(player).putBoolean(ApothicBossMarkers.INVADER_KEY, true);

			helper.assertFalse(CrowdControlImmunity.isProtected(player), "a tagged, marked player is protected");
			helper.assertValueEqual(CrowdControlImmunity.reason(player), CrowdControlImmunity.Reason.NONE, "the player's reason");

			// Neither the player nor a tagged armor stand protects the mob it rides.
			Mob playerMount = fixture.mob(EntityTypes.SPIDER, 1, 1);
			Mob standMount = fixture.mob(EntityTypes.SPIDER, 4, 1);
			ArmorStand stand = EntityTypes.ARMOR_STAND.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
			helper.assertTrue(stand != null, "could not create an armor stand");
			stand.snapTo(standMount.position());
			stand.addTag(CrowdControlImmunity.IMMUNE_ENTITY_TAG);
			helper.getLevel().addFreshEntity(stand);
			fixture.spawned.add(stand);
			helper.assertTrue(player.startRiding(playerMount, true, false), "the player could not mount the spider");
			helper.assertTrue(stand.startRiding(standMount, true, false), "the armor stand could not mount the spider");
			Holder<Spell> stun = fixture.spell("cc_stun");

			fixture.cast(stun, playerMount, 0);
			fixture.cast(stun, standMount, 0);

			helper.assertValueEqual(duration(playerMount, "spell_engine:stun"), 40, "stun ticks on the player's spider");
			helper.assertValueEqual(duration(standMount, "spell_engine:stun"), 40, "stun ticks on the armor stand's spider");
			helper.assertValueEqual(CrowdControlImmunity.reason(playerMount), CrowdControlImmunity.Reason.NONE,
					"the player's spider's reason");
			helper.assertValueEqual(CrowdControlImmunity.reason(standMount), CrowdControlImmunity.Reason.NONE,
					"the armor stand's spider's reason");
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void checkCommandReportsTheReasonToOperatorsOnly(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			Mob plain = fixture.zombie(1, 1);
			Mob invader = fixture.zombie(3, 1);
			PersistentData.of(invader).putBoolean(ApothicBossMarkers.INVADER_KEY, true);
			// Loads SpellImpacts, so the hook state is "applied" whichever test runs first.
			fixture.cast(fixture.spell("cc_stun"), plain, 0);
			MinecraftServer server = helper.getLevel().getServer();
			String command = "lampas ccimmunity check " + invader.getStringUUID();

			List<String> operator = new ArrayList<>();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture(operator)), command);
			helper.assertValueEqual(operator, List.of("minecraft:zombie " + invader.getStringUUID()
							+ " protected=true reason=INVADER spellEngineHook=applied placebo=READY"),
					"the operator's check output");

			List<String> player = new ArrayList<>();
			server.getCommands().performPrefixedCommand(fixture.player().createCommandSourceStack().withSource(capture(player)),
					command);
			helper.assertFalse(player.stream().anyMatch(line -> line.contains("protected=")),
					"a non-operator player could run the check: " + player);
		} finally {
			fixture.cleanUp();
		}
		helper.succeed();
	}

	/** A command output that records every message it receives. */
	private static CommandSource capture(List<String> lines) {
		return new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				lines.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
	}

	private static boolean has(LivingEntity target, String effect) {
		return target.getEffect(effect(effect)) != null;
	}

	private static int duration(LivingEntity target, String effect) {
		MobEffectInstance instance = target.getEffect(effect(effect));
		return instance == null ? 0 : instance.getDuration();
	}

	private static Holder<MobEffect> effect(String id) {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id))
				.orElseThrow(() -> new IllegalStateException("effect not registered: " + id));
	}

	private static void assertMotion(GameTestHelper helper, Entity entity, Vec3 expected, String name) {
		Vec3 actual = entity.getDeltaMovement();
		helper.assertTrue(actual.distanceTo(expected) < VELOCITY_TOLERANCE, name + " was " + actual + ", expected " + expected);
	}

	/** The caster, the mobs a test creates, and their removal. */
	private static final class Fixture {
		private final GameTestHelper helper;
		private final List<Entity> spawned = new ArrayList<>();
		private ServerPlayer caster;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
		}

		ServerPlayer player() {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 6))));
			spawned.add(player);
			return player;
		}

		Mob zombie(int x, int z) {
			return mob(EntityTypes.ZOMBIE, x, z);
		}

		<T extends Mob> T mob(EntityType<T> type, int x, int z) {
			T mob = type.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
			helper.assertTrue(mob != null, "could not create " + type);
			mob.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z))));
			mob.setNoAi(true);
			helper.getLevel().addFreshEntity(mob);
			spawned.add(mob);
			return mob;
		}

		<T extends Mob> T tagged(T mob) {
			mob.addTag(CrowdControlImmunity.IMMUNE_ENTITY_TAG);
			return mob;
		}

		/** Sets 200 max health, above the upstream Shout and Throw limits at power 40 and below the raised ones. */
		<T extends Mob> T healthy(T mob) {
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			mob.setHealth(200);
			return mob;
		}

		Holder<Spell> spell(String name) {
			return SpellRegistry.from(helper.getLevel()).get(Identifier.fromNamespaceAndPath(NAMESPACE, name))
					.map(reference -> (Holder<Spell>) reference)
					.orElseThrow(() -> new IllegalStateException("test spell not loaded: " + NAMESPACE + ":" + name));
		}

		/** Casts every impact of the spell on the target at the given power, with no crit, as Spell Engine's deliveries do. */
		boolean cast(Holder<Spell> spell, LivingEntity target, double power) {
			if (caster == null) {
				caster = player();
			}
			SpellPower.Result result = new SpellPower.Result(spell.value().school, power, 0, 0);
			SpellExecution.ImpactContext context = new SpellExecution.ImpactContext().power(result).position(target.position());
			return SpellImpacts.performImpacts(helper.getLevel(), caster, target, target, spell, spell.value().impacts, context);
		}

		void cleanUp() {
			for (Entity entity : spawned) {
				if (entity instanceof ServerPlayer player) {
					helper.getLevel().getServer().getPlayerList().remove(player);
				} else {
					entity.discard();
				}
			}
		}
	}
}
