package com.zipirhavaci.physics;

import com.zipirhavaci.entity.FallingBlockProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class FallingBlockBlastHandler {

    private static final double CYLINDER_DOT         = 0.65;
    private static final double BASE_CYLINDER_RADIUS = 1.8;
    private static final double MAX_SPEED            = 2.2;

    private FallingBlockBlastHandler() {}

    public static void blast(ServerLevel level, Player player, Vec3 look, int skillLevel) {

        // Lvl1=3.0, Lvl2=4.25, Lvl3=5.5, Lvl4=6.75, Lvl5=8.0
        double scanRadius   = 3.0 + (skillLevel - 1) * 1.25;
        double scanRadiusSq = scanRadius * scanRadius;

        Vec3 dir    = new Vec3(look.x, Math.max(look.y, -0.15), look.z).normalize();
        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);

        // Max fırlatılacak blok  ──────────────────────────────────
        // Lvl1=3, Lvl2=4, Lvl3=5, Lvl4=7, Lvl5=8
        int maxBlocks = 3 + (skillLevel - 1) + (skillLevel >= 4 ? 1 : 0);

        double cylRadius   = BASE_CYLINDER_RADIUS + (skillLevel * 0.15);
        double cylRadiusSq = cylRadius * cylRadius;

        // ──────────
        List<Candidate> candidates = new ArrayList<>();

        AABB scanBox = player.getBoundingBox().inflate(scanRadius);

        int minX = (int) Math.floor(scanBox.minX);
        int maxX = (int) Math.ceil(scanBox.maxX);
        int minY = (int) Math.floor(scanBox.minY);
        int maxY = (int) Math.ceil(scanBox.maxY);
        int minZ = (int) Math.floor(scanBox.minZ);
        int maxZ = (int) Math.ceil(scanBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos   = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) continue;
                    if (!(state.getBlock() instanceof FallingBlock)) continue;

                    Vec3 blockCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    candidates.add(new Candidate(blockCenter, state, pos, null));
                }
            }
        }

        List<FallingBlockEntity> fallers =
                level.getEntitiesOfClass(FallingBlockEntity.class, scanBox);
        for (FallingBlockEntity faller : fallers) {
            candidates.add(new Candidate(faller.position(), faller.getBlockState(), null, faller));
        }

        if (candidates.isEmpty()) return;

        // ── 4. En yakını önce fırlat ─────────────────────────────────────────
        candidates.sort(java.util.Comparator.comparingDouble(
                c -> c.pos.distanceToSqr(origin.x, origin.y, origin.z)));

        int launched = 0;

        for (Candidate candidate : candidates) {
            if (launched >= maxBlocks) break;

            // ── 5. Mesafe filtresi ────────────────────────────────────────────
            Vec3   toBlock = candidate.pos.subtract(origin);
            double distSq  = toBlock.lengthSqr();
            if (distSq > scanRadiusSq || distSq < 0.01) continue;

            double dist   = Math.sqrt(distSq);
            Vec3   toNorm = toBlock.scale(1.0 / dist);
            double dot    = toNorm.dot(dir);
            if (dot < 0.10) continue;

            // ── 6. Silindir filtresi ──────────────────────────────────────────
            Vec3   axial   = dir.scale(toBlock.dot(dir));
            Vec3   lateral = toBlock.subtract(axial);
            if (lateral.lengthSqr() > cylRadiusSq * 2.25) continue;

            // ── 7. Hız yönü ───────────────────────────────────────────────────
            Vec3   blastDir;
            double latDist = lateral.length();
            if (latDist <= cylRadius && dot >= CYLINDER_DOT) {
                blastDir = dir;
            } else {
                double outStr = Math.min(1.0, latDist / cylRadius) * 0.5;
                Vec3 outward  = latDist > 0.01
                        ? lateral.scale(1.0 / latDist).scale(outStr)
                        : Vec3.ZERO;
                blastDir = dir.scale(1.0 - outStr * 0.4).add(outward).normalize();
            }

            // ── 8. Hız büyüklüğü ─────────────────────────────────────────────
            double distFactor  = Math.max(0.35, 1.0 - (dist / scanRadius) * 0.55);
            double levelFactor = 0.70 + (skillLevel * 0.12);
            double speed       = Math.min(1.6 * distFactor * levelFactor, MAX_SPEED);

            Vec3 vel = new Vec3(
                    blastDir.x * speed,
                    Math.abs(blastDir.y) * speed + 0.20,
                    blastDir.z * speed
            );

            // ── 9. kaldır, projectile spawn et ───────────────────────
            double spawnX, spawnY, spawnZ;

            if (candidate.blockPos != null) {

                spawnX = candidate.blockPos.getX() + 0.5;
                spawnY = candidate.blockPos.getY() + 0.5;
                spawnZ = candidate.blockPos.getZ() + 0.5;
                level.removeBlock(candidate.blockPos, false);
            } else {

                spawnX = candidate.fallingEntity.getX();
                spawnY = candidate.fallingEntity.getY();
                spawnZ = candidate.fallingEntity.getZ();
                candidate.fallingEntity.discard();
            }

            FallingBlockProjectileEntity projectile = FallingBlockProjectileEntity.create(
                    level, spawnX, spawnY, spawnZ,
                    candidate.state, vel, player, skillLevel);
            level.addFreshEntity(projectile);

            launched++;
        }
    }

    // ── İç yardımcı sınıf ────────────────────────────────────────────────────

    private static final class Candidate {
        final Vec3               pos;
        final BlockState         state;
        final BlockPos           blockPos;
        final FallingBlockEntity fallingEntity;

        Candidate(Vec3 pos, BlockState state, BlockPos blockPos, FallingBlockEntity fallingEntity) {
            this.pos           = pos;
            this.state         = state;
            this.blockPos      = blockPos;
            this.fallingEntity = fallingEntity;
        }
    }
}