package lampas2overrides.client.diagonalbars;

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
 * Applies the Diagonal Blocks rotated bars post fix only to the audited Diagonal Blocks 26.2.0 release. Any other installed
 * version is left unchanged and named in a WARN, and an absent mod is silent.
 */
public final class DiagonalBarsMixinPlugin implements IMixinConfigPlugin {

	static final String MOD_ID = "diagonalblocks";
	static final String AUDITED_VERSION = "26.2.0";

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/diagonal-bars");

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
			LOGGER.info("Enabled the Diagonal Blocks {} rotated bars post fix", AUDITED_VERSION);
		} else {
			LOGGER.warn("Diagonal Blocks {} is installed but is not the audited {} release; "
				+ "the rotated bars post fix is not applied", installed, AUDITED_VERSION);
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
