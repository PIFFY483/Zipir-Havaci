package com.zipirhavaci.energy;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * Bir enerji tasima grubunu temsil eder.
 *
 * Merkez + Chebyshev mesafesi MAX_POWER (20) = 41x41x41 blok kup alani.
 *
 * Bir blok bu alana giriyorsa gruba katilir.
 * Iki grubun alanina giriyorsa kopru olur.
 * Hicbir alana girmiyorsa yeni grup kurar, kendisi merkez olur.
 *
 * BlockEntity yok. Tum veriler EnergyNetworkManager'da.
 */
public class FlagGroup {

    public final int groupId;
    public int parentGroupId;
    public int powerLevel;
    public long centerPosLong;
    public int powerSourceId = -1;
    public int sourceCount = 0;

    // Blok pozisyonlari - packed BlockPos
    public final LongOpenHashSet blockPositions = new LongOpenHashSet();

    public int distanceToSource = Integer.MAX_VALUE; // Mesafe sonsuzla başlar

    // Dogrudan enerji kaynagina dokunan bloklar
    // Bos degilse grup bagimsiz calisir
    public final LongOpenHashSet sourceAdjacentBlocks = new LongOpenHashSet();

    // Komsu grup ID'leri
    public final IntOpenHashSet neighborGroupIds = new IntOpenHashSet();

    // Onceki tick'teki power — applyGroupPower'in gereksiz cagrilmasini onler
    public int lastAppliedPower = -1;

    public FlagGroup(int groupId, int parentGroupId, long centerPosLong) {
        this.groupId = groupId;
        this.parentGroupId = parentGroupId;
        this.centerPosLong = centerPosLong;
        this.powerLevel = 0;
    }

    /**
     * Chebyshev mesafesi kontrolu: her eksende MAX_POWER'i gecemez.
     * Bu sayede alan bir kup seklinde olur.
     */
    public boolean isInArea(BlockPos pos) {
        BlockPos center = BlockPos.of(centerPosLong);
        return Math.abs(pos.getX() - center.getX()) <= EnergyCarrierBlock.MAX_POWER &&
                Math.abs(pos.getY() - center.getY()) <= EnergyCarrierBlock.MAX_POWER &&
                Math.abs(pos.getZ() - center.getZ()) <= EnergyCarrierBlock.MAX_POWER;
    }

    /**
     * Merkez ile verilen pos arasindaki Chebyshev mesafesi.
     * Spatial index'te hangi gruplarin yakin oldugunu bulmak icin kullanir.
     */
    public int chebyshevDistance(BlockPos pos) {
        BlockPos center = BlockPos.of(centerPosLong);
        return Math.max(
                Math.max(Math.abs(pos.getX() - center.getX()),
                        Math.abs(pos.getY() - center.getY())),
                Math.abs(pos.getZ() - center.getZ())
        );
    }

    public boolean hasDirectSource() { return !sourceAdjacentBlocks.isEmpty(); }
    public boolean isAlive() { return powerLevel > 0; }

    public void addBlock(long packedPos) { blockPositions.add(packedPos); }

    public void removeBlock(long packedPos) {
        blockPositions.remove(packedPos);
        sourceAdjacentBlocks.remove(packedPos);
    }

    public boolean isEmpty() { return blockPositions.isEmpty(); }
}