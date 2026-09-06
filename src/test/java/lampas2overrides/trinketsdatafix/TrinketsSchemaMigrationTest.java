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
import java.util.function.Supplier;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** Semantic regression for the deployed Trinkets V1460 remainder wrapper. */
final class TrinketsSchemaMigrationTest {
	private static final DSL.TypeReference ENTITY = () -> "test:entity";
	private static final DSL.TypeReference ITEM_STACK = () -> "test:item_stack";

	private static TypeTemplate oldTrinketData(Schema schema, TypeTemplate item) {
		TypeTemplate slot = DSL.optional(DSL.compoundList(DSL.optionalFields("Items", DSL.list(item))));
		return DSL.optional(DSL.compoundList(DSL.and(slot,
			DSL.optionalFields("Items", DSL.list(item)),
			DSL.optionalFields("cosmetic", DSL.list(item)))));
	}

	private static TypeTemplate oldEntity(Schema schema, boolean repaired) {
		TypeTemplate item = ITEM_STACK.in(schema);
		TypeTemplate data = oldTrinketData(schema, item);
		TypeTemplate cardinal = DSL.optional(DSL.field("cardinal_components",
			DSL.optionalFields("trinkets:trinkets", data)));
		TypeTemplate trinkets = DSL.optional(DSL.optionalFields("trinkets", data));
		TypeTemplate original = DSL.optionalFields("Item", item,
			"Inventory", DSL.list(item), "ArmorItems", DSL.list(item), "HandItems", DSL.list(item));
		TypeTemplate deployed = DSL.allWithRemainder(cardinal, trinkets, original);
		return repaired ? TrinketsSchemaRepair.repair(deployed, item) : deployed;
	}

	private abstract static class TestSchema extends Schema {
		TestSchema(int version, Schema parent) { super(version, parent); }
		protected abstract boolean repaired();

		@Override public void registerTypes(Schema schema,
			Map<String, Supplier<TypeTemplate>> entities,
			Map<String, Supplier<TypeTemplate>> blocks) {
			schema.registerType(true, ITEM_STACK, DSL::remainder);
			schema.registerType(true, ENTITY, () -> oldEntity(schema, repaired()));
		}

		@Override public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
			return new HashMap<>();
		}

		@Override public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
			return new HashMap<>();
		}
	}
	private static final class UnrepairedSchema extends TestSchema {
		UnrepairedSchema(int version, Schema parent) { super(version, parent); }
		@Override protected boolean repaired() { return false; }
	}
	private static final class RepairedSchema extends TestSchema {
		RepairedSchema(int version, Schema parent) { super(version, parent); }
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

	private static DataFixer fixer(boolean repaired) {
		DataFixerBuilder builder = new DataFixerBuilder(1);
		BiFunction<Integer, Schema, Schema> schemaFactory = repaired ? RepairedSchema::new : UnrepairedSchema::new;
		Schema output = builder.addSchema(0, schemaFactory);
		Schema target = builder.addSchema(1, schemaFactory);
		builder.addFixer(new MarkerFix(target));
		return builder.build().fixer();
	}

	private static JsonObject fixture() {
		return JsonParser.parseString("""
			{"Item":{"id":"minecraft:spyglass","legacy_marker":"entity"},
			 "unknown":{"legacy_marker":"must-stay-legacy","x":7},
			 "Inventory":[{"id":"minecraft:apple","legacy_marker":"player-inventory"}],
			 "ArmorItems":[{"id":"minecraft:boots","legacy_marker":"armor"}],
			 "HandItems":[{"id":"minecraft:stick","legacy_marker":"hand"}],
			 "trinkets":{"__version":1,"hand":{"Items":[{"id":"minecraft:apple","legacy_marker":"trinket"}],"cosmetic":[{"id":"minecraft:stick","legacy_marker":"cosmetic"}]}},
			 "cardinal_components":{"trinkets:trinkets":{"hand":{"ring":{"Items":[{"id":"minecraft:torch","legacy_marker":"cardinal"}]}}}}}
			""").getAsJsonObject();
	}

	@Test void repairedSchemaMigratesTypedItemsAndPreservesRemainder() {
		JsonObject input = fixture();
		JsonObject baseline = fixer(false).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		assertEquals("entity", baseline.getAsJsonObject("Item").get("legacy_marker").getAsString());
		assertFalse(baseline.getAsJsonObject("Item").has("migrated_marker"), baseline.toString());

		JsonObject actual = fixer(true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject expected = fixture();
		migrate(expected, "Item");
		migrate(expected, "Inventory", "0");
		migrate(expected, "ArmorItems", "0");
		migrate(expected, "HandItems", "0");
		migrate(expected, "trinkets", "hand", "Items", "0");
		migrate(expected, "trinkets", "hand", "cosmetic", "0");
		migrate(expected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		assertEquals(expected, actual);
	}

	@Test void repairedSchemaMigratesRootLegacyGroupAndSlotLayout() {
		JsonObject input = fixture();
		input.remove("trinkets");
		input.add("trinkets", JsonParser.parseString(
			"{\"hand\":{\"ring\":{\"Items\":[{\"id\":\"minecraft:torch\",\"legacy_marker\":\"root-legacy\"}]}}}").getAsJsonObject());
		input.getAsJsonObject("cardinal_components").add("other_component",
			JsonParser.parseString("{\"keep\":true}").getAsJsonObject());
		JsonObject actual = fixer(true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject expected = input.deepCopy();
		migrate(expected, "Item");
		migrate(expected, "Inventory", "0");
		migrate(expected, "ArmorItems", "0");
		migrate(expected, "HandItems", "0");
		migrate(expected, "trinkets", "hand", "ring", "Items", "0");
		migrate(expected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		assertEquals(expected, actual);
	}

	@Test void repairedSchemaPreservesAbsentAndEmptyInventoryData() {
		JsonObject input = fixture();
		input.remove("trinkets");
		input.add("trinkets", JsonParser.parseString(
			"{\"__version\":1,\"hand\":{\"Items\":[],\"cosmetic\":[]},\"extra\":[1,2,3]}").getAsJsonObject());
		input.getAsJsonObject("cardinal_components").add("sibling", JsonParser.parseString("7").getAsJsonPrimitive());
		JsonObject actual = fixer(true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, input), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject expected = input.deepCopy();
		migrate(expected, "Item");
		migrate(expected, "Inventory", "0");
		migrate(expected, "ArmorItems", "0");
		migrate(expected, "HandItems", "0");
		migrate(expected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		assertEquals(expected, actual);

		JsonObject absent = fixture();
		absent.remove("trinkets");
		JsonObject absentActual = fixer(true).update(ENTITY, new Dynamic<>(JsonOps.INSTANCE, absent), 0, 1)
			.getValue().getAsJsonObject();
		JsonObject absentExpected = absent.deepCopy();
		migrate(absentExpected, "Item");
		migrate(absentExpected, "Inventory", "0");
		migrate(absentExpected, "ArmorItems", "0");
		migrate(absentExpected, "HandItems", "0");
		migrate(absentExpected, "cardinal_components", "trinkets:trinkets", "hand", "ring", "Items", "0");
		assertEquals(absentExpected, absentActual);
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
