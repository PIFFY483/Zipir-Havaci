package com.zipirhavaci.block.entity;

import com.zipirhavaci.block.DoorShakePlaceholderBlock;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;


public class DoorShakeBlockEntity extends BlockEntity {

    // --- Yapılandırma (setup ta set edilir) ---
    private BlockState doorStateBottom = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    private BlockState doorStateTop    = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    private boolean    ironDoor        = false; // true = demir kapı hızı/açısı

    //  Animasyon parametre
    //40 tick
    // 54 tick
    private float  maxAngleDeg;   // maksimum açı (derece)
    private int    periodTicks;   // bir tam salınım (ileri-geri)
    private int    totalTicks;    // toplam animasyon süresi

    // --- Çalışma zamanı ---
    private int    tick       = 0;
    private boolean placeholdersPlaced = false;
    private boolean doorRestored       = false;
    private boolean finished           = false;
    private boolean singleBlock        = false; // true = çit/tuzak kapısı (tek blok)
    private int     serverSwingCount   = 0;     // server tarafı salınım sayacı

    // Client tarafı smooth interpolasyon için önceki açı
    private float prevAngle  = 0f;
    private float currAngle  = 0f;
    // Client tick — server dan bağımsız, load()  ezmez
    private transient int clientTick = -1; // -1 = henüz başlamadı

    public DoorShakeBlockEntity(BlockPos pos, BlockState state) {
        super(ItemRegistry.DOOR_SHAKE_BE.get(), pos, state);
    }

    /**
     * server tarafında çağır, animasyonu başlat.
     * @param bottomState  kapının ALT yarısının BlockState i
     * @param topState     kapının ÜST yarısının BlockState i
     * @param ironDoor     true = demir/sert kapı davranışı
     */
    public void setup(BlockState bottomState, BlockState topState, boolean ironDoor) {
        this.doorStateBottom = bottomState;
        this.doorStateTop    = topState;
        this.ironDoor        = ironDoor;
        // Çit kapısı tek blok — üst yarı yok
        this.singleBlock = !(bottomState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF));

