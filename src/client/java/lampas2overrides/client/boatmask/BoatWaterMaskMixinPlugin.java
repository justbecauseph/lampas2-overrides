package lampas2overrides.client.boatmask;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/** Applies the boat mask compatibility hook only when the audited optional mods are present. */
public final class BoatWaterMaskMixinPlugin implements IMixinConfigPlugin {

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		FabricLoader loader = FabricLoader.getInstance();
		apply = loader.getModContainer(BoatWaterMaskProfiles.EMF_MOD_ID)
			.map(container -> BoatWaterMaskProfiles.supportedEmfVersion(
				container.getMetadata().getVersion().getFriendlyString())
				&& BoatWaterMaskProfiles.matchesArtifact(
					container, BoatWaterMaskProfiles.EXPECTED_EMF_ARTIFACT_SHA256))
			.orElse(false)
			&& BoatWaterMaskProfiles.hasSupportedProvider(loader);
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
