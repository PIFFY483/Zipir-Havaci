package com.zipirhavaci.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class EnergyCarrierBlock extends PipeBlock {

    public static final int MAX_POWER = 20;

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, MAX_POWER);

    public EnergyCarrierBlock(Properties props) {
        super(0.15F, props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(POWERED, false)
                .setValue(POWER, 0));
    }

    // --- REDSTONE UYUMLULUĞU ---

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return Math.min(state.getValue(POWER), 15);
    }

    // --- MINECRAFT OLAYLARI → MANAGER'A İLET ---

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (level.isClientSide) return;

        if (!state.is(oldState.getBlock()) || isMoving) {
            EnergyNetworkManager.get(level).onBlockPlaced(level, pos);
        }

        // Yeni blok yerleşince komşular kendi updateShape'ini otomatik almaz.
        // level.updateNeighborsAt yerine sadece EnergyCarrierBlock komşularını hedefle.
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof EnergyCarrierBlock) {
                BlockState updated = neighborState.updateShape(dir.getOpposite(), state, level, neighborPos, pos);
                if (updated != neighborState) {
                    level.setBlock(neighborPos, updated, 2);
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level.isClientSide) return;
        // isMoving=true → piston tarafından çekildi.
        // Her iki durumda da onBlockRemoved çağrılmalı.
        if (!state.is(newState.getBlock()) || isMoving) {
            EnergyNetworkManager.get(level).onBlockRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            EnergyNetworkManager.get(level).onNeighborChanged(level, pos);
        }
    }

    // --- BAĞLANTI MANTIĞI (görsel pipe şekli için) ---

    public boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof EnergyCarrierBlock) return true;
        return state.isFaceSturdy(level, pos, direction.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return makeConnections(context.getLevel(), context.getClickedPos());
    }

    public BlockState makeConnections(LevelAccessor level, BlockPos pos) {
        return this.defaultBlockState()
                .setValue(NORTH, canConnectTo(level, pos.north(), Direction.NORTH))
                .setValue(SOUTH, canConnectTo(level, pos.south(), Direction.SOUTH))
                .setValue(EAST,  canConnectTo(level, pos.east(),  Direction.EAST))
                .setValue(WEST,  canConnectTo(level, pos.west(),  Direction.WEST))
                .setValue(UP,    canConnectTo(level, pos.above(), Direction.UP))
                .setValue(DOWN,  canConnectTo(level, pos.below(), Direction.DOWN));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), canConnectTo(level, neighborPos, dir));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, POWERED, POWER);
    }
}