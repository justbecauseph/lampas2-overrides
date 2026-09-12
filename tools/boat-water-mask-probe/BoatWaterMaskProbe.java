import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

/**
 * A self-closing structural client probe for the EMF boat water-mask conflict.
 *
 * It deliberately has no compile dependency on EMF, ETF, Pyrite, or any other
 * optional provider. Their runtime contracts are inspected through reflection
 * so a missing optional jar is reported in the evidence rather than causing the
 * probe itself to link on a dedicated server.
 */
public final class BoatWaterMaskProbe implements ClientModInitializer {
    private static final String FA_RESOURCE_PACK_SUFFIX = "FA+All_Extensions-v1.9.2.zip";
    private static final String FA_BASE_PACK_SUFFIX = "FreshAnimations_v1.10.5.zip";
    private static final String FA_PLAYER_PACK_SUFFIX = "FA+Player-v1.1.zip";
    private static final String[] RESOURCE_PATHS = {
            "optifine/cem/boat.jem",
            "optifine/cem/boat_patch.jem",
            "optifine/cem/chest_boat.jem"
    };
    private static final String FA_BOAT_PATCH_SHA256 =
            "4d924242a3787ca708b0165961078bcf91bd455d0bb881a10b9a28a0a3fa725b";
    private static final Map<String, Integer> EXPECTED_PROVIDER_MASK_COUNTS = Map.of(
            "pyrite", 52,
            "promenade", 6,
            "wilderwild", 10,
            "betterend", 18,
            "betternether", 22);
    private static final Set<String> EXPECTED_PROVIDER_NAMESPACES =
            EXPECTED_PROVIDER_MASK_COUNTS.keySet();
    private static final String[] REQUIRED_IDS = {
            "pyrite:cyan_stained_boat",
            "pyrite:cyan_stained_chest_boat",
            "minecraft:oak_boat",
            "minecraft:oak_chest_boat",
            "promenade:sakura_boat",
            "promenade:sakura_chest_boat",
            "wilderwild:baobab_boat",
            "wilderwild:baobab_chest_boat",
            "betterend:dragon_tree_boat",
            "betterend:dragon_tree_chest_boat",
            "betternether:anchor_tree_boat",
            "betternether:anchor_tree_chest_boat",
            "bloomingnature:mod_boat",
            "bloomingnature:mod_chest_boat"
    };
    private static final List<Stage> STAGES = List.of(
            new Stage("ON-1", true),
            new Stage("OFF", false),
            new Stage("ON-2", true),
            new Stage("OFF-2", false)
    );

