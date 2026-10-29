package lampas2overrides.resourcefix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * RPG Series spell patches for the 17 Oct 2026 class balance decisions D3 P1 and D9, with the frost multipliers and
 * fire damage coefficients of the 29 Oct 2026 caster loot scaling calibration.
 * <p>
 * Expected values are the decisions written out here, not values read back from the replacements:
 * the decided spell lists, P1 (haste_affected false on every martial spell with a cooldown), the D9 apply-limit
 * table and the fire coefficient table. Top-band health uses BALANCE.md's per-level health (+2.5 Overworld, +3.0
 * Nether, +3.5 End) on 20 (zombie, piglin) and 40 (enderman) base health at level 200.
 */
public class RpgSeriesSpellPatchTest {

	/** Opt-in: the dev server's mods directory. Pass {@code -PserverModsDir=<dir>} to Gradle. */
	private static final String SERVER_MODS_PROPERTY = "lampas2.serverModsDir";

	private static final String RPG = "3.1.3.003+26.2";

	/** jar file, mod id, declared version, jar SHA-256 (pack 2.3.54, pack.lock.json and dev server). */
	private static final String[][] JARS = {
		{"rogues-fabric-3.1.3.003+26.2.jar", "rogues", RPG, "77bdff881c01011bff6e6e722a1f47dc01ca7d377a1f08231d305fc11b08f113"},
		{"wizards-fabric-3.1.3.003+26.2.jar", "wizards", RPG, "ced172215b3f5fb4301252d171e26c621fbebcc579fc67bd639fb2d92a8034a2"},
		{"paladins-fabric-3.1.3.003+26.2.jar", "paladins", RPG, "f36009e6a60fae66ca195fc9165a2d7a59c92bd55fd75b1e2e18620ffa4fc492"},
		{"archers-fabric-3.1.3.003+26.2.jar", "archers", RPG, "4ee9c35f54d74f28f742f36a4f0b319d4d3936bc0731bd95cb823c17225db22d"},
		{"spell_engine-fabric-1.10.9+26.2.jar", "spell_engine", "1.10.9+26.2", "bfc258875662071d76c448e603f05f0f519bf3e8a0c32207d232d820cab0ab2a"},
		{"skill_tree-fabric-1.6.2+26.2.jar", "skill_tree_rpgs", "1.6.2+26.2", "405ff761ba518c87a14d8923f0c8db5f01311b86d58ca78c3e1f2731d3469901"},
		{"relics-fabric-1.4.1+26.2.jar", "relics_rpgs", "1.4.1+26.2", "013eae223a352138342e5d629e5fec75ee7f19405b82589435993f403be8469f"},
	};

	/** D3 P1 (Q3 default: all 59 martial spells, relic procs included), as "mod version path". */
	private static final Set<String> P1 = Set.copyOf(concat(
		spells("rogues", RPG, "rogues", "bear_trap", "charge", "mortal_strike", "mutilate", "shadow_step", "shock_powder",
			"shout", "slice_and_dice", "throw", "throw_net", "vanish"),
		spells("spell_engine", "1.10.9+26.2", "rpg_series", "cleave", "fan_of_knives", "flurry", "ground_slam", "impale",
			"smash", "swift_strikes", "swipe", "thrust", "whirlwind"),
		spells("paladins", RPG, "paladins", "judgement"),
		spells("archers", RPG, "archers", "barrage", "entangling_roots", "magic_arrow", "power_shot", "rain_of_arrows"),
		spells("skill_tree_rpgs", "1.6.2+26.2", "skill_tree_rpgs", "archer_tier_1_passive_1", "archer_tier_2_passive_1",
			"archer_tier_2_passive_2", "archer_tier_3_passive_1", "archer_tier_3_passive_2", "paladin_tier_1_passive_1",
			"paladin_tier_3_passive_2", "rogue_tier_1_passive_2", "rogue_tier_2_passive_2", "rogue_tier_3_passive_1",
			"rogue_tier_3_passive_2", "warrior_tier_1_passive_1", "warrior_tier_1_passive_2", "warrior_tier_3_passive_1",
			"warrior_tier_3_passive_2", "warrior_tier_3_spell_1_modifier_2", "weapon_bow_passive_1", "weapon_swipe_modifier_2"),
		spells("relics_rpgs", "1.4.1+26.2", "relics_rpgs", "greater_perk_defense_danger", "greater_perk_evasion_attack",
			"greater_perk_melee_stun", "greater_perk_ranged_levitate", "greater_proc_physical_trance", "lesser_use_damage",
			"lesser_use_dex", "lesser_use_ranged", "medium_proc_attack_damage", "medium_proc_attack_speed",
			"medium_proc_defense", "medium_proc_evasion", "medium_proc_ranged_damage", "superior_use_area_attack_damage")));

