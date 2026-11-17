package lampas2overrides.spellheal;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_power.api.SpellPower;

/**
 * Live coverage of the HEAL share on a GameTest server running real Spell Engine 1.10.9+26.2 and Spell Power 1.6.2. The
 * test spells in {@code data/lampas_gametest/spell/} have a healing school and HEAL impacts, cast through
 * {@link SpellImpacts#performImpacts} with the caster's {@code spell_power:healing} at 40 (and 40 in the impact context,
 * except where a test says otherwise).
 *
 * <p>A blended impact with {@code attribute minecraft:max_health} heals (max health + sum of w × P) / (1 + sum of w) × c, so with
 * one healing entry at weight 10 and c = 1.32 the heal is 1.32 × (380 + 10 × 40) / 11 = 93.6 on a 380 max health target. The
 * upstream 1.2 × 40 is 48. The caster-read case uses the caster's max health (100): 0.275 × (100 + 10 × 40) / 11 = 12.5.
 * The appended share of Lightwell Heal and the relic perks is its own impact after the upstream one:
 * {@code heal_appended_share} has an upstream 0.25 with min_power 8 and an appended 0.025 share of the target's max health.
 * At context power 1 the upstream part clamps to 8 (2.0) and the share reads 380 (9.5), a heal of 11.5. Expected values are
 * these numbers, derived from the rule, not read back from the implementation.
 *
 * <p>The caster and the target share one scoreboard team with friendly fire off, so the target is an ally of the caster.
 */
public final class SpellHealShareGameTests {
	private static final String NAMESPACE = "lampas_gametest";
	private static final Identifier HEALING_POWER = Identifier.fromNamespaceAndPath("spell_power", "healing");
	private static final String TEAM = "lampas_heal_share";
	private static final double CASTER_POWER = 40;
	private static final double TOLERANCE = 0.01;

	@GameTest(maxTicks = 20)
	public void healShareAddsTwelvePercentOfTheTargetsMaxHealth(GameTestHelper helper) {
		Fixture fixture = null;
		try {
			fixture = new Fixture(helper);
			ServerPlayer target = fixture.ally(380, 100);

			fixture.cast("heal_upstream", target, CASTER_POWER);
			helper.assertTrue(Math.abs(target.getHealth() - 148) < TOLERANCE,
					"the upstream heal left the target at " + target.getHealth() + ", expected 148 (1.2 x 40 = 48)");

			target.setHealth(100);
			fixture.cast("heal_target_share", target, CASTER_POWER);
			helper.assertTrue(Math.abs(target.getHealth() - 193.6) < TOLERANCE,
					"the target share left the target at " + target.getHealth() + ", expected 193.6 (48 + 0.12 x 380)");
		} finally {
			if (fixture != null) {
				fixture.cleanUp();
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void casterShareUsesTheCastersMaxHealthNotTheTargets(GameTestHelper helper) {
		Fixture fixture = null;
		try {
			fixture = new Fixture(helper);
			ServerPlayer target = fixture.ally(380, 100);
			LivingEntity caster = fixture.caster();
			caster.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
			caster.setHealth(10);

			fixture.cast("heal_caster_share", target, CASTER_POWER);

			helper.assertTrue(Math.abs(target.getHealth() - 112.5) < TOLERANCE,
					"the caster share left the target at " + target.getHealth() + ", expected 112.5 (0.275 x (100 + 400) / 11)");
			helper.assertTrue(Math.abs(caster.getHealth() - 10) < TOLERANCE,
					"the caster share healed the caster to " + caster.getHealth());
		} finally {
			if (fixture != null) {
				fixture.cleanUp();
			}
		}
		helper.succeed();
	}

	/**
	 * Context power 1 is below the upstream min_power of 8, so the upstream part is 0.25 x 8 = 2. The appended share does not
	 * use the power at all: it reads the target's max health, 0.025 x 380 = 9.5. Total 11.5 on 100 health, which is 111.5.
	 */
	@GameTest(maxTicks = 20)
	public void appendedShareKeepsItsMaxHealthPartBelowMinPower(GameTestHelper helper) {
		Fixture fixture = null;
		try {
			fixture = new Fixture(helper);
			ServerPlayer target = fixture.ally(380, 100);

			fixture.cast("heal_appended_share", target, 1);

			helper.assertTrue(Math.abs(target.getHealth() - 111.5) < TOLERANCE,
					"the appended share left the target at " + target.getHealth() + ", expected 111.5 (0.25 x 8 + 0.025 x 380)");
		} finally {
			if (fixture != null) {
				fixture.cleanUp();
			}
		}
		helper.succeed();
	}

	private static Holder<Attribute> attribute(Identifier id) {
		return BuiltInRegistries.ATTRIBUTE.get(id).orElseThrow(() -> new IllegalStateException("attribute not registered: " + id));
	}

	/** The caster with healing power 40, the allies a test adds, and their removal. */
	private static final class Fixture {
		private final GameTestHelper helper;
		private final List<Entity> spawned = new ArrayList<>();
		private final Scoreboard scoreboard;
		private ServerPlayer caster;
		private PlayerTeam team;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			scoreboard = helper.getLevel().getScoreboard();
			try {
				caster = player(1, 1);
				caster.getAttribute(attribute(HEALING_POWER)).setBaseValue(CASTER_POWER);
			} catch (RuntimeException e) {
				// The caller never gets a Fixture to clean up, so a failed setup removes what it spawned here.
				cleanUp();
				throw e;
			}
		}

		LivingEntity caster() {
			return caster;
		}

		/** A second mock player on the caster's team, with the given max health and current health. */
		ServerPlayer ally(double maxHealth, double health) {
			ServerPlayer ally = player(3, 1);
			team = scoreboard.addPlayerTeam(TEAM);
			team.setAllowFriendlyFire(false);
			scoreboard.addPlayerToTeam(caster.getScoreboardName(), team);
			scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
			helper.assertTrue(ally.getTeam() == caster.getTeam() && caster.getTeam() != null,
					"the caster and the ally are not on the same team");
			ally.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
			ally.setHealth((float) health);
			return ally;
		}

		private ServerPlayer player(int x, int z) {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z))));
			spawned.add(player);
			return player;
		}

		/** Casts every impact of the named test spell on the target at the given context power, with no crit. */
		void cast(String name, LivingEntity target, double power) {
			Holder<Spell> spell = spell(name);
			SpellPower.Result result = new SpellPower.Result(spell.value().school, power, 0, 0);
			SpellExecution.ImpactContext context = new SpellExecution.ImpactContext().power(result).position(target.position());
			helper.assertTrue(SpellImpacts.performImpacts(helper.getLevel(), caster, target, target, spell, spell.value().impacts,
					context), "the " + name + " impact was not performed");
		}

		private Holder<Spell> spell(String name) {
			return SpellRegistry.from(helper.getLevel()).get(Identifier.fromNamespaceAndPath(NAMESPACE, name))
					.map(reference -> (Holder<Spell>) reference)
					.orElseThrow(() -> new IllegalStateException("test spell not loaded: " + NAMESPACE + ":" + name));
		}

		void cleanUp() {
			if (team != null) {
				scoreboard.removePlayerTeam(team);
			}
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
