package lampas2overrides.lootrfastframes.mixin;

import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fuzs.fastitemframes.common.world.level.block.entity.ItemFrameBlockEntity;
import lampas2overrides.lootrfastframes.FixedLootrInstance;
import lampas2overrides.lootrfastframes.LootrFastItemFrame;
import lampas2overrides.lootrfastframes.LootrFastItemFrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.LootrRegistry;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.api.interfaces.advancement.IContainerTrigger;
import noobanidus.mods.lootr.common.api.interfaces.type.ILootrType;
import noobanidus.mods.lootr.common.entity.LootrItemFrame;

/** Adds Lootr identity and per-player state to Fast Item Frames' physical block entity. */
@Mixin(value = ItemFrameBlockEntity.class, remap = false)
public abstract class ItemFrameBlockEntityMixin implements LootrFastItemFrame, ILootrBlockEntity {

	private static final String LAMPAS2_LOOTR_FRAME = "Lampas2LootrFrame";

	@Shadow
	public abstract NonNullList<ItemStack> getItems();

	@Shadow
	public abstract ItemStack getItem();

	private boolean lampas2$lootrFrame;
	private final FixedLootrInstance lampas2$lootrInstance =
			new FixedLootrInstance(this::getVisualOpeners);

	@Override
	public boolean lampas2$isLootrFrame() {
		return lampas2$lootrFrame;
	}

	@Override
	public void lampas2$initializeFrom(LootrItemFrame source) {
		lampas2$lootrFrame = true;
		lampas2$lootrInstance.setId(source.getUUID());
		lampas2$lootrInstance.setReferenceInventory(source.getDataReferenceInventory());
		if (source.hasBeenOpened()) {
			lampas2$lootrInstance.setHasBeenOpened();
		}
		markInstanceChanged();
	}

	@Override
	public void lampas2$markOpened() {
		lampas2$lootrInstance.setHasBeenOpened();
	}

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void lampas2$saveLootrState(ValueOutput output, CallbackInfo ci) {
		if (lampas2$lootrFrame) {
			output.putBoolean(LAMPAS2_LOOTR_FRAME, true);
			BlockEntity self = (BlockEntity) (Object) this;
			lampas2$lootrInstance.saveAdditional(output, self.getLevel() == null || self.getLevel().isClientSide());
		}
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void lampas2$loadLootrState(ValueInput input, CallbackInfo ci) {
		lampas2$lootrFrame = input.getBooleanOr(LAMPAS2_LOOTR_FRAME, false);
		if (lampas2$lootrFrame) {
			lampas2$lootrInstance.loadAdditional(input);
		}
	}

	@Inject(method = "getUpdateTag", at = @At("RETURN"), cancellable = true)
	private void lampas2$addOpenersToUpdateTag(HolderLookup.Provider registries,
			CallbackInfoReturnable<CompoundTag> cir) {
		if (lampas2$lootrFrame) {
			BlockEntity self = (BlockEntity) (Object) this;
			CompoundTag result = cir.getReturnValue();
			result.merge(lampas2$lootrInstance.fillUpdateTag(registries,
					self.getLevel() != null && self.getLevel().isClientSide(), self));
			cir.setReturnValue(result);
		}
	}

	@Inject(method = "serverTick", at = @At("TAIL"))
	private void lampas2$tickLootr(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci) {
		if (lampas2$lootrFrame) {
			LootrAPI.handleInstanceTick(this);
		}
	}

	/** Overrides the default no-op supplied by Puzzles Lib's ticking interface. */
	public void clientTick(Level level, BlockPos pos, BlockState state) {
		if (lampas2$lootrFrame) {
			LootrAPI.handleInstanceClientTick(this);
		}
	}

	@Inject(method = "getEntityType", at = @At("HEAD"), cancellable = true)
	private void lampas2$useLootrRenderer(CallbackInfoReturnable<EntityType<? extends ItemFrame>> cir) {
		if (lampas2$lootrFrame) {
			cir.setReturnValue(LootrRegistry.getItemFrame());
		}
	}

	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void lampas2$doNotDropSharedReference(Level level, BlockPos pos, BlockState state,
			boolean dropItem, CallbackInfo ci) {
		if (lampas2$lootrFrame) {
			ci.cancel();
		}
	}

	@Override
	public @Nullable Set<UUID> getClientOpeners() {
		return lampas2$lootrInstance.getClientOpeners();
	}

	@Override
	public boolean isClientOpened() {
		return lampas2$lootrInstance.isClientOpened();
	}

	@Override
	public void setClientOpened(boolean opened) {
		lampas2$lootrInstance.setClientOpened(opened);
	}

	@Override
	public void markInstanceChanged() {
		BlockEntity self = (BlockEntity) (Object) this;
		self.setChanged();
		markSectionChanged();
	}

	@Override
	public ILootrType getDataType() {
		return LootrFastItemFrameType.INSTANCE;
	}

	@Override
	public UUID getDataId() {
		return lampas2$lootrInstance.getId();
	}

	@Override
	public Identifier getDataIdentifier() {
		return lampas2$lootrInstance.getIdentifier();
	}

	@Override
	public boolean hasBeenOpened() {
		return lampas2$lootrInstance.hasBeenOpened();
	}

	@Override
	public boolean isPhysicallyOpen() {
		return false;
	}

	@Override
	public BlockPos getDataPos() {
		return ((BlockEntity) (Object) this).getBlockPos();
	}

	@Override
	public @Nullable Component getDataDisplayName() {
		return Component.translatable("entity.lootr.item_frame");
	}

	@Override
	public ResourceKey<Level> getDataDimension() {
		return getDataLevel().dimension();
	}

	@Override
	public int getDataContainerSize() {
		return 1;
	}

	@Override
	public @Nullable NonNullList<ItemStack> getDataReferenceInventory() {
		return lampas2$lootrInstance.getCustomInventory();
	}

	@Override
	public void setDataReferenceInventory(@Nullable NonNullList<ItemStack> referenceInventory) {
		lampas2$lootrInstance.setReferenceInventory(referenceInventory);
	}

	@Override
	public boolean isDataReferenceInventory() {
		return lampas2$lootrInstance.isCustomInventory();
	}

	@Override
	public @Nullable ResourceKey<LootTable> getDataLootTable() {
		return null;
	}

	@Override
	public long getDataLootSeed() {
		return 0L;
	}

	@Override
	public Level getDataLevel() {
		return ((BlockEntity) (Object) this).getLevel();
	}

	@Override
	public void setLootTableInternal(ResourceKey<LootTable> lootTable, long seed) {
	}

	@Override
	public @Nullable IContainerTrigger getTrigger() {
		return LootrRegistry.getItemFrameTrigger();
	}

	@Override
	public double getParticleYOffset() {
		return 0.4;
	}

	@Override
	public double[] getParticleXBounds() {
		return new double[]{0.15, 0.85};
	}

	@Override
	public double[] getParticleZBounds() {
		return new double[]{0.15, 0.85};
	}
}