	/** D9: "mod version path" -> {impact index, upstream base, upstream k, new base, new k}. Q8 default: flat Roots 780. */
	private static final Map<String, double[]> LIMITS = new LinkedHashMap<>();
	/**
	 * Pinnacle power of each D9 spell's user: the audit's optimized scratch models (unblended, no tree node), except
	 * frost, which is the caster loot calibration's realistic Pinnacle frost power with Apotheosis loot.
	 */
	private static final Map<String, Double> PINNACLE_POWER = new LinkedHashMap<>();
	/** Fire Wall and burning ground: "mod version path" -> {impact index, upstream coefficient, new coefficient}. */
	private static final Map<String, double[]> FIRE = new LinkedHashMap<>();

	static {
		// Frost k: the smallest multiple of 0.5 with base + k x 119.19 >= 1.1 x 736.5 (caster loot calibration).
		limit("wizards", RPG, "data/wizards/spell/frost_nova.json", 1, 60, 4, 60, 6.5, 119.19);
		limit("wizards", RPG, "data/wizards/spell/frost_spikes.json", 1, 60, 4, 60, 6.5, 119.19);
		limit("wizards", RPG, "data/wizards/spell/frostbolt.json", 1, 100, 4, 100, 6.0, 119.19);
		limit("wizards", RPG, "data/wizards/spell/frost_lance.json", 1, 100, 4, 100, 6.0, 119.19);
		limit("wizards", RPG, "data/wizards/spell/frost_blizzard.json", 1, 80, 4, 80, 6.5, 119.19);
		limit("paladins", RPG, "data/paladins/spell/judgement.json", 1, 50, 2, 50, 7.5, 103.04);
		limit("rogues", RPG, "data/rogues/spell/shout.json", 0, 50, 2, 50, 9.5, 84.0);
		limit("rogues", RPG, "data/rogues/spell/throw.json", 1, 100, 2, 100, 8.5, 84.0);
		limit("rogues", RPG, "data/rogues/spell/throw_net.json", 1, 100, 2, 100, 8.5, 84.0);
		limit("rogues", RPG, "data/rogues/spell/shock_powder.json", 0, 50, 2, 50, 7.5, 105.65);
		limit("rogues", RPG, "data/rogues/spell/bear_trap.json", 1, 100, 2, 100, 5.5, 133.7);
		limit("archers", RPG, "data/archers/spell/entangling_roots.json", 0, 50, 5, 780, 5, 1.0);

		// Damage spell_power_coefficient x 0.4. Upstream values as read from the deployed jars: Fire Wall 0.8 (0.8 x 0.4 =
		// 0.32), burning ground 0.3 (0.3 x 0.4 = 0.12; the balance model also uses 0.3).
		coefficient("wizards", RPG, "data/wizards/spell/fire_wall.json", 0, 0.8, 0.32);
		coefficient("skill_tree_rpgs", "1.6.2+26.2", "data/skill_tree_rpgs/spell/fire_tier_3_spell_1_modifier_2.json", 0, 0.3, 0.12);
	}

	private static final Set<String> PHYSICAL = Set.of(
		"spell_power:physical_melee", "spell_power:physical_melee_dual", "spell_power:physical_ranged");

	private static final double OVERWORLD_TOP = 20 + 199 * 2.5;
	private static final double NETHER_TOP = 20 + 199 * 3.0;
	private static final double END_TOP = 40 + 199 * 3.5;

	/** Realistic frost power per World Tier, Haven to Pinnacle (caster loot calibration, 29 Oct 2026). */
	private static final double[] FROST_TIER_POWER = {22.15, 40.54, 67.38, 112.32, 119.19};
	/** Overworld top-band max health per World Tier, Haven to Pinnacle. */
	private static final double[] OVERWORLD_TIER_TOP = {67.5, 142.5, 267.5, 392.5, 517.5};

