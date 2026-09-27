import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.frozenblock.lib.wind.WindManager;
import net.frozenblock.lib.wind.extension.WindManagerExtension;
import net.frozenblock.lib.wind.extension.WindManagerExtensionType;
import net.frozenblock.wilderwild.wind.WWWindManagerExtension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Read-only-by-default observations of FrozenLib 3.x wind lifecycle and sync behavior.
 * Kept separate from the strict FrozenLib 2.5.3 regression probe because the 3.x
 * reset and attachment contracts differ.
 */
public final class WindStateObservationProbe implements ClientModInitializer {
    private static final Path RESULT_PATH = Path.of("wind-observation-result.txt");
    private static final Field LOADED_EXTENSIONS;
    private static final Field EXTENSIONS;
    private static final Field SEED;
    private static final Field WIND_OVERRIDE;
    private static final Field[] WIND_VALUES;
    private static final Field CLOUD_X;
    private static final Field CLOUD_Z;
    private static final Field PREV_CLOUD_X;
    private static final Field PREV_CLOUD_Z;
    private static final Method APPLY_FROM_STREAM_CODEC;
    private static final Unsafe UNSAFE;
    private static final long CLIENT_SIDE_OFFSET;
    private static final long DIMENSION_OFFSET;
    private static final long REGISTRY_ACCESS_OFFSET;
    private static final StringBuilder EVIDENCE = new StringBuilder();

