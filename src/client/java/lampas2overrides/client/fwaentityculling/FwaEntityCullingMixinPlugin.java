package lampas2overrides.client.fwaentityculling;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import lampas2overrides.Lampas2Overrides;
import lampas2overrides.fwaentityculling.FwaEntityCullingProfile;

/** Applies the animation culling mixin only to the audited Fancy World Animations 1.3.4 and EntityCulling 1.12.0 pair. */
public final class FwaEntityCullingMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/fwa-entityculling");

	private boolean enabled;

	@Override
	public void onLoad(String mixinPackage) {
		enabled = FwaEntityCullingProfile.active();
		if (enabled) {
			LOGGER.info("Enabled Fancy World Animations {} animation culling for EntityCulling {}",
				FwaEntityCullingProfile.FWA_VERSION, FwaEntityCullingProfile.ENTITY_CULLING_VERSION);
			return;
		}
		String fwa = FwaEntityCullingProfile.installedVersion(FwaEntityCullingProfile.FWA_MOD_ID);
		String entityCulling = FwaEntityCullingProfile.installedVersion(FwaEntityCullingProfile.ENTITY_CULLING_MOD_ID);
		if (fwa != null && entityCulling != null) {
			LOGGER.warn("Fancy World Animations {} with EntityCulling {} is not the audited {} + {} pair; "
					+ "Fancy World Animations' own EntityCulling mixin is left unchanged",
				fwa, entityCulling, FwaEntityCullingProfile.FWA_VERSION, FwaEntityCullingProfile.ENTITY_CULLING_VERSION);
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
