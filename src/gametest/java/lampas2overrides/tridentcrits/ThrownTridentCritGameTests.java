package lampas2overrides.tridentcrits;

import net.critical_strike.api.CriticalDamageSource;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

/**
 * Live coverage of thrown-trident crits on a GameTest server running the real Critical Strike 1.0.6+26.2 with its default
 * config ({@code enable_ranged_criticals} on, {@code balance_ranged_damage_multiplier} 1). Each lane has a mock player throw a
 * real trident through {@code TridentItem.releaseUsing} at a husk three blocks away (husks do not burn in daylight), with no
 * AI, no armour and 100 health, so the health it loses is the trident's damage.
 *
 * <p>Expected values come from Critical Strike's attribute rules, not from the bridge: with the innate bonus removed, a
 * {@code critical_strike:chance} value v gives a crit chance of (v - 100) / 100 and a {@code critical_strike:damage} value v
 * multiplies a crit's damage by v / 100. Every lane throws with ×2 crit damage.
 * <ul>
 * <li>The crit lane throws at 100% chance, then drops to 0% and raises crit damage to ×4 before the hit.</li>
 * <li>The plain lane throws at 0% chance and rises to 100% before the hit.</li>
 * <li>The trident lane has 0% chance of its own and throws a trident whose main-hand modifier gives +100%. Mock players are
 * creative, so the throw leaves the trident in hand; the lane empties the hand and ticks the player, which takes the
 * modifier off, before the hit.</li>
 * </ul>
 * So the crit and trident lanes' husks must each lose exactly twice the plain lane's, and only their damage sources may carry
 * the crit: chance and multiplier are the ones at the throw, including the thrown trident's own.
 */
public final class ThrownTridentCritGameTests {
	private static final Identifier CHANCE = Identifier.fromNamespaceAndPath("critical_strike", "chance");
	private static final Identifier DAMAGE = Identifier.fromNamespaceAndPath("critical_strike", "damage");
	private static final Identifier INNATE_BONUS = Identifier.fromNamespaceAndPath("critical_strike", "innate_bonus");
	private static final Identifier TRIDENT_CHANCE = Identifier.fromNamespaceAndPath("lampas_gametest", "trident_crit_chance");
	private static final double DAMAGE_TOLERANCE = 1.0e-3;

	@GameTest(maxTicks = 60)
	public void thrownTridentsCritWithTheStatsTheyWereThrownWith(GameTestHelper helper) {
		helper.assertValueEqual(TridentCriticalStrikes.state(), TridentCriticalStrikes.State.READY, "the Critical Strike bridge state");
		Lane crit = new Lane(helper, 1, 1.0);
		Lane held = new Lane(helper, 3, 0.0);
		Lane plain = new Lane(helper, 5, 0.0);

		helper.assertTrue(crit.throwTrident(new ItemStack(Items.TRIDENT)), "the crit lane's trident was not thrown");
		crit.setChance(0.0);
		crit.attribute(DAMAGE).setBaseValue(400);

		helper.assertTrue(plain.throwTrident(new ItemStack(Items.TRIDENT)), "the plain lane's trident was not thrown");
		plain.setChance(1.0);

		ItemStack critTrident = new ItemStack(Items.TRIDENT);
		critTrident.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
				.add(attribute(CHANCE), new AttributeModifier(TRIDENT_CHANCE, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
						EquipmentSlotGroup.MAINHAND)
				.build());
		held.hold(critTrident);
		held.player.doTick();
		helper.assertValueEqual(held.attribute(CHANCE).getValue(), 200.0, "crit chance value with the trident in the main hand");
		helper.assertTrue(held.release(critTrident), "the trident lane's trident was not thrown");
		held.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		held.player.doTick();
		helper.assertValueEqual(held.attribute(CHANCE).getValue(), 100.0, "crit chance value after the trident left the hand");

		helper.succeedWhen(() -> {
			float plainTaken = plain.taken();
			helper.assertTrue(crit.taken() > 0 && held.taken() > 0 && plainTaken > 0, "a trident has not hit its husk yet");
			for (Lane lane : new Lane[] { crit, held }) {
				helper.assertTrue(Math.abs(lane.taken() - 2 * plainTaken) < DAMAGE_TOLERANCE,
						"the husk at x=" + lane.x + " lost " + lane.taken() + ", expected twice the plain lane's " + plainTaken);
				helper.assertTrue(isCritical(lane.target.getLastDamageSource()), "the trident hit at x=" + lane.x + " was not marked critical");
			}
			helper.assertTrue(!isCritical(plain.target.getLastDamageSource()), "the plain lane's trident hit was marked critical");
		});
	}

	private static boolean isCritical(DamageSource source) {
		return source != null && ((CriticalDamageSource) source).rng_isCritical();
	}

	private static Holder<Attribute> attribute(Identifier id) {
		return BuiltInRegistries.ATTRIBUTE.get(id).orElseThrow(() -> new IllegalStateException("attribute not registered: " + id));
	}

	/** A mock player facing +Z at (x, 1, 1) with ×2 crit damage, and a husk at (x, 1, 4). */
	private static final class Lane {
		private final GameTestHelper helper;
		private final int x;
		private final ServerPlayer player;
		private final Mob target;

		Lane(GameTestHelper helper, int x, double chance) {
			this.helper = helper;
			this.x = x;
			player = helper.makeMockServerPlayerInLevel();
			Vec3 from = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, 1)));
			// Yaw 0 looks along +Z.
			player.snapTo(from.x, from.y, from.z, 0F, 0F);
			attribute(DAMAGE).removeModifier(INNATE_BONUS);
			attribute(DAMAGE).setBaseValue(200);
			attribute(CHANCE).removeModifier(INNATE_BONUS);
			setChance(chance);

			target = EntityTypes.HUSK.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
			helper.assertTrue(target != null, "could not create a husk");
			target.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, 4))));
			target.setNoAi(true);
			target.getAttribute(Attributes.ARMOR).setBaseValue(0);
			target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
			target.setHealth(100);
			helper.getLevel().addFreshEntity(target);
		}

		AttributeInstance attribute(Identifier id) {
			return player.getAttribute(ThrownTridentCritGameTests.attribute(id));
		}

		void setChance(double chance) {
			attribute(CHANCE).setBaseValue(100 * (1 + chance));
		}

		void hold(ItemStack trident) {
			player.setItemInHand(InteractionHand.MAIN_HAND, trident);
		}

		/** Releases a fully charged trident, as releasing the use key does. */
		boolean release(ItemStack trident) {
			return ((TridentItem) Items.TRIDENT).releaseUsing(trident, helper.getLevel(), player, trident.getUseDuration(player) - 20);
		}

		boolean throwTrident(ItemStack trident) {
			hold(trident);
			return release(trident);
		}

		float taken() {
			return target.getMaxHealth() - target.getHealth();
		}
	}
}
