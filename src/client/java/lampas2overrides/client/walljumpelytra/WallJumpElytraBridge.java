package lampas2overrides.client.walljumpelytra;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lampas2overrides.Lampas2Overrides;
import lampas2overrides.client.compat.BridgeException;
import lampas2overrides.client.compat.Reflection;
import lampas2overrides.walljumpelytra.StopFallFlyingC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reflective client bridge to Wall-Jump TXF's config and movement state. No Wall-Jump TXF type is referenced at
 * compile time; a missing or failing member disables the bridge loudly once and falls back to vanilla behavior.
 */
public final class WallJumpElytraBridge {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/wall-jump-elytra");

	private static final String WALL_JUMP_CLIENT = "com.jahirtrap.walljump.WallJumpClient";
	private static final String MOD_CONFIG = "com.jahirtrap.walljump.init.ModConfig";
	private static final String WALL_JUMP_LOGIC = "com.jahirtrap.walljump.logic.WallJumpLogic";
	private static final String DOUBLE_JUMP_LOGIC = "com.jahirtrap.walljump.logic.DoubleJumpLogic";

	private static final WallJumpElytraPolicy POLICY = new WallJumpElytraPolicy();

	private static boolean attemptedBind;
	private static boolean bound;
	private static boolean disabled;

	private static Field keyWallJumpField;
	private static Field enableEnchantmentsField;
	private static Field enableDoubleJumpField;
	private static Field useDoubleJumpField;
	private static Field onFallDoubleJumpField;
	private static Field blockListField;
	private static Field blockListModeField;
	private static Field ticksWallClingedField;
	private static Field wallJumpCountField;
	private static Field stopSlidField;
	private static Field ticksKeyDownField;
	private static Method canWallJumpMethod;
	private static Field doubleJumpCountField;
	private static Field doubleJumpKeyField;

	private WallJumpElytraBridge() {
	}

	/** Updates the post-wall-jump grace tracker and cling-latch at the start of {@code LocalPlayer#aiStep}. */
	public static void onAiStepHead(LocalPlayer player) {
		if (!ensureBound()) {
			return;
		}
		try {
			int wallJumpCount = readInt(wallJumpCountField, WALL_JUMP_LOGIC + ".wallJumpCount");
			boolean resetGrace = player.onGround()
				|| player.getAbilities().flying
				|| player.isInWater()
				|| player.isFallFlying();
			POLICY.onTickHead(player, wallJumpCount, resetGrace);
			POLICY.releaseClingSuppressionIfNeeded(readWallJumpKeyDown(), player.onGround());
		} catch (BridgeException e) {
			disable("failed reading Wall-Jump TXF tick state", e);
		}
	}

	/** Returns {@code true} when vanilla jump-to-elytra activation should be skipped for this tick. */
	public static boolean shouldSuppressFallFlying(LocalPlayer player) {
		if (!ensureBound()) {
			return false;
		}
		try {
			int ticksWallClinged = readInt(ticksWallClingedField, WALL_JUMP_LOGIC + ".ticksWallClinged");
			boolean stopSlid = readBoolean(stopSlidField, WALL_JUMP_LOGIC + ".stopSlid");
			boolean wallJumpKeyDown = readWallJumpKeyDown();
			int ticksKeyDown = readInt(ticksKeyDownField, WALL_JUMP_LOGIC + ".ticksKeyDown");
			boolean canWallJump = invokeBoolean(canWallJumpMethod, WALL_JUMP_LOGIC + ".canWallJump", player);

			boolean doubleJumpEnabled =
				(readBoolean(enableEnchantmentsField, MOD_CONFIG + ".enableEnchantments")
					&& readBoolean(enableDoubleJumpField, MOD_CONFIG + ".enableDoubleJump"))
					|| readBoolean(useDoubleJumpField, MOD_CONFIG + ".useDoubleJump");
			int doubleJumpCount = readInt(doubleJumpCountField, DOUBLE_JUMP_LOGIC + ".jumpCount");
			boolean doubleJumpKeyHeld = readBoolean(doubleJumpKeyField, DOUBLE_JUMP_LOGIC + ".jumpKey");
			boolean onFallDoubleJump = readBoolean(onFallDoubleJumpField, MOD_CONFIG + ".onFallDoubleJump");

			boolean needWallCheck = !POLICY.isClingSuppressedUntilKeyRelease()
				&& ticksWallClinged <= 0 && !stopSlid && wallJumpKeyDown && ticksKeyDown < 3 && canWallJump;
			boolean hasAdjacentAllowedWall = false;
			boolean wallClingClearOfGround = false;
			if (needWallCheck) {
				hasAdjacentAllowedWall = hasAdjacentAllowedWall(player);
				wallClingClearOfGround = player.level().noCollision(player.getBoundingBox().move(0.0, -0.8, 0.0));
			}

			boolean inUpperLiquid = false;
			if (doubleJumpEnabled && doubleJumpCount > 0 && !doubleJumpKeyHeld
				&& !player.onGround() && !player.isPassenger() && !player.getAbilities().mayfly) {
				inUpperLiquid = isInUpperLiquid(player);
			}

			WallJumpElytraPolicy.Snapshot snapshot = new WallJumpElytraPolicy.Snapshot(
				ticksWallClinged,
				canWallJump,
				stopSlid,
				wallJumpKeyDown,
				ticksKeyDown,
				hasAdjacentAllowedWall,
				wallClingClearOfGround,
				player.onClimbable(),
				player.onGround(),
				player.getAbilities().flying,
				player.level().getFluidState(player.blockPosition()).isEmpty(),
				player.isHandsBusy(),
				player.getFoodData().getFoodLevel(),
				player.getDeltaMovement().y,
				doubleJumpEnabled,
				doubleJumpCount,
				doubleJumpKeyHeld,
				player.isPassenger(),
				player.getAbilities().mayfly,
				inUpperLiquid,
				onFallDoubleJump
			);
			return POLICY.shouldSuppressElytra(snapshot);
		} catch (BridgeException e) {
			disable("failed evaluating Wall-Jump TXF elytra suppression", e);
			return false;
		}
	}

