package com.zipirhavaci.block;

import com.zipirhavaci.block.entity.HeavyPistonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class HeavyPistonBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING; // Altı yönü de destekler (Up, Down, North, South, East, West)
    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;

    public HeavyPistonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(EXTENDED, false));
    }


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Sadece ana el ve shift basılıyken çalış
        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof HeavyPistonBlockEntity be) {
                    // Modu değiştir
                    be.toggleStickyMode();

                    // Yeni modu kontrol et
                    boolean currentMode = be.isStickyMode();

                    // Oyuncuya mesaj
                    String msg = currentMode ? "§bDual-Polarized Traction" : "§7Linear Thrust Only";
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);

                    // Ses efekti
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 1.5f);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EXTENDED);
    }

    // --- KRİTİK DÜZELTME BURASI ---
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {

        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    // ----------------

    // Sinyal geldiğinde piston event tetikler
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean hasSignal = level.hasNeighborSignal(pos);
            boolean isExtended = state.getValue(EXTENDED);

            if (hasSignal && !isExtended) {
                // İTME: Kafa dışarı çıkarken blokları itsin
                level.blockEvent(pos, this, 0, state.getValue(FACING).get3DDataValue());
            } else if (!hasSignal && isExtended) {
                // ÇEKME: Kafa içeri girerken zinciri çeksin
                level.blockEvent(pos, this, 1, state.getValue(FACING).get3DDataValue());
            }
        }
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HeavyPistonBlockEntity piston) {

                //  Eğer piston zaten hareket halindeyse, yeni komutu görmezden gel
                if (piston.isMoving()) return false;

                if (id == 0) { // İTME
                    // Sadece itme başarılı olursa EXTENDED=true yap
                    boolean pushed = piston.performPushLogic();
                    if (pushed) {
                        level.setBlock(pos, state.setValue(EXTENDED, true), 3);
                    }
                } else if (id == 1) { // ÇEKME
                    level.setBlock(pos, state.setValue(EXTENDED, false), 3);
                    piston.performAdvancedStickyPull();
                }
            }
        }
        return super.triggerEvent(state, level, pos, id, param);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeavyPistonBlockEntity(pos, state);
    }

    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        return 0; // Modelin rastgele ofset almasını engelle
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);
        VoxelShape baseShape = Block.box(0, 0, 0, 16, 16, 16);

        // Eğer piston uzatılmışsa, kafa için fiziksel kutu
        if (state.getValue(EXTENDED)) {
            VoxelShape headShape = Block.box(0, 0, 0, 16, 16, 16)
                    .move(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            return Shapes.or(baseShape, headShape);
        }

        return baseShape;
    }

}