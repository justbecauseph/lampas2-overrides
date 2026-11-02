package lampas2overrides.client.figurapunchy;

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
 * Applies the Figura avatar root-pose mixin only when Figura and Punchy! Moves are both installed.
 *
 * <p>Punchy! Moves is the mod in the pack that poses the player model's root part; without it the
 * root stays at rest and the mixin has nothing to carry over.
 */
public final class FiguraPunchyMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/figura-punchy");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		apply = loader.isModLoaded("figura") && loader.isModLoaded("punchy_moves");
		if (apply) {
			LOGGER.info("Enabled the player model root pose for Figura avatars under Punchy! Moves");
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
