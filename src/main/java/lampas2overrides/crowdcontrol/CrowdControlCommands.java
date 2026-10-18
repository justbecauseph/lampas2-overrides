package lampas2overrides.crowdcontrol;

import java.util.Collection;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * {@code /lampas ccimmunity check <targets>}: read-only, op level 2. Prints one line per entity saying whether it is protected
 * from spell crowd control and why, plus whether the Spell Engine hook and the Placebo marker lookup are working. Registered
 * only when Spell Engine is installed.
 */
public final class CrowdControlCommands implements ModInitializer {

	@Override
	public void onInitialize() {
		if (FabricLoader.getInstance().isModLoaded(SpellEngineMixinPlugin.SPELL_ENGINE_MOD_ID)) {
			CommandRegistrationCallback.EVENT.register(CrowdControlCommands::registerCommands);
		}
	}

	public static void registerCommands(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection
	) {
		dispatcher.register(
				Commands.literal("lampas")
						.then(Commands.literal("ccimmunity")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("check")
										.then(Commands.argument("targets", EntityArgument.entities())
												.executes(ctx -> check(ctx.getSource(),
														EntityArgument.getEntities(ctx, "targets"))))))
		);
	}

	private static int check(CommandSourceStack source, Collection<? extends Entity> targets) {
		String hook = SpellEngineMixinPlugin.hookState().name().toLowerCase(Locale.ROOT);
		String placebo = ApothicBossMarkers.state().name();
		for (Entity target : targets) {
			String line = describe(target, hook, placebo);
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return targets.size();
	}

	static String describe(Entity target, String hook, String placebo) {
		CrowdControlImmunity.Reason reason = CrowdControlImmunity.reason(target);
		return BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())
				+ " " + target.getStringUUID()
				+ " protected=" + reason.isProtected()
				+ " reason=" + reason
				+ " spellEngineHook=" + hook
				+ " placebo=" + placebo;
	}
}
