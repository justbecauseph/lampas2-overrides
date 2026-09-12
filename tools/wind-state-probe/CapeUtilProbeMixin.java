package windprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import windprobe.WindProbeExecutors;
import net.frozenblock.lib.cape.api.CapeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ExecutorService;

/** Captures the exact discarded executor created by the audited FrozenLib cape method. */
@Mixin(value = CapeUtil.class, remap = false)
public abstract class CapeUtilProbeMixin {
    @WrapOperation(
            method = "registerCapesFromURL(Ljava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/Executors;newCachedThreadPool()Ljava/util/concurrent/ExecutorService;"
            ),
            require = 1
    )
    private static ExecutorService windProbe$captureCapeExecutor(Operation<ExecutorService> original) {
        ExecutorService executor = original.call();
        WindProbeExecutors.capture(executor);
        return executor;
    }
}