	/**
	 * Called from {@code WallJumpLogic.doWallJump} via {@code isHandsBusy()}: blocks wall-clinging while gliding and
	 * cancels elytra flight when pressing/holding the cling key against an adjacent wall without clinging on that grab.
	 */
	public static boolean handleWallClingWhileGliding(LocalPlayer player) {
		if (!ensureBound()) {
			return false;
		}
		try {
			boolean wallJumpKeyDown = readWallJumpKeyDown();
			boolean isFallFlying = player.isFallFlying();
			boolean hasAdjacentAllowedWall = isFallFlying && wallJumpKeyDown && hasAdjacentAllowedWall(player);
			WallJumpElytraPolicy.WallClingAction action =
				POLICY.evaluateWallCling(isFallFlying, wallJumpKeyDown, hasAdjacentAllowedWall);
			if (action == WallJumpElytraPolicy.WallClingAction.CANCEL_ELYTRA_AND_BLOCK) {
				if (ClientPlayNetworking.canSend(StopFallFlyingC2SPayload.TYPE)) {
					player.stopFallFlying();
					ClientPlayNetworking.send(StopFallFlyingC2SPayload.INSTANCE);
				}
				return true;
			}
			return action == WallJumpElytraPolicy.WallClingAction.BLOCK;
		} catch (BridgeException e) {
			disable("failed handling Wall-Jump TXF cling check while gliding", e);
			return false;
		}
	}

	private static boolean readWallJumpKeyDown() {
		KeyMapping wallJumpKey = (KeyMapping) Reflection.read(keyWallJumpField, null);
		if (wallJumpKey == null) {
			throw new BridgeException(WALL_JUMP_CLIENT + ".KEY_WALL_JUMP is null");
		}
		return wallJumpKey.isDown();
	}

