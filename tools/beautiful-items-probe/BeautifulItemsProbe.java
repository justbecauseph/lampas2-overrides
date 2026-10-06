import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BeautifulItemsProbe implements ClientModInitializer {
    private static final String MODE = System.getProperty("beautifulItemsProbe.mode", "baseline");
    private static final Path RESULT_FILE = Path.of(System.getProperty("beautifulItemsProbe.resultFile"));
    private static final ModSpec BEB = new ModSpec("beb", "6.0.0",
            "com.cerbon.beb.fabric.BeautifulEnchantedBooksFabric", 695, "minecraft:sharpness");
    private static final ModSpec POTIONS = new ModSpec("potions", "2.0.1",
            "com.cerbon.beautiful_potions.fabric.BeautifulPotionsFabric", 828, "minecraft:swiftness/normal");
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_STARTED = new AtomicBoolean();
    private static final StringBuilder EVIDENCE = new StringBuilder();
    private static final Set<String> FIXTURE_ITEM_COMPONENTS = new LinkedHashSet<>();
    private static int initialWaitTicks;
    private static final String FILTER_PACK_ID = "file/beautiful-items-probe-filter";
    private static final String EMPTY_FILTER_PACK_ID = "file/beautiful-items-probe-empty";

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> CLIENT_STARTED.set(true));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!CLIENT_STARTED.get() || STARTED.get()) return;
            if (!client.isGameLoadFinished()) {
                if (++initialWaitTicks > 600) {
                    if (STARTED.compareAndSet(false, true)) {
                        fail("initial.wait.error", "game load did not finish within 600 client ticks");
                        finish(client);
                    }
                }
                return;
            }
            if (!STARTED.compareAndSet(false, true)) return;
            beginAfterInitialLoad(client);
        });
    }

    private static void beginAfterInitialLoad(Minecraft client) {
        try {
            record("status", "RUNNING");
            record("mode", MODE);
            record("initial.waitTicks", Integer.toString(initialWaitTicks));
            verifyTargetArtifact(client, BEB, "beautifulItemsProbe.expected.beb.jar");
            verifyTargetArtifact(client, POTIONS, "beautifulItemsProbe.expected.potions.jar");
            verifyOverridePresence();
            verifyInitialLoad(client, BEB);
            verifyInitialLoad(client, POTIONS);
            verifyParticleSprites(client, "initial");
            reloadAndThen(client, "reload1", () -> {
                verifyFullStage(client, BEB, "reload1");
                verifyFullStage(client, POTIONS, "reload1");
                verifyParticleSprites(client, "reload1");
                reloadAndThen(client, "reload2", () -> {
                    verifyFullStage(client, BEB, "reload2");
                    verifyFullStage(client, POTIONS, "reload2");
                    verifyParticleSprites(client, "reload2");
                    setFilterPackEnabled(client, true);
                    reloadAndThen(client, "filtered", () -> {
                        verifyFilteredStage(client, BEB);
                        verifyFilteredStage(client, POTIONS);
                        verifyParticleSprites(client, "filtered");
                        setFilterPackEnabled(client, false);
                        setEmptyFilterPackEnabled(client, true);
                        reloadAndThen(client, "empty", () -> {
                            verifyEmptyStage(client, BEB);
                            verifyEmptyStage(client, POTIONS);
                            record("resolver.empty.skipped",
                                    "all item model resources are blocked; no baked particle material exists");
                            setEmptyFilterPackEnabled(client, false);
                            reloadAndThen(client, "restored", () -> {
                                verifyFullStage(client, BEB, "restored");
                                verifyFullStage(client, POTIONS, "restored");
                                verifyParticleSprites(client, "restored");
                                record("status", "PASS");
                                finish(client);
                            });
                        });
                    });
                });
            });
        } catch (Throwable failure) {
            fail("probe.error", describe(failure));
            failure.printStackTrace();
            finish(client);
        }
    }

    private static void verifyTargetArtifact(Minecraft client, ModSpec spec, String expectedPathProperty)
            throws Exception {
        ModContainer container = FabricLoader.getInstance().getModContainer(spec.id())
                .orElseThrow(() -> new AssertionError("Fabric Loader did not load mod " + spec.id()));
        String version = container.getMetadata().getVersion().getFriendlyString();
        if (!spec.version().equals(version)) {
            throw new AssertionError(spec.id() + " version=" + version + " expected=" + spec.version());
        }
        Path expectedPath = Path.of(System.getProperty(expectedPathProperty)).toAbsolutePath().normalize();
        Path originPath = onlyOriginPath(container);
        if (!originPath.equals(expectedPath)) {
            throw new AssertionError(spec.id() + " loader origin=" + originPath + " expected=" + expectedPath);
        }
        String hash = sha256(originPath);
        record(spec.name() + ".version", version);
        record(spec.name() + ".origin", originPath.toString());
        record(spec.name() + ".sha256", hash);
    }

    private static void verifyOverridePresence() throws Exception {
        boolean expected = "patched".equals(MODE);
        Optional<ModContainer> loaded = FabricLoader.getInstance().getModContainer("lampas2-overrides");
        if (loaded.isPresent() != expected) {
            throw new AssertionError("Override presence=" + loaded.isPresent() + " expected=" + expected);
        }
        record("override.loaded", Boolean.toString(loaded.isPresent()));
        if (!expected) return;
        Path expectedPath = Path.of(System.getProperty("beautifulItemsProbe.expected.override.jar"))
                .toAbsolutePath().normalize();
        String expectedHash = System.getProperty("beautifulItemsProbe.expected.override.sha256", "");
        ModContainer container = loaded.orElseThrow();
        Path originPath = onlyOriginPath(container);
        if (!originPath.equals(expectedPath)) {
            throw new AssertionError("Override loader origin=" + originPath + " expected=" + expectedPath);
        }
        String actualHash = sha256(originPath);
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
            throw new AssertionError("Override origin SHA-256=" + actualHash + " expected=" + expectedHash);
        }
        record("override.version", container.getMetadata().getVersion().getFriendlyString());
        record("override.originKind", container.getOrigin().getKind().name());
        record("override.origin", originPath.toString());
        record("override.sha256", actualHash);
    }

    private static Path onlyOriginPath(ModContainer container) {
        List<Path> paths = container.getOrigin().getPaths();
        if (paths.size() != 1) {
            throw new AssertionError("Expected one physical JAR origin for "
                    + container.getMetadata().getId() + ", got " + paths);
        }
        Path path = paths.getFirst().toAbsolutePath().normalize();
        record(container.getMetadata().getId() + ".originKind", container.getOrigin().getKind().name());
        return path;
    }

    private static void verifyInitialLoad(Minecraft client, ModSpec spec) {
        ModelStats stats = inspectModels(client, spec);
        record(spec.name() + ".initial.models", Integer.toString(stats.models()));
        record(spec.name() + ".initial.hits", Integer.toString(stats.hits()));
        record(spec.name() + ".initial.misses", Integer.toString(stats.missingCount()));
        record(spec.name() + ".initial.missingKeySamples", String.join("|", stats.missingKeySamples()));
        record(spec.name() + ".initial.selectedKeyPresent", Boolean.toString(stats.selectedKeyPresent()));
        if (stats.models() != spec.expectedModels() || stats.hits() != spec.expectedModels()
                || !stats.selectedKeyPresent()) {
            throw new AssertionError(spec.name() + " initial models=" + stats.models()
                    + " hits=" + stats.hits() + " selectedKeyPresent=" + stats.selectedKeyPresent()
                    + " expected=" + spec.expectedModels());
        }
    }

    private static void verifyFullStage(Minecraft client, ModSpec spec, String stage) {
        ModelStats stats = inspectModels(client, spec);
        int expectedHits = "patched".equals(MODE) ? spec.expectedModels() : 0;
        record(spec.name() + "." + stage + ".models", Integer.toString(stats.models()));
        record(spec.name() + "." + stage + ".hits", Integer.toString(stats.hits()));
        record(spec.name() + "." + stage + ".misses", Integer.toString(stats.missingCount()));
        record(spec.name() + "." + stage + ".missingKeySamples", String.join("|", stats.missingKeySamples()));
        record(spec.name() + "." + stage + ".selectedKeyPresent", Boolean.toString(stats.selectedKeyPresent()));
        if (stats.models() != spec.expectedModels() || stats.hits() != expectedHits
                || !stats.selectedKeyPresent()) {
            throw new AssertionError(spec.name() + " " + stage + " models=" + stats.models()
                    + " hits=" + stats.hits() + " expectedHits=" + expectedHits
                    + " selectedKeyPresent=" + stats.selectedKeyPresent()
                    + " misses=" + stats.missingCount() + " sample=" + stats.missingKeySamples());
        }
    }

    private static void verifyFilteredStage(Minecraft client, ModSpec spec) {
        ModelStats stats = inspectModels(client, spec);
        boolean patched = "patched".equals(MODE);
        int expectedModels = spec.expectedModels() - (patched ? 1 : 0);
        int expectedHits = patched ? expectedModels : 0;
        boolean expectedKeyPresent = !patched;
        record(spec.name() + ".filtered.models", Integer.toString(stats.models()));
        record(spec.name() + ".filtered.hits", Integer.toString(stats.hits()));
        record(spec.name() + ".filtered.misses", Integer.toString(stats.missingCount()));
        record(spec.name() + ".filtered.missingKeySamples", String.join("|", stats.missingKeySamples()));
        record(spec.name() + ".filtered.selectedKeyPresent", Boolean.toString(stats.selectedKeyPresent()));
        if (stats.models() != expectedModels || stats.hits() != expectedHits
                || stats.selectedKeyPresent() != expectedKeyPresent) {
            throw new AssertionError(spec.name() + " filtered models=" + stats.models()
                    + " hits=" + stats.hits() + " expectedModels=" + expectedModels
                    + " expectedHits=" + expectedHits + " selectedKeyPresent=" + stats.selectedKeyPresent());
        }
    }

    private static void verifyEmptyStage(Minecraft client, ModSpec spec) {
        ModelStats stats = inspectModels(client, spec);
        boolean patched = "patched".equals(MODE);
        int expectedModels = patched ? 0 : spec.expectedModels();
        int expectedHits = 0;
        record(spec.name() + ".empty.models", Integer.toString(stats.models()));
        record(spec.name() + ".empty.hits", Integer.toString(stats.hits()));
        record(spec.name() + ".empty.misses", Integer.toString(stats.missingCount()));
        record(spec.name() + ".empty.missingKeySamples", String.join("|", stats.missingKeySamples()));
        record(spec.name() + ".empty.selectedKeyPresent", Boolean.toString(stats.selectedKeyPresent()));
        if (stats.models() != expectedModels || stats.hits() != expectedHits
                || stats.selectedKeyPresent() != !patched) {
            throw new AssertionError(spec.name() + " empty models=" + stats.models()
                    + " hits=" + stats.hits() + " expectedModels=" + expectedModels
                    + " expectedHits=" + expectedHits + " selectedKeyPresent=" + stats.selectedKeyPresent());
        }
    }

    private static void verifyParticleSprites(Minecraft client, String stage) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        Holder.Reference<Enchantment> sharpness = Holder.Reference.createStandAlone(
                new HolderOwner<Enchantment>() { }, Enchantments.SHARPNESS);
        sharpness.bindComponents(DataComponentMap.EMPTY);
        enchantments.set(sharpness, 1);
        bindFixtureItemComponents(Items.ENCHANTED_BOOK, "minecraft:enchanted_book");
        bindFixtureItemComponents(Items.POTION, "minecraft:potion");
        ItemStack book = Items.ENCHANTED_BOOK.getDefaultInstance();
        book.set(DataComponents.ITEM_MODEL, Identifier.parse("minecraft:enchanted_book"));
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
        potion.set(DataComponents.ITEM_MODEL, Identifier.parse("minecraft:potion"));
        String bookSprite = particleSprite(client, book);
        String potionSprite = particleSprite(client, potion);
        boolean custom = "initial".equals(stage) || ("patched".equals(MODE)
                && !"filtered".equals(stage) && !"empty".equals(stage));
        String expectedBook = custom
                ? "minecraft:item/enchanted_book/sharpness"
                : "minecraft:item/enchanted_book";
        String expectedPotion = custom
                ? "minecraft:item/potion/swiftness/normal"
                : "minecraft:item/potion_overlay";
        record("resolver." + stage + ".sharpnessHolder", "synthetic:minecraft:sharpness");
        record("resolver." + stage + ".sharpnessHolderComponents", "empty");
        record("resolver." + stage + ".fixtureStackInputs",
                "ITEM_MODEL=minecraft:enchanted_book,minecraft:potion;"
                        + "STORED_ENCHANTMENTS=synthetic:sharpness;POTION_CONTENTS=swiftness");
        record("resolver." + stage + ".worldSynchronization", "none; isolated title-screen fixture");
        record("resolver." + stage + ".bebParticleSprite", bookSprite);
        record("resolver." + stage + ".potionsParticleSprite", potionSprite);
        record("resolver." + stage + ".expectedBebParticleSprite", expectedBook);
        record("resolver." + stage + ".expectedPotionsParticleSprite", expectedPotion);
        if (!expectedBook.equals(bookSprite) || !expectedPotion.equals(potionSprite)) {
            throw new AssertionError("resolver " + stage + " particle sprites book=" + bookSprite
                    + " expected=" + expectedBook + ", potion=" + potionSprite + " expected=" + expectedPotion);
        }
    }

    private static void bindFixtureItemComponents(net.minecraft.world.item.Item item, String id) {
        Holder.Reference<net.minecraft.world.item.Item> holder = item.builtInRegistryHolder();
        if (!FIXTURE_ITEM_COMPONENTS.add(id)) return;
        boolean alreadyBound = holder.areComponentsBound();
        if (!alreadyBound) holder.bindComponents(DataComponentMap.EMPTY);
        record("resolver.fixtureItem." + id + ".components",
                alreadyBound ? "already-bound" : "bound-empty-map");
        record("resolver.fixtureItem." + id + ".worldSynchronization", "none");
    }

    private static String particleSprite(Minecraft client, ItemStack stack) {
        ItemModelResolver resolver = client.getItemModelResolver();
        ItemStackRenderState state = new ItemStackRenderState();
        resolver.updateForTopItem(state, stack, ItemDisplayContext.GUI, null, null, 0);
        var material = state.pickParticleMaterial(RandomSource.create());
        if (material == null) throw new AssertionError("Item model has no particle material for " + stack);
        return material.sprite().contents().name().toString();
    }

    private static void setFilterPackEnabled(Minecraft client, boolean enabled) {
        setPackEnabled(client, FILTER_PACK_ID, enabled);
    }

    private static void setEmptyFilterPackEnabled(Minecraft client, boolean enabled) {
        setPackEnabled(client, EMPTY_FILTER_PACK_ID, enabled);
    }

    private static void setPackEnabled(Minecraft client, String packId, boolean enabled) {
        var repository = client.getResourcePackRepository();
        if (!repository.getAvailableIds().contains(packId)) {
            throw new AssertionError("Fixture resource pack is not available: " + packId
                    + " available=" + repository.getAvailableIds());
        }
        Set<String> selected = new LinkedHashSet<>(repository.getSelectedIds());
        if (enabled) selected.add(packId);
        else selected.remove(packId);
        repository.setSelected(selected);
        boolean selectedNow = repository.getSelectedIds().contains(packId);
        if (selectedNow != enabled) {
            throw new AssertionError("Filter pack " + packId + " selected=" + selectedNow + " expected=" + enabled
                    + " selected=" + repository.getSelectedIds());
        }
        record(packId + "." + (enabled ? "enabled" : "disabled"), Boolean.toString(selectedNow));
    }

    private static void reloadAndThen(Minecraft client, String stage, Runnable continuation) {
        client.reloadResourcePacks().whenComplete((ignored, error) -> client.execute(() -> {
            if (error != null) {
                fail(stage + ".reload.error", describe(error));
                finish(client);
                return;
            }
            record(stage + ".reloadComplete", "true");
            try {
                continuation.run();
            } catch (Throwable failure) {
                fail(stage + ".error", describe(failure));
                failure.printStackTrace();
                finish(client);
            }
        }));
    }

    private static ModelStats inspectModels(Minecraft client, ModSpec spec) {
        try {
            Class<?> owner = Class.forName(spec.ownerClass(), true, BeautifulItemsProbe.class.getClassLoader());
            Object value = owner.getField("REGISTERED_MODELS").get(null);
            if (!(value instanceof Map<?, ?> models)) {
                throw new AssertionError(spec.name() + " REGISTERED_MODELS is not a Map: " + value);
            }
            FabricModelManager manager = (FabricModelManager) (Object) client.getModelManager();
            int hits = 0;
            int missingCount = 0;
            boolean selectedKeyPresent = false;
            List<String> misses = new ArrayList<>();
            for (Map.Entry<?, ?> entry : models.entrySet()) {
                if (!(entry.getKey() instanceof Identifier id)) {
                    throw new AssertionError(spec.name() + " map contains non-Identifier key " + entry.getKey());
                }
                if (!(entry.getValue() instanceof ExtraModelKey<?> key)) {
                    throw new AssertionError(spec.name() + " map contains non-ExtraModelKey value " + entry.getValue());
                }
                if (id.toString().equals(spec.selectedKey())) selectedKeyPresent = true;
                Object model = manager.getModel(castKey(key));
                if (model == null) {
                    missingCount++;
                    if (misses.size() < 12) misses.add(id.toString());
                } else {
                    hits++;
                }
            }
            return new ModelStats(models.size(), hits, missingCount, selectedKeyPresent, List.copyOf(misses));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot inspect model cache for " + spec.name(), error);
        }
    }

    @SuppressWarnings("unchecked")
    private static ExtraModelKey<Object> castKey(ExtraModelKey<?> key) {
        return (ExtraModelKey<Object>) key;
    }

    private static void finish(Minecraft client) {
        try {
            Files.createDirectories(RESULT_FILE.toAbsolutePath().getParent());
            Files.writeString(RESULT_FILE, EVIDENCE.toString());
        } catch (IOException error) {
            error.printStackTrace();
        } finally {
            client.stop();
        }
    }

    private static void record(String key, String value) {
        EVIDENCE.append(key).append('=').append(value.replace('\n', ' ')).append('\n');
        System.out.println("BEAUTIFUL_ITEMS_PROBE " + key + '=' + value);
    }

    private static void fail(String key, String value) {
        record("status", "FAIL");
        record(key, value);
    }

    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getName() + ": " + String.valueOf(root.getMessage());
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record ModSpec(String name, String version, String ownerClass,
                           int expectedModels, String selectedKey) {
        String id() {
            return "beb".equals(name) ? "beb" : "beautiful_potions";
        }
    }

    private record ModelStats(int models, int hits, int missingCount,
                              boolean selectedKeyPresent, List<String> missingKeySamples) {
    }
}
