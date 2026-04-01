package com.zipirhavaci.block.entity;

import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;


public class DoorShakePlaceholderEntity extends BlockEntity {

    // Kaç tick daha yaşayacak — DoorShakeBlockEntity tarafından set
    private int ttl = 200; // Güvenlik: 10 saniye sonra temizle

    public DoorShakePlaceholderEntity(BlockPos pos, BlockState state) {
        super(ItemRegistry.DOOR_SHAKE_PLACEHOLDER_BE.get(), pos, state);
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DoorShakePlaceholderEntity be) {
        if (level.isClientSide) return;

        be.ttl--;
        if (be.ttl <= 0) {
            // TTL doldu, güvenlik temizliği
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("ttl", ttl);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.ttl = tag.getInt("ttl");
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