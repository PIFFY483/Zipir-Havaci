package com.zipirhavaci.block;

import com.zipirhavaci.block.entity.CoreRodBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CoreRodBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING  = BlockStateProperties.FACING;
    public static final BooleanProperty   POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty   DELAY   = IntegerProperty.create("delay", 1, 6);
    public static final EnumProperty<PulseMode> MODE = EnumProperty.create("mode", PulseMode.class);


    public CoreRodBlock(Properties props) {
        super(props);
        this.registerDefaultState(stateDefinition.any()
                .setValue(FACING,  Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(DELAY,   1)
                .setValue(MODE,    PulseMode.REPEATER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POWERED, DELAY, MODE);
    }

    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreRodBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING,  ctx.getNearestLookingDirection())
                .setValue(DELAY,   1)
                .setValue(POWERED, false)
                .setValue(MODE,    PulseMode.REPEATER);
    }

    // ── Sağ Tık ─────────────────────────────────────────────────────────────
    // Shift + boş el → sonraki moda geç
    // Normal sağ tık  → gecikme/sayaç değeri artır

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
                // Mod döngüsü: REPEATER → PULSE → INVERTER → LATCH → COUNTER → AND → REPEATER
                PulseMode current = state.getValue(MODE);
                PulseMode next = switch (current) {
                    case REPEATER -> PulseMode.PULSE;
                    case PULSE    -> PulseMode.INVERTER;
                    case INVERTER -> PulseMode.LATCH;
                    case LATCH    -> PulseMode.COUNTER;
                    case COUNTER  -> PulseMode.AND;
                    case AND      -> PulseMode.REPEATER;
                };
                // Mod değişince state sıfırla
                level.setBlock(pos, state.setValue(MODE, next).setValue(POWERED, false), 3);
                // LATCH ve COUNTER , BlockEntity  sıfırla
                if (level.getBlockEntity(pos) instanceof CoreRodBlockEntity be) {
                    be.resetCounter();
                }
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(modeDescription(next)), true);

            } else if (!player.isShiftKeyDown()) {
                // Normal sağ tık: gecikme değiştir (LATCH hariç — onun gecikmesi yok)
                PulseMode mode = state.getValue(MODE);
                if (mode != PulseMode.LATCH && mode != PulseMode.AND) {
                    int next = (state.getValue(DELAY) % 6) + 1;
                    level.setBlock(pos, state.setValue(DELAY, next), 3);
                    String label = mode == PulseMode.COUNTER ? "Sayaç hedefi: " : "Gecikme: ";
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "\u00a76" + label + "\u00a7e" + next + " tick"), true);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private String modeDescription(PulseMode mode) {
        return switch (mode) {
            case REPEATER -> "\u00a76Repeater \u00a77\u2014 geciktirici";
            case PULSE    -> "\u00a7dPulse \u00a77\u2014 yaniip soner";
            case INVERTER -> "\u00a7cInverter \u00a77\u2014 NOT kapisi";
            case LATCH    -> "\u00a7aLatch \u00a77\u2014 toggle hafiza";
            case COUNTER  -> "\u00a7bCounter \u00a77\u2014 N. sinyalde cikis";
            case AND      -> "\u00a7eAND \u00a77\u2014 iki giris gerekli";
        };
    }

    // ── Tetikleyiciler ───────────────

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide) {
            ((ServerLevel) level).scheduleTick(pos, this, state.getValue(DELAY));
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) {
            ((ServerLevel) level).scheduleTick(pos, this, state.getValue(DELAY));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
    }

    // Tick

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rng) {
        switch (state.getValue(MODE)) {
            case REPEATER -> tickRepeater(state, level, pos);
            case PULSE    -> tickPulse(state, level, pos);
            case INVERTER -> tickInverter(state, level, pos);
            case LATCH    -> tickLatch(state, level, pos);
            case COUNTER  -> tickCounter(state, level, pos);
            case AND      -> tickAnd(state, level, pos);
        }
    }

    // REPEATER: giriş sinyalini geciktirerek iletir
    private void tickRepeater(BlockState state, ServerLevel level, BlockPos pos) {
        boolean hasInput = getInputSignal(state, level, pos);
        setPowered(state, level, pos, hasInput);
    }

    // PULSE: enerji varken DELAY tick te bir yanıp söner
    private void tickPulse(BlockState state, ServerLevel level, BlockPos pos) {
        boolean hasInput = getInputSignal(state, level, pos);
        if (!hasInput) {
            setPowered(state, level, pos, false);
            return;
        }
        boolean next = !state.getValue(POWERED);
        setPowered(state, level, pos, next);
        level.scheduleTick(pos, this, state.getValue(DELAY));
    }

    // INVERTER: sinyal yoksa yaner, sinyal varsa söner
    private void tickInverter(BlockState state, ServerLevel level, BlockPos pos) {
        boolean hasInput = getInputSignal(state, level, pos);
        setPowered(state, level, pos, !hasInput);
    }

    // LATCH: her sinyal yukselen kenarinda toggle yapar, hafizada tutar
    private void tickLatch(BlockState state, ServerLevel level, BlockPos pos) {
        boolean hasInput = getInputSignal(state, level, pos);
        if (!(level.getBlockEntity(pos) instanceof CoreRodBlockEntity be)) return;

        boolean wasHigh = be.wasInputHigh();


        if (hasInput && !wasHigh) {
            boolean newPowered = !state.getValue(POWERED); // toggle
            setPowered(state, level, pos, newPowered);
        }
        // Input durumunu kaydet
        be.setInputHigh(hasInput);
    }

    // COUNTER: her N. yükselen kenarda bir çıkış verir
    private void tickCounter(BlockState state, ServerLevel level, BlockPos pos) {
        boolean hasInput = getInputSignal(state, level, pos);
        if (!(level.getBlockEntity(pos) instanceof CoreRodBlockEntity be)) return;

        boolean wasHigh = be.wasInputHigh();

        if (hasInput && !wasHigh) {
            // Yükselen kenar — sayacı artır
            int count = be.incrementCounter();
            int target = state.getValue(DELAY); // DELAY = hedef sayı (1-6)
            if (count >= target) {
                be.resetCounter();
                // Kısa bir pulse ver (DELAY tick sonra kapat)
                setPowered(state, level, pos, true);
                level.scheduleTick(pos, this, 1);
            }
        } else if (!hasInput && state.getValue(POWERED)) {
            // Pulse kapat
            setPowered(state, level, pos, false);
        }

        be.setInputHigh(hasInput);
    }

    // AND: giriş yönü + giriş yönünün sağındaki yüzden de sinyal geliyorsa çıkış ver
    private void tickAnd(BlockState state, ServerLevel level, BlockPos pos) {
        boolean input1 = getInputSignal(state, level, pos);
        boolean input2 = getAndSecondInput(state, level, pos);
        setPowered(state, level, pos, input1 && input2);
    }

    // ── Yardımcılar ───────────────────────────────────

    // Ana giriş: FACING.getOpposite() yönünden
    private boolean getInputSignal(BlockState state, ServerLevel level, BlockPos pos) {
        Direction inputSide = state.getValue(FACING).getOpposite();
        return level.getSignal(pos.relative(inputSide), inputSide) > 0;
    }

    // AND için ikinci giriş: FACING yönünün sağı
    // (FACING = NORTH ise sağ = EAST)
    private boolean getAndSecondInput(BlockState state, ServerLevel level, BlockPos pos) {
        Direction second = getClockwise(state.getValue(FACING));
        return level.getSignal(pos.relative(second), second) > 0;
    }

    // Bir yönün saat yönünde döndürülmüş hali (yatay + dikey)
    private Direction getClockwise(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.EAST;
            case EAST  -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST  -> Direction.NORTH;
            case UP    -> Direction.SOUTH;
            case DOWN  -> Direction.NORTH;
        };
    }

    private void setPowered(BlockState state, ServerLevel level, BlockPos pos, boolean powered) {
        if (state.getValue(POWERED) == powered) return;
        level.setBlock(pos, state.setValue(POWERED, powered), 3);
        level.getLightEngine().checkBlock(pos);
    }

    // ── Sinyal ────────────

    @Override
    public boolean isSignalSource(BlockState state) { return state.getValue(POWERED); }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (!state.getValue(POWERED)) return 0;
        if (dir == state.getValue(FACING)) return 0; // giriş yönüne verme
        return 15;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}