	/**
	 * Read-only equivalent of {@code WallJumpLogic.updateWalls(LocalPlayer)}: never mutates
	 * {@code player.horizontalCollision} or Wall-Jump TXF's static sets.
	 */
	@SuppressWarnings("unchecked")
	private static boolean hasAdjacentAllowedWall(LocalPlayer player) {
		Level level = player.level();
		Vec3 pos = player.position();
		AABB core = new AABB(
			pos.x - 0.001,
			pos.y,
			pos.z - 0.001,
			pos.x + 0.001,
			pos.y + player.getEyeHeight(),
			pos.z + 0.001
		);
		double dist = player.getBbWidth() / 2.0 + 0.06;

		Object rawList = Reflection.read(blockListField, null);
		List<String> blockList = rawList instanceof List<?> list ? (List<String>) list : List.of();
		Object rawMode = Reflection.read(blockListModeField, null);
		String modeName = rawMode instanceof Enum<?> mode ? mode.name() : "DISABLED";
		BlockPos basePos = player.blockPosition();

		for (int i = 0; i < 4; i++) {
			Direction dir = Direction.from2DDataValue(i);
			AABB probe = core.expandTowards(dir.getStepX() * dist, 0.0, dir.getStepZ() * dist);
			if (!level.noCollision(probe)) {
				String lowerId = solidBlockId(level, basePos.relative(dir));
				String upperId = solidBlockId(level, basePos.above().relative(dir));
				if (WallJumpElytraPolicy.isBlockAllowed(modeName, blockList, lowerId, upperId)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String solidBlockId(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isSolid() ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : null;
	}

	private static boolean isInUpperLiquid(LocalPlayer player) {
		Vec3 pos = player.position();
		AABB upper = new AABB(
			pos.x,
			pos.y + player.getEyeHeight() * 0.8,
			pos.z,
			pos.x,
			pos.y + player.getBbHeight(),
			pos.z
		);
		return player.level().containsAnyLiquid(upper);
	}

	private static boolean ensureBound() {
		if (disabled) {
			return false;
		}
		if (bound) {
			return true;
		}
		if (attemptedBind) {
			return false;
		}
		attemptedBind = true;
		String missing = bind();
		if (missing != null) {
			disabled = true;
			LOGGER.error("Wall-Jump TXF member {} is unavailable; elytra compatibility is disabled", missing);
			return false;
		}
		bound = true;
		return true;
	}

	private static String bind() {
		Class<?> wallJumpClient = Reflection.findClass(WALL_JUMP_CLIENT);
		if (wallJumpClient == null) {
			return WALL_JUMP_CLIENT;
		}
		keyWallJumpField = Reflection.findField(wallJumpClient, "KEY_WALL_JUMP");
		if (keyWallJumpField == null) {
			return WALL_JUMP_CLIENT + ".KEY_WALL_JUMP";
		}

		Class<?> modConfig = Reflection.findClass(MOD_CONFIG);
		if (modConfig == null) {
			return MOD_CONFIG;
		}
		enableEnchantmentsField = Reflection.findField(modConfig, "enableEnchantments");
		if (enableEnchantmentsField == null) {
			return MOD_CONFIG + ".enableEnchantments";
		}
		enableDoubleJumpField = Reflection.findField(modConfig, "enableDoubleJump");
		if (enableDoubleJumpField == null) {
			return MOD_CONFIG + ".enableDoubleJump";
		}
		useDoubleJumpField = Reflection.findField(modConfig, "useDoubleJump");
		if (useDoubleJumpField == null) {
			return MOD_CONFIG + ".useDoubleJump";
		}
		onFallDoubleJumpField = Reflection.findField(modConfig, "onFallDoubleJump");
		if (onFallDoubleJumpField == null) {
			return MOD_CONFIG + ".onFallDoubleJump";
		}
		blockListField = Reflection.findField(modConfig, "blockList");
		if (blockListField == null) {
			return MOD_CONFIG + ".blockList";
		}
		blockListModeField = Reflection.findField(modConfig, "blockListMode");
		if (blockListModeField == null) {
			return MOD_CONFIG + ".blockListMode";
		}

		Class<?> wallJumpLogic = Reflection.findClass(WALL_JUMP_LOGIC);
		if (wallJumpLogic == null) {
			return WALL_JUMP_LOGIC;
		}
		ticksWallClingedField = Reflection.findField(wallJumpLogic, "ticksWallClinged");
		if (ticksWallClingedField == null) {
			return WALL_JUMP_LOGIC + ".ticksWallClinged";
		}
		wallJumpCountField = Reflection.findField(wallJumpLogic, "wallJumpCount");
		if (wallJumpCountField == null) {
			return WALL_JUMP_LOGIC + ".wallJumpCount";
		}
		stopSlidField = Reflection.findField(wallJumpLogic, "stopSlid");
		if (stopSlidField == null) {
			return WALL_JUMP_LOGIC + ".stopSlid";
		}
		ticksKeyDownField = Reflection.findField(wallJumpLogic, "ticksKeyDown");
		if (ticksKeyDownField == null) {
			return WALL_JUMP_LOGIC + ".ticksKeyDown";
		}
		canWallJumpMethod = Reflection.findMethod(wallJumpLogic, "canWallJump", LocalPlayer.class);
		if (canWallJumpMethod == null) {
			return WALL_JUMP_LOGIC + ".canWallJump(LocalPlayer)";
		}

		Class<?> doubleJumpLogic = Reflection.findClass(DOUBLE_JUMP_LOGIC);
		if (doubleJumpLogic == null) {
			return DOUBLE_JUMP_LOGIC;
		}
		doubleJumpCountField = Reflection.findField(doubleJumpLogic, "jumpCount");
		if (doubleJumpCountField == null) {
			return DOUBLE_JUMP_LOGIC + ".jumpCount";
		}
		doubleJumpKeyField = Reflection.findField(doubleJumpLogic, "jumpKey");
		if (doubleJumpKeyField == null) {
			return DOUBLE_JUMP_LOGIC + ".jumpKey";
		}
		return null;
	}

	private static int readInt(Field field, String label) {
		Object value = Reflection.read(field, null);
		if (!(value instanceof Integer number)) {
			throw new BridgeException(label + " did not return an Integer");
		}
		return number;
	}

	private static boolean readBoolean(Field field, String label) {
		Object value = Reflection.read(field, null);
		if (!(value instanceof Boolean flag)) {
			throw new BridgeException(label + " did not return a Boolean");
		}
		return flag;
	}

	private static boolean invokeBoolean(Method method, String label, Object... args) {
		Object value = Reflection.invoke(method, null, args);
		if (!(value instanceof Boolean flag)) {
			throw new BridgeException(label + " did not return a Boolean");
		}
		return flag;
	}

	private static void disable(String message, Throwable error) {
		disabled = true;
		LOGGER.error("{}; disabling Wall-Jump TXF elytra compatibility", message, error);
	}
}