	/** Python json.dumps(indent=2, ensure_ascii=False), the format every upstream spell file is written in. */
	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@BeforeEach
	void setUp() {
		ResourcePatchResolver.clearLogHistoryForTests();
	}

	@Test
	void registersExactlyTheDecidedSpells() {
		assertEquals(59, P1.size());
		assertEquals(12, LIMITS.size());
		assertEquals(2, FIRE.size());
		Set<String> decided = new HashSet<>(P1);
		decided.addAll(LIMITS.keySet());
		decided.addAll(FIRE.keySet());
		assertEquals(66, decided.size(), "Seven files carry both P1 and D9 in one replacement");
		assertEquals(decided, spellPatches().stream().map(RpgSeriesSpellPatchTest::id).collect(Collectors.toSet()));

		assertNotNull(ResourcePatchRegistry.findPatch("spell_engine", "1.10.9+26.2", "data/rpg_series/spell/whirlwind.json"));
		assertNull(ResourcePatchRegistry.findPatch("spell_engine", "1.10.10+26.2", "data/rpg_series/spell/whirlwind.json"));
		assertNull(ResourcePatchRegistry.findPatch("wizards", RPG, "data/wizards/spell/fireball.json"),
			"Caster spells keep hasted cooldowns");
		assertNull(ResourcePatchRegistry.findPatch("archers", RPG, "data/archers/spell/spirit_wolf.json"),
			"Spirit Wolf already sets haste_affected false upstream");
		assertNull(ResourcePatchRegistry.findPatch("rogues", RPG, "data/rogues/spell/last_stand.json"),
			"The health school has no haste source");
	}

	/** Each replacement is its upstream jar entry, byte for byte, with only the decided keys changed. */
	@Test
	void replacementsChangeOnlyTheDecidedKeys() throws IOException {
		for (ResourcePatch patch : spellPatches()) {
			String id = id(patch);
			byte[] upstream = fixture(patch);
			assertEquals(patch.expectedSha256(), ResourcePatchResolver.sha256Hex(upstream), id);
			String upstreamText = new String(upstream, StandardCharsets.UTF_8);
			JsonObject expected = JsonParser.parseString(upstreamText).getAsJsonObject();
			assertEquals(upstreamText, PRETTY.toJson(expected), "Fixture is not in the generator's format: " + id);

			boolean physical = PHYSICAL.contains(expected.get("school").getAsString());
			assertEquals(P1.contains(id), physical, "P1 must cover exactly the martial spells: " + id);
			if (physical) {
				JsonObject cooldown = expected.getAsJsonObject("cost").getAsJsonObject("cooldown");
				assertTrue(cooldown.get("duration").getAsDouble() > 0, id);
				assertFalse(cooldown.has("haste_affected"), "Upstream leaves haste_affected at its default: " + id);
				cooldown.addProperty("haste_affected", false);
			}
			double[] limit = LIMITS.get(id);
			if (limit != null) {
				JsonObject applyLimit = applyLimit(expected, (int) limit[0]);
				assertEquals(limit[1], applyLimit.get("health_base").getAsDouble(), id);
				assertEquals(limit[2], applyLimit.get("spell_power_multiplier").getAsDouble(), id);
				applyLimit.addProperty("health_base", limit[3]);
				applyLimit.addProperty("spell_power_multiplier", limit[4]);
			}
			double[] coefficient = FIRE.get(id);
			if (coefficient != null) {
				JsonObject damage = expected.getAsJsonArray("impacts").get((int) coefficient[0]).getAsJsonObject()
					.getAsJsonObject("action").getAsJsonObject("damage");
				assertEquals(coefficient[1], damage.get("spell_power_coefficient").getAsDouble(), id);
				damage.addProperty("spell_power_coefficient", coefficient[2]);
			}
			assertEquals(PRETTY.toJson(expected), new String(replacement(patch), StandardCharsets.UTF_8), id);
		}
	}

