package com.zipirhavaci.common;

import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.entity.SilentCaptiveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StructureSpawnHelper {

    private static final Logger LOGGER = LogManager.getLogger("ZipirHavaci/SpawnHelper");

    private static final Set<Long> PROCESSED_CELLS = ConcurrentHashMap.newKeySet();
    private static final int MAX_CACHE_SIZE = 2000;

    // NBT analizi: merkeze göre X=-1 offset, Y dinamik tarama
    private static final int SPAWN_X_OFFSET = -2;
    private static final int SPAWN_Z_OFFSET = -1;

    private StructureSpawnHelper() {}

    public static boolean isProcessed(long posKey) {
        return PROCESSED_CELLS.contains(posKey);
    }

    public static void spawnCaptiveAtCenter(ServerLevel level, BlockPos center) {
        if (PROCESSED_CELLS.size() > MAX_CACHE_SIZE) {
            PROCESSED_CELLS.clear();
        }

        long posKey = center.asLong();

        if (PROCESSED_CELLS.contains(posKey)) {
            LOGGER.debug("[SpawnHelper] Zaten işlendi, atlanıyor: {}", center);
            return;
        }

        // Sunucu restart koruma
        boolean alreadyExists = !level.getEntitiesOfClass(
                SilentCaptiveEntity.class,
                new AABB(center).inflate(8)
        ).isEmpty();

        if (alreadyExists) {
            LOGGER.info("[SpawnHelper] Entity zaten mevcut, cache'e ekleniyor: {}", center);
            PROCESSED_CELLS.add(posKey);
            return;
        }

        BlockPos targetXZ = new BlockPos(
                center.getX() + SPAWN_X_OFFSET,
                center.getY(),
                center.getZ() + SPAWN_Z_OFFSET
        );

        BlockPos spawnPos = findBestSpawnPos(level, center);

        if (spawnPos == null) {
            LOGGER.warn("[SpawnHelper] Geçerli spawn pozisyonu bulunamadı! center={}, targetXZ={}", center, targetXZ);
            LOGGER.warn("[SpawnHelper] Çevre blokları:");
            for (int dy = -5; dy <= 5; dy++) {
                BlockPos check = targetXZ.above(dy);
                LOGGER.warn("  Y={} → below={}, self={}, above={}",
                        check.getY(),
                        level.getBlockState(check.below()).getBlock(),
                        level.getBlockState(check).getBlock(),
                        level.getBlockState(check.above()).getBlock()
                );
            }
            return;
        }

        LOGGER.info("[SpawnHelper] Spawn pozisyonu bulundu: {} (center: {})", spawnPos, center);

        SilentCaptiveEntity captive = EntityRegistry.SILENT_CAPTIVE.get().create(level);

        if (captive != null) {
            captive.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0F, 0F);
            captive.setPersistenceRequired();
            captive.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.STRUCTURE, null, null);

            if (level.addFreshEntity(captive)) {
                PROCESSED_CELLS.add(posKey);
                LOGGER.info("[SpawnHelper] ✓ Entity başarıyla spawn edildi: {}", spawnPos);
            } else {
                LOGGER.error("[SpawnHelper] ✗ addFreshEntity başarısız! pos={}", spawnPos);
            }
        } else {
            LOGGER.error("[SpawnHelper] ✗ Entity oluşturulamadı (create() null döndü)");
        }
    }

    private static BlockPos findValidSpawnPos(ServerLevel level, BlockPos target) {
        int x = target.getX();
        int z = target.getZ();

        for (int offset = 0; offset <= 8; offset++) {
            BlockPos candidate = new BlockPos(x, target.getY() - offset, z);
            if (isValidSpawnPos(level, candidate)) return candidate;

            if (offset > 0) {
                candidate = new BlockPos(x, target.getY() + offset, z);
                if (isValidSpawnPos(level, candidate)) return candidate;
            }
        }
        return null;
    }

    private static boolean isValidSpawnPos(ServerLevel level, BlockPos candidate) {
        return !level.getBlockState(candidate.below()).isAir()
                && level.getBlockState(candidate).isAir()
                && level.getBlockState(candidate.above()).isAir();
    }

    public static BlockPos findBestSpawnPos(ServerLevel level, BlockPos center) {

        // --- 1. OFFSET DENEME
        BlockPos target = new BlockPos(
                center.getX() + SPAWN_X_OFFSET,
                center.getY(),
                center.getZ() + SPAWN_Z_OFFSET
        );

        BlockPos pos = findValidSpawnPos(level, target);
        if (pos != null) {
            LOGGER.info("[SpawnHelper] Offset ile bulundu: {}", pos);
            return pos;
        }

        // --- 2. HÜCREYİ TARA
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -6; y <= 6; y++) {

                    BlockPos check = center.offset(x, y, z);

                    if (isValidSpawnPos(level, check)) {

                        // ekstra filtre: kapalı alan (hücre hissi)
                        int solidCount = 0;

                        if (!level.getBlockState(check.north()).isAir()) solidCount++;
                        if (!level.getBlockState(check.south()).isAir()) solidCount++;
                        if (!level.getBlockState(check.east()).isAir()) solidCount++;
                        if (!level.getBlockState(check.west()).isAir()) solidCount++;

                        if (solidCount >= 2) { // duvar hissi
                            LOGGER.info("[SpawnHelper] Hücre içi bulundu: {}", check);
                            return check;
                        }
                    }
                }
            }
        }

        // --- 3. SON ÇARE
        LOGGER.warn("[SpawnHelper] Hücre bulunamadı, fallback çalıştı");

        for (int y = center.getY(); y > center.getY() - 10; y--) {
            BlockPos check = new BlockPos(center.getX(), y, center.getZ());
            if (isValidSpawnPos(level, check)) {
                return check;
            }
        }

        return null;
    }
}