    private final boolean strict = Boolean.parseBoolean(System.getProperty("boat.probe.strict", "true"));
    private Minecraft client;
    private int stageIndex;
    private boolean hadCheckFailures;
    private final List<String> resultLines = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            client = mc;
            stageIndex = 0;
            System.out.println("BOAT_PROBE_STARTED strict=" + strict + " runDir=" + mc.gameDirectory);
            mc.execute(this::startStage);
        });
    }

    private void startStage() {
        Stage stage = STAGES.get(stageIndex);
        try {
            PackRepository repository = client.getResourcePackRepository();
            repository.reload();
            String faPackId = findPack(repository, FA_RESOURCE_PACK_SUFFIX);
            String basePackId = findPack(repository, FA_BASE_PACK_SUFFIX);
            String playerPackId = findPack(repository, FA_PLAYER_PACK_SUFFIX);
            List<String> selected = new ArrayList<>();
            if (stage.faEnabled) {
                selected.add(faPackId);
            }
            selected.add(basePackId);
            selected.add(playerPackId);
            repository.setSelected(selected);
            client.options.updateResourcePacks(repository);
            System.out.println("BOAT_PROBE_RELOAD stage=" + stage.name + " fa=" + stage.faEnabled
                    + " selected=" + repository.getSelectedIds());
            CompletableFuture<Void> reload = client.reloadResourcePacks();
            reload.whenComplete((unused, error) -> client.execute(() -> finishStage(stage, error)));
        } catch (Throwable error) {
            failAndStop(stage.name + " setup", error);
        }
    }

    private static String findPack(PackRepository repository, String suffix) {
        return repository.getAvailableIds().stream()
                .filter(id -> id.endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "required resource pack is not available (" + suffix + "): "
                                + repository.getAvailableIds()));
    }

    private void finishStage(Stage stage, Throwable error) {
        if (error != null) {
            failAndStop(stage.name + " resource reload", error);
            return;
        }
        try {
            List<Snapshot> snapshots = inspectRenderers();
            String resources = inspectResources();
            String emfCache = inspectEmfCache();
            String line = "BOAT_PROBE_STAGE stage=" + stage.name + " fa=" + stage.faEnabled
                    + " renderers=" + snapshots.size() + " " + snapshots.stream()
                    .map(Snapshot::compact)
                    .sorted()
                    .reduce((a, b) -> a + ";" + b)
                    .orElse("none");
            resultLines.add(line);
            resultLines.add("BOAT_PROBE_RESOURCES stage=" + stage.name + " " + resources);
            resultLines.add("BOAT_PROBE_EMF_CACHE stage=" + stage.name + " " + emfCache);
            System.out.println(line);
            System.out.println("BOAT_PROBE_RESOURCES stage=" + stage.name + " " + resources);
            System.out.println("BOAT_PROBE_EMF_CACHE stage=" + stage.name + " " + emfCache);
            checkStage(stage, snapshots, resources);

            if (++stageIndex < STAGES.size()) {
                client.execute(this::startStage);
            } else {
                writeResult(hadCheckFailures ? "CHECK_FAILURES" : "PASS");
                client.stop();
            }
        } catch (Throwable problem) {
            failAndStop(stage.name + " inspection", problem);
        }
    }

    private List<Snapshot> inspectRenderers() throws ReflectiveOperationException {
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Field field = findField(dispatcher.getClass(), "renderers");
        Object value = field.get(dispatcher);
        if (!(value instanceof Map<?, ?> rendererMap)) {
            throw new IllegalStateException("dispatcher.renderers is " + value);
        }

        List<Snapshot> snapshots = new ArrayList<>();
        for (Map.Entry<?, ?> entry : rendererMap.entrySet()) {
            if (!(entry.getKey() instanceof EntityType<?> type)) {
                continue;
            }
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null) {
                continue;
            }
            String id = key.toString();
            String path = key.getPath();
            if (!path.contains("boat") && !path.contains("raft")) {
                continue;
            }
            Object renderer = entry.getValue();
            if (!(renderer instanceof AbstractBoatRenderer)) {
                snapshots.add(new Snapshot(id, renderer.getClass().getName(), false,
                        false, false, false, "", "", "", "", "", ""));
                continue;
            }
            Object model = fieldValue(renderer, "model");
            Object patch = fieldValueOrNull(renderer, "waterPatchModel");
            RootInfo body = rootInfo(model);
            RootInfo water = rootInfo(patch);
            snapshots.add(new Snapshot(id, renderer.getClass().getName(), patch != null,
                    body.emf, water.emf, water.root != null && vanillaPatchGeometry(water.root),
                    body.rootClass, water.rootClass, body.emfName, water.emfName,
                    geometrySignature(body.root), geometrySignature(water.root)));
        }
        snapshots.sort(Comparator.comparing(s -> s.id));
        return snapshots;
    }

    private void checkStage(Stage stage, List<Snapshot> snapshots, String resources) {
        Map<String, Snapshot> byId = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> byId.put(snapshot.id, snapshot));
        List<String> errors = new ArrayList<>();
        for (String id : REQUIRED_IDS) {
            if (!byId.containsKey(id)) {
                errors.add("missing renderer " + id);
            }
        }

        if (stage.faEnabled) {
            Snapshot cyanBoat = byId.get("pyrite:cyan_stained_boat");
            Snapshot cyanChestBoat = byId.get("pyrite:cyan_stained_chest_boat");
            require(errors, cyanBoat, "pyrite cyan ordinary", false, false, true);
            require(errors, cyanChestBoat, "pyrite cyan chest", false, false, true);

            Snapshot oakBoat = byId.get("minecraft:oak_boat");
            Snapshot oakChestBoat = byId.get("minecraft:oak_chest_boat");
            require(errors, oakBoat, "oak ordinary control", true, true, false);
            require(errors, oakChestBoat, "oak chest control", true, true, false);

            requireNoPatch(errors, byId.get("bloomingnature:mod_boat"),
                    "BloomingNature ordinary custom renderer");
            requireNoPatch(errors, byId.get("bloomingnature:mod_chest_boat"),
                    "BloomingNature chest custom renderer");

            for (Snapshot snapshot : snapshots) {
                if (!snapshot.hasPatch || !snapshot.id.startsWith("pyrite:")) {
                    continue;
                }
                require(errors, snapshot, snapshot.id + " Pyrite mask", false, false, true);
            }
            checkProviderMaskCounts(errors, snapshots);
            checkOtherProviderMasks(errors, snapshots);
            checkBetterXRafts(errors, snapshots);
            if (!effectiveFaPatchHash(resources)) {
                errors.add("effective FA boat_patch resource hash is absent or changed");
            }
        } else {
            for (Snapshot snapshot : snapshots) {
                if (snapshot.hasPatch) {
                    require(errors, snapshot, snapshot.id + " FA-off reset", false, false, true);
                }
            }
        }
        if (!errors.isEmpty()) {
            hadCheckFailures = true;
            String message = "BOAT_PROBE_CHECK stage=" + stage.name + " errors=" + String.join("|", errors);
            System.out.println(message);
            resultLines.add(message);
            if (strict) {
                throw new AssertionError(message);
            }
        } else {
            String message = "BOAT_PROBE_CHECK stage=" + stage.name + " PASS";
            System.out.println(message);
            resultLines.add(message);
        }
    }

    private static void checkProviderMaskCounts(List<String> errors, List<Snapshot> snapshots) {
        int supportedMasks = 0;
        for (Map.Entry<String, Integer> expected : EXPECTED_PROVIDER_MASK_COUNTS.entrySet()) {
            int actual = 0;
            for (Snapshot snapshot : snapshots) {
                if (snapshot.hasPatch && namespace(snapshot.id).equals(expected.getKey())) {
                    actual++;
                }
            }
            supportedMasks += actual;
            if (actual != expected.getValue()) {
                errors.add(expected.getKey() + " mask count=" + actual
                        + " expected=" + expected.getValue());
            }
        }
        if (supportedMasks != 108) {
            errors.add("supported provider mask count=" + supportedMasks + " expected=108");
        }
    }

    private static void checkOtherProviderMasks(List<String> errors, List<Snapshot> snapshots) {
        for (Snapshot snapshot : snapshots) {
            if (!snapshot.hasPatch || !EXPECTED_PROVIDER_NAMESPACES.contains(namespace(snapshot.id))) {
                continue;
            }
            require(errors, snapshot, snapshot.id + " provider mask", false, false, true);
        }
    }

    private static void checkBetterXRafts(List<String> errors, List<Snapshot> snapshots) {
        for (Snapshot snapshot : snapshots) {
            String namespace = namespace(snapshot.id);
            if ((namespace.equals("betterend") || namespace.equals("betternether"))
                    && snapshot.rendererClass.endsWith("RaftRenderer")) {
                requireNoPatch(errors, snapshot, snapshot.id + " raft renderer");
            }
        }
    }

    private static String namespace(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? "" : id.substring(0, separator);
    }

    private static void require(List<String> errors, Snapshot snapshot, String label,
                                boolean bodyEmf, boolean patchEmf, boolean vanillaPatch) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.bodyEmf != bodyEmf) {
            errors.add(label + " bodyEmf=" + snapshot.bodyEmf + " expected=" + bodyEmf);
        }
        if (!snapshot.hasPatch) {
            errors.add(label + " missing waterPatchModel");
        } else if (snapshot.patchEmf != patchEmf) {
            errors.add(label + " patchEmf=" + snapshot.patchEmf + " expected=" + patchEmf);
        } else if (snapshot.patchVanillaGeometry != vanillaPatch) {
            errors.add(label + " patchVanillaGeometry=" + snapshot.patchVanillaGeometry
                    + " expected=" + vanillaPatch);
        }
    }

    private static void requireNoPatch(List<String> errors, Snapshot snapshot, String label) {
        if (snapshot != null && snapshot.hasPatch) {
            errors.add(label + " unexpectedly has waterPatchModel");
        }
    }

    private String inspectResources() {
        ResourceManager manager = client.getResourceManager();
        List<String> parts = new ArrayList<>();
        for (String path : RESOURCE_PATHS) {
            Identifier id = Identifier.fromNamespaceAndPath("minecraft", path);
            List<Resource> stack = manager.getResourceStack(id);
            List<String> resources = new ArrayList<>();
            for (Resource resource : stack) {
                resources.add(describeResource(resource));
            }
            Optional<Resource> effective = manager.getResource(id);
            parts.add(path + "=[" + String.join(",", resources) + "] effective="
                    + effective.map(BoatWaterMaskProbe::describeResource).orElse("none"));
        }
        return String.join(" ", parts);
    }

    private static String describeResource(Resource resource) {
        try (InputStream input = resource.open()) {
            return resource.sourcePackId() + "#" + sha256(input);
        } catch (IOException error) {
            return resource.sourcePackId() + "#ERROR:" + error.getClass().getSimpleName();
        }
    }

    private static boolean effectiveFaPatchHash(String resources) {
        String marker = "optifine/cem/boat_patch.jem=";
        int markerIndex = resources.indexOf(marker);
        if (markerIndex < 0) {
            return false;
        }
        int effectiveIndex = resources.indexOf("effective=", markerIndex);
        if (effectiveIndex < 0) {
            return false;
        }
        String effective = resources.substring(effectiveIndex + "effective=".length());
        int nextPath = effective.indexOf(" optifine/cem/", 1);
        if (nextPath >= 0) {
            effective = effective.substring(0, nextPath);
        }
        return effective.endsWith(FA_RESOURCE_PACK_SUFFIX + "#" + FA_BOAT_PATCH_SHA256);
    }

    private String inspectEmfCache() {
        try {
            Class<?> managerClass = Class.forName("traben.entity_model_features.EMFManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            List<String> packs = new ArrayList<>();
            Object packList = managerClass.getMethod("getResourcePackList").invoke(manager);
            if (packList instanceof Collection<?> collection) {
                for (Object item : collection) {
                    packs.add(String.valueOf(item));
                }
            }
            List<String> layers = mapKeysContaining(manager, "cache_LayersByModelName", "boat");
            List<String> jems = mapKeysContaining(manager, "cache_JemDataByFileName", "boat");
            return "packs=" + packs + " layers=" + layers + " jems=" + jems;
        } catch (Throwable error) {
            return "unavailable=" + error.getClass().getSimpleName() + ":" + safe(error.getMessage());
        }
    }

    private static List<String> mapKeysContaining(Object owner, String fieldName, String needle)
            throws ReflectiveOperationException {
        Object value = findField(owner.getClass(), fieldName).get(owner);
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        return map.keySet().stream()
                .map(String::valueOf)
                .filter(valueString -> valueString.toLowerCase().contains(needle))
                .sorted()
                .toList();
    }

    private void writeResult(String status) {
        resultLines.add("BOAT_PROBE_RESULT " + status);
        try {
            Path result = Path.of(client.gameDirectory.toString())
                    .resolve("boat-water-mask-probe-result.txt");
            Files.writeString(result,
                    String.join(System.lineSeparator(), resultLines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            System.out.println("BOAT_PROBE_RESULT " + status);
        } catch (IOException error) {
            System.err.println("BOAT_PROBE_RESULT_WRITE_ERROR " + error);
        }
    }

    private void failAndStop(String phase, Throwable error) {
        String message = "BOAT_PROBE_RESULT FAIL phase=" + phase + " error="
                + error.getClass().getName() + ":" + safe(error.getMessage());
        System.err.println(message);
        resultLines.add(message);
        writeResult("FAIL");
        if (client != null) {
            client.stop();
        }
    }

    private static Object fieldValue(Object owner, String name) throws ReflectiveOperationException {
        return findField(owner.getClass(), name).get(owner);
    }

    private static Object fieldValueOrNull(Object owner, String name) throws ReflectiveOperationException {
        if (owner == null) {
            return null;
        }
        try {
            return fieldValue(owner, name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static RootInfo rootInfo(Object model) throws ReflectiveOperationException {
        if (model == null) {
            return new RootInfo(null, false, "", "");
        }
        Method rootMethod = model.getClass().getMethod("root");
        Object root = rootMethod.invoke(model);
        boolean emf = false;
        Object emfRoot = null;
        String emfName = "";
        try {
            Class<?> emfModel = Class.forName("traben.entity_model_features.models.IEMFModel");
            if (emfModel.isInstance(model)) {
                emf = (Boolean) emfModel.getMethod("emf$isEMFModel").invoke(model);
                emfRoot = emfModel.getMethod("emf$getEMFRootModel").invoke(model);
                if (emfRoot != null) {
                    Field modelName = findField(emfRoot.getClass(), "modelName");
                    emfName = String.valueOf(modelName.get(emfRoot));
                }
            }
        } catch (ClassNotFoundException ignored) {
            // EMF is an optional fixture; vanilla-only runs remain useful.
        }
        String rootClass = root == null ? "" : root.getClass().getName();
        return new RootInfo(root, emf, rootClass, emfName);
    }

    private static boolean vanillaPatchGeometry(Object root) {
        if (!(root instanceof ModelPart)) {
            return false;
        }
        try {
            Object vanilla = BoatModel.createWaterPatch().bakeRoot();
            String expected = geometrySignature(vanilla);
            String actual = geometrySignature(root);
            if (expected.isEmpty() || expected.startsWith("ERROR:")
                    || actual.isEmpty() || actual.startsWith("ERROR:")) {
                return false;
            }
            return Objects.equals(expected, actual);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String geometrySignature(Object root) {
        if (!(root instanceof ModelPart)) {
            return "";
        }
        try {
            Field cubes = ModelPart.class.getDeclaredField("cubes");
            Field children = ModelPart.class.getDeclaredField("children");
            cubes.setAccessible(true);
            children.setAccessible(true);
            StringBuilder out = new StringBuilder();
            appendPart(out, "root", root, cubes, children);
            return sha256(out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (ReflectiveOperationException error) {
            return "ERROR:" + error.getClass().getSimpleName();
        }
    }

    private static void appendPart(StringBuilder out, String name, Object part,
                                   Field cubes, Field children) throws ReflectiveOperationException {
        ModelPart modelPart = (ModelPart) part;
        out.append(name).append('|')
                .append(Float.floatToIntBits(modelPart.x)).append(',')
                .append(Float.floatToIntBits(modelPart.y)).append(',')
                .append(Float.floatToIntBits(modelPart.z)).append(',')
                .append(Float.floatToIntBits(modelPart.xRot)).append(',')
                .append(Float.floatToIntBits(modelPart.yRot)).append(',')
                .append(Float.floatToIntBits(modelPart.zRot)).append(',')
                .append(Float.floatToIntBits(modelPart.xScale)).append(',')
                .append(Float.floatToIntBits(modelPart.yScale)).append(',')
                .append(Float.floatToIntBits(modelPart.zScale)).append(':');
        List<?> cubeList = (List<?>) cubes.get(modelPart);
        out.append("cubes=").append(cubeList.size());
        for (Object cube : cubeList) {
            Field minX = cube.getClass().getField("minX");
            Field minY = cube.getClass().getField("minY");
            Field minZ = cube.getClass().getField("minZ");
            Field maxX = cube.getClass().getField("maxX");
            Field maxY = cube.getClass().getField("maxY");
            Field maxZ = cube.getClass().getField("maxZ");
            Field polygons = cube.getClass().getField("polygons");
            out.append('[').append(minX.getFloat(cube)).append(',').append(minY.getFloat(cube))
                    .append(',').append(minZ.getFloat(cube)).append(',').append(maxX.getFloat(cube))
                    .append(',').append(maxY.getFloat(cube)).append(',').append(maxZ.getFloat(cube))
                    .append(",faces=").append(((Object[]) polygons.get(cube)).length).append(']');
        }
        Object childValue = children.get(modelPart);
        if (!(childValue instanceof Map<?, ?> rawChildren)) {
            return;
        }
        Map<String, Object> childMap = new TreeMap<>();
        for (Map.Entry<?, ?> child : rawChildren.entrySet()) {
            if (child.getKey() instanceof String childName) {
                childMap.put(childName, child.getValue());
            }
        }
        for (Map.Entry<String, Object> child : childMap.entrySet()) {
            out.append('{');
            appendPart(out, child.getKey(), child.getValue(), cubes, children);
            out.append('}');
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    private static String safe(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private record Stage(String name, boolean faEnabled) {}

    private record RootInfo(Object root, boolean emf, String rootClass, String emfName) {}

    private record Snapshot(String id, String rendererClass, boolean hasPatch, boolean bodyEmf,
                            boolean patchEmf, boolean patchVanillaGeometry, String bodyRootClass,
                            String patchRootClass, String bodyEmfName, String patchEmfName,
                            String bodyGeometry, String patchGeometry) {
        private String compact() {
            return id + "{r=" + rendererClass.substring(rendererClass.lastIndexOf('.') + 1)
                    + ",body=" + bool(bodyEmf) + ",patch=" + (hasPatch ? bool(patchEmf) : "none")
                    + ",vanilla=" + (hasPatch && patchVanillaGeometry) + ",br=" + shortName(bodyRootClass)
                    + ",pr=" + shortName(patchRootClass) + ",bn=" + safe(bodyEmfName)
                    + ",pn=" + safe(patchEmfName) + ",bg=" + shortHash(bodyGeometry)
                    + ",pg=" + shortHash(patchGeometry) + "}";
        }

        private static String bool(boolean value) {
            return value ? "emf" : "vanilla";
        }

        private static String shortName(String name) {
            if (name == null || name.isEmpty()) {
                return "";
            }
            int split = name.lastIndexOf('.');
            return split >= 0 ? name.substring(split + 1) : name;
        }

        private static String shortHash(String hash) {
            return hash == null || hash.length() < 8 ? hash : hash.substring(0, 8);
        }
    }
}