	@Test
	void raisedLimitsReachEndTopBandForOptimizedPinnacleUsers() {
		assertEquals(517.5, OVERWORLD_TOP);
		assertEquals(617.0, NETHER_TOP);
		assertEquals(736.5, END_TOP);
		String roots = "archers " + RPG + " data/archers/spell/entangling_roots.json";
		double lowestScaled = Double.MAX_VALUE;
		for (Map.Entry<String, double[]> entry : LIMITS.entrySet()) {
			double[] limit = entry.getValue();
			double power = PINNACLE_POWER.get(entry.getKey());
			double upstream = limit[1] + limit[2] * power;
			double raised = limit[3] + limit[4] * power;
			// The raise is needed only if the upstream limit misses the End top band it targets. The Overworld top band
			// is no bound for every spell: with Apotheosis caster loot the frost power is 119.19, and upstream Frost Nova
			// already holds 60 + 4 x 119.19 = 536.76 health, above the Overworld 517.5.
			assertTrue(upstream < END_TOP, "Upstream limit already reached the End top band: " + entry.getKey());
			assertTrue(raised >= END_TOP * 1.05, entry.getKey() + " reaches only " + raised);
			if (!entry.getKey().equals(roots)) {
				lowestScaled = Math.min(lowestScaled, raised);
			}
		}
		// Lowest power-scaled limit: Throw and Throw Net, 100 + 8.5 x 84 (Frostbolt and Frost Lance are next at
		// 100 + 6 x 119.19 = 815.14). Roots is flat: 780 + 5 x 1 attack damage.
		assertEquals(814.0, lowestScaled, 1e-9);
		assertEquals(785.0, LIMITS.get(roots)[3] + LIMITS.get(roots)[4] * PINNACLE_POWER.get(roots), 1e-9);
	}

	/**
	 * Caster loot calibration rule for the frost limits: keep the upstream health_base and take the smallest multiple of
	 * 0.5 for k with base + k x 119.19 >= 1.1 x 736.5. Each tier's realistic frost power then still holds that tier's
	 * Overworld top band, and Pinnacle also holds the Nether and End top bands.
	 */
	@Test
	void frostLimitsAreTheSmallestHalfStepReachingTenPercentOverEndTopBand() {
		double target = 1.1 * END_TOP;
		assertEquals(810.15, target, 1e-9);
		assertEquals(OVERWORLD_TOP, OVERWORLD_TIER_TOP[4]);
		int frost = 0;
		for (Map.Entry<String, double[]> entry : LIMITS.entrySet()) {
			if (!entry.getKey().startsWith("wizards ")) {
				continue;
			}
			frost++;
			String id = entry.getKey();
			double[] limit = entry.getValue();
			double base = limit[3];
			double k = limit[4];
			assertEquals(limit[1], base, "Frost keeps the upstream health_base: " + id);
			assertEquals(FROST_TIER_POWER[4], PINNACLE_POWER.get(id), id);
			assertEquals(0.0, k % 0.5, id);
			assertTrue(base + k * FROST_TIER_POWER[4] >= target, id + " reaches only " + (base + k * FROST_TIER_POWER[4]));
			assertTrue(base + (k - 0.5) * FROST_TIER_POWER[4] < target, id + " would reach the target with k - 0.5");
			for (int tier = 0; tier < FROST_TIER_POWER.length; tier++) {
				double reached = base + k * FROST_TIER_POWER[tier];
				assertTrue(reached >= OVERWORLD_TIER_TOP[tier], id + " at tier " + tier + " reaches only " + reached);
			}
			assertTrue(base + k * FROST_TIER_POWER[4] >= NETHER_TOP, id);
			assertTrue(base + k * FROST_TIER_POWER[4] >= END_TOP, id);
		}
		assertEquals(5, frost);
	}

	/** Fire Wall and the Meteor burning ground deal 0.4 x their upstream damage; Meteor's own hit is unchanged. */
	@Test
	void fireCoefficientsAreUpstreamTimesFactor() {
		for (Map.Entry<String, double[]> entry : FIRE.entrySet()) {
			String[] id = entry.getKey().split(" ");
			double[] coefficient = entry.getValue();
			assertEquals(coefficient[1] * 0.4, coefficient[2], 1e-12, entry.getKey());
			assertNotNull(ResourcePatchRegistry.findPatch(id[0], id[1], id[2]), entry.getKey());
		}
		assertNull(ResourcePatchRegistry.findPatch("wizards", RPG, "data/wizards/spell/fire_meteor.json"));
	}

