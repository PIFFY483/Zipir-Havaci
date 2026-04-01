package com.zipirhavaci.block.entity;

import com.zipirhavaci.block.HeavyPistonBlock;
import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class FakeMovingBlockEntity extends BlockEntity {

    private BlockState movedState = Blocks.AIR.defaultBlockState();
    @Nullable
    private CompoundTag movedNbt;
    private Direction direction = Direction.NORTH;
    private int totalTicks = 8;
    private int progress = 0;
    private boolean finished = false;
    private BlockPos targetPos = BlockPos.ZERO;

    private int animationTick = 0;
    private boolean forceExtendAnimation = false;

    public int getAnimationTick() { return this.animationTick; }
    public boolean isForceExtendAnimation() { return this.forceExtendAnimation; }

    public void setForceExtendAnimation(boolean force) {
        this.forceExtendAnimation = force;
    }

    @Nullable
    private transient Runnable onFinished = null;
    @Nullable
    private transient CompoundTag pendingStateNbt = null;

    @Nullable
    public CompoundTag getMovedNbt() {
        return this.movedNbt;
    }

    public FakeMovingBlockEntity(BlockPos pos, BlockState state) {
        super(ItemRegistry.FAKE_MOVING_BLOCK_BE.get(), pos, state);
    }

    public void setup(BlockState movedState, @Nullable CompoundTag movedNbt, Direction direction, int totalTicks, BlockPos targetPos) {
        this.movedState = movedState;
        this.movedNbt = movedNbt;
        this.direction = direction;
        this.totalTicks = totalTicks;
        this.targetPos = targetPos;
        this.progress = 0;
        this.finished = false;
        this.setChanged();
    }

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public BlockState getMovedState() { return movedState; }
    public Direction getDirection() { return direction; }
    public int getTotalTicks() { return totalTicks; }
    public int getProgress() { return progress; }
    public BlockPos getTargetPos() { return targetPos; }

    public float getProgressFraction(float partialTick) {
        return Math.min(1.0f, (progress + partialTick) / (float) totalTicks);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FakeMovingBlockEntity be) {

        if (level.isClientSide) {
            be.animationTick++;
        }
        // Bekleyen NBT varsa şimdi parse et
        if (be.pendingStateNbt != null) {
            be.movedState = NbtUtils.readBlockState(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    be.pendingStateNbt);
            be.pendingStateNbt = null;
        }

        if (be.finished) return;

        be.progress++;

        if (!level.isClientSide) {
            level.sendBlockUpdated(pos, state, state, 8);
        }

        if (be.progress >= be.totalTicks) {
            be.finished = true;
            if (!level.isClientSide) {
                finishMovement(level, pos, be);
            }
        }
    }

    private static void finishMovement(Level level, BlockPos sourcePos, FakeMovingBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockState finalState = be.movedState;
        CompoundTag finalNbt = be.movedNbt;
        BlockPos target = be.targetPos;
        Runnable callback = be.onFinished;

        level.removeBlock(sourcePos, false);
        // Piston geri kayıyorsa hedefte EXTENDED=true ile  — GeckoLib retract
        BlockState spawnState = (be.forceExtendAnimation && finalState.hasProperty(HeavyPistonBlock.EXTENDED))
                ? finalState.setValue(HeavyPistonBlock.EXTENDED, true)
                : finalState;
        level.setBlock(target, spawnState, 11);

        // Hemen EXTENDED=false — GeckoLib state değişimini görür retract animasyonu oynar
        // flag=2: sadece client render update, neighborChanged tetikleme (redstone bozulmasın)
        if (be.forceExtendAnimation && finalState.hasProperty(HeavyPistonBlock.EXTENDED)) {
            level.setBlock(target, finalState.setValue(HeavyPistonBlock.EXTENDED, false), 2);
        }
        level.updateNeighborsAt(target, finalState.getBlock());
        for (Direction d : Direction.values()) {
            level.updateNeighborsAt(target.relative(d), finalState.getBlock());
        }
        level.sendBlockUpdated(target, finalState, finalState, 3);
        serverLevel.getChunkSource().getLightEngine().checkBlock(target);

        if (finalNbt != null) {
            BlockEntity newBE = level.getBlockEntity(target);
            if (newBE != null) newBE.load(finalNbt);
        }

        // End portal çerçevesiyse portal aktivasyonunu kontrol et
        if (finalState.is(Blocks.END_PORTAL_FRAME)) {
            checkAndSpawnEndPortal(serverLevel, target);
        }

        if (callback != null) {
            callback.run();
        }
    }

    private static void checkAndSpawnEndPortal(ServerLevel level, BlockPos framePos) {
        BlockState frameState = level.getBlockState(framePos);
        if (!frameState.is(Blocks.END_PORTAL_FRAME)) return;

        Direction facing = frameState.getValue(EndPortalFrameBlock.FACING);
        BlockPos center = framePos.relative(facing, 2);

        if (!checkAllFrames(level, center)) return;

        // 3x3 portal koy
        Direction perp = facing.getClockWise();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos portalPos = center.relative(perp, i).relative(facing.getOpposite(), j);
                if (!level.getBlockState(portalPos).is(Blocks.END_PORTAL)) {
                    level.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 3);
                }
            }
        }
    }

    private static boolean checkAllFrames(ServerLevel level, BlockPos center) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = -1; i <= 1; i++) {
                Direction perp = dir.getClockWise();
                BlockPos checkPos = center.relative(dir, 2).relative(perp, i);
                BlockState checkState = level.getBlockState(checkPos);
                if (!checkState.is(Blocks.END_PORTAL_FRAME)) return false;
                if (!checkState.getValue(EndPortalFrameBlock.HAS_EYE)) return false;
                if (checkState.getValue(EndPortalFrameBlock.FACING) != dir.getOpposite()) return false;
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("movedState", NbtUtils.writeBlockState(movedState));
        tag.putInt("direction", direction.get3DDataValue());
        tag.putInt("totalTicks", totalTicks);
        tag.putInt("progress", progress);
        tag.putLong("targetPos", targetPos.asLong());
        if (movedNbt != null) tag.put("movedNbt", movedNbt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("movedState")) {
            if (level != null) {
                this.movedState = NbtUtils.readBlockState(
                        level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                        tag.getCompound("movedState"));
            } else {
                this.pendingStateNbt = tag.getCompound("movedState");
            }
        }
        this.direction = Direction.from3DDataValue(tag.getInt("direction"));
        this.totalTicks = tag.getInt("totalTicks");
        this.progress = tag.getInt("progress");
        this.targetPos = BlockPos.of(tag.getLong("targetPos"));
        if (tag.contains("movedNbt")) this.movedNbt = tag.getCompound("movedNbt");
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(2);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }
}