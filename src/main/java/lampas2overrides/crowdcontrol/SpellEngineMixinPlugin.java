package lampas2overrides.crowdcontrol;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Applies the crowd-control immunity hooks whenever Spell Engine is installed, at any version. The bytecode contract
 * {@code SpellImpactsContractTest} checks is the inspected 1.10.9+26.2; other releases also activate but are not claimed as
 * inspected or tested. The config uses {@code defaultRequire: 1}, so a release that moved a wrapped call site stops startup
 * naming the target instead of leaving Apotheosis elites and invaders silently unprotected.
 */
public final class SpellEngineMixinPlugin implements IMixinConfigPlugin {

	static final String SPELL_ENGINE_MOD_ID = "spell_engine";
	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/crowdcontrol");

	/** Whether the hooks are enabled for this session and whether Spell Engine's class has been transformed yet. */
	public enum HookState {
		/** Spell Engine is not installed. */
		DISABLED,
		/** Spell Engine is installed; {@code SpellImpacts} has not been loaded yet. */
		ENABLED,
		/** The hooks were applied to {@code SpellImpacts}. */
		APPLIED
	}

	private static volatile HookState hookState = HookState.DISABLED;
	private boolean applies;

	public static HookState hookState() {
		return hookState;
	}

	@Override
	public void onLoad(String mixinPackage) {
		Optional<ModContainer> spellEngine = FabricLoader.getInstance().getModContainer(SPELL_ENGINE_MOD_ID);
		if (spellEngine.isEmpty()) {
			return;
		}
		applies = true;
		hookState = HookState.ENABLED;
		LOGGER.info("Enabled Spell Engine {} crowd-control immunity",
				spellEngine.get().getMetadata().getVersion().getFriendlyString());
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return applies;
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
		hookState = HookState.APPLIED;
	}
}
