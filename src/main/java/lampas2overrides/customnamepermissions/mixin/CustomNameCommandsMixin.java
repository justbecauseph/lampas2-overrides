package lampas2overrides.customnamepermissions.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.customnamepermissions.CustomNameOtherPermission;
import net.minecraft.commands.CommandSourceStack;

/**
 * Restores the operator gate on {@code /name other} under any installed Custom Name version with {@code require_permissions}
 * false. The bytecode was inspected on 0.4.4-26.2; other versions are logged as unaudited by the plugin.
 *
 * <p>{@code CustomNameCommands#permissionCheck(CommonPermissionNode)} returns {@code CommonPermissions.require(node,
 * GAMEMASTERS)} when {@code require_permissions} is true and an always-true predicate when it is false. Custom Name
 * attaches that predicate to the {@code other} literal and to each {@code other <type>} literal, so with the setting
 * false every player can run {@code /name other}. This injection runs at {@code HEAD} and, only for the
 * {@code customname:other} node, returns the predicate Custom Name builds when the setting is true. Every other node
 * keeps the original behaviour.
 *
 * <p>The predicate is a lazy {@code CommonPermissions.check(source, node, GAMEMASTERS)}. Brigadier's
 * {@code CommandNode#canUse} tests it on every use, so it reads each source's permission level at the time it is used.
 * A permissions mod that grants {@code customname:other} through Common Permissions is honoured the same way.
 *
 * <p>Does not touch {@code BYPASS_RESTRICTIONS}, which Custom Name reads through a direct {@code checkPermission} call.
 *
 * <p>The node is typed {@link Coerce Object} because {@code CommonPermissionNode} is not on the compile classpath; the
 * identity check and the {@code require} call go through {@link CustomNameOtherPermission}.
 */
@Mixin(
	targets = "xyz.eclipseisoffline.eclipsescustomname.CustomNameCommands",
	remap = false
)
public abstract class CustomNameCommandsMixin {

	@Inject(
		method = "permissionCheck(Lxyz/eclipseisoffline/commonpermissionsapi/api/CommonPermissionNode;)"
			+ "Ljava/util/function/Predicate;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void lampas2$requireGamemastersForOther(@Coerce Object node,
			CallbackInfoReturnable<Predicate<CommandSourceStack>> cir) {
		if (CustomNameOtherPermission.isOther(node)) {
			cir.setReturnValue(CustomNameOtherPermission.requireGamemasters(node));
		}
	}
}
