package com.zipirhavaci.block.entity;

import com.zipirhavaci.core.ItemRegistry;
import com.zipirhavaci.block.FakeMovingBlock;
import com.zipirhavaci.block.HeavyPistonBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

public class HeavyPistonBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean isSticky = false;
    private boolean isMoving = false;

    public boolean isMoving() { return this.isMoving; }

    public HeavyPistonBlockEntity(BlockPos pos, BlockState state) {
        super(ItemRegistry.HEAVY_PISTON_BE.get(), pos, state);
    }

    // --- GECKOLIB ANIMASYON ---
    private boolean justPlaced = true;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            boolean isExtended = state.getAnimatable().getBlockState().getValue(HeavyPistonBlock.EXTENDED);

            if (justPlaced && !isExtended) {
                // Hiç animasyon oynama
                return PlayState.STOP;
            }
            justPlaced = false;

            String animName = isExtended ? "animation.heavy_piston.piston_extend" : "animation.heavy_piston.piston_retract";
            return state.setAndContinue(RawAnimation.begin().then(animName, Animation.LoopType.HOLD_ON_LAST_FRAME));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(getBlockPos()).inflate(20); }

    // --- VERİ YÖNETİMİ ---
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("isStickyMode", this.isSticky);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.isSticky = tag.getBoolean("isStickyMode");
    }

    public boolean isStickyMode() { return this.isSticky; }

    // Renderer ın cache inden çağrılır
    public void markNotJustPlaced() {
        this.justPlaced = false;
    }

    public void toggleStickyMode() {
        this.isSticky = !this.isSticky;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // --- MEKANİKLER ---

    // --- BLOK KONTROL YARDIMCILARI ---

    // Portalın kendisi — kesinlikle itilip çekilemez
    private boolean isPortalBlock(BlockState state) {
        return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    // Yakınında nether portalı var mı? (3x3x3 alan)
    private boolean hasNearbyNetherPortal(BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            if (level.getBlockState(check).is(Blocks.NETHER_PORTAL)) return true;
        }
        return false;
    }

    // Yakınında end portalı var mı? (3x3x3 alan)
    private boolean hasNearbyEndPortal(BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            BlockState s = level.getBlockState(check);
            if (s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)) return true;
        }
        return false;
    }

    // Bu blok itilebilir mi?
    private boolean canPush(BlockPos pos, BlockState state) {
        // Portal bloğu — kesinlikle hayır
        if (isPortalBlock(state)) return false;
        // Katman kayası — hayır
        if (state.is(Blocks.BEDROCK)) return false;
        // Obsidyen — yakınında nether portalı varsa hayır, yoksa evet
        if (state.is(Blocks.OBSIDIAN)) return !hasNearbyNetherPortal(pos);
        // End portal çerçevesi — yakınında end portalı varsa hayır, yoksa evet
        if (state.is(Blocks.END_PORTAL_FRAME)) return !hasNearbyEndPortal(pos);
        // Diğer kırılamaz bloklar — hayır
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return true;
    }

    // Bu blok çekilebilir mi?
    private boolean canPull(BlockPos pos, BlockState state) {
        // Portal bloğu — kesinlikle hayır
        if (isPortalBlock(state)) return false;
        // Katman kayası — hayır (ve arkasındaki bloklar da çekilemez, loop break edilmeli)
        if (state.is(Blocks.BEDROCK)) return false;
        // Obsidyen — yakınında nether portalı varsa hayır
        if (state.is(Blocks.OBSIDIAN) && hasNearbyNetherPortal(pos)) return false;
        // End portal çerçevesi — yakınında end portalı varsa hayır
        if (state.is(Blocks.END_PORTAL_FRAME) && hasNearbyEndPortal(pos)) return false;
        return true;
    }

    // Çer çöp mü? collision shape boş, hava/sıvı değil
    private boolean isClutter(BlockPos pos, BlockState state) {
        if (state.isAir() || state.liquid()) return false;

        // REDSTONE KONTROLÜ: Redstone ile ilgiliyse çöp sayma (kırılmasın)
        if (state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_TORCH) ||
                state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(Blocks.REPEATER) ||
                state.is(Blocks.COMPARATOR)) {
            return false;
        }

        // Collision shape boşsa (ot, çiçek, meşale vb.) çöptür
        return state.getCollisionShape(level, pos).isEmpty();
    }

    public boolean performPushLogic() {
        if (this.level == null || this.level.isClientSide) return false;
        Direction dir = this.getBlockState().getValue(BlockStateProperties.FACING);

        BlockPos frontPos = this.worldPosition.relative(dir, 1);
        BlockState frontState = level.getBlockState(frontPos);

        // Önümüz bomboşsa  işlem tamam
        if (frontState.isAir()) return true;

        // 2. ADIM: Tara — Çer çöpü temizle, gerçek blokları listeye al
        List<BlockPos> toPush = new ArrayList<>();
        boolean canPushFront = true;

        for (int i = 1; i <= 21; i++) {
            BlockPos current = this.worldPosition.relative(dir, i);
            BlockState state = level.getBlockState(current);

            // Boşluğa geldiysek tarama biter
            if (state.isAir()) break;

            // Piston kafası veya hareket halindeki blokları geç (zinciri bozmazlar)
            if (state.is(Blocks.PISTON_HEAD) || state.is(Blocks.MOVING_PISTON)) continue;

            // --- AKILLI ÇÖP MANTIĞI ---
            if (isClutter(current, state)) {
                level.destroyBlock(current, true); // Otu/Çiçeği kır
                // Çöp kırıldı, temas koptu çöpün arkasındaki bloğu ittiremez.
                // Bu yüzden continue yerine break.
                break;
            }

            // İtilemeyen bir blokla karşılaşırsak (Obsidyen vb.)
            if (!canPush(current, state)) {
                canPushFront = false;
                break;
            }

            // Gerçek bloğu listeye ekle
            toPush.add(current);

            // Limit kontrolü
            if (toPush.size() > 20) {
                canPushFront = false;
                break;
            }

        }

        // Listede gerçek bloklar varsa ve yol uygunsa ittir
        if (canPushFront && !toPush.isEmpty()) {
            startSmoothMove(toPush, dir, 8);
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.5f);
            return true;
        }

        // Önde sadece çöp var, hepsi temizlendi (toPush boş kaldı)
        if (canPushFront && toPush.isEmpty()) {
            return true;
        }

        // Eğer ön taraf itilemiyorsa (canPushFront false), piston kendini geri iter
        return performPushBack(dir);
    }

    private boolean performPushBack(Direction dir) {
        Direction backDir = dir.getOpposite(); // Pistonun baktığı yönün tersini (itme yönünü) al
        List<BlockPos> toMoveBack = new ArrayList<>(); // Arkada itilecek blokların listesi

        // 1. ADIM: Arkadaki blokları ve çöpleri tara
        for (int i = 1; i <= 19; i++) {
            BlockPos current = this.worldPosition.relative(backDir, i);
            BlockState state = level.getBlockState(current);

            if (state.isAir()) break; // Hava gördüğünde taramayı bitir
            if (state.is(Blocks.PISTON_HEAD) || state.is(Blocks.MOVING_PISTON)) continue; // Kafa veya hareketli blokları atla

            //  AMELELİK: Çöp Temizliği
            if (isClutter(current, state)) {
                level.destroyBlock(current, true);

                break;
            }

            if (!canPush(current, state)) break; // İtilemez blok gelirse dur
            toMoveBack.add(current); // İtilebilir bloğu listeye ekle
        }

        // 2. Limit ve Engel Kontrolleri
        if (toMoveBack.size() >= 19) {
            spawnBlockedSmoke(); // 19 blok sınırı aşıldıysa duman çıkar
            return false;
        }

        // Zincirin en sonundaki yerin boş (hava) olduğundan emin ol
        int chainLength = 1 + toMoveBack.size();
        BlockPos freePos = this.worldPosition.relative(backDir, chainLength);
        if (!level.getBlockState(freePos).isAir()) {
            spawnBlockedSmoke(); // Yol kapalıysa duman çıkar ve dur
            return false;
        }

        this.isMoving = true; // Hareket başladı, pistonu kilitle

        // 3. Blokları Paketle ve Taşı
        List<BlockPos> allBlocks = new ArrayList<>();
        allBlocks.add(this.worldPosition); // Pistonun kendisini ekle
        allBlocks.addAll(toMoveBack); // Arkadaki blokları ekle
        Collections.reverse(allBlocks); // En uzaktaki bloktan başlayarak işle

        for (int i = 0; i < allBlocks.size(); i++) {
            BlockPos pos = allBlocks.get(i);
            boolean isLast = (i == allBlocks.size() - 1); // Listenin sonu

            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(ItemRegistry.FAKE_MOVING_BLOCK.get())) continue;

            BlockEntity oldBE = level.getBlockEntity(pos);
            CompoundTag nbt = (oldBE != null) ? oldBE.saveWithFullMetadata() : null; // Varsa NBT  al
            BlockPos targetPos = pos.relative(backDir); // Bloğun  hedef konum

            level.removeBlock(pos, false); // Orijinal bloğu dünyadan kaldır
            level.setBlock(pos, ItemRegistry.FAKE_MOVING_BLOCK.get().defaultBlockState(), 3); // Yerine sahte blok koy

            if (level.getBlockEntity(pos) instanceof FakeMovingBlockEntity fakeBE) {
                fakeBE.setup(state, nbt, backDir, 8, targetPos); // Sahte bloğu kur (hız, yön, veri)

                // Piston geri giderken kolu açık görünsün diye sinyali gönder
                if (state.getBlock() instanceof HeavyPistonBlock) {
                    fakeBE.setForceExtendAnimation(true);
                }

                // Hareket bittiğinde pistonun kilitli durumunu (isMoving) aç
                if (isLast) {
                    fakeBE.setOnFinished(() -> this.isMoving = false);
                }
            }
        }


        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.3f); // Hareket sesi
        return false; // Metot akışını tamamla
    }

    private void performSlimeSlide(Direction dir) {

        BlockState pistonState = level.getBlockState(this.worldPosition);
        BlockPos targetPos = this.worldPosition.relative(dir, 1);

        this.isMoving = true;

        level.removeBlock(this.worldPosition, false);
        level.setBlock(this.worldPosition, ItemRegistry.FAKE_MOVING_BLOCK.get().defaultBlockState(), 3);

        if (level.getBlockEntity(this.worldPosition) instanceof FakeMovingBlockEntity fakeBE) {
            fakeBE.setup(pistonState, null, dir, 8, targetPos);
            fakeBE.setOnFinished(() -> this.isMoving = false);
        }

        if (this.level instanceof ServerLevel serverLevel) {
            Direction facing = this.getBlockState().getValue(HeavyPistonBlock.FACING);

            // Pistonun tam merkezinden başlasın
            double x = this.worldPosition.getX() + 0.5;
            double y = this.worldPosition.getY() + 0.5;
            double z = this.worldPosition.getZ() + 0.5;

            // facing.getStepX()  pistonun baktığı tarafa doğru dik bir şekilde akar.
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                    x, y, z,
                    1, // Adet
                    (double)facing.getStepX(), // X yönünde fırlat
                    (double)facing.getStepY(),
                    (double)facing.getStepZ(),
                    0.0); // Hız (yön olarak kullanım, 0 kalmalı)
        }

        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    private void spawnBlockedSmoke() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.01
            );
        }
    }

    public void performAdvancedStickyPull() {
        if (this.level == null || this.level.isClientSide) return;

        Direction dir = this.getBlockState().getValue(BlockStateProperties.FACING);


        // Şart: piston ile slime arasında 1 blok  boşluk olmalı
        if (!this.isStickyMode()) {
            BlockPos airPos = this.worldPosition.relative(dir, 1);
            BlockPos slimePos = this.worldPosition.relative(dir, 2);
            BlockState airState = level.getBlockState(airPos);
            BlockState slimeState = level.getBlockState(slimePos);

            boolean gapPassable = airState.isAir()
                    || airState.liquid()
                    || airState.getCollisionShape(level, airPos).isEmpty();

            if (gapPassable && slimeState.is(Blocks.SLIME_BLOCK)) {

                if (!airState.isAir() && !airState.liquid()) {
                    level.destroyBlock(airPos, true);
                }
                performSlimeSlide(dir);
            }
            return;
        }
        List<BlockPos> chain = new ArrayList<>();
        BlockPos startPos = this.worldPosition.relative(dir, 2);

        for (int i = 0; i < 20; i++) {
            BlockPos currentPos = startPos.relative(dir, i);
            BlockState currentState = level.getBlockState(currentPos);

            if (currentState.isAir()) break;
            if (currentState.is(Blocks.PISTON_HEAD) || currentState.getBlock() instanceof HeavyPistonBlock) continue;

            //  AMELELİK: Çöp Temizliği
            if (isClutter(currentPos, currentState)) {
                level.destroyBlock(currentPos, true);
                continue; //  aramaya devam et
            }

            if (!canPull(currentPos, currentState)) break;

            chain.add(currentPos);

            BlockPos nextPos = currentPos.relative(dir);
            BlockState nextState = level.getBlockState(nextPos);
            boolean currentIsSticky = currentState.is(Blocks.SLIME_BLOCK) || currentState.is(Blocks.HONEY_BLOCK);
            boolean nextIsSticky = nextState.is(Blocks.SLIME_BLOCK) || nextState.is(Blocks.HONEY_BLOCK);
            if (!currentIsSticky && !nextIsSticky) break;
        }

        // performAdvancedStickyPull, çekme başladığı an:
        if (this.level instanceof ServerLevel serverLevel) {
            Direction facing = this.getBlockState().getValue(HeavyPistonBlock.FACING);

            // Pistonun tam merkezinden başlasın
            double x = this.worldPosition.getX() + 0.5;
            double y = this.worldPosition.getY() + 0.5;
            double z = this.worldPosition.getZ() + 0.5;

            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                    x, y, z,
                    1, // Adet
                    (double)facing.getStepX(),
                    (double)facing.getStepY(),
                    (double)facing.getStepZ(),
                    0.0);
        }

        if (!chain.isEmpty()) {
            startSmoothMove(chain, dir.getOpposite(), 5);
            BlockPos lastBlockPos = chain.get(chain.size() - 1);
            level.playSound(null, lastBlockPos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 0.7f, 1.5f);
            level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
    }

    private void startSmoothMove(List<BlockPos> blocks, Direction dir, int duration) {
        this.isMoving = true;

        // Yön kontrolü
        if (dir == this.getBlockState().getValue(BlockStateProperties.FACING)) {
            Collections.reverse(blocks);
        }

        // Son geçerli blok
        int lastValidIndex = -1;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            BlockState s = level.getBlockState(blocks.get(i));
            if (!s.is(Blocks.PISTON_HEAD)) { lastValidIndex = i; break; }
        }

        if (lastValidIndex == -1) {
            this.isMoving = false;
            return;
        }

        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.PISTON_HEAD)) continue;

            BlockEntity oldBE = level.getBlockEntity(pos);
            CompoundTag nbt = (oldBE != null) ? oldBE.saveWithFullMetadata() : null;
            BlockPos targetPos = pos.relative(dir);

            level.removeBlock(pos, false);
            level.setBlock(pos, ItemRegistry.FAKE_MOVING_BLOCK.get().defaultBlockState(), 3);

            if (level.getBlockEntity(pos) instanceof FakeMovingBlockEntity fakeBE) {
                fakeBE.setup(state, nbt, dir, duration, targetPos);

                if (state.getBlock() instanceof HeavyPistonBlock) {
                    fakeBE.setForceExtendAnimation(true);
                }

                if (i == lastValidIndex) {
                    fakeBE.setOnFinished(() -> this.isMoving = false);
                }
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag() { return this.saveWithoutMetadata(); }
}