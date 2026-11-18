package lampas2overrides.client.signsfaster;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Applies the Signs Faster blank-first-line fix only to the audited Signs Faster 1.0.3 release. Any other installed
 * version is left unchanged and named in a WARN, and an absent mod is silent.
 */
public final class SignsFasterMixinPlugin implements IMixinConfigPlugin {

	static final String MOD_ID = "signfasterlegacy";
	static final String AUDITED_VERSION = "1.0.3";

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/signs-faster");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
		if (container.isEmpty()) {
			return;
		}
		String installed = container.get().getMetadata().getVersion().getFriendlyString();
		if (AUDITED_VERSION.equals(installed)) {
			apply = true;
			LOGGER.info("Enabled the Signs Faster {} blank-first-line sign text fix", AUDITED_VERSION);
		} else {
			LOGGER.warn("Signs Faster {} is installed but is not the audited {} release; "
				+ "the blank-first-line sign text fix is not applied", installed, AUDITED_VERSION);
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
