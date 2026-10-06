package lampas2overrides.trinketsdatafix;

import static org.junit.jupiter.api.Assertions.*;

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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;
import org.junit.jupiter.api.Test;

/** Semantic regressions use the exact 4.1.1 attachment method and DFU typed migration. */
final class TrinketsSchemaMigrationTest {
	private static final DSL.TypeReference ENTITY = () -> "test:entity";
	private static final DSL.TypeReference ITEM_STACK = References.ITEM_STACK;

	private static TypeTemplate oldTrinketData(TypeTemplate item) {
		TypeTemplate slot = DSL.optional(DSL.compoundList(DSL.optionalFields("Items", DSL.list(item))));
		return DSL.optional(DSL.compoundList(DSL.and(slot,
			DSL.optionalFields("Items", DSL.list(item)),
			DSL.optionalFields("cosmetic", DSL.list(item)))));
	}

	private static TypeTemplate entityTemplate(Schema schema, boolean current411, boolean repaired) {
		TypeTemplate item = ITEM_STACK.in(schema);
		TypeTemplate original = DSL.optionalFields("Item", item,
			"Inventory", DSL.list(item), "ArmorItems", DSL.list(item), "HandItems", DSL.list(item));
		if (current411) {
			TypeTemplate deployed;
			try {
				deployed = AuditedTrinkets411.attach(schema, original);
			} catch (Exception e) {
				throw new IllegalStateException("Could not invoke the pinned Trinkets 4.1.1 schema attachment", e);
			}
			return repaired
				? TrinketsSchemaRepair.repair(deployed, item, TrinketsSchemaRepair.Profile.V4_1_1)
				: deployed;
		}

		TypeTemplate data = oldTrinketData(item);
		TypeTemplate cardinal = DSL.optional(DSL.field("cardinal_components",
			DSL.optionalFields("trinkets:trinkets", data)));
		TypeTemplate trinkets = DSL.optional(DSL.optionalFields("trinkets", data));
		TypeTemplate deployed = DSL.allWithRemainder(cardinal, trinkets, original);
		return repaired
			? TrinketsSchemaRepair.repair(deployed, item, TrinketsSchemaRepair.Profile.V4_1_0)
			: deployed;
	}

	private abstract static class TestSchema extends Schema {
		TestSchema(int version, Schema parent) { super(version, parent); }
		protected abstract boolean current411();
		protected abstract boolean repaired();

		@Override public void registerTypes(Schema schema,
			Map<String, Supplier<TypeTemplate>> entities,
			Map<String, Supplier<TypeTemplate>> blocks) {
			schema.registerType(true, ITEM_STACK, DSL::remainder);
			schema.registerType(true, ENTITY, () -> entityTemplate(schema, current411(), repaired()));
		}

		@Override public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
			return new HashMap<>();
		}

