import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.frozenblock.lib.registry.FrozenLibRegistries;
import net.frozenblock.lib.wind.WindManager;
import net.frozenblock.lib.wind.extension.WindManagerExtension;
import net.frozenblock.lib.wind.extension.WindManagerExtensionType;
import net.frozenblock.wilderwild.wind.WWWindManagerExtension;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentChange;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentTargetInfo.LevelTarget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;
import windprobe.WindProbeExecutors;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.FutureTask;

/**
 * Real-artifact client probe for FrozenLib's WindManager lifecycle and attachment sync.
 *
 * The class is deliberately outside the production source set.  It is compiled against the
 * current Loom client classpath plus the audited FrozenLib and Wilder Wild jars, then packaged as
 * a temporary client-only Fabric mod by run-probe.ps1.
 */
public final class WindStateProbe implements ClientModInitializer {
    private static final String RESULT_FILE = "wind-probe-result.txt";
    private static final String WW_EXTENSION = "net.frozenblock.wilderwild.wind.WWWindManagerExtension";
    private static final String APPLY_METHOD = "applyFromStreamCodec";
    private static final boolean EXPECT_PATCHED = Boolean.parseBoolean(
            System.getProperty("windProbe.expectPatched", "true"));

    private static Field loadedExtensionsField;
    private static Field extensionsField;
    private static Field windXField;
    private static Field windYField;
    private static Field windZField;
    private static Field laggedWindXField;
    private static Field laggedWindYField;
    private static Field laggedWindZField;
    private static Field seedField;
    private static Unsafe unsafe;
    private static long isClientSideOffset;
    private static long dimensionOffset;
    private static long registryAccessOffset;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            String result = "status=FAIL\nerror=probe did not execute\n";
            boolean passed = false;
            try {
                result = runProbe();
                passed = true;
            } catch (Throwable error) {
                result = "status=FAIL\nerror=" + describe(error) + "\n";
                System.err.println("WIND_PROBE_FAILURE " + describe(error));
                error.printStackTrace();
            } finally {
                try {
                    WindProbeExecutors.ShutdownEvidence cleanup = WindProbeExecutors.shutdown();
                    result += cleanup.asText();
                    if (!cleanup.terminated()) {
                        passed = false;
                        result = result.replaceFirst("^status=PASS", "status=FAIL");
                    }
                } catch (Throwable cleanupError) {
                    passed = false;
                    result += "cleanup.error=" + describe(cleanupError) + "\n";
                    result = result.replaceFirst("^status=PASS", "status=FAIL");
                }
                try {
                    Files.writeString(Path.of(RESULT_FILE), resultForFile(result, passed));
                } catch (Exception writeError) {
                    writeError.printStackTrace();
                }
                System.out.println("WIND_PROBE_RESULT\n" + resultForFile(result, passed));
                mc.stop();
            }
            if (!passed) {
                throw new AssertionError(result);
            }
        });
    }

    private static String runProbe() throws Exception {
        WindManager manager = WindManager.INSTANCE;
        initialiseReflection();

        RegistryEvidence registry = verifyWilderWildRegistry();
        ResetEvidence reset = verifyReset(manager, registry.type());
        DecodeEvidence decode = verifyWorkerDecode(manager, registry.type());
        ApplyEvidence apply = verifyAttachmentApply(manager, decode.decoded());

        return "status=PASS\n"
                + "expectPatched=" + EXPECT_PATCHED + "\n"
                + "clientThread=" + Thread.currentThread().getName() + "\n"
                + registry.asText()
                + reset.asText()
                + decode.asText()
                + apply.asText();
    }

    private static void initialiseReflection() throws ReflectiveOperationException {
        loadedExtensionsField = WindManager.class.getDeclaredField("loadedExtensions");
        extensionsField = WindManager.class.getField("extensions");
        windXField = WindManager.class.getField("windX");
        windYField = WindManager.class.getField("windY");
        windZField = WindManager.class.getField("windZ");
        laggedWindXField = WindManager.class.getField("laggedWindX");
        laggedWindYField = WindManager.class.getField("laggedWindY");
        laggedWindZField = WindManager.class.getField("laggedWindZ");
        seedField = WindManager.class.getDeclaredField("seed");
        loadedExtensionsField.setAccessible(true);
        extensionsField.setAccessible(true);
        seedField.setAccessible(true);

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);
        isClientSideOffset = unsafe.objectFieldOffset(Level.class.getDeclaredField("isClientSide"));
        dimensionOffset = unsafe.objectFieldOffset(Level.class.getDeclaredField("dimension"));
        registryAccessOffset = unsafe.objectFieldOffset(Level.class.getDeclaredField("registryAccess"));
    }

    private static RegistryEvidence verifyWilderWildRegistry() throws Exception {
        // The common Wilder Wild initializer calls WWWindManagerExtension.init().  Loading the
        // actual extension class here also makes an omitted initializer fail loudly in the probe.
        Class.forName(WW_EXTENSION, true, WindStateProbe.class.getClassLoader());
        WindManagerExtensionType<?> type = WWWindManagerExtension.TYPE;
        if (type == null) {
            throw new AssertionError("Wilder Wild TYPE is null");
        }

        Object supplied = type.supplier().get();
        if (!(supplied instanceof WWWindManagerExtension)) {
            throw new AssertionError("Wilder Wild supplier returned " + supplied);
        }

        long registryMatches = FrozenLibRegistries.WIND_MANAGER_EXTENSION_TYPE.stream()
                .filter(candidate -> candidate == type)
                .count();
        if (registryMatches != 1) {
            throw new AssertionError("Wilder Wild extension type registry matches=" + registryMatches);
        }
        return new RegistryEvidence(type, supplied.getClass().getName(), registryMatches);
    }

    private static ResetEvidence verifyReset(WindManager manager, WindManagerExtensionType<?> type)
            throws Exception {
        List<WindManagerExtension> extensions = extensions(manager);
        extensions.clear();
        WindManagerExtension extension = suppliedExtension(type);
        extensions.add(extension);
        loadedExtensionsField.setBoolean(manager, true);

        manager.reset();

        boolean cleared = extensions.isEmpty();
        boolean loaded = loadedExtensionsField.getBoolean(manager);
        if (!cleared) {
            throw new AssertionError("reset left extensions=" + extensions.size());
        }
        if (loaded == !EXPECT_PATCHED) {
            // This is the expected upstream behavior in baseline mode, and the expected repaired
            // behavior in patched mode.  Keep the message below focused on the contract under test.
        } else {
            throw new AssertionError("reset loadedExtensions=" + loaded
                    + ", expected=" + (!EXPECT_PATCHED));
        }
        int rehydrated = -1;
        if (EXPECT_PATCHED) {
            Level syntheticLevel = syntheticClientLevel();
            WindManager.getOrCreate(syntheticLevel);
            rehydrated = extensions.size();
            if (!loadedExtensionsField.getBoolean(manager)
                    || rehydrated != 1
                    || !(extensions.get(0) instanceof WWWindManagerExtension)) {
                throw new AssertionError("reset rehydration failed: loaded="
                        + loadedExtensionsField.getBoolean(manager) + ", extensions=" + extensions);
            }
            WWWindManagerExtension lookedUp = WWWindManagerExtension.get(syntheticLevel);
            if (lookedUp != extensions.get(0)) {
                throw new AssertionError("Wilder Wild extension lookup returned " + lookedUp);
            }
        }
        return new ResetEvidence(cleared, loaded, rehydrated);
    }

    private static DecodeEvidence verifyWorkerDecode(WindManager manager,
                                                       WindManagerExtensionType<?> type)
            throws Exception {
        List<WindManagerExtension> extensions = extensions(manager);
        extensions.clear();
        WindManagerExtension oldExtension = suppliedExtension(type);
        setClouds(oldExtension, 91.0, 92.0, 89.0, 90.0);
        extensions.add(oldExtension);
        loadedExtensionsField.setBoolean(manager, true);
        setWindValues(manager, 101.0, 102.0, 103.0, 201.0, 202.0, 203.0);
        manager.windOverride = Optional.of(new net.minecraft.world.phys.Vec3(301.0, 302.0, 303.0));
        seedField.set(manager, Optional.of(404040404L));

        WWWindManagerExtension payload = new WWWindManagerExtension();
        setClouds(payload, 11.0, 12.0, 9.0, 10.0);
        List<WindManagerExtension> payloadExtensions = List.of(payload);
        WindState before = state(manager);
        List<WindManagerExtension> beforeExtensions = new ArrayList<>(extensions);

        Method decoder = findApplyMethod();
        FutureTask<WindManager> task = new FutureTask<>(() -> invokeDecoder(decoder, payloadExtensions));
        Thread worker = new Thread(task, "wind-state-probe-worker");
        worker.start();
        worker.join(5000L);
        if (worker.isAlive()) {
            throw new AssertionError("decoder worker did not terminate");
        }
        WindManager decoded = task.get();
        WindState after = state(manager);
        List<WindManagerExtension> afterExtensions = new ArrayList<>(extensions);

        WindState expectedDecoded = new WindState(
                new double[]{11.0, 12.0, 13.0, 21.0, 22.0, 23.0},
                Optional.of(new net.minecraft.world.phys.Vec3(31.0, 32.0, 33.0)),
                Optional.of(123456789L));
        if (!expectedDecoded.equals(state(decoded))) {
            throw new AssertionError("decoded payload state=" + state(decoded));
        }

        boolean sameInstance = decoded == manager;
        boolean instanceUnchanged = before.equals(after)
                && sameIdentityList(beforeExtensions, afterExtensions);
        if (EXPECT_PATCHED) {
            if (sameInstance) {
                throw new AssertionError("patched decoder returned WindManager.INSTANCE");
            }
            if (!instanceUnchanged) {
                throw new AssertionError("patched worker decoder mutated INSTANCE");
            }
        } else {
            if (!sameInstance) {
                throw new AssertionError("baseline decoder returned a detached manager");
            }
            if (instanceUnchanged) {
                throw new AssertionError("baseline decoder did not mutate INSTANCE");
            }
        }

        if (decoded.extensions.size() != 1
                || !(decoded.extensions.get(0) instanceof WWWindManagerExtension)) {
            throw new AssertionError("decoded extensions=" + decoded.extensions);
        }
        return new DecodeEvidence(worker.getName(), sameInstance, instanceUnchanged, decoded);
    }

    private static ApplyEvidence verifyAttachmentApply(WindManager manager,
                                                        WindManager decoded)
            throws Exception {
        List<WindManagerExtension> extensions = extensions(manager);
        WindManagerExtension oldExtension = suppliedExtension(WWWindManagerExtension.TYPE);
        setClouds(oldExtension, 71.0, 72.0, 69.0, 70.0);
        extensions.clear();
        extensions.add(oldExtension);
        setWindValues(manager, -101.0, -102.0, -103.0, -201.0, -202.0, -203.0);

        Level syntheticLevel = syntheticClientLevel();
        AttachmentChange change = new AttachmentChange(
                LevelTarget.INSTANCE, WindManager.ATTACHMENT_TYPE, decoded);
        change.tryApply(syntheticLevel);
        AttachmentTarget target = (AttachmentTarget) syntheticLevel;
        Object attached = target.getAttached(WindManager.ATTACHMENT_TYPE);

        if (attached == null) {
            throw new AssertionError("AttachmentChange did not set the real level target");
        }
        if (EXPECT_PATCHED) {
            if (attached != manager) {
                throw new AssertionError("patched attachment stored " + attached
                        + " instead of INSTANCE");
            }
            if (manager == decoded || !state(decoded).equals(state(manager))) {
                throw new AssertionError("patched client merge did not update INSTANCE");
            }
            if (extensions.size() != decoded.extensions.size()
                    || !(extensions.get(0) instanceof WWWindManagerExtension)) {
                throw new AssertionError("patched merge extension list=" + extensions);
            }
        } else if (attached != manager) {
            throw new AssertionError("baseline attachment stored " + attached
                    + " instead of INSTANCE");
        }

        // Replaying INSTANCE itself is a valid identity edge.  It must not iterate and mutate the
        // same extension list while trying to merge it.
        AttachmentChange identityChange = new AttachmentChange(
                LevelTarget.INSTANCE, WindManager.ATTACHMENT_TYPE, manager);
        identityChange.tryApply(syntheticLevel);
        if (target.getAttached(WindManager.ATTACHMENT_TYPE) != manager) {
            throw new AssertionError("INSTANCE identity attachment was not preserved");
        }

        // Apply a second packet after the first one.  Both modes must leave the latest decoded
        // values installed, which checks that the real AttachmentChange seam preserves packet
        // order rather than applying a worker result out of band.
        WWWindManagerExtension secondPayload = new WWWindManagerExtension();
        setClouds(secondPayload, 51.0, 52.0, 49.0, 50.0);
        WindManager secondDecoded = invokeDecoderOnWorker(List.of(secondPayload), "wind-state-probe-worker-2");
        WindState expectedSecond = new WindState(
                new double[]{41.0, 42.0, 43.0, 61.0, 62.0, 63.0},
                Optional.of(new net.minecraft.world.phys.Vec3(71.0, 72.0, 73.0)),
                Optional.of(7654321L));
        if (!expectedSecond.equals(state(secondDecoded))) {
            throw new AssertionError("second decoded payload state=" + state(secondDecoded));
        }
        new AttachmentChange(LevelTarget.INSTANCE, WindManager.ATTACHMENT_TYPE, secondDecoded)
                .tryApply(syntheticLevel);
        boolean orderPreserved = expectedSecond.equals(state(manager))
                && target.getAttached(WindManager.ATTACHMENT_TYPE) == manager;
        if (!orderPreserved) {
            throw new AssertionError("second attachment did not replace the first in order");
        }

        // A null attachment is the Fabric deletion form.  It must reach the original target
        // setter after any wind-specific wrapper has finished.
        new AttachmentChange(LevelTarget.INSTANCE, WindManager.ATTACHMENT_TYPE, null)
                .tryApply(syntheticLevel);
        boolean deletionCleared = target.getAttached(WindManager.ATTACHMENT_TYPE) == null;
        if (!deletionCleared) {
            throw new AssertionError("null attachment did not clear the real level target");
        }
        return new ApplyEvidence(Thread.currentThread().getName(), attached == manager,
                manager == decoded, extensions.size(), orderPreserved, deletionCleared);
    }

    private static Level syntheticClientLevel() throws Exception {
        Level level = (Level) unsafe.allocateInstance(ClientLevel.class);
        unsafe.putBoolean(level, isClientSideOffset, true);
        unsafe.putObject(level, dimensionOffset, Level.OVERWORLD);
        RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        unsafe.putObject(level, registryAccessOffset, registryAccess);
        return level;
    }

    private static Method findApplyMethod() throws NoSuchMethodException {
        for (Method method : WindManager.class.getDeclaredMethods()) {
            if (method.getName().equals(APPLY_METHOD)
                    && method.getParameterCount() == 9
                    && method.getReturnType() == WindManager.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(WindManager.class.getName() + "#" + APPLY_METHOD);
    }

    private static WindManager invokeDecoder(Method method,
                                             List<WindManagerExtension> payloadExtensions)
            throws Exception {
        try {
            return (WindManager) method.invoke(null,
                    Optional.of(new net.minecraft.world.phys.Vec3(31.0, 32.0, 33.0)),
                    11.0, 12.0, 13.0,
                    21.0, 22.0, 23.0,
                    payloadExtensions,
                    Optional.of(123456789L));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static WindManager invokeDecoderOnWorker(List<WindManagerExtension> payloadExtensions,
                                                     String workerName) throws Exception {
        Method decoder = findApplyMethod();
        FutureTask<WindManager> task = new FutureTask<>(() -> invokeDecoder(decoder, payloadExtensions,
                new net.minecraft.world.phys.Vec3(71.0, 72.0, 73.0),
                41.0, 42.0, 43.0, 61.0, 62.0, 63.0, Optional.of(7654321L)));
        Thread worker = new Thread(task, workerName);
        worker.start();
        worker.join(5000L);
        if (worker.isAlive()) {
            throw new AssertionError("decoder worker did not terminate: " + workerName);
        }
        return task.get();
    }

    private static WindManager invokeDecoder(Method method,
                                             List<WindManagerExtension> payloadExtensions,
                                             net.minecraft.world.phys.Vec3 windOverride,
                                             double windX,
                                             double windY,
                                             double windZ,
                                             double laggedWindX,
                                             double laggedWindY,
                                             double laggedWindZ,
                                             Optional<Long> seed)
            throws Exception {
        try {
            return (WindManager) method.invoke(null,
                    Optional.of(windOverride),
                    windX, windY, windZ,
                    laggedWindX, laggedWindY, laggedWindZ,
                    payloadExtensions,
                    seed);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<WindManagerExtension> extensions(WindManager manager) {
        try {
            return (List<WindManagerExtension>) extensionsField.get(manager);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    private static WindManagerExtension suppliedExtension(WindManagerExtensionType<?> type) {
        return (WindManagerExtension) type.supplier().get();
    }

    private static void setClouds(WindManagerExtension extension,
                                  double cloudX,
                                  double cloudZ,
                                  double previousCloudX,
                                  double previousCloudZ) {
        if (!(extension instanceof WWWindManagerExtension wilderWild)) {
            throw new AssertionError("unexpected extension " + extension);
        }
        wilderWild.cloudX = cloudX;
        wilderWild.cloudZ = cloudZ;
        wilderWild.prevCloudX = previousCloudX;
        wilderWild.prevCloudZ = previousCloudZ;
    }

    private static void setWindValues(WindManager manager,
                                      double windX,
                                      double windY,
                                      double windZ,
                                      double laggedWindX,
                                      double laggedWindY,
                                      double laggedWindZ) throws IllegalAccessException {
        windXField.setDouble(manager, windX);
        windYField.setDouble(manager, windY);
        windZField.setDouble(manager, windZ);
        laggedWindXField.setDouble(manager, laggedWindX);
        laggedWindYField.setDouble(manager, laggedWindY);
        laggedWindZField.setDouble(manager, laggedWindZ);
    }

    private static double[] windValues(WindManager manager) throws IllegalAccessException {
        return new double[]{
                windXField.getDouble(manager),
                windYField.getDouble(manager),
                windZField.getDouble(manager),
                laggedWindXField.getDouble(manager),
                laggedWindYField.getDouble(manager),
                laggedWindZField.getDouble(manager)
        };
    }

    private static WindState state(WindManager manager) throws IllegalAccessException {
        return new WindState(windValues(manager), manager.windOverride, seedField.get(manager));
    }

    private static boolean valuesEqual(double[] left, double[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (Double.doubleToLongBits(left[i]) != Double.doubleToLongBits(right[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIdentityList(List<?> left, List<?> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) {
                return false;
            }
        }
        return true;
    }

    private record WindState(double[] values, Optional<?> windOverride, Object seed) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WindState state)) {
                return false;
            }
            return valuesEqual(values, state.values)
                    && windOverride.equals(state.windOverride)
                    && java.util.Objects.equals(seed, state.seed);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(java.util.Arrays.hashCode(values), windOverride, seed);
        }
    }

    private static String describe(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getName() + (message == null ? "" : ":" + message)
                .replace('\n', ' ');
    }

    private static String resultForFile(String result, boolean passed) {
        if (result == null) {
            return "status=FAIL\nerror=probe produced no result\n";
        }
        if (result.startsWith("status=")) {
            return result;
        }
        return "status=" + (passed ? "PASS" : "FAIL") + "\n" + result;
    }

    private record RegistryEvidence(WindManagerExtensionType<?> type,
                                    String suppliedClass,
                                    long registryMatches) {
        String asText() {
            return "registryType=" + type.getClass().getName() + "\n"
                    + "suppliedExtension=" + suppliedClass + "\n"
                    + "registryMatches=" + registryMatches + "\n";
        }
    }

    private record ResetEvidence(boolean extensionsCleared,
                                 boolean loadedExtensions,
                                 int rehydratedExtensions) {
        String asText() {
            return "reset.extensionsCleared=" + extensionsCleared + "\n"
                    + "reset.loadedExtensions=" + loadedExtensions + "\n"
                    + "reset.rehydratedExtensions=" + rehydratedExtensions + "\n";
        }
    }

    private record DecodeEvidence(String workerName,
                                  boolean returnedInstance,
                                  boolean instanceUnchanged,
                                  WindManager decoded) {
        String asText() {
            return "decode.worker=" + workerName + "\n"
                    + "decode.returnedInstance=" + returnedInstance + "\n"
                    + "decode.instanceUnchanged=" + instanceUnchanged + "\n"
                    + "decode.decodedIdentity=" + System.identityHashCode(decoded) + "\n";
        }
    }

    private record ApplyEvidence(String threadName,
                                 boolean storedInstance,
                                 boolean decodedWasInstance,
                                 int extensionCount,
                                 boolean orderPreserved,
                                 boolean deletionCleared) {
        String asText() {
            return "apply.thread=" + threadName + "\n"
                    + "apply.storedInstance=" + storedInstance + "\n"
                    + "apply.decodedWasInstance=" + decodedWasInstance + "\n"
                    + "apply.extensionCount=" + extensionCount + "\n"
                    + "apply.orderPreserved=" + orderPreserved + "\n"
                    + "apply.deletionCleared=" + deletionCleared + "\n";
        }
    }

}