    static {
        try {
            LOADED_EXTENSIONS = WindManager.class.getDeclaredField("loadedExtensions");
            LOADED_EXTENSIONS.setAccessible(true);
            EXTENSIONS = WindManager.class.getField("extensions");
            SEED = WindManager.class.getDeclaredField("seed");
            SEED.setAccessible(true);
            WIND_OVERRIDE = WindManager.class.getField("windOverride");
            WIND_VALUES = new Field[]{
                    WindManager.class.getField("windX"),
                    WindManager.class.getField("windY"),
                    WindManager.class.getField("windZ"),
                    WindManager.class.getField("laggedWindX"),
                    WindManager.class.getField("laggedWindY"),
                    WindManager.class.getField("laggedWindZ")
            };
            CLOUD_X = WWWindManagerExtension.class.getField("cloudX");
            CLOUD_Z = WWWindManagerExtension.class.getField("cloudZ");
            PREV_CLOUD_X = WWWindManagerExtension.class.getField("prevCloudX");
            PREV_CLOUD_Z = WWWindManagerExtension.class.getField("prevCloudZ");
            APPLY_FROM_STREAM_CODEC = findDecoder();

            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            UNSAFE = (Unsafe) unsafeField.get(null);
            CLIENT_SIDE_OFFSET = UNSAFE.objectFieldOffset(Level.class.getDeclaredField("isClientSide"));
            DIMENSION_OFFSET = UNSAFE.objectFieldOffset(Level.class.getDeclaredField("dimension"));
            REGISTRY_ACCESS_OFFSET = UNSAFE.objectFieldOffset(Level.class.getDeclaredField("registryAccess"));
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            EVIDENCE.append("status=RUNNING\n");
            EVIDENCE.append("probe.mode=unpatched-observation\n");
            EVIDENCE.append("client.thread=").append(Thread.currentThread().getName()).append('\n');
            persist();

            WindManager manager = WindManager.INSTANCE;
            observe("reset", () -> observeReset(manager));
            observe("rehydration", () -> observeRehydration(manager));
            observe("sync.first", () -> observeSync(manager, "sync.first", "wind-state-observation-worker-1",
                    new double[]{11.0, 12.0, 9.0, 10.0},
                    new double[]{11.0, 12.0, 13.0, 21.0, 22.0, 23.0}, 123456789L));
            observe("sync.second", () -> observeSync(manager, "sync.second", "wind-state-observation-worker-2",
                    new double[]{51.0, 52.0, 49.0, 50.0},
                    new double[]{41.0, 42.0, 43.0, 61.0, 62.0, 63.0}, 7654321L));
            EVIDENCE.append("status=PASS\n");
            persist();
            System.out.println("WIND_OBSERVATION_RESULT\n" + EVIDENCE);
            client.stop();
        });
    }

    private static void observeReset(WindManager manager) throws Exception {
        List<WindManagerExtension> extensions = extensions(manager);
        extensions.clear();
        extensions.add(suppliedExtension());
        LOADED_EXTENSIONS.setBoolean(manager, true);
        manager.reset();

        EVIDENCE.append("reset.extensionsAfterReset=").append(extensions.size()).append('\n');
        EVIDENCE.append("reset.loadedExtensionsAfterReset=")
                .append(LOADED_EXTENSIONS.getBoolean(manager)).append('\n');
        EVIDENCE.append("reset.seedAfterReset=").append(SEED.get(manager)).append('\n');
    }

    private static void observeRehydration(WindManager manager) throws Exception {
        Level level = syntheticClientLevel();
        WindManager returned = WindManager.getOrCreate(level);
        List<WindManagerExtension> extensions = extensions(manager);
        EVIDENCE.append("rehydration.returnedSingleton=").append(returned == manager).append('\n');
        EVIDENCE.append("rehydration.loadedExtensions=")
                .append(LOADED_EXTENSIONS.getBoolean(manager)).append('\n');
        EVIDENCE.append("rehydration.extensionCount=").append(extensions.size()).append('\n');
        EVIDENCE.append("rehydration.extensionClasses=").append(classNames(extensions)).append('\n');
        EVIDENCE.append("rehydration.extensionType=")
                .append(WWWindManagerExtension.TYPE.getClass().getName()).append('\n');
    }

    private static void observeSync(WindManager manager,
                                    String prefix,
                                    String workerName,
                                    double[] cloudValues,
                                    double[] windValues,
                                    long seed) throws Exception {
        List<WindManagerExtension> extensions = extensions(manager);
        WindManagerExtension existing = suppliedExtension();
        setCloudValues(existing, new double[]{80.0, 81.0, 78.0, 79.0});
        extensions.clear();
        extensions.add(existing);
        LOADED_EXTENSIONS.setBoolean(manager, true);
        setWindValues(manager, new double[]{-101.0, -102.0, -103.0, -201.0, -202.0, -203.0});
        WIND_OVERRIDE.set(manager, Optional.of(new Vec3(-301.0, -302.0, -303.0)));
        SEED.set(manager, Optional.of(-404040404L));

        List<WindManagerExtension> beforeExtensions = new ArrayList<>(extensions);
        WindState before = snapshot(manager);
        double[] beforeClouds = cloudValues(existing);
        WindManagerExtension payload = suppliedExtension();
        setCloudValues(payload, cloudValues);
        FutureTask<WindManager> task = new FutureTask<>(() -> invokeDecoder(
                payload, windValues, seed));
        Thread worker = new Thread(task, workerName);
        worker.start();
        worker.join(TimeUnit.SECONDS.toMillis(5));
        if (worker.isAlive()) {
            worker.interrupt();
            throw new IllegalStateException("decoder worker did not terminate");
        }

        try {
            WindManager decoded = task.get();
            WindState afterDecode = snapshot(manager);
            boolean managerStateChanged = !before.equals(afterDecode);
            boolean extensionListSameIdentity = sameIdentityList(beforeExtensions, extensions);
            boolean workerLeftSingletonUnchanged = !managerStateChanged
                    && extensionListSameIdentity
                    && !extensions.isEmpty()
                    && extensions.get(0) == existing
                    && java.util.Arrays.equals(beforeClouds, cloudValues(existing));
            EVIDENCE.append(prefix).append(".worker=").append(workerName).append('\n');
            EVIDENCE.append(prefix).append(".completed=true\n");
            EVIDENCE.append(prefix).append(".errorClass=none\n");
            EVIDENCE.append(prefix).append(".returnedSingleton=").append(decoded == manager).append('\n');
            EVIDENCE.append(prefix).append(".managerStateChangedDuringDecode=").append(managerStateChanged).append('\n');
            EVIDENCE.append(prefix).append(".workerLeftSingletonUnchanged=")
                    .append(workerLeftSingletonUnchanged).append('\n');
            EVIDENCE.append(prefix).append(".extensionListSameIdentity=")
                    .append(extensionListSameIdentity).append('\n');
            EVIDENCE.append(prefix).append(".extensionSameIdentity=")
                    .append(!extensions.isEmpty() && extensions.get(0) == existing).append('\n');
            EVIDENCE.append(prefix).append(".extensionCount=").append(extensions.size()).append('\n');
            EVIDENCE.append(prefix).append(".cloudsBefore=").append(values(beforeClouds)).append('\n');
            EVIDENCE.append(prefix).append(".cloudsAfter=")
                    .append(extensions.isEmpty() ? "empty" : values(cloudValues(extensions.get(0))))
                    .append('\n');
            EVIDENCE.append(prefix).append(".managerWindAfter=").append(values(windValues(manager))).append('\n');
        } catch (ExecutionException error) {
            Throwable cause = rootCause(error);
            EVIDENCE.append(prefix).append(".worker=").append(workerName).append('\n');
            EVIDENCE.append(prefix).append(".completed=false\n");
            EVIDENCE.append(prefix).append(".errorClass=").append(cause.getClass().getName()).append('\n');
            EVIDENCE.append(prefix).append(".errorMessage=").append(clean(cause.getMessage())).append('\n');
            EVIDENCE.append(prefix).append(".returnedSingleton=unavailable\n");
            EVIDENCE.append(prefix).append(".managerStateChanged=").append(!before.equals(snapshot(manager))).append('\n');
            EVIDENCE.append(prefix).append(".extensionListSameIdentity=")
                    .append(sameIdentityList(beforeExtensions, extensions)).append('\n');
            EVIDENCE.append(prefix).append(".extensionSameIdentity=")
                    .append(!extensions.isEmpty() && extensions.get(0) == existing).append('\n');
            EVIDENCE.append(prefix).append(".extensionCount=").append(extensions.size()).append('\n');
            EVIDENCE.append(prefix).append(".cloudsBefore=").append(values(beforeClouds)).append('\n');
            EVIDENCE.append(prefix).append(".cloudsAfter=")
                    .append(extensions.isEmpty() ? "empty" : values(cloudValues(extensions.get(0))))
                    .append('\n');
            EVIDENCE.append(prefix).append(".managerWindAfter=").append(values(windValues(manager))).append('\n');
        }
    }

    private static WindManager invokeDecoder(WindManagerExtension payload,
                                             double[] winds,
                                             long seed) throws Exception {
        try {
            return (WindManager) APPLY_FROM_STREAM_CODEC.invoke(null,
                    Optional.of(new Vec3(winds[0], winds[1], winds[2])),
                    winds[0], winds[1], winds[2], winds[3], winds[4], winds[5],
                    List.of(payload), Optional.of(seed));
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw error;
        }
    }

    private static Level syntheticClientLevel() throws Exception {
        Level level = (Level) UNSAFE.allocateInstance(ClientLevel.class);
        UNSAFE.putBoolean(level, CLIENT_SIDE_OFFSET, true);
        UNSAFE.putObject(level, DIMENSION_OFFSET, Level.OVERWORLD);
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        UNSAFE.putObject(level, REGISTRY_ACCESS_OFFSET, access);
        return level;
    }

    private static Method findDecoder() throws NoSuchMethodException {
        for (Method method : WindManager.class.getDeclaredMethods()) {
            if (method.getName().equals("applyFromStreamCodec")
                    && method.getParameterCount() == 9
                    && method.getReturnType() == WindManager.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("WindManager#applyFromStreamCodec/9");
    }

    private static WindManagerExtension suppliedExtension() throws Exception {
        WindManagerExtensionType<?> type = WWWindManagerExtension.TYPE;
        return (WindManagerExtension) type.supplier().get();
    }

    @SuppressWarnings("unchecked")
    private static List<WindManagerExtension> extensions(WindManager manager) throws Exception {
        return (List<WindManagerExtension>) EXTENSIONS.get(manager);
    }

    private static void setCloudValues(WindManagerExtension extension, double[] values) throws Exception {
        CLOUD_X.setDouble(extension, values[0]);
        CLOUD_Z.setDouble(extension, values[1]);
        PREV_CLOUD_X.setDouble(extension, values[2]);
        PREV_CLOUD_Z.setDouble(extension, values[3]);
    }

    private static double[] cloudValues(WindManagerExtension extension) throws Exception {
        return new double[]{
                CLOUD_X.getDouble(extension), CLOUD_Z.getDouble(extension),
                PREV_CLOUD_X.getDouble(extension), PREV_CLOUD_Z.getDouble(extension)
        };
    }

    private static void setWindValues(WindManager manager, double[] values) throws IllegalAccessException {
        for (int i = 0; i < WIND_VALUES.length; i++) {
            WIND_VALUES[i].setDouble(manager, values[i]);
        }
    }

    private static double[] windValues(WindManager manager) throws IllegalAccessException {
        double[] values = new double[WIND_VALUES.length];
        for (int i = 0; i < WIND_VALUES.length; i++) {
            values[i] = WIND_VALUES[i].getDouble(manager);
        }
        return values;
    }

    private static WindState snapshot(WindManager manager) throws Exception {
        return new WindState(windValues(manager), WIND_OVERRIDE.get(manager), SEED.get(manager));
    }

    private static boolean sameIdentityList(List<?> before, List<?> after) {
        if (before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i) != after.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static String classNames(List<?> values) {
        return values.stream().map(value -> value.getClass().getName()).toList().toString();
    }

    private static String values(double[] values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(Double.toString(values[i]));
        }
        return text.toString();
    }

    private static void observe(String name, ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable error) {
            EVIDENCE.append(name).append(".errorClass=").append(rootCause(error).getClass().getName()).append('\n');
            EVIDENCE.append(name).append(".errorMessage=").append(clean(rootCause(error).getMessage())).append('\n');
        } finally {
            persist();
        }
    }

    private static void persist() {
        try {
            Files.writeString(RESULT_PATH, EVIDENCE.toString());
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while ((current instanceof InvocationTargetException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String clean(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private interface ThrowingAction {
        void run() throws Throwable;
    }

    private record WindState(double[] winds, Object override, Object seed) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WindState state)) {
                return false;
            }
            if (!java.util.Objects.equals(override, state.override)
                    || !java.util.Objects.equals(seed, state.seed)) {
                return false;
            }
            for (int i = 0; i < winds.length; i++) {
                if (Double.doubleToLongBits(winds[i]) != Double.doubleToLongBits(state.winds[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
