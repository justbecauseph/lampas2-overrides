import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;
import lampas2overrides.trinketsdatafix.TrinketsSchemaRepair;

/** Disposable production-JAR probe for the exact Trinkets 4.1.1 V1460 schema. */
public final class TrinketsDfuProbe {
	private static final String EXPECTED_TINKETS_VERSION = "4.1.1+26.2";
	private static final String EXPECTED_TINKETS_JAR_SHA256 = "2365e8a19b5f62c812e95ac5fc23ee163bc753a5d9ec38f517f72da269eb0c4b";
	private static final String EXPECTED_V1460_CLASS_SHA256 = "07f35c1ac7a56705c9671b7703b3c58dddbdc419c0fb00edf89b13e757810265";
	private static final String V1460_CLASS = "eu.pb4.trinkets.mixin.datafixer.V1460Mixin";
	private static final String V1460_ENTRY = "eu/pb4/trinkets/mixin/datafixer/V1460Mixin.class";
	private static final DSL.TypeReference ENTITY = () -> "probe:entity";
	private static final DSL.TypeReference ITEM_STACK = References.ITEM_STACK;
	private static Path trinketsJarPath;

	public static void main(String[] args) throws Exception {
		Path productionJar = Path.of(argument(args, "--production-jar")).toAbsolutePath().normalize();
		trinketsJarPath = Path.of(argument(args, "--trinkets-jar")).toAbsolutePath().normalize();
		verifyProductionJar(productionJar);
		verifyTrinketsJar(trinketsJarPath);

		JsonObject input = fixture();
		JsonObject upstream = fixer(false).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		check(upstream.getAsJsonObject("trinkets").isEmpty(),
			"The exact upstream V1460 template did not reproduce the modern root data loss: " + upstream);
		check(!upstream.getAsJsonObject("cardinal_components").has("other_component"),
			"The exact upstream V1460 template did not reproduce the Cardinal sibling loss: " + upstream);

		JsonObject migrated = fixer(true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject expected = fixture();
		migrate(expected, "Item");
		migrate(expected, "Inventory", "0");
		migrate(expected, "ArmorItems", "0");
		migrate(expected, "HandItems", "0");
		migrate(expected, "trinkets", "hand/ring", "Items", "0");
		migrate(expected, "trinkets", "hand/ring", "cosmetic", "0");
		migrate(expected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		check(expected.equals(migrated), "Migration mismatch. Expected " + expected + ", got " + migrated);

		JsonObject roundTrip = readWrite(fixture(), true);
		check(fixture().equals(roundTrip), "No-rewrite read/write changed data. Got " + roundTrip);

		JsonObject legacy = legacyFixture();
		JsonObject legacyMigrated = fixer(true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, legacy), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject legacyExpected = legacyFixture();
		migrate(legacyExpected, "Item");
		migrate(legacyExpected, "Inventory", "0");
		migrate(legacyExpected, "ArmorItems", "0");
		migrate(legacyExpected, "HandItems", "0");
		migrate(legacyExpected, "trinkets", "hand", "ring", "Items", "0");
		migrate(legacyExpected, "trinkets", "hand", "ring", "cosmetic", "0");
		migrate(legacyExpected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		check(legacyExpected.equals(legacyMigrated), "Legacy grouped migration mismatch. Expected "
			+ legacyExpected + ", got " + legacyMigrated);

		System.out.println("PASS: exact Trinkets 4.1.1 V1460 class, production helper, modern/legacy item migration, "
			+ "Cardinal siblings, metadata, scalar/empty remainder, and no-rewrite round trip");
	}

	private static String argument(String[] args, String key) {
		for (int i = 0; i + 1 < args.length; i++) if (key.equals(args[i])) return args[i + 1];
		throw new IllegalArgumentException("Missing required argument " + key);
	}

	private static void verifyProductionJar(Path path) throws Exception {
		try (JarFile jar = new JarFile(path.toFile())) {
			check(jar.getJarEntry("lampas2overrides/trinketsdatafix/TrinketsSchemaRepair.class") != null,
				"Production jar does not contain TrinketsSchemaRepair: " + path);
		}
	}

	private static void verifyTrinketsJar(Path path) throws Exception {
		try (JarFile jar = new JarFile(path.toFile())) {
			JsonObject metadata;
			try (var in = jar.getInputStream(jar.getJarEntry("fabric.mod.json"));
				var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				metadata = JsonParser.parseReader(reader).getAsJsonObject();
			}
			check("trinkets_updated".equals(metadata.get("id").getAsString()), "Wrong Trinkets mod ID");
			check(EXPECTED_TINKETS_VERSION.equals(metadata.get("version").getAsString()), "Wrong Trinkets version");
			check(EXPECTED_TINKETS_JAR_SHA256.equals(sha256(path)), "Wrong Trinkets JAR SHA-256");
			check(EXPECTED_V1460_CLASS_SHA256.equals(sha256(jar, V1460_ENTRY)),
				"Wrong V1460 class SHA-256");
		}
		Class<?> upstream = Class.forName(V1460_CLASS);
		Path loadedFrom = Path.of(upstream.getProtectionDomain().getCodeSource().getLocation().toURI())
			.toAbsolutePath().normalize();
		check(path.equals(loadedFrom), "V1460 class loaded from " + loadedFrom + " instead of " + path);
	}

	private static String sha256(Path path) throws Exception {
		try (var in = java.nio.file.Files.newInputStream(path)) {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
		}
	}

	private static String sha256(JarFile jar, String entryName) throws Exception {
		try (var in = jar.getInputStream(jar.getJarEntry(entryName))) {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
		}
	}

	private static TypeTemplate upstreamTemplate(Schema schema, TypeTemplate vanillaTemplate) {
		try {
			Class<?> mixin = Class.forName(V1460_CLASS);
			Field field = mixin.getDeclaredField("schema");
			field.setAccessible(true);
			field.set(null, schema);
			Method method = mixin.getDeclaredMethod("attachTrinketFixer", TypeTemplate.class);
			method.setAccessible(true);
			return (TypeTemplate) method.invoke(null, vanillaTemplate);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot invoke exact upstream V1460 schema method", e);
		}
	}

	private static TypeTemplate entityTemplate(Schema schema, boolean repaired) {
		TypeTemplate item = ITEM_STACK.in(schema);
		TypeTemplate vanilla = DSL.optionalFields("Item", item,
			"Inventory", DSL.list(item), "ArmorItems", DSL.list(item), "HandItems", DSL.list(item));
		TypeTemplate upstream = upstreamTemplate(schema, vanilla);
		return repaired ? TrinketsSchemaRepair.repair(upstream, item, TrinketsSchemaRepair.Profile.V4_1_1) : upstream;
	}

	private abstract static class ProbeSchema extends Schema {
		ProbeSchema(int version, Schema parent) { super(version, parent); }
		protected abstract boolean repaired();

		@Override public void registerTypes(Schema schema,
			Map<String, Supplier<TypeTemplate>> entities,
			Map<String, Supplier<TypeTemplate>> blocks) {
			schema.registerType(true, ITEM_STACK, DSL::remainder);
			schema.registerType(true, ENTITY, () -> entityTemplate(schema, repaired()));
		}

		@Override public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
			return new HashMap<>();
		}

		@Override public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
			return new HashMap<>();
		}
	}

	private static final class UnrepairedSchema extends ProbeSchema {
		UnrepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean repaired() { return false; }
	}

	private static final class RepairedSchema extends ProbeSchema {
		RepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean repaired() { return true; }
	}

	private static final class MarkerFix extends DataFix {
		MarkerFix(Schema output) { super(output, true); }

		@Override protected TypeRewriteRule makeRule() {
			Type<?> item = getInputSchema().getType(ITEM_STACK);
			return fixTypeEverywhereTyped("probe migrate marker", item,
				typed -> typed.update(DSL.remainderFinder(), value ->
					value.get("legacy_marker").result().isPresent()
						? value.set("migrated_marker", value.get("legacy_marker").result().get())
							.remove("legacy_marker") : value));
		}
	}

	private static DataFixer fixer(boolean repaired) {
		DataFixerBuilder builder = new DataFixerBuilder(1);
		BiFunction<Integer, Schema, Schema> schemaFactory = (version, parent) -> repaired
			? new RepairedSchema(version, parent)
			: new UnrepairedSchema(version, parent);
		builder.addSchema(0, schemaFactory);
		Schema output = builder.addSchema(1, schemaFactory);
		builder.addFixer(new MarkerFix(output));
		return builder.build().fixer();
	}

	private static JsonObject readWrite(JsonObject input, boolean repaired) {
		Schema schema = repaired ? new RepairedSchema(0, null) : new UnrepairedSchema(0, null);
		return (JsonObject) schema.getType(ENTITY)
			.readTyped(new Dynamic<>(JsonOps.INSTANCE, input)).getOrThrow().getFirst()
			.write().getOrThrow().getValue();
	}

	private static JsonObject base() {
		return JsonParser.parseString("""
			{"Item":{"id":"minecraft:spyglass","legacy_marker":"entity"},
			 "unknown":{"legacy_marker":"must-stay-legacy","x":7},
			 "Inventory":[{"id":"minecraft:apple","legacy_marker":"player-inventory"}],
			 "ArmorItems":[{"id":"minecraft:boots","legacy_marker":"armor"}],
			 "HandItems":[{"id":"minecraft:stick","legacy_marker":"hand"}]}
			""").getAsJsonObject();
	}

	private static JsonObject fixture() {
		JsonObject input = base();
		input.add("trinkets", JsonParser.parseString("""
			{"__version":1,
			 "hand/ring":{"Items":[{"id":"minecraft:apple","legacy_marker":"trinket"}],
			              "cosmetic":[{"id":"minecraft:stick","legacy_marker":"cosmetic"}],
			              "hidden_slots":[],"slot_extra":true},
			 "scalar":7,"empty_object":{},"empty_array":[],"unknown":{"x":true}}
			""").getAsJsonObject());
		input.add("cardinal_components", JsonParser.parseString("""
			{"other_component":{"keep":true},"scalar_sibling":9,
			 "trinkets:trinkets":{"hand":{"ring":{"Items":[{"id":"minecraft:torch","legacy_marker":"cardinal"}],"extra":"keep"}}}}
			""").getAsJsonObject());
		return input;
	}

	private static JsonObject legacyFixture() {
		JsonObject input = base();
		input.add("trinkets", JsonParser.parseString("""
			{"hand":{"ring":{"Items":[{"id":"minecraft:torch","legacy_marker":"legacy-root"}],
			                 "cosmetic":[{"id":"minecraft:apple","legacy_marker":"legacy-cosmetic"}]}}}
			""").getAsJsonObject());
		input.add("cardinal_components", JsonParser.parseString("""
			{"sibling":{"keep":true},
			 "trinkets:trinkets":{"hand":{"ring":{"Items":[{"id":"minecraft:stick","legacy_marker":"legacy-cardinal"}]}}}}
			""").getAsJsonObject());
		return input;
	}

	private static void migrate(JsonObject root, String... path) {
		com.google.gson.JsonElement current = root;
		for (String segment : path) {
			current = current.isJsonArray()
				? current.getAsJsonArray().get(Integer.parseInt(segment))
				: current.getAsJsonObject().get(segment);
		}
		var item = current.getAsJsonObject();
		item.add("migrated_marker", item.remove("legacy_marker"));
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