	@Test
	void appliesSpellPatchForEachModWithLoaderNormalizedVersion() throws IOException {
		String[][] samples = {
			{"rogues", RPG, "3.1.3.3+26.2", "data/rogues/spell/charge.json"},
			{"wizards", RPG, "3.1.3.3+26.2", "data/wizards/spell/frost_nova.json"},
			{"paladins", RPG, "3.1.3.3+26.2", "data/paladins/spell/judgement.json"},
			{"archers", RPG, "3.1.3.3+26.2", "data/archers/spell/power_shot.json"},
			{"spell_engine", "1.10.9+26.2", "1.10.9+26.2", "data/rpg_series/spell/flurry.json"},
			{"skill_tree_rpgs", "1.6.2+26.2", "1.6.2+26.2", "data/skill_tree_rpgs/spell/warrior_tier_3_passive_2.json"},
			{"relics_rpgs", "1.4.1+26.2", "1.4.1+26.2", "data/relics_rpgs/spell/medium_proc_attack_speed.json"},
		};
		for (String[] sample : samples) {
			ResourcePatch patch = ResourcePatchRegistry.findPatch(sample[0], sample[1], sample[3]);
			assertNotNull(patch, sample[3]);
			ModContainer mod = ResourcePatchResolverTest.createMockModContainer(sample[0], sample[1]);
			assertEquals(sample[2], mod.getMetadata().getVersion().getFriendlyString());
			byte[] upstream = fixture(patch);
			try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
				IoSupplier<InputStream> resolved = ResourcePatchResolver.resolve(mod, sample[3], () -> new ByteArrayInputStream(upstream));
				assertNotNull(resolved, sample[3]);
				try (InputStream in = resolved.get()) {
					assertArrayEquals(replacement(patch), in.readAllBytes(), sample[3]);
				}
				assertTrue(logs.has(Level.INFO, "Applied " + sample[0] + " " + sample[1] + " compatibility resource: " + sample[3]),
					logs.messages().toString());
			}
		}
	}

	@Test
	void warnsAndKeepsUpstreamSpellForUnregisteredVersion() throws IOException {
		String path = "data/wizards/spell/frost_nova.json";
		byte[] upstream = fixture(ResourcePatchRegistry.findPatch("wizards", RPG, path));
		try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
			assertNull(ResourcePatchResolver.resolve(ResourcePatchResolverTest.createMockModContainer("wizards", "3.1.3.004+26.2"),
				path, () -> new ByteArrayInputStream(upstream)));
			assertTrue(logs.has(Level.WARN, "Skipping wizards resource patch for " + path
				+ ": version 3.1.3.4+26.2 is not registered (patch targets 3.1.3.003+26.2)"), logs.messages().toString());
		}
	}

	@Test
	void warnsAndKeepsUpstreamSpellWhenResourceChanges() throws IOException {
		String path = "data/rpg_series/spell/whirlwind.json";
		ResourcePatch patch = ResourcePatchRegistry.findPatch("spell_engine", "1.10.9+26.2", path);
		byte[] tampered = fixture(patch).clone();
		tampered[tampered.length - 1] = (byte) ' ';
		try (ResourcePatchResolverTest.LogCapture logs = ResourcePatchResolverTest.LogCapture.attach()) {
			IoSupplier<InputStream> resolved = ResourcePatchResolver.resolve(
				ResourcePatchResolverTest.createMockModContainer("spell_engine", "1.10.9+26.2"), path, () -> new ByteArrayInputStream(tampered));
			assertNotNull(resolved);
			try (InputStream in = resolved.get()) {
				assertArrayEquals(tampered, in.readAllBytes(), "A changed spell must pass through unchanged");
			}
			assertTrue(logs.has(Level.WARN,
				"SHA-256 mismatch (expected dfa96b6720080946f2fd5213d7cd8fefa0ea20a9e23053c3bfd740d78c67e47c"),
				logs.messages().toString());
		}
	}

	/**
	 * Completeness guard read from the deployed jars rather than the registry: every hasted martial cooldown, every
	 * apply_limit and both fire coefficient spells in the pinned releases have a registered patch for the jar's exact
	 * bytes, every apply_limit is in the decided D9 table, and no registered spell patch is stale. Reads jars outside the
	 * repository, so it runs only when {@value #SERVER_MODS_PROPERTY} names the dev server's mods directory.
	 */
	@Test
	void deployedJarsHaveNoUnpatchedMartialCooldownOrApplyLimit() throws IOException {
		String serverMods = System.getProperty(SERVER_MODS_PROPERTY);
		assumeTrue(serverMods != null && !serverMods.isBlank(),
			"Set -PserverModsDir=<lampas-server-fabric/mods> to check the deployed RPG Series jars");
		Path mods = Path.of(serverMods);
		Set<String> found = new HashSet<>();
		for (String[] jar : JARS) {
			Path file = mods.resolve(jar[0]);
			assertTrue(Files.isRegularFile(file), "Missing deployed jar " + file);
			assertEquals(jar[3], ResourcePatchResolver.sha256Hex(Files.readAllBytes(file)), jar[0]);
			try (ZipFile zip = new ZipFile(file.toFile())) {
				for (ZipEntry entry : Collections.list(zip.entries())) {
					String name = entry.getName();
					if (!name.matches("data/[^/]+/spell/.+\\.json")) {
						continue;
					}
					byte[] bytes;
					try (InputStream in = zip.getInputStream(entry)) {
						bytes = in.readAllBytes();
					}
					JsonObject spell = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
					JsonObject cost = spell.getAsJsonObject("cost");
					JsonObject cooldown = cost != null && cost.has("cooldown") ? cost.getAsJsonObject("cooldown") : new JsonObject();
					boolean hastedMartial = spell.has("school") && PHYSICAL.contains(spell.get("school").getAsString())
						&& cooldown.has("duration") && cooldown.get("duration").getAsDouble() > 0
						&& (!cooldown.has("haste_affected") || cooldown.get("haste_affected").getAsBoolean());
					boolean limited = new String(bytes, StandardCharsets.UTF_8).contains("\"apply_limit\"");
					String id = jar[1] + " " + jar[2] + " " + name;
					assertEquals(hastedMartial, P1.contains(id), "P1 list disagrees with the deployed jar: " + id);
					assertEquals(limited, LIMITS.containsKey(id), "D9 table disagrees with the deployed jar: " + id);
					if (hastedMartial || limited || FIRE.containsKey(id)) {
						found.add(id);
						ResourcePatch patch = ResourcePatchRegistry.findPatch(jar[1], jar[2], name);
						assertNotNull(patch, "Unpatched " + id);
						assertEquals(patch.expectedSha256(), ResourcePatchResolver.sha256Hex(bytes), id);
						assertArrayEquals(bytes, fixture(patch), "Test fixture differs from the deployed entry: " + id);
					}
				}
			}
		}
		assertEquals(66, found.size());
		assertEquals(found, spellPatches().stream().map(RpgSeriesSpellPatchTest::id).collect(Collectors.toSet()));
	}

	private static List<ResourcePatch> spellPatches() {
		return ResourcePatchRegistry.getAllPatches().values().stream()
			.filter(patch -> patch.resourcePath().contains("/spell/")).toList();
	}

	private static String id(ResourcePatch patch) {
		return patch.modId() + " " + patch.expectedVersion() + " " + patch.resourcePath();
	}

	private static List<String> spells(String modId, String version, String namespace, String... names) {
		return java.util.Arrays.stream(names)
			.map(name -> modId + " " + version + " data/" + namespace + "/spell/" + name + ".json").toList();
	}

	@SafeVarargs
	private static List<String> concat(List<String>... lists) {
		return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
	}

	private static void limit(String modId, String version, String path, int impact, double base, double k,
			double newBase, double newK, double pinnaclePower) {
		String id = modId + " " + version + " " + path;
		LIMITS.put(id, new double[] {impact, base, k, newBase, newK});
		PINNACLE_POWER.put(id, pinnaclePower);
	}

	private static void coefficient(String modId, String version, String path, int impact, double upstream, double patched) {
		FIRE.put(modId + " " + version + " " + path, new double[] {impact, upstream, patched});
	}

	private static JsonObject applyLimit(JsonObject spell, int impact) {
		return spell.getAsJsonArray("impacts").get(impact).getAsJsonObject()
			.getAsJsonObject("action").getAsJsonObject("status_effect").getAsJsonObject("apply_limit");
	}

	private static byte[] fixture(ResourcePatch patch) throws IOException {
		return ResourcePatchResolverTest.readFixtureResource(
			"upstream/" + patch.modId() + "/" + patch.expectedVersion() + "/" + patch.resourcePath());
	}

	private static byte[] replacement(ResourcePatch patch) throws IOException {
		try (InputStream in = RpgSeriesSpellPatchTest.class.getClassLoader().getResourceAsStream(patch.replacementPath())) {
			assertNotNull(in, patch.replacementPath());
			return in.readAllBytes();
		}
	}
}
