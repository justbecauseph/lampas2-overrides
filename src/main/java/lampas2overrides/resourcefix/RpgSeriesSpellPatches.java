package lampas2overrides.resourcefix;

import java.util.function.Consumer;

/**
 * RPG Series spell data patches for the Lampas class balance decisions of 17 Oct 2026 and the caster loot scaling
 * calibration of 29 Oct 2026.
 * <p>
 * D3 P1: every spell in these jars whose school is physical_melee, physical_melee_dual or physical_ranged and whose
 * cost.cooldown.duration is above 0 gets cost.cooldown.haste_affected=false, so attack-speed, spell and ranged haste
 * no longer shorten martial cooldowns ({@code SpellParameters.getCooldownDuration}). D9: twelve status-effect apply
 * limits ({@code health_base + spell_power_multiplier x non-critical power >= target max health}) are raised so crowd
 * control reaches End top-band mobs (736.5 health at level 200) for an optimized Pinnacle user; the five frost
 * multipliers are the smallest multiples of 0.5 that reach 1.1 x 736.5 at the modelled Pinnacle frost power with
 * Apotheosis caster loot (119.19). Fire: Fire Wall and the Meteor burning ground ({@code fire_tier_3_spell_1_modifier_2})
 * deal 0.4 x their upstream damage spell_power_coefficient. Each replacement is the upstream JSON with only those keys
 * changed, and a file that needs both P1 and D9 has a single replacement.
 * {@code tools/generate_rpg_spell_patches.py} rebuilds the replacements from the pinned jars; the upstream and
 * replacement SHA-256 of every entry are in {@code docs/evidence/rpg-spell-patches.manifest.json}.
 */
final class RpgSeriesSpellPatches {

	private static final String ROOT = "lampas2-overrides/resource-patches/";

