package lampas2overrides.client.voxychat;

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
 * Applies the Voxy chat suppression whenever Voxy is installed.
 *
 * <p>There is no version or artifact-hash gate: the mixin binds to {@code Logger.error} and its {@code showInHUD} call
 * by name and fails at startup if either moves.
 */
public final class VoxyChatMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/voxy-chat");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		apply = FabricLoader.getInstance().isModLoaded("voxy");
		if (apply) {
			LOGGER.info("Enabled Voxy error suppression in chat (errors still go to the log)");
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
