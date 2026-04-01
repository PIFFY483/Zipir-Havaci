package com.zipirhavaci.physics;

import com.zipirhavaci.entity.FlyingDoorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;


public class DoorBlastHandler {

    // ── Tarama parametreleri ──────────────────────────────────────────────────
    private static final double FORWARD_REACH = 4.5;
    private static final double SIDE_REACH    = 1.8;
    private static final double VERT_REACH    = 2.5;

    // ── Hız parametreleri ─────────────────────────────────────────────────────
    private static final double BASE_FORWARD_SPEED = 1.4;
    private static final double SPEED_PER_LEVEL    = 0.16;
    private static final double MAX_SPEED          = 2.2;
    private static final double VERTICAL_LIFT      = 0.22;
    private static final double LATERAL_SCATTER    = 0.32;

    private DoorBlastHandler() {}

    // =========================================================================
    //  PUSH / SUPERPUSH
    // =========================================================================
    public static void blast(ServerLevel level, Player player, Vec3 look, int skillLevel) {
        Vec3   flatLook     = new Vec3(look.x, 0, look.z).normalize();
        Vec3   origin       = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3   sideVec      = new Vec3(-flatLook.z, 0, flatLook.x);
        double forwardSpeed = Math.min(BASE_FORWARD_SPEED + (skillLevel * SPEED_PER_LEVEL), MAX_SPEED);

        scanAndProcess(level, player, look, flatLook, sideVec, origin, forwardSpeed, skillLevel);
    }

    // =========================================================================
    // GİRİŞ NOKTASI — IMPACT
    // =========================================================================
    public static void blastFromImpact(ServerLevel level, Player player, Vec3 impactVelocity, double impactSpeed) {
        Vec3 dir     = impactVelocity.normalize();
        Vec3 flatDir = new Vec3(dir.x, 0, dir.z);

        if (flatDir.lengthSqr() < 0.01) {
            flatDir = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        }
        flatDir = flatDir.normalize();

        Vec3   origin       = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3   sideVec      = new Vec3(-flatDir.z, 0, flatDir.x);
        double forwardSpeed = Math.min(impactSpeed * 0.85, MAX_SPEED);

        int effectiveLevel = (int) Math.min(5, Math.max(1, Math.ceil(impactSpeed)));

        scanAndProcess(level, player, dir, flatDir, sideVec, origin, forwardSpeed, effectiveLevel);
    }

    // =========================================================================
    // ORTAK TARAMA VE İŞLEME
    // =========================================================================
    private static void scanAndProcess(ServerLevel level, Player player,
                                       Vec3 look, Vec3 flatLook, Vec3 sideVec, Vec3 origin,
                                       double forwardSpeed, int skillLevel) {
        int minX = (int) Math.floor(origin.x - FORWARD_REACH);
        int maxX = (int) Math.ceil(origin.x  + FORWARD_REACH);
        int minY = (int) Math.floor(origin.y  - VERT_REACH);
        int maxY = (int) Math.ceil(origin.y   + VERT_REACH);
        int minZ = (int) Math.floor(origin.z  - FORWARD_REACH);
        int maxZ = (int) Math.ceil(origin.z   + FORWARD_REACH);

        Set<BlockPos> processed = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos     = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!isDoor(state)) continue;
                    if (isUpperHalf(state)) continue;
                    if (processed.contains(pos)) continue;

                    Vec3   toBlock  = new Vec3(x + 0.5 - origin.x, 0, z + 0.5 - origin.z);
                    double fwdDot   = toBlock.dot(flatLook);
                    double sideDot  = Math.abs(toBlock.dot(sideVec));

                    // Filtreler
                    if (fwdDot < 0.25 || fwdDot > FORWARD_REACH) continue;
                    if (sideDot > SIDE_REACH) continue;

                    processed.add(pos);

