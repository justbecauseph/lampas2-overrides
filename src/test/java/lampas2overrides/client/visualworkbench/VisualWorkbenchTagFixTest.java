package lampas2overrides.client.visualworkbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class VisualWorkbenchTagFixTest {

	@Test
	void testIsVisualWorkbenchTarget() {
		assertTrue(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("visualworkbench", "crafting_table")));
		assertTrue(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("visualworkbench", "pyrite/rose_stained_crafting_table")));
		assertTrue(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("visualworkbench", "custom_workbench")));

		assertFalse(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("minecraft", "crafting_table")));
		assertFalse(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("pyrite", "rose_stained_crafting_table")));
		assertFalse(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("puzzleslib", "conversion_test")));
		assertFalse(VisualWorkbenchTagFix.isVisualWorkbenchTarget(
				Identifier.fromNamespaceAndPath("othermod", "workbench")));
		assertFalse(VisualWorkbenchTagFix.isVisualWorkbenchTarget(null));
	}

	@Test
	void testNeedsRebind() {
		TagKey<Block> tagA = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "workbench"));
		TagKey<Block> tagB = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "mineable/axe"));
		TagKey<Block> tagC = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "mineable/pickaxe"));

		Set<TagKey<Block>> empty = Collections.emptySet();
		Set<TagKey<Block>> sourceAB = Set.of(tagA, tagB);
		Set<TagKey<Block>> targetAB = Set.of(tagA, tagB);
		Set<TagKey<Block>> targetABC = Set.of(tagA, tagB, tagC);
		Set<TagKey<Block>> targetC = Set.of(tagC);

		// Equal sets -> no rebind
		assertFalse(VisualWorkbenchTagFix.needsRebind(sourceAB, targetAB));
		assertFalse(VisualWorkbenchTagFix.needsRebind(empty, empty));

		// Different sets -> rebind needed
		assertTrue(VisualWorkbenchTagFix.needsRebind(sourceAB, targetABC));
		assertTrue(VisualWorkbenchTagFix.needsRebind(sourceAB, targetC));
		assertTrue(VisualWorkbenchTagFix.needsRebind(sourceAB, empty));
		assertTrue(VisualWorkbenchTagFix.needsRebind(empty, targetAB));
	}

	@Test
	void testHolderBindTags() {
		ResourceKey<Block> blockKey = ResourceKey.create(
				Registries.BLOCK,
				Identifier.fromNamespaceAndPath("visualworkbench", "pyrite/rose_stained_crafting_table"));

		HolderOwner<Block> owner = new HolderOwner<>() {
			@Override
			public boolean canSerializeIn(HolderOwner<Block> o) {
				return true;
			}
		};

		Holder.Reference<Block> holder = Holder.Reference.createStandAlone(owner, blockKey);

		TagKey<Block> tagA = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "workbench"));
		TagKey<Block> tagB = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "mineable/axe"));
		TagKey<Block> tagC = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "mineable/pickaxe"));

		// Initial bind
		VisualWorkbenchTagFix.bindTags(holder, Set.of(tagA, tagB, tagC));
		assertEquals(Set.of(tagA, tagB, tagC), holder.tags().collect(Collectors.toSet()));

		// Rebind with new tags (e.g. tag reload removing tagC)
		VisualWorkbenchTagFix.bindTags(holder, Set.of(tagA, tagB));
		assertEquals(Set.of(tagA, tagB), holder.tags().collect(Collectors.toSet()));

		// Rebind to empty
		VisualWorkbenchTagFix.bindTags(holder, Collections.emptySet());
		assertEquals(Collections.emptySet(), holder.tags().collect(Collectors.toSet()));
	}
}
