package lampas2overrides.client.frozenwind.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lampas2overrides.client.frozenwind.WindManagerDecoded;
import lampas2overrides.client.frozenwind.WindManagerSeedAccess;
import net.frozenblock.lib.wind.WindManager;
import net.frozenblock.lib.wind.extension.WindManagerExtension;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps FrozenLib's singleton untouched while Netty decodes an attachment packet.
 * The original merge code still runs once Fabric applies the decoded attachment on the client thread.
 */
@Mixin(value = WindManager.class, remap = false)
public abstract class WindManagerMixin implements WindManagerDecoded, WindManagerSeedAccess {

	@Shadow @Final public List<WindManagerExtension> extensions;
	@Shadow public Optional<Vec3> windOverride;
	@Shadow public double windX;
	@Shadow public double windY;
	@Shadow public double windZ;
	@Shadow public double laggedWindX;
	@Shadow public double laggedWindY;
	@Shadow public double laggedWindZ;
	@Shadow private boolean loadedExtensions;
	@Shadow private Optional<Long> seed;

	@Unique
	private static final ThreadLocal<Boolean> lampas2$APPLYING_DECODED_STATE = new ThreadLocal<>();

	@Invoker("createFromCodec")
	private static WindManager lampas2$createFromCodec(Optional<Vec3> windOverride, double windX, double windY,
			double windZ, double laggedWindX, double laggedWindY, double laggedWindZ,
			List<WindManagerExtension> extensions) {
		throw new AssertionError();
	}

	@Invoker("applyFromStreamCodec")
	private static WindManager lampas2$applyFromStreamCodec(Optional<Vec3> windOverride, double windX, double windY,
			double windZ, double laggedWindX, double laggedWindY, double laggedWindZ,
			List<WindManagerExtension> extensions, Optional<Long> seed) {
		throw new AssertionError();
	}

	@Inject(method = "reset()V", at = @At("RETURN"), require = 1)
	private void lampas2$resetLoadedExtensions(CallbackInfo ci) {
		loadedExtensions = false;
	}

	@Inject(
		method = "applyFromStreamCodec(Ljava/util/Optional;DDDDDDLjava/util/List;Ljava/util/Optional;)Lnet/frozenblock/lib/wind/WindManager;",
		at = @At("HEAD"),
		cancellable = true,
		require = 1
	)
	private static void lampas2$decodeIntoDetachedState(Optional<Vec3> windOverride, double windX, double windY,
			double windZ, double laggedWindX, double laggedWindY, double laggedWindZ,
			List<WindManagerExtension> extensions, Optional<Long> seed,
			CallbackInfoReturnable<WindManager> cir) {
		if (Boolean.TRUE.equals(lampas2$APPLYING_DECODED_STATE.get())) {
			return;
		}

		WindManager detached = lampas2$createFromCodec(windOverride, windX, windY, windZ,
			laggedWindX, laggedWindY, laggedWindZ, extensions);
		((WindManagerSeedAccess) detached).lampas2$setDecodedSeed(seed);
		cir.setReturnValue(detached);
	}

	@Override
	public void lampas2$setDecodedSeed(Optional<Long> seed) {
		this.seed = seed;
	}

	@Override
	public WindManager lampas2$applyDecodedToSingleton() {
		WindManager singleton = WindManager.INSTANCE;
		if ((Object) this == singleton) {
			return singleton;
		}

		Boolean previous = lampas2$APPLYING_DECODED_STATE.get();
		try {
			lampas2$APPLYING_DECODED_STATE.set(Boolean.TRUE);
			return lampas2$applyFromStreamCodec(windOverride, windX, windY, windZ,
				laggedWindX, laggedWindY, laggedWindZ, extensions, seed);
		} finally {
			if (previous == null) {
				lampas2$APPLYING_DECODED_STATE.remove();
			} else {
				lampas2$APPLYING_DECODED_STATE.set(previous);
			}
		}
	}
}
