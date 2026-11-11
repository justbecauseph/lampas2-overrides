package lampas2overrides.client.statuseffectbars;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the Status Effect Bars alignment only when Status Effect Bars and VanillaHUD are both installed.
 *
 * <p>Without VanillaHUD, vanilla draws the icon where Status Effect Bars looks for it, and there is nothing to correct.
 * There is no version or artifact-hash gate: the mixins bind to members by name and fail at startup if they move.
 */
public final class StatusEffectBarsMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/status-effect-bars");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		apply = loader.isModLoaded("status-effect-bars") && loader.isModLoaded("vanillahud");
		if (apply) {
			LOGGER.info("Enabled Status Effect Bars alignment with VanillaHUD's status effect icons");
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return apply;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
