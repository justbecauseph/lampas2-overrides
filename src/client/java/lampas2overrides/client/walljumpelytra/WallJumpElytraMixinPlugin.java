package lampas2overrides.client.walljumpelytra;

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
 * Applies Wall-Jump TXF elytra takeoff suppression whenever {@code walljump} is installed. The audited artifact is
 * {@value #AUDITED_VERSION}; other versions still apply the client hook and log a warning so an upgrade is surfaced.
 */
public final class WallJumpElytraMixinPlugin implements IMixinConfigPlugin {

	static final String MOD_ID = "walljump";
	static final String AUDITED_VERSION = "26.2-1.3.8-fabric";

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/wall-jump-elytra");

	private boolean apply;

	@Override
	public void onLoad(String mixinPackage) {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
		if (container.isEmpty()) {
			return;
		}
		apply = true;
		String installed = container.get().getMetadata().getVersion().getFriendlyString();
		LOGGER.info("Enabled Wall-Jump TXF {} elytra takeoff suppression", installed);
		if (!AUDITED_VERSION.equals(installed)) {
			LOGGER.warn("Wall-Jump TXF {} is installed but is not the audited {} release; "
				+ "elytra takeoff suppression stays enabled and will disable itself if a reflected member moved",
				installed, AUDITED_VERSION);
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