		@Override public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
			return new HashMap<>();
		}
	}

	private static final class CurrentUnrepairedSchema extends TestSchema {
		CurrentUnrepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean current411() { return true; }
		@Override protected boolean repaired() { return false; }
	}

	private static final class CurrentRepairedSchema extends TestSchema {
		CurrentRepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean current411() { return true; }
		@Override protected boolean repaired() { return true; }
	}

	private static final class LegacyUnrepairedSchema extends TestSchema {
		LegacyUnrepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean current411() { return false; }
		@Override protected boolean repaired() { return false; }
	}

	private static final class LegacyRepairedSchema extends TestSchema {
		LegacyRepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean current411() { return false; }
		@Override protected boolean repaired() { return true; }
	}

	private static final class MarkerFix extends DataFix {
		MarkerFix(Schema output) { super(output, true); }

		@Override protected TypeRewriteRule makeRule() {
			Type<?> item = getInputSchema().getType(ITEM_STACK);
			return fixTypeEverywhereTyped("migrate test item marker", item,
				typed -> typed.update(DSL.remainderFinder(), value ->
					value.get("legacy_marker").result().isPresent()
						? value.set("migrated_marker", value.get("legacy_marker").result().get())
							.remove("legacy_marker") : value));
		}
	}

	private static DataFixer fixer(boolean current411, boolean repaired) {
		DataFixerBuilder builder = new DataFixerBuilder(1);
		BiFunction<Integer, Schema, Schema> factory = (version, parent) -> makeSchema(version, parent,
			current411, repaired);
		builder.addSchema(0, factory);
		Schema target = builder.addSchema(1, factory);
		builder.addFixer(new MarkerFix(target));
		return builder.build().fixer();
	}

	private static TestSchema makeSchema(int version, Schema parent, boolean current411, boolean repaired) {
		if (current411) return repaired
			? new CurrentRepairedSchema(version, parent)
			: new CurrentUnrepairedSchema(version, parent);
		return repaired
			? new LegacyRepairedSchema(version, parent)
			: new LegacyUnrepairedSchema(version, parent);
	}

	private static JsonObject vanillaFixture() {
		return JsonParser.parseString("""
			{"Item":{"id":"minecraft:spyglass","legacy_marker":"entity"},
			 "unknown":{"legacy_marker":"must-stay-legacy","x":7},
			 "Inventory":[{"id":"minecraft:apple","legacy_marker":"player-inventory"}],
			 "ArmorItems":[{"id":"minecraft:boots","legacy_marker":"armor"}],
			 "HandItems":[{"id":"minecraft:stick","legacy_marker":"hand"}]}
			""").getAsJsonObject();
	}

	private static JsonObject currentFixture() {
		JsonObject input = vanillaFixture();
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

	@Test void pinnedArtifactMetadataAndClassHashMatchTheIndependentGate() throws Exception {
		JsonObject metadata = AuditedTrinkets411.metadata();
		assertEquals("trinkets_updated", metadata.get("id").getAsString());
		assertEquals("4.1.1+26.2", metadata.get("version").getAsString());
		assertEquals(TrinketsDataFixMixinPlugin.CURRENT_CLASS_SHA256, AuditedTrinkets411.classSha256());
	}

	@Test void exactUpstreamSchemaReproducesFlatAndCardinalLossBeforeRepair() {
		JsonObject input = currentFixture();
		JsonObject actual = fixer(true, false).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		assertEquals(new JsonObject(), actual.getAsJsonObject("trinkets"), actual.toString());
		assertFalse(actual.getAsJsonObject("cardinal_components").has("other_component"), actual.toString());
	}

	@Test void exactUpstreamSchemaRepairMigratesVanillaFlatAndCardinalItems() {
		JsonObject input = currentFixture();
		JsonObject actual = fixer(true, true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject expected = currentFixture();
		migrate(expected, "Item");
		migrate(expected, "Inventory", "0");
		migrate(expected, "ArmorItems", "0");
		migrate(expected, "HandItems", "0");
		migrate(expected, "trinkets", "hand/ring", "Items", "0");
		migrate(expected, "trinkets", "hand/ring", "cosmetic", "0");
		migrate(expected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		assertEquals(expected, actual);
	}

	@Test void exactUpstreamSchemaRepairPreservesFlatDataOnReadWriteWithoutMigration() throws Exception {
		JsonObject input = currentFixture();
		Schema schema = new CurrentRepairedSchema(0, null);
		JsonObject actual = (JsonObject) schema.getType(ENTITY)
			.readTyped(new Dynamic<>(JsonOps.INSTANCE, input)).getOrThrow().getFirst()
			.write().getOrThrow().getValue();
		assertEquals(input, actual);
	}

	@Test void exactUpstreamSchemaRepairMigratesLegacyGroupedItems() {
		JsonObject input = vanillaFixture();
		input.add("trinkets", JsonParser.parseString("""
			{"hand":{"ring":{"Items":[{"id":"minecraft:torch","legacy_marker":"legacy-root"}],
			                 "cosmetic":[{"id":"minecraft:apple","legacy_marker":"legacy-cosmetic"}]}}}
			""").getAsJsonObject());
		input.add("cardinal_components", JsonParser.parseString("""
			{"sibling":{"keep":true},
			 "trinkets:trinkets":{"hand":{"ring":{"Items":[{"id":"minecraft:stick","legacy_marker":"legacy-cardinal"}]}}}}
			""").getAsJsonObject());
		JsonObject actual = fixer(true, true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject expected = input.deepCopy();
		migrate(expected, "Item");
		migrate(expected, "Inventory", "0");
		migrate(expected, "ArmorItems", "0");
		migrate(expected, "HandItems", "0");
		migrate(expected, "trinkets", "hand", "ring", "Items", "0");
		migrate(expected, "trinkets", "hand", "ring", "cosmetic", "0");
		migrate(expected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		assertEquals(expected, actual);
	}

	@Test void oldArtifactProfileStillMigratesLegacyItems() {
		JsonObject input = vanillaFixture();
		input.add("trinkets", JsonParser.parseString(
			"{\"hand\":{\"ring\":{\"Items\":[{\"id\":\"minecraft:torch\",\"legacy_marker\":\"legacy\"}]}}}")
			.getAsJsonObject());
		JsonObject actual = fixer(false, true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		migrate(input, "Item");
		migrate(input, "Inventory", "0");
		migrate(input, "ArmorItems", "0");
		migrate(input, "HandItems", "0");
		migrate(input, "trinkets", "hand", "ring", "Items", "0");
		assertEquals(input, actual);
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
}
