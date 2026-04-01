package com.zipirhavaci.common;

import com.zipirhavaci.core.ZipirHavaci;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID)
public class StructureSpawnListener {

    private static final Logger LOGGER = LogManager.getLogger("ZipirHavaci/SpawnListener");

    private static final ResourceLocation SILENT_CELL =
            new ResourceLocation("zipirhavaci", "silent_life_cell");

    private static final Map<Long, Integer> PENDING_SPAWN = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Long> PENDING_CLEAN = new ConcurrentLinkedQueue<>();

    private static final int SETTLE_TICKS = 40;

    // ----------------------------------------------------------------
    // CHUNK LOAD — sadece kuyruğa ekle
    // ----------------------------------------------------------------
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.NETHER) return;

        var chunk = event.getChunk();

        // getAllReferences() Map<Structure, Set<Long>> döndürür
        chunk.getAllReferences().forEach((structure, refs) -> {
            var key = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getKey(structure);

            if (key == null || !key.equals(SILENT_CELL)) return;

            for (long ref : refs) {
                ChunkPos chunkPos = new ChunkPos(ref);
                StructureStart start = level.structureManager().getStartForStructure(
                        SectionPos.of(chunkPos, 0),
                        structure,
                        chunk
                );


                if (start != null && start.isValid()) {

                    BoundingBox bbox = start.getBoundingBox();
                    BlockPos realCenter = new BlockPos(
                            bbox.minX() + (bbox.maxX() - bbox.minX()) / 2,
                            bbox.minY() + (bbox.maxY() - bbox.minY()) / 2,
                            bbox.minZ() + (bbox.maxZ() - bbox.minZ()) / 2
                    );

                    long posKey = realCenter.asLong();
                    if (!StructureSpawnHelper.isProcessed(posKey)) {
                        LOGGER.info("[SpawnListener] GERÇEK CENTER: {}", realCenter);
                        PENDING_CLEAN.offer(posKey);
                    }
                }
            }
        });
    }

    // ----------------------------------------------------------------
    // SERVER TICK — 2 aşamalı: temizle → bekle → spawn
    // ----------------------------------------------------------------
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        // --- AŞAMA 1: Temizle ---
        Long cleanKey = PENDING_CLEAN.poll();
        if (cleanKey != null) {
            BlockPos center = BlockPos.of(cleanKey);
            if (!nether.isLoaded(center)) {
                PENDING_CLEAN.offer(cleanKey);
            } else {
                cleanStructureArea(nether, center);
                PENDING_SPAWN.put(cleanKey, SETTLE_TICKS);
                LOGGER.info("[SpawnListener] Alan temizlendi, {} tick bekleniyor: {}", SETTLE_TICKS, center);
            }
        }

        // --- AŞAMA 2: Bekle → Spawn ---
        Iterator<Map.Entry<Long, Integer>> it = PENDING_SPAWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                it.remove();
                BlockPos center = BlockPos.of(entry.getKey());

                if (!nether.isLoaded(center)) {
                    PENDING_CLEAN.offer(entry.getKey());
                    LOGGER.warn("[SpawnListener] Chunk unload oldu, yeniden kuyruğa alındı: {}", center);
                } else {
                    StructureSpawnHelper.spawnCaptiveAtCenter(nether, center);
                }
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private static void cleanStructureArea(ServerLevel level, BlockPos center) {
        int x0 = center.getX() - 6;
        int x1 = center.getX() + 6;
        int z0 = center.getZ() - 4;
        int z1 = center.getZ() + 4;

        int yMin = center.getY() - 6;
        int yMax = center.getY() + 6;

        // 1. TÜM FLUID'LERİ TEMİZLE
        for (int y = yMin; y <= yMax; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        LOGGER.debug("[SpawnListener] Fluid temizlendi: {} → {}", pos, state.getBlock());
                    }
                }
            }
        }

        // 2. FALLEN BLOCK ENTİTİ LERİ
        AABB innerArea = new AABB(x0, yMin, z0, x1, yMax, z1);
        level.getEntities((Entity) null, innerArea,
                e -> e instanceof net.minecraft.world.entity.item.FallingBlockEntity
        ).forEach(e -> {
            LOGGER.debug("[SpawnListener] FallingBlock kaldırıldı: {} @ {}", e, e.blockPosition());
            e.discard();
        });

        LOGGER.debug("[SpawnListener] Alan temizlendi: center={}, x=[{},{}], z=[{},{}], y=[{},{}]",
                center, x0, x1, z0, z1, yMin, yMax);
    }
}