	static void registerAll(Consumer<ResourcePatch> register) {
		Target rogues = new Target(register, "rogues", "3.1.3.003+26.2", "rogues"); // rogues-fabric-3.1.3.003+26.2.jar
		rogues.spell("bear_trap", "9fdebb8c04f5d6e149f41b42828d74b9365af18f0076d18c8a36358e0c861f65"); // D9 impacts[1] apply_limit 100+2P -> 100+5.5P
		rogues.spell("charge", "5ec55a59a4f4338da4a00a37addc2bf7706130169177b01c73318b453a798d14");
		rogues.spell("mortal_strike", "a65ff216447ea19acda86d03be4b2da82a8256f35db8c8305104265a16fda60c");
		rogues.spell("mutilate", "1210f6df29f0dd26be31f0f891b28b9f5cc2c309b2dc63b91266fe0da04aab49");
		rogues.spell("shadow_step", "b52f39d281d3b035f858c6f929345b475610e7c0e53a4d84f8948976abcea86b");
		rogues.spell("shock_powder", "4ca2e20baa1c4acb9c0124b2777ed10eb648091bfe96dfc8c8c47da422e6f49e"); // D9 impacts[0] apply_limit 50+2P -> 50+7.5P
		rogues.spell("shout", "30309fe053f65474953a51c399d65d0bd114a8027c1db1171827550653543327"); // D9 impacts[0] apply_limit 50+2P -> 50+9.5P
		rogues.spell("slice_and_dice", "7db998548e148df03450cd6baaeed141570bac7b86762acfe7855a48d963b4f0");
		rogues.spell("throw", "c3f2f91da9cf586d991b22655d48199cddc7e9c04d02809c2c0d9cccb2080300"); // D9 impacts[1] apply_limit 100+2P -> 100+8.5P
		rogues.spell("throw_net", "0954c4025d5f084dbf0d4a60554a04015741681c7f264d11b6db895e06095823"); // D9 impacts[1] apply_limit 100+2P -> 100+8.5P
		rogues.spell("vanish", "33314bb34d54a0dc9c916adafaa9f727b3a5e579d49e5ebd3755c14ca73a1523");

		Target spellEngine = new Target(register, "spell_engine", "1.10.9+26.2", "rpg_series"); // spell_engine-fabric-1.10.9+26.2.jar
		spellEngine.spell("cleave", "4dee409d8601b7c4b7fb9252b578d3a2b1a333732964461d15662cf7f450d533");
		spellEngine.spell("fan_of_knives", "4609da90e39b5f1664abd453f63ba31776af9e99f734f2acc800fda27602364e");
		spellEngine.spell("flurry", "bc7d7af4b01e83329655955b8333a1fcd48e7ca5cc7752499bf38abcb4b3b56d");
		spellEngine.spell("ground_slam", "19dd7d6b30a971342aa4dac655dc867d1e020a3177a095d717a828fd7564c578");
		spellEngine.spell("impale", "9301ad4271bb744c6cdae1306d5296d7d0f803afb47678d04efc8a8951780e29");
		spellEngine.spell("smash", "fb8b0f72201a2fcdda05d467339c097f12ba72fb20eda23c201631d4ad2129d9");
		spellEngine.spell("swift_strikes", "7899863baa2a82a415604cedf4a61f77f2425df006d5c539a4c34dae7bab1ed1");
		spellEngine.spell("swipe", "50d265ce27c3e6e618aa970327f8586a625cff74f4bfef4c3989d578d732c44a");
		spellEngine.spell("thrust", "5b4346f7eebe20beb76f8cf1f31b52cbf2fca8a837aeb40d56814f9c9e8dff3b");
		spellEngine.spell("whirlwind", "dfa96b6720080946f2fd5213d7cd8fefa0ea20a9e23053c3bfd740d78c67e47c");

		Target paladins = new Target(register, "paladins", "3.1.3.003+26.2", "paladins"); // paladins-fabric-3.1.3.003+26.2.jar
		paladins.spell("judgement", "a85576a3d32814d575117a9c82b7780676679add9e36164c73304ecec39a6fc0"); // D9 impacts[1] apply_limit 50+2P -> 50+7.5P

		Target archers = new Target(register, "archers", "3.1.3.003+26.2", "archers"); // archers-fabric-3.1.3.003+26.2.jar
		archers.spell("barrage", "a0b9787786cf1811793379f990e21d2a42f08db07831818380201fdd2f589ff8");
		archers.spell("entangling_roots", "18125be43cd04274b3904bea2a61240630c10d7397e336a9fb7d895b78ef4c7c"); // D9 impacts[0] apply_limit 50+5P -> 780+5P
		archers.spell("magic_arrow", "fe06abd905c430a1b617df5641ed750486fbca5fab21016c03af82381e838053");
		archers.spell("power_shot", "debfd779430db6b48c6b41f0b6d3a4c054d6e04c50090d03142a5ae2e6a7894c");
		archers.spell("rain_of_arrows", "c0861848d520d1bc5e49aab6f73c9a45176010bd7a3eadb0157c03e98bc74b36");

		Target wizards = new Target(register, "wizards", "3.1.3.003+26.2", "wizards"); // wizards-fabric-3.1.3.003+26.2.jar
		wizards.spell("fire_wall", "11de90f3f0f887af7321a9dc4102fb8cd4501dd7cfc1007404c63dd5388e54bd"); // impacts[0] damage spell_power_coefficient 0.8 -> 0.32, no P1 (caster)
		wizards.spell("frost_blizzard", "1d3204d22b34efb887fba229b5e5bef5c71c10b0e161b31f7a173991eaddf641"); // D9 impacts[1] apply_limit 80+4P -> 80+6.5P, no P1 (caster)
		wizards.spell("frost_lance", "7e2a00708692ca03ee6f9d9418ab836310309de95940671ffff98e2764151520"); // D9 impacts[1] apply_limit 100+4P -> 100+6P, no P1 (caster)
		wizards.spell("frost_nova", "cf7d6159a4c0a85260e06e901ca38048ed593273b9eaddd385a8b4c9e712856c"); // D9 impacts[1] apply_limit 60+4P -> 60+6.5P, no P1 (caster)
		wizards.spell("frost_spikes", "50d2d9e0e6d4525a1779178d6f70a1b2254ca22f010493f088d165b74e6aa96a"); // D9 impacts[1] apply_limit 60+4P -> 60+6.5P, no P1 (caster)
		wizards.spell("frostbolt", "92d85ac73a1441eeb0e8de4871da5bf7bb0cc27d0a682e30c3c6011c14547477"); // D9 impacts[1] apply_limit 100+4P -> 100+6P, no P1 (caster)

		Target skillTree = new Target(register, "skill_tree_rpgs", "1.6.2+26.2", "skill_tree_rpgs"); // skill_tree-fabric-1.6.2+26.2.jar
		skillTree.spell("archer_tier_1_passive_1", "ba97a94a0e3bc9920d17a44c4a0cec8c3cd7241ba8c5b5cb318e8ac2d1158352");
		skillTree.spell("archer_tier_2_passive_1", "a660998a1e1645be2d0332b2b4f76264c2768fd1da366ee7965554f6bbedb0c6");
		skillTree.spell("archer_tier_2_passive_2", "9dce597e94ef2324e8305ed92a29e74344ed15c16ff0f96895ce9297d396cdbf");
		skillTree.spell("archer_tier_3_passive_1", "76d9925eb3fdbe4404c29db29ec7bbb308aebef7ea94f6cabc354952d7f64b54");
		skillTree.spell("archer_tier_3_passive_2", "6b98d8eacb90199234a7f29f38673ecb2327d1ea36d02681a3c1ba4747fc44fb");
		skillTree.spell("fire_tier_3_spell_1_modifier_2", "2544bbb53753ef276caa1488deb430b60010cf39842cc88a8d07219cb36a2ad1"); // Meteor burning ground: impacts[0] damage spell_power_coefficient 0.3 -> 0.12
		skillTree.spell("paladin_tier_1_passive_1", "edabee86309d3d3eafc0c52ea5f4a6feb497e7cbbc0e46b2ce380cd5aeb2b549");
		skillTree.spell("paladin_tier_3_passive_2", "87caab49c3b400009198ff4e79541ee5682e6ccba18ff6e574a5b6b161872694");
		skillTree.spell("rogue_tier_1_passive_2", "6ecd5b00ab1598fc2c23d5327cb1c0d493ca91c094f58cc7a72c9b494783524c");
		skillTree.spell("rogue_tier_2_passive_2", "a13709a6a6d9585f5eab9459e4a03dda0806b64357f303f9c6847d821129cc35");
		skillTree.spell("rogue_tier_3_passive_1", "41473ec727c3d2b13ecdf33aadb37bc4bd3a6330725811d87fb0e6432aca9a9f");
		skillTree.spell("rogue_tier_3_passive_2", "a1ad5617bcb70961ee66a492a777efbacda49ee96f339c5a96f14d98fae1458f");
		skillTree.spell("warrior_tier_1_passive_1", "754bf09a56688923ff383f72911c97ff7e457bb5f53f3ca41a91d29e6a25262b");
		skillTree.spell("warrior_tier_1_passive_2", "d95e1f20ea7ea35bd5588ea3e8701c0634f5833e770fe2752febb3fd359cc77d");
		skillTree.spell("warrior_tier_3_passive_1", "5398782fb1ec3aaedde7cfdc06e0103d67f6e496de5b64f88fe83c8ad664839d");
		skillTree.spell("warrior_tier_3_passive_2", "1d0e9ef8be8153293aa75ad064db250f0d6f11f6caae66ddbdcda3abd2def3b7");
		skillTree.spell("warrior_tier_3_spell_1_modifier_2", "fcbcf060971790521516be5fd651f56179e87520ca07fc0004bd141edefdacdb");
		skillTree.spell("weapon_bow_passive_1", "0a56ccf989ef6caf9dc8f1fd5694ae1c3f92f2ab1ae3c6b6df7c97c342938365");
		skillTree.spell("weapon_swipe_modifier_2", "72158c5732d410f3d4dd3f342c9e7a277fa0f465c35e02120f9f81ad5f9c25f2");

		Target relics = new Target(register, "relics_rpgs", "1.4.1+26.2", "relics_rpgs"); // relics-fabric-1.4.1+26.2.jar
		relics.spell("greater_perk_defense_danger", "343c5d2fe74b4a2ccbbf23b14f9ca6c60cbdd5bc118e85e5edf2f2a6e4a3c20e");
		relics.spell("greater_perk_evasion_attack", "d357983ec923e8fdaf456178aa49d1ca8e092917fb723e21126c2c831aec4e70");
		relics.spell("greater_perk_melee_stun", "78131e0de3052abc4b801214d0f586488cdc7730fa752a12a49e6e1e8bb084e3");
		relics.spell("greater_perk_ranged_levitate", "6f17c142beb379aa13e44503eea519b07e415696346d84074f368839297a82ab");
		relics.spell("greater_proc_physical_trance", "270a703789c63eab88f9ee256497a0616e6199c3b74832c3b5078a86c2c72c68");
		relics.spell("lesser_use_damage", "7d0c694f12b82515bc028c66ccae076761272ebaa24658fb6c80cf1ba25fe8b3");
		relics.spell("lesser_use_dex", "58f111155804deee60fd77df0811b979966c0c5321b9c23325e5ff48d8b2301c");
		relics.spell("lesser_use_ranged", "fece6782588d65d10b16f1af03cbeb949026000fcd268546e92ec567f57fbe04");
		relics.spell("medium_proc_attack_damage", "259abdbc25fce5deead91c6209b8b4a361485c77c1a589a42bc5976ff9882f55");
		relics.spell("medium_proc_attack_speed", "bd9a337ccbc19aba3efc206232c10d5283fa8589c33b07dd06b967b0a7c6555f");
		relics.spell("medium_proc_defense", "65fae0be384dae07eaceb04a87f2c49249ab7c5142663dc658f438188f0ee46f");
		relics.spell("medium_proc_evasion", "a289e0db233d0219ebc6a8c8f4f5e6d04ec516c25ce9127ac03812a2273be8f6");
		relics.spell("medium_proc_ranged_damage", "646708a6b0d7138e63c4f7ec65ae755465844fbbe3186339c995174c6597eb71");
		relics.spell("superior_use_area_attack_damage", "67faabf286bb21bac63f771cae97b283eab0060924befc5bb23ee5fa47bf2a79");
	}

	/** One pinned jar: patches its {@code data/<namespace>/spell/<name>.json} entries at the declared version. */
	private record Target(Consumer<ResourcePatch> register, String modId, String version, String namespace) {
		void spell(String name, String upstreamSha256) {
			String path = "data/" + namespace + "/spell/" + name + ".json";
			register.accept(new ResourcePatch(modId, version, path, upstreamSha256,
				ROOT + modId + "/" + version + "/" + path));
		}
	}

	private RpgSeriesSpellPatches() {
	}
}
