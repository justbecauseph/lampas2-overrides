package lampas2overrides.customnamepermissions;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import xyz.eclipseisoffline.eclipsescustomname.CustomName;

/**
 * Runs Custom Name 0.4.4-26.2's real command tree with {@code require_permissions} false, the production shape, and checks
 * which sources Brigadier's {@code canUse} admits. {@code CommandNode#canUse} is the check Brigadier applies to a literal
 * while parsing a command, and it calls the requirement predicate on every use. Each source is tested in turn on the same
 * node, so a predicate that cached its first answer would fail the level-0 and level-2 cases.
 *
 * <p>Without the fix, {@code /name other} is usable at level 0 with {@code require_permissions} false, and the first
 * level-0 assertion fails. The gate was disabled once to confirm that.
 */
public final class CustomNameOtherPermissionGameTests {

	@GameTest(maxTicks = 5)
	public void otherRequiresGamemastersAndOwnNameCommandsStayOpen(GameTestHelper helper) {
		helper.assertFalse(CustomName.getConfig().requirePermissions(),
			"the GameTest config must set require_permissions false; the fix exists for that setting");
		helper.assertTrue(CustomName.getConfig().formattingEnabled(),
			"the GameTest config must enable formatting, as production does, for the /itemname and /itemlore checks");

		MinecraftServer server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();
		CommandNode<CommandSourceStack> name = root.getChild("name");
		helper.assertTrue(name != null, "/name is not registered");
		CommandNode<CommandSourceStack> other = name.getChild("other");
		helper.assertTrue(other != null, "/name other is not registered");

		CommandSourceStack level0 = server.createCommandSourceStack()
			.withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.ALL));
		CommandSourceStack level2 = server.createCommandSourceStack()
			.withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CommandSourceStack playerSource = player.createCommandSourceStack();
		helper.assertFalse(hasLevel2(player),
			"the mock player must start without operator level 2; the op test depends on it");

		helper.assertFalse(other.canUse(level0), "/name other is usable at permission level 0");
		helper.assertTrue(other.canUse(level2), "/name other is not usable at permission level 2");
		helper.assertFalse(other.canUse(playerSource), "/name other is usable by a player without operator permission");
		helper.assertFalse(other.canUse(level0), "/name other is usable at level 0 after a level-2 source was tested");

		for (String sub : List.of("nickname", "prefix", "suffix")) {
			CommandNode<CommandSourceStack> otherSub = other.getChild(sub);
			helper.assertTrue(otherSub != null, "/name other " + sub + " is not registered");
			helper.assertFalse(otherSub.canUse(level0), "/name other " + sub + " is usable at permission level 0");
			helper.assertTrue(otherSub.canUse(level2), "/name other " + sub + " is not usable at permission level 2");
		}

		// Own-name commands also require CommandSourceStack#isPlayer, so the check uses the player source, not the
		// level-0 console-like source. /itemname and /itemlore are root literals, not children of /name.
		for (String sub : List.of("nickname", "prefix", "suffix")) {
			CommandNode<CommandSourceStack> ownSub = name.getChild(sub);
			helper.assertTrue(ownSub != null, "/name " + sub + " is not registered");
			helper.assertTrue(ownSub.canUse(playerSource), "/name " + sub + " must stay open to a non-operator player");
		}
		for (String item : List.of("itemname", "itemlore")) {
			CommandNode<CommandSourceStack> itemSub = root.getChild(item);
			helper.assertTrue(itemSub != null, "/" + item + " is not registered at the root");
			helper.assertTrue(itemSub.canUse(playerSource), "/" + item + " must stay open to a non-operator player");
		}
		helper.succeed();
	}

	/**
	 * Ops the mock player at permission level 2 through the player list, then de-ops it. The permission is read on every
	 * use, so {@code /name other} is usable exactly while the player holds level 2.
	 */
	@GameTest(maxTicks = 5)
	public void otherIsUsableByAPlayerAtLevel2OnlyWhileOpped(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandNode<CommandSourceStack> other = server.getCommands().getDispatcher().getRoot()
			.getChild("name").getChild("other");
		helper.assertTrue(other != null, "/name other is not registered");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		NameAndId identity = player.nameAndId();
		CommandSourceStack playerSource = player.createCommandSourceStack();
		helper.assertFalse(hasLevel2(player), "the mock player must start without operator level 2");

		try {
			server.getPlayerList().op(identity,
				Optional.of(LevelBasedPermissionSet.forLevel(PermissionLevel.GAMEMASTERS)), Optional.empty());
			helper.assertTrue(other.canUse(playerSource),
				"/name other is not usable by an operator at permission level 2");
		} finally {
			server.getPlayerList().deop(identity);
		}
		helper.assertFalse(other.canUse(playerSource),
			"/name other is still usable by the player after the operator entry was removed");
		helper.succeed();
	}

	/** The player's own permission set, read without Custom Name, so the precondition does not depend on the gate. */
	private static boolean hasLevel2(ServerPlayer player) {
		return player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
	}
}
