package lampas2overrides.client.defaultpacks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.sugar.Local;

import lampas2overrides.client.defaultpacks.DefaultEnabledPacks;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;
import net.minecraft.resources.Identifier;

/**
 * Selects the packs in {@link DefaultEnabledPacks} by default. Every built-in pack registration in Fabric API, the
 * v1 {@code ResourceLoader} overloads and the deprecated v0 {@code ResourceManagerHelper} ones alike, ends in this
 * five-argument {@code registerBuiltinPack}. It creates the client resource pack with its first
 * {@code ModNioPackResources.create} call ({@code PackType.CLIENT_RESOURCES}) and the data pack with its second. Only
 * the first call's {@code activationType} is raised from {@code NORMAL} to {@code DEFAULT_ENABLED}, so a data pack
 * registered under the same ID keeps the mod's own activation type. A pack a mod registers as {@code ALWAYS_ENABLED}
 * or {@code DEFAULT_ENABLED} is left as it is.
 */
@Mixin(value = ResourceLoaderImpl.class, remap = false)
abstract class ResourceLoaderImplMixin {

	@ModifyArg(
		method = "registerBuiltinPack(Lnet/minecraft/resources/Identifier;Ljava/lang/String;"
			+ "Lnet/fabricmc/loader/api/ModContainer;Lnet/minecraft/network/chat/Component;"
			+ "Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;)Z",
		at = @At(value = "INVOKE",
			target = "Lnet/fabricmc/fabric/impl/resource/pack/ModNioPackResources;create(Ljava/lang/String;"
				+ "Lnet/fabricmc/loader/api/ModContainer;Ljava/lang/String;Lnet/minecraft/server/packs/PackType;"
				+ "Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;Z)"
				+ "Lnet/fabricmc/fabric/impl/resource/pack/ModNioPackResources;",
			ordinal = 0),
		index = 4)
	private static PackActivationType lampas2$selectByDefault(PackActivationType activationType,
			@Local(argsOnly = true) Identifier id) {
		if (activationType != PackActivationType.NORMAL || !DefaultEnabledPacks.isDefaultEnabled(id.toString())) {
			return activationType;
		}
		DefaultEnabledPacks.logRaised(id.toString());
		return PackActivationType.DEFAULT_ENABLED;
	}
}
