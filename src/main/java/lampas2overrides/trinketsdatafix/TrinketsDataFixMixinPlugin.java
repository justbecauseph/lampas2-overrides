package lampas2overrides.trinketsdatafix;

import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies the V1460 repair only to the audited Trinkets artifact. */
public final class TrinketsDataFixMixinPlugin implements IMixinConfigPlugin {
	static final String EXPECTED_VERSION = "4.1.0+26.2";
	static final String EXPECTED_CLASS_SHA256 = "089c59253b7b9ab9f7f0a54c1f0dc7912379ad5bfa335c4acfcfbb91e17914f9";
	static final String CURRENT_VERSION = "4.1.1+26.2";
	static final String CURRENT_CLASS_SHA256 = "07f35c1ac7a56705c9671b7703b3c58dddbdc419c0fb00edf89b13e757810265";
	private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/trinketsdatafix");
	private static Boolean gate;
	private static TrinketsSchemaRepair.Profile activeProfile;

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return mixinClassName.endsWith("V1460Mixin") && gate();
	}

	public static TrinketsSchemaRepair.Profile activeProfile() {
		return gate() ? activeProfile : null;
	}

	private static boolean gate() {
		if (gate != null) return gate;
		var loader = FabricLoader.getInstance();
		var trinkets = loader.getModContainer("trinkets_updated");
		var minecraft = loader.getModContainer("minecraft");
		String trinketsVersion = trinkets.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
		String minecraftVersion = minecraft.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
		if (!"26.2".equals(minecraftVersion)
			|| (!EXPECTED_VERSION.equals(trinketsVersion) && !CURRENT_VERSION.equals(trinketsVersion))) {
			LOGGER.info("Skipping Trinkets DFU repair: audited Minecraft/Trinkets versions are absent");
			return gate = false;
		}
		try (InputStream in = Files.newInputStream(trinkets.get().getPath("eu/pb4/trinkets/mixin/datafixer/V1460Mixin.class"))) {
			activeProfile = profileFor(minecraftVersion, trinketsVersion,
				HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
			gate = activeProfile != null;
			LOGGER.info("Trinkets DFU repair {} for audited V1460 class{}", gate ? "enabled" : "disabled",
				gate ? " (" + trinketsVersion + ")" : "");
		} catch (Exception e) {
			LOGGER.info("Skipping Trinkets DFU repair: cannot read audited V1460 class", e);
			gate = false;
		}
		return gate;
	}

	static boolean matches(String minecraftVersion, String trinketsVersion, String classSha256) {
		return profileFor(minecraftVersion, trinketsVersion, classSha256) != null;
	}

	static TrinketsSchemaRepair.Profile profileFor(String minecraftVersion, String trinketsVersion, String classSha256) {
		if (!"26.2".equals(minecraftVersion)) return null;
		if (EXPECTED_VERSION.equals(trinketsVersion) && EXPECTED_CLASS_SHA256.equals(classSha256)) {
			return TrinketsSchemaRepair.Profile.V4_1_0;
		}
		if (CURRENT_VERSION.equals(trinketsVersion) && CURRENT_CLASS_SHA256.equals(classSha256)) {
			return TrinketsSchemaRepair.Profile.V4_1_1;
		}
		return null;
	}
}
