package lampas2overrides.client.voxyreversez;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import lampas2overrides.Lampas2Overrides;
import net.fabricmc.loader.api.FabricLoader;

/** Applies the Voxy reverse-Z mixins only to the audited Voxy 0.2.19-beta and Iris 1.11.4 artifacts. */
public final class VoxyReverseZMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/voxy-reverse-z");

	private boolean enabled;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		VoxyReverseZProfiles.Status status = VoxyReverseZProfiles.status(loader);
		enabled = status == VoxyReverseZProfiles.Status.SUPPORTED;
		if (enabled) {
			LOGGER.info("Enabled the version-gated Voxy {} reverse-Z fix for Iris {} shader packs",
				VoxyReverseZProfiles.VOXY.version(), VoxyReverseZProfiles.IRIS.version());
		} else if (status == VoxyReverseZProfiles.Status.UNSUPPORTED) {
			LOGGER.warn("Voxy reverse-Z fix disabled: installed {} and {} do not match the audited Voxy {} and Iris {} "
					+ "artifacts (version and JAR SHA-256)",
				VoxyReverseZProfiles.describe(loader, VoxyReverseZProfiles.VOXY),
				VoxyReverseZProfiles.describe(loader, VoxyReverseZProfiles.IRIS),
				VoxyReverseZProfiles.VOXY.version(), VoxyReverseZProfiles.IRIS.version());
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return enabled;
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
