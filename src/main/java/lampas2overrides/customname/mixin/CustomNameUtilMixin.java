package lampas2overrides.customname.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fixes Custom Name 0.4.4-26.2 silently truncating multi-word player names at the first space
 * when operators_bypass_restrictions is false.
 *
 * <p>Root cause: {@code CustomNameCommands} passes {@code bypassRestrictions} as the
 * {@code spaceAllowed} argument to {@code CustomNameUtil#playerNameArgumentToComponent}, which
 * in turn passes it verbatim as argument index 2 of
 * {@code nameArgumentToComponent(String, boolean, boolean, boolean)}. When restrictions are
 * not bypassed, {@code spaceAllowed} is {@code false} and the name is truncated at the first
 * ASCII space — so {@code /name nickname The Admin} silently produces {@code The}.
 *
 * <p>This mixin changes only that one call-site argument to {@code true}, leaving all actual
 * restriction checks (max_name_length, blacklisted_names, permissions, group membership,
 * format validation) completely intact. Do not replace this with
 * {@code bypassRestrictions=true} in the caller — that would disable the blacklist and
 * length enforcement as well.
 *
 * <p>The patch is intentionally broad across {@code NICKNAME}, {@code PREFIX}, and
 * {@code SUFFIX} because all three use the same {@code playerNameArgumentToComponent} path.
 * Spaces are a syntax feature, not a bypass of restrictions.
 *
 * <p>Do not move this fix into {@code jadecustomname}. Jade is only a display consumer;
 * {@code /name} parsing happens on the logical server and must work on a dedicated server.
 */
@Mixin(
	targets = "xyz.eclipseisoffline.eclipsescustomname.CustomNameUtil",
	remap = false
)
public abstract class CustomNameUtilMixin {

	/**
	 * Forces {@code spaceAllowed=true} at the one internal call site within
	 * {@code playerNameArgumentToComponent}, so that multi-word names are never truncated
	 * at the first space regardless of whether bypass restrictions is active.
	 */
	@ModifyArg(
		method = "playerNameArgumentToComponent(Ljava/lang/String;Z)Lnet/minecraft/network/chat/Component;",
		at = @At(
			value = "INVOKE",
			target = "Lxyz/eclipseisoffline/eclipsescustomname/CustomNameUtil;"
				+ "nameArgumentToComponent(Ljava/lang/String;ZZZ)Lnet/minecraft/network/chat/Component;"
		),
		index = 2
	)
	private static boolean lampas2$allowSpacesInPlayerNames(boolean original) {
		return true;
	}
}