                    if (!isIronLikeDoor(state)) {
                        breakAndLaunch(level, player, pos, state, look, sideVec, forwardSpeed, skillLevel);
                    }
                }
            }
        }
    }

    // =========================================================================
    // KIRMA VE FIRLATMA
    // =========================================================================
    private static void breakAndLaunch(ServerLevel level, Player player,
                                       BlockPos bottomPos, BlockState bottomState,
                                       Vec3 look, Vec3 sideVec,
                                       double forwardSpeed, int skillLevel) {

        boolean isSingle = !(bottomState.hasProperty(DoorBlock.HALF));
        BlockState topState = null;

        if (!isSingle) {
            BlockPos topPos = bottomPos.above();
            topState = level.getBlockState(topPos);
            if (topState.isAir()) topState = null;
        }

        Vec3 hingeOffset = getHingeLateralOffset(bottomState, sideVec);

        // Alt Parçayı Uçur
        level.removeBlock(bottomPos, false);
        Vec3 velBottom = computeVelocity(look, hingeOffset, forwardSpeed, VERTICAL_LIFT, LATERAL_SCATTER, false);
        spawnFlyingDoor(level, player, bottomPos, bottomState, velBottom, false, skillLevel);

        // Üst Parçayı Uçur
        if (!isSingle && topState != null) {
            BlockPos topPos = bottomPos.above();
            level.removeBlock(topPos, false);
            Vec3 velTop = computeVelocity(look, hingeOffset, forwardSpeed, VERTICAL_LIFT * 1.65, LATERAL_SCATTER * 0.65, true);
            spawnFlyingDoor(level, player, topPos, topState, velTop, true, skillLevel);
        }

        // Efektler
        net.minecraft.world.level.block.SoundType snd = bottomState.getSoundType();
        level.playSound(null, bottomPos, snd.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, snd.getVolume() * 1.5f, snd.getPitch() * (0.82f + (float)(Math.random() * 0.3f)));
        level.levelEvent(2001, bottomPos, Block.getId(bottomState));
    }

    // =========================================================================
    // HIZ VE YARDIMCI METOTLAR
    // =========================================================================
    private static Vec3 computeVelocity(Vec3 look, Vec3 hingeOffset, double forwardSpeed, double vertLift, double lateralStr, boolean isUpperHalf) {
        double vx = look.x * forwardSpeed;
        double vy = look.y * forwardSpeed * 0.55 + vertLift + (isUpperHalf ? 0.20 : 0.0);
        double vz = look.z * forwardSpeed;

        vx += hingeOffset.x * lateralStr;
        vz += hingeOffset.z * lateralStr;

        double totalSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (totalSpeed > MAX_SPEED) {
            double scale = MAX_SPEED / totalSpeed;
            vx *= scale; vy *= scale; vz *= scale;
        }
        return new Vec3(vx, vy, vz);
    }

    private static Vec3 getHingeLateralOffset(BlockState state, Vec3 sideVec) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) {
            boolean isRight = state.hasProperty(DoorBlock.HINGE) && state.getValue(DoorBlock.HINGE) == net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
            return isRight ? sideVec.scale(-1.0) : sideVec;
        }
        if (block instanceof TrapDoorBlock) {
            Direction facing = state.hasProperty(TrapDoorBlock.FACING) ? state.getValue(TrapDoorBlock.FACING) : Direction.NORTH;
            Vec3 fv = Vec3.atLowerCornerOf(facing.getOpposite().getNormal());
            return new Vec3(fv.x, 0, fv.z).normalize();
        }
        if (block instanceof FenceGateBlock) {
            Direction facing = state.hasProperty(FenceGateBlock.FACING) ? state.getValue(FenceGateBlock.FACING) : Direction.NORTH;
            Vec3 fv = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
            return new Vec3(fv.x, 0, fv.z).normalize();
        }
        return Vec3.ZERO;
    }

    private static void spawnFlyingDoor(ServerLevel level, Player player, BlockPos pos, BlockState state, Vec3 velocity, boolean isUpper, int skillLevel) {
        FlyingDoorEntity entity = FlyingDoorEntity.create(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, state, velocity, player, skillLevel, isUpper);
        level.addFreshEntity(entity);
    }

    public static boolean isDoor(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
    }

    private static boolean isUpperHalf(BlockState state) {
        return state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    public static boolean isIronLikeDoor(BlockState state) {
        Block block = state.getBlock();
        if (block == net.minecraft.world.level.block.Blocks.IRON_DOOR || block == net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR) return true;
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            net.minecraft.resources.ResourceLocation rl = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
            if (rl != null && rl.getNamespace().equals("minecraft")) return false;
            return true;
        }
        return false;
    }
}