        if (bottomState.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
            // Çit kapısı: 2s 10 salınım
            this.maxAngleDeg  = 12f;
            this.periodTicks  = 4;   // 40 tick / 10 salınım = 4 tick/salınım
            this.totalTicks   = 40;  // 2s
        } else if (ironDoor) {
            this.maxAngleDeg  = 5.3f;
            this.periodTicks  = 7;
            this.totalTicks   = 40;
        } else {
            this.maxAngleDeg  = 12f;
            this.periodTicks  = 12;
            this.totalTicks   = 60;
        }
        this.setChanged();
    }

    // Client tarafı
    public float getAngle(float partialTick) {
        return prevAngle + (currAngle - prevAngle) * partialTick;
    }

    public BlockState getDoorStateBottom() { return doorStateBottom; }
    public boolean    isIronDoor()         { return ironDoor; }

    // Menteşe tarafını bul — kapının hinge property sinden
    public Direction getHingeSide() {
        if (doorStateBottom.hasProperty(DoorBlock.HINGE)) {
            var hinge = doorStateBottom.getValue(DoorBlock.HINGE);
            Direction facing = doorStateBottom.hasProperty(DoorBlock.FACING)
                    ? doorStateBottom.getValue(DoorBlock.FACING)
                    : Direction.NORTH;
            return hinge == net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                    ? facing.getCounterClockWise()
                    : facing.getClockWise();
        }
        return Direction.NORTH;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DoorShakeBlockEntity be) {
        if (be.finished) return;

        // Client tarafı: sadece açı interpolasyonu
        if (level.isClientSide) {
            if (be.clientTick < 0) be.clientTick = 0;

            be.prevAngle = be.currAngle;
            float progress = (float) be.clientTick / be.totalTicks;
            float wave = (float) Math.sin(progress * Math.PI * 2 * (be.totalTicks / (float) be.periodTicks));
            float envelope = (float) Math.sin(progress * Math.PI);
            be.currAngle = wave * envelope * be.maxAngleDeg;

            be.clientTick++;
            if (be.clientTick >= be.totalTicks) be.clientTick = be.totalTicks - 1;
            return;
        }

        //  SERVER TARAFI
        be.tick++;

        // Salınım geçişi tespiti ve ses
        if (be.tick > 1 && be.tick < be.totalTicks) {
            float prevProgress = (float)(be.tick - 1) / be.totalTicks;
            float currProgress = (float) be.tick / be.totalTicks;
            float prevWave = (float)(Math.sin(prevProgress * Math.PI * 2 * (be.totalTicks / (float) be.periodTicks))
                    * Math.sin(prevProgress * Math.PI));
            float currWave = (float)(Math.sin(currProgress * Math.PI * 2 * (be.totalTicks / (float) be.periodTicks))
                    * Math.sin(currProgress * Math.PI));

            // Matematiksel kör nokta
            boolean crossedZero = (prevWave >= 0 && currWave < 0) || (prevWave <= 0 && currWave > 0);

            if (crossedZero) {
                be.serverSwingCount++;

                // Ana kapı sesi
                net.minecraft.sounds.SoundEvent doorSound;
                net.minecraft.world.level.block.Block doorBlock = be.getDoorStateBottom().getBlock();

                if (doorBlock instanceof net.minecraft.world.level.block.TrapDoorBlock) {
                    doorSound = be.isIronDoor()
                            ? net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE
                            : net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE;
                } else if (doorBlock instanceof net.minecraft.world.level.block.FenceGateBlock) {
                    doorSound = net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE;
                } else {
                    doorSound = be.isIronDoor()
                            ? net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE
                            : net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE;
                }

                //  level.playSound ile etrafa yayınla
                level.playSound(null, pos, doorSound, net.minecraft.sounds.SoundSource.BLOCKS, 0.18f, 0.35f);

                if (be.serverSwingCount % 2 == 0) {
                    if (be.isIronDoor()) {
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHAIN_BREAK,
                                net.minecraft.sounds.SoundSource.BLOCKS, 0.22f, 0.5f);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP,
                                net.minecraft.sounds.SoundSource.BLOCKS, 0.15f, 0.35f);
                    } else {
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE,
                                net.minecraft.sounds.SoundSource.BLOCKS, 0.25f, 0.3f);
                    }
                }
            }
        }

        // Tick 1: gerçek kapıyı kaldır, placeholder koy
        if (be.tick == 1 && !be.placeholdersPlaced) {
            placeDoorPlaceholders(level, pos, be);
            be.placeholdersPlaced = true;
            level.sendBlockUpdated(pos, state, state, 8);
        }

        // Tick N-1: gerçek kapıyı geri koy
        if (be.tick == be.totalTicks - 1 && !be.doorRestored) {
            restoreDoor(level, pos, be);
            be.doorRestored = true;
        }

        // Tick N: temizlik
        if (be.tick >= be.totalTicks) {
            cleanup(level, pos, be);
            be.finished = true;
        }

        // Client a tick güncelle
        level.sendBlockUpdated(pos, state, state, 8);
    }

    private static void placeDoorPlaceholders(Level level, BlockPos bottomPos, DoorShakeBlockEntity be) {
        if (be.singleBlock) return; // Çit kapısı — üst yarı yok, sadece alt blok yeter

        BlockPos topPos = bottomPos.above();

        // Alt konum: zaten DoorShakeBlock — sadece üst yarı placeholder.
        // DoorShakeRenderer hem alt hem üstü çizecek.
        int ttl = be.totalTicks + 10;
        BlockState phState = ItemRegistry.DOOR_SHAKE_PLACEHOLDER.get().defaultBlockState();
        // flag=18 (2|16): client a gönder  !ışık motorunu tetikleme
        // Placeholder geçici görünmez blok, ışık güncellemesi gereksiz
        level.setBlock(topPos, phState, 18);

        if (level.getBlockEntity(topPos) instanceof DoorShakePlaceholderEntity ph) {
            ph.setTtl(ttl);
        }
    }

    private static void restoreDoor(Level level, BlockPos bottomPos, DoorShakeBlockEntity be) {
        level.setBlock(bottomPos, be.doorStateBottom, 3);
        if (!be.singleBlock) {
            BlockPos topPos = bottomPos.above();
            level.setBlock(topPos, be.doorStateTop, 3);
        }
    }

    private static void cleanup(Level level, BlockPos bottomPos, DoorShakeBlockEntity be) {
        BlockPos topPos = bottomPos.above();

        // Güvenlik: eğer restore olmadıysa (sunucu crash recovery vb.) yap
        if (!be.doorRestored) {
            level.setBlock(bottomPos, be.doorStateBottom, 3);
            level.setBlock(topPos,    be.doorStateTop,    3);
            return;
        }


        // Eğer hala placeholder kalmışsa (olası race condition) temizle.
        if (level.getBlockState(topPos).getBlock() instanceof DoorShakePlaceholderBlock) {
            level.setBlock(topPos, be.doorStateTop, 3);
        }
        if (level.getBlockState(bottomPos).getBlock() instanceof com.zipirhavaci.block.DoorShakeBlock) {
            level.setBlock(bottomPos, be.doorStateBottom, 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        // Üst yarıyı da kapsar
        return new AABB(getBlockPos()).inflate(1).expandTowards(0, 1, 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("doorBottom", NbtUtils.writeBlockState(doorStateBottom));
        tag.put("doorTop",    NbtUtils.writeBlockState(doorStateTop));
        tag.putBoolean("ironDoor",  ironDoor);
        tag.putFloat("maxAngle",    maxAngleDeg);
        tag.putInt("periodTicks",   periodTicks);
        tag.putInt("totalTicks",    totalTicks);
        tag.putInt("tick",          tick);
        tag.putBoolean("placeholders", placeholdersPlaced);
        tag.putBoolean("restored",     doorRestored);
        tag.putBoolean("singleBlock",   singleBlock);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("doorBottom") && level != null) {
            this.doorStateBottom = NbtUtils.readBlockState(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    tag.getCompound("doorBottom"));
            this.doorStateTop = NbtUtils.readBlockState(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    tag.getCompound("doorTop"));
        }
        this.ironDoor         = tag.getBoolean("ironDoor");
        this.maxAngleDeg      = tag.getFloat("maxAngle");
        this.periodTicks      = tag.getInt("periodTicks");
        this.totalTicks       = tag.getInt("totalTicks");
        this.tick             = tag.getInt("tick");
        this.placeholdersPlaced = tag.getBoolean("placeholders");
        this.doorRestored       = tag.getBoolean("restored");
        this.singleBlock        = tag.getBoolean("singleBlock");
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