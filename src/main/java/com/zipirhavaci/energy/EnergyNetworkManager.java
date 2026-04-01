package com.zipirhavaci.energy;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;


public class EnergyNetworkManager {

    // Kopru bloklari icin sentinel deger
    private static final int BRIDGE_ID = -999;

    private static final Map<Level, EnergyNetworkManager> INSTANCES = new WeakHashMap<>();

    private final Long2IntMap bridgeInputSourceGroup = new Long2IntOpenHashMap();

    public static EnergyNetworkManager get(Level level) {
        return INSTANCES.computeIfAbsent(level, l -> new EnergyNetworkManager());
    }

    // Sık kullanılan dizileri her seferinde 'new' yapmamak için ThreadLocal havuzu
    private static final ThreadLocal<int[]> NEIGHBOR_CACHE = ThreadLocal.withInitial(() -> new int[6]);


    public static void remove(Level level) {
        INSTANCES.remove(level);
    }

    // Blok -> grup haritasi. BRIDGE_ID ise kopru.
    private final Long2IntMap blockToGroup = new Long2IntOpenHashMap();

    // Tum gruplar
    private final Int2ObjectMap<FlagGroup> groups = new Int2ObjectOpenHashMap<>();

    // SPATIAL INDEX: packed ChunkPos -> bu chunk'in icindeki grup merkezlerinin groupId listesi
    private final Long2ObjectMap<List<Integer>> chunkToGroups = new Long2ObjectOpenHashMap<>();

    private final Map<Integer, Long> groupLastUpdateTick = new HashMap<>();

    // Wave propagation: sonraki tick'te islenmesi gereken gruplar
    private final Set<Integer> pendingGroups = new LinkedHashSet<>();

    // Hayalet flag koruması: dış enerji alıcıları (piston, lamp vs.) ile
    // onlara komşu boru bloklarını takip eder.
    // key = receiver BlockPos (packed), value = komşu boru BlockPos (packed)
    private final Long2LongOpenHashMap receiverToBorderBlock = new Long2LongOpenHashMap();

    private int nextGroupId = 1;
    private boolean ticking = false;
    // onBlockRemoved işlenirken neighborChanged'in araya girmesini engeller
    private boolean processingBlockRemoval = false;
    // applyGroupPower çalışırken neighborChanged'in araya girmesini engeller
    // (flag=146 ile setBlock yapılsa da Minecraft bazı durumlarda yine ateşleyebilir)
    private boolean applyingPower = false;

    // -------------------------------------------------------------------
    // BLOK YERLESTIRME
    // -------------------------------------------------------------------

    public void onBlockPlaced(Level level, BlockPos pos) {
        long packed = pos.asLong();

        // [OPTIMIZASYON]: ThreadLocal üzerinden cache lenmiş diziyi al (GC yükünü keser)
        int[] neighborGroups = NEIGHBOR_CACHE.get();
        int count = 0;

        // Komşu tarama: 6 yöne bak ve benzersiz Bayrak ID'lerini topla
        for (Direction dir : Direction.values()) {
            long neighborPacked = pos.relative(dir).asLong();
            int nid = blockToGroup.getOrDefault(neighborPacked, Integer.MIN_VALUE);

            if (nid == Integer.MIN_VALUE || nid == BRIDGE_ID) continue;

            boolean exists = false;
            for (int i = 0; i < count; i++) {
                if (neighborGroups[i] == nid) {
                    exists = true;
                    break;
                }
            }

            if (!exists && count < 6) {
                neighborGroups[count++] = nid;
            }
        }

        // 1. DURUM: Hiç komşu yoksa yeni bir "Bayrak" (Grup) oluştur
        if (count == 0) {
            setupNewGroup(level, pos, packed);
            return;
        }

        // 2. DURUM: Tek bir gruba değiyorsa o grubun bir parçası ol
        if (count == 1) {
            joinGroup(level, neighborGroups[0], pos, packed);
            return;
        }

        // 3. DURUM: MERGE (Birden fazla bayrağı tek çatıda birleştir)
        int mainGroupId = -1;
        int maxBlocks = -1;

        // [STRATEJİ]: En büyük grubu "ana" grup seç ki daha az bloğu taşımak zorunda kalalım.
        for (int i = 0; i < count; i++) {
            int gid = neighborGroups[i];
            FlagGroup g = groups.get(gid);
            if (g != null) {
                int size = g.blockPositions.size();
                if (size > maxBlocks) {
                    maxBlocks = size;
                    mainGroupId = gid;
                }
            }
        }

        FlagGroup mainGroup = groups.get(mainGroupId);
        if (mainGroup == null) return;

        // Diğer küçük grupları ana gruba dök
        for (int i = 0; i < count; i++) {
            int otherId = neighborGroups[i];
            if (otherId == mainGroupId) continue;

            FlagGroup smallGroup = groups.get(otherId);
            if (smallGroup == null) continue;

            mainGroup.sourceCount += smallGroup.sourceCount;

            // [OPTIMIZASYON]: Iterator oluşturmadan ham long dizisi üzerinden taşıma yap
            long[] smallGroupBlocks = smallGroup.blockPositions.toLongArray();
            for (long p : smallGroupBlocks) {
                mainGroup.addBlock(p); // Kendi metodun (içinde spatial index varsa orayı da günceller)
                blockToGroup.put(p, mainGroupId);
            }

            if (!smallGroup.sourceAdjacentBlocks.isEmpty()) {
                mainGroup.sourceAdjacentBlocks.addAll(smallGroup.sourceAdjacentBlocks);
            }

            dissolveGroupSafe(otherId);
        }

        // Grubun durumunu sıfırla (Delta Update öncesi hazırlık)
        mainGroup.powerLevel = 0;
        mainGroup.powerSourceId = -1;
        mainGroup.lastAppliedPower = -1;
        mainGroup.distanceToSource = Integer.MAX_VALUE;

        // Yeni yerleştirilen bloğu da ana gruba mühürle
        joinGroup(level, mainGroupId, pos, packed);

        // Mimarini koruyan son güncellemeler
        rebuildSourceAdjacency(level, mainGroup, mainGroupId);
        updateNeighborGroupRelationsForGroup(mainGroup);

        // Merge sonrası hemen evaluate + apply yap — tick'i bekleme.
        // Piston aynı tick içinde enerji durumunu sorgularsa stale okuma yapar.
        // evaluateGroup power'ı hesaplar, applyGroupPower changed listesini doldurur
        // ve dış komşulara (piston dahil) neighborChanged gönderir.
        evaluateGroup(level, mainGroupId);
        applyGroupPower(level, mainGroup);
    }

    private void setupNewGroup(Level level, BlockPos pos, long packed) {

        int newId = createGroup(-1, packed);

        FlagGroup group = groups.get(newId);
        if (group == null) return;

        group.addBlock(packed);
        blockToGroup.put(packed, newId);

        if (hasDirectSource(level, pos, newId)) {
            group.sourceAdjacentBlocks.add(packed);
            group.sourceCount++;
        }

        updateNeighborGroupRelations(pos, newId);
        scheduleEvaluation(newId);
        // Topoloji değişti: yeni blok yerleşti, dış komşuları uyar.
        // Power aynı kalsa bile piston/comparator yeni group ID'yi bu sayede algılar.
        notifyExternalNeighborsOf(level, pos);
    }


    private void joinGroup(Level level, int groupId, BlockPos pos, long packed) {

        FlagGroup group = groups.get(groupId);
        if (group == null) return;

        group.addBlock(packed);
        blockToGroup.put(packed, groupId);

        if (hasDirectSource(level, pos, groupId)) {
            group.sourceAdjacentBlocks.add(packed);
            group.sourceCount++;
        }

        if (!group.hasDirectSource()) {
            rebuildSourceAdjacency(level, group, groupId);
        }

        //  Yeni blok gruba katıldı — applyGroupPower dirty check'i bypass et.
        // lastAppliedPower == power olsa bile yeni blok setBlock almalı (enerji göstermeli).
        // Kırıp yeniden koyana kadar enerji gözükmüyor hatasını düzeltir.
        group.lastAppliedPower = -1;

        updateNeighborGroupRelations(pos, groupId);

        scheduleEvaluation(groupId);
        // Topoloji değişti: blok mevcut gruba katıldı, dış komşuları uyar.
        // Power aynı kalsa bile piston/comparator yeni group ID'yi bu sayede algılar.
        notifyExternalNeighborsOf(level, pos);
    }

    // -------------------------------------------------------------------
    // BLOK KIRMA
    // -------------------------------------------------------------------

    public void onBlockRemoved(Level level, BlockPos pos) {
        long packed = pos.asLong();

        if (!blockToGroup.containsKey(packed)) return;

        int groupId = blockToGroup.get(packed);
        blockToGroup.remove(packed);

        if (groupId == BRIDGE_ID) {
            List<Integer> coveringGroups = getGroupsCoveringPos(pos);
            bridgeInputSourceGroup.remove(packed);
            for (int gid : coveringGroups) {
                FlagGroup g = groups.get(gid);
                if (g == null) continue;
                recheckNeighborRelations(g, coveringGroups);
                scheduleEvaluation(gid);
            }
            return;
        }

        FlagGroup group = groups.get(groupId);
        if (group == null) return;

        processingBlockRemoval = true;
        try {
            LongOpenHashSet oldBlockPositions = new LongOpenHashSet(group.blockPositions);

            // --- OPTIMIZASYON 1: Agresif BFS Karar Mekanizması ---
            List<Long> innerNeighbors = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                long nPacked = pos.relative(dir).asLong();
                if (group.blockPositions.contains(nPacked)) {
                    innerNeighbors.add(nPacked);
                }
            }

            boolean canSplit = innerNeighbors.size() >= 2;
            // Eğer tam 2 komşu varsa ve bunlar birbirine dokunuyorsa (L veya Üçgen yapısı), BFS gereksizdir.
            if (innerNeighbors.size() == 2) {
                BlockPos p1 = BlockPos.of(innerNeighbors.get(0));
                BlockPos p2 = BlockPos.of(innerNeighbors.get(1));
                if (p1.closerThan(p2, 1.5)) canSplit = false;
            }

            // --- OPTIMIZASYON 2: Incremental Source Update (O(1)) ---
            // resegment öncesi sayaçtan düş ki tarama maliyeti bitsin
            if (group.sourceAdjacentBlocks.remove(packed)) {
                group.sourceCount = Math.max(0, group.sourceCount - 1);
            }
            group.removeBlock(packed);


            pendingGroups.clear();
            if (!group.isEmpty()) {
                if (canSplit) {
                    resegmentGroup(level, group);
                } else {
                    group.lastAppliedPower = -1;

                    rebuildSourceAdjacency(level, group, groupId);
                    updateNeighborGroupRelationsForGroup(group);
                    scheduleEvaluation(groupId);
                }
            } else {
                dissolveGroupSafe(groupId);
            }

            // Etkilenen grupları topla
            Set<Integer> affectedNow = new LinkedHashSet<>();
            affectedNow.addAll(pendingGroups);
            pendingGroups.clear();

            if (groups.containsKey(groupId)) affectedNow.add(groupId);

            for (Direction dir : Direction.values()) {
                long nPacked = pos.relative(dir).asLong();
                int nid = blockToGroup.getOrDefault(nPacked, Integer.MIN_VALUE);
                if (nid != Integer.MIN_VALUE && nid != BRIDGE_ID && groups.containsKey(nid)) {
                    affectedNow.add(nid);
                }
            }

            // --- TEMIZLIK VE SIFIRLAMA ---
            for (int affectedId : affectedNow) {
                FlagGroup ag = groups.get(affectedId);
                if (ag == null) continue;
                ag.powerLevel = 0;
                ag.powerSourceId = -1;
                ag.distanceToSource = Integer.MAX_VALUE;
                ag.lastAppliedPower = -1;
            }

            // Eski blokları sıfıra çek (Görsel tutarlılık)
            for (long oldPacked : oldBlockPositions) {
                BlockPos oldPos = BlockPos.of(oldPacked);
                BlockState oldState = level.getBlockState(oldPos);
                if (oldState.getBlock() instanceof EnergyCarrierBlock) {
                    if (oldState.getValue(EnergyCarrierBlock.POWER) != 0) {
                        level.setBlock(oldPos, oldState
                                .setValue(EnergyCarrierBlock.POWER, 0)
                                .setValue(EnergyCarrierBlock.POWERED, false), 2);
                    }
                }
            }

            // --- FINAL EVALUATE (Önce Kaynaklılar) ---
            List<Integer> withSource = new ArrayList<>();
            List<Integer> withoutSource = new ArrayList<>();
            for (int affectedId : affectedNow) {
                FlagGroup ag = groups.get(affectedId);
                if (ag == null) continue;
                rebuildSourceAdjacency(level, ag, affectedId);
                if (ag.hasDirectSource()) withSource.add(affectedId);
                else withoutSource.add(affectedId);
            }

            for (int affectedId : withSource) {
                FlagGroup ag = groups.get(affectedId);
                if (ag != null) {
                    evaluateGroup(level, affectedId);
                    applyGroupPower(level, ag);
                }
            }
            for (int affectedId : withoutSource) {
                FlagGroup ag = groups.get(affectedId);
                if (ag != null) {
                    evaluateGroup(level, affectedId);
                    applyGroupPower(level, ag);
                }
            }

        } finally {
            processingBlockRemoval = false;
        }
    }

    private void updateBridgePower(Level level, BlockPos pos) {
        List<Integer> touchingGroups = getGroupsCoveringPos(pos);
        int maxPower = 0;
        int bestSourceGroup = -1;

        for (int gid : touchingGroups) {
            FlagGroup g = groups.get(gid);
            if (g != null && g.powerLevel > maxPower) {
                maxPower = g.powerLevel;
                bestSourceGroup = gid;
            }
        }

        long packed = pos.asLong();
        int oldBridgePower = 0;
        BlockState oldState = level.getBlockState(pos);
        if (oldState.getBlock() instanceof EnergyCarrierBlock) {
            oldBridgePower = oldState.getValue(EnergyCarrierBlock.POWER);
        }

        bridgeInputSourceGroup.put(packed, bestSourceGroup);
        applyPowerToSingleBlock(level, pos, maxPower);

        // BUG FIX #6: Köprü söndüğünde komşu grupları uyar
        if (maxPower == 0 && oldBridgePower > 0) {
            for (int gid : touchingGroups) {
                scheduleEvaluation(gid);
            }
        } else {
            // Enerji almadığı diğer grupları uyar
            for (int gid : touchingGroups) {
                if (gid != bestSourceGroup) scheduleEvaluation(gid);
            }
        }
    }

    // -------------------------------------------------------------------
    // KOMSU DEGISIKLIGI
    // -------------------------------------------------------------------

    public void onNeighborChanged(Level level, BlockPos pos) {
        // Block removal işlemi sırasında neighborChanged araya girmesin.
        // onBlockRemoved kendi evaluate/applyPower zincirini yönetiyor.
        if (processingBlockRemoval) return;
        // applyGroupPower setBlock çağrısı sırasında tetiklenen neighborChanged'leri kes.
        // Bu olmadan 5000+ bloklu ağda kaskad oluşur (her setBlock 6 komşuya neighborChanged yollar).
        if (applyingPower) return;

        long packed = pos.asLong();
        if (!blockToGroup.containsKey(packed)) return;

        int groupId = blockToGroup.get(packed);

        if (groupId == BRIDGE_ID) {
            updateBridgePower(level, pos);
            return;
        }

        FlagGroup group = groups.get(groupId);
        if (group == null) return;

        // OPTİMİZASYON: rebuildSourceAdjacency tüm grubu (O(N)) tarar.
        // Oysa neighborChanged sadece TEK bir bloğun komşusu değiştiğinde gelir.
        // O bloğu (pos) zaten biliyoruz — sadece onu kontrol etmek yeterli (O(1)).
        // rebuildSourceAdjacency hâlâ onBlockPlaced/onBlockRemoved/resegmentGroup/tick
        // içinde çağrılıyor; tam tarama gereken yerler korunuyor.
        boolean hadSource = group.hasDirectSource();
        boolean blockWasSource = group.sourceAdjacentBlocks.contains(packed);
        boolean blockIsSource  = hasDirectSource(level, pos, groupId);
        if (blockWasSource != blockIsSource) {
            if (blockIsSource) {
                group.sourceAdjacentBlocks.add(packed);
                group.sourceCount++;
            } else {
                group.sourceAdjacentBlocks.remove(packed);
                group.sourceCount = Math.max(0, group.sourceCount - 1);
            }
        }
        boolean hasSource = group.hasDirectSource();

        evaluateGroup(level, groupId);
        applyGroupPower(level, group);

        for (int nid : group.neighborGroupIds) {
            scheduleEvaluation(nid);
        }

        // KRİTİK: Kaynak değişti (yeni geldi veya gitti) ise kızıltaşın 6 komşu
        // bloğuna bak. Onların ait olduğu gruplar bu değişikliği bilmeyebilir
        // (örneğin aralarında kırık blok varsa neighborGroupIds de yokturlar).
        if (hadSource != hasSource) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborBlockPos = pos.relative(dir);
                long neighborPacked = neighborBlockPos.asLong();
                int nid = blockToGroup.getOrDefault(neighborPacked, Integer.MIN_VALUE);
                if (nid != Integer.MIN_VALUE && nid != BRIDGE_ID && nid != groupId) {
                    scheduleEvaluation(nid);
                }
            }

        }
    }

    // -------------------------------------------------------------------
    // WAVE PROPAGATION TICK
    // -------------------------------------------------------------------

    public void tick(Level level) {
        // MAX_POWER (20) blok boyunca sönme/yanma dalgası için en kötü durumda 20 sub-tick.
        // Güvenlik: MAX_POWER + 4 sub-tick, her sub-tick'te işlenecek grup sayısı sınırlı.
        if (pendingGroups.isEmpty()) return; // Erken çıkış: işlenecek grup yok
        int maxSubTicks = EnergyCarrierBlock.MAX_POWER + 4;
        // Toplam evaluate sayısı sınırı: sonsuz döngü önlemi
        int totalEvaluations = 0;
        final int MAX_EVALUATIONS_PER_TICK = groups.size() * (EnergyCarrierBlock.MAX_POWER + 2) + 64;

        while (!pendingGroups.isEmpty() && maxSubTicks > 0 && totalEvaluations < MAX_EVALUATIONS_PER_TICK) {
            Set<Integer> toProcess = new LinkedHashSet<>(pendingGroups);
            pendingGroups.clear();

            for (int groupId : toProcess) {
                if (totalEvaluations >= MAX_EVALUATIONS_PER_TICK) break;
                FlagGroup g = groups.get(groupId);
                if (g == null) continue;
                // lastAppliedPower == -1: grup yeni kuruldu (chunk load sonrası).
                // Kaynak/komşu tespiti chunk load sırasında atlandı, şimdi güvenle yapıyoruz.
                // lastAppliedPower == -1: yeni grup (chunk load sonrası)
                // Kaynak ve komşu tespiti güvenli ortamda yap.
                if (g.lastAppliedPower == -1) {
                    rebuildSourceAdjacency(level, g, groupId);
                    updateNeighborGroupRelationsForGroup(g);
                    // Komşu gruplar da bu grubun varlığından haberdar olsun
                    for (int nid : new ArrayList<>(g.neighborGroupIds)) {
                        FlagGroup ng = groups.get(nid);
                        if (ng != null && !ng.neighborGroupIds.contains(groupId)) {
                            ng.neighborGroupIds.add(groupId);
                        }
                    }
                }
                evaluateGroup(level, groupId);
                applyGroupPower(level, g);
                totalEvaluations++;
            }
            maxSubTicks--;
        }

        // Hayalet flag koruması: receiver senkronizasyonu
        syncReceivers(level);
    }

    // -------------------------------------------------------------------
    // GRUP DEGERLENDIRME
    // -------------------------------------------------------------------

    private void evaluateGroup(Level level, int groupId) {
        FlagGroup group = groups.get(groupId);
        if (group == null) return;

        int oldPower = group.powerLevel;
        int oldSource = group.powerSourceId;
        int oldDist = group.distanceToSource;

        int newPower = 0;
        int newSource = -1;
        int newDist = Integer.MAX_VALUE;

        // --- ADIM 1: Doğrudan kaynak kontrolü ---
        if (group.hasDirectSource()) {
            int srcPower = getGroupSourcePower(level, group, groupId);
            if (srcPower > 0) {
                newPower = srcPower;
                newSource = -2;
                newDist = 0;
            }
        }

        // --- ADIM 2: Komşu gruplardan güç alma ---
        if (newPower == 0) {
            for (int neighborId : group.neighborGroupIds) {

                // Self-loop skip (set mutate etme)
                if (neighborId == groupId) continue;

                FlagGroup neighbor = groups.get(neighborId);
                if (neighbor == null) continue;

                int nPower = neighbor.powerLevel;
                int nDist = neighbor.distanceToSource;

                if (nPower > 0 && nDist < Integer.MAX_VALUE) {

                    // Döngü kilidi
                    if (neighbor.powerSourceId == groupId) continue;

                    int candidateDist = nDist + 1;
                    int candidatePower = nPower - 1;

                    if (candidatePower < 0) candidatePower = 0;

                    if (candidateDist < newDist) {
                        newPower = candidatePower;
                        newSource = neighborId;
                        newDist = candidateDist;
                    } else if (candidateDist == newDist && candidatePower > newPower) {
                        newPower = candidatePower;
                        newSource = neighborId;
                    }
                }
            }
        }

        // --- ADIM 3: Köprüler üzerinden güç alma ---
        if (newPower == 0) {

            LongOpenHashSet positions = group.blockPositions;

            for (long packedPos : positions) {

                BlockPos bPos = BlockPos.of(packedPos);

                for (Direction dir : Direction.values()) {

                    BlockPos nPos = bPos.relative(dir);

                    if (!isBridge(nPos)) continue;

                    BlockState bState = level.getBlockState(nPos);
                    if (!(bState.getBlock() instanceof EnergyCarrierBlock)) continue;

                    int bPower = bState.getValue(EnergyCarrierBlock.POWER);
                    if (bPower <= 0) continue;

                    int bSrc = bridgeInputSourceGroup.getOrDefault(nPos.asLong(), -1);

                    if (bSrc == groupId) continue;

                    int candidatePower = bPower - 1;

                    if (candidatePower > newPower) {
                        newPower = candidatePower;
                        newSource = -3; // köprü kaynağı
                    }
                }
            }
        }

        // --- Değerleri güncelle ---
        int finalPower = Math.max(0, newPower);

        group.powerLevel = finalPower;
        group.powerSourceId = newSource;
        group.distanceToSource = (finalPower > 0) ? newDist : Integer.MAX_VALUE;

        // --- Propagation ---
        if (oldPower != finalPower) {
            for (int nid : group.neighborGroupIds) {
                if (nid != groupId) {
                    scheduleEvaluation(nid);
                }
            }
        }
    }

    private int getGroupSourcePower(Level level, FlagGroup group, int groupId) {
        int maxPower = 0;
        for (long packed : group.sourceAdjacentBlocks) {
            int p = getExternalPowerAtBlock(level, BlockPos.of(packed), groupId);
            if (p > maxPower) maxPower = p;
        }
        return maxPower;
    }

    // -------------------------------------------------------------------
    // GRUP BOLUNME (resegment)
    // -------------------------------------------------------------------

    private void resegmentGroup(Level level, FlagGroup originalGroup) {
        // 1. HIZLI ÇIKIŞ: Grup bölünemeyecek kadar küçükse BFS yüküne girme.
        if (originalGroup.blockPositions.size() <= 1) {
            originalGroup.lastAppliedPower = -1;
            rebuildSourceAdjacency(level, originalGroup, originalGroup.groupId);
            updateNeighborGroupRelationsForGroup(originalGroup);
            scheduleEvaluation(originalGroup.groupId);
            return;
        }

        // [OPTIMIZASYON]: HashMap yerine primitive Long2IntOpenHashMap kullanarak GC yükünü siliyoruz.
        int size = originalGroup.blockPositions.size();
        long[] blockArray = originalGroup.blockPositions.toLongArray();
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap blockToIndex = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(size);
        for (int i = 0; i < size; i++) {
            blockToIndex.put(blockArray[i], i);
        }

        BitSet globalVisited = new BitSet(size);
        List<LongOpenHashSet> components = new ArrayList<>();

        // BFS Döngüsü: Tüm bağlı parçaları (islands) tespit et.
        for (int i = 0; i < size; i++) {
            if (globalVisited.get(i)) continue;

            LongOpenHashSet component = new LongOpenHashSet();
            Deque<Integer> bfsQueue = new ArrayDeque<>();

            bfsQueue.add(i);
            globalVisited.set(i);
            component.add(blockArray[i]);

            while (!bfsQueue.isEmpty()) {
                int currentIdx = bfsQueue.poll();
                BlockPos currentPos = BlockPos.of(blockArray[currentIdx]);

                for (Direction dir : Direction.values()) {
                    long neighborPacked = currentPos.relative(dir).asLong();
                    // [KRİTİK]: O(1) hızında primitive kontrol
                    int neighborIdx = blockToIndex.getOrDefault(neighborPacked, -1);

                    if (neighborIdx != -1 && !globalVisited.get(neighborIdx)) {
                        globalVisited.set(neighborIdx);
                        component.add(neighborPacked);
                        bfsQueue.add(neighborIdx);
                    }
                }
            }
            components.add(component);

            // [OPTIMIZASYON]: Eğer ilk parça tüm bloğu kapsıyorsa (parçalanma yok), döngüyü kır.
            if (component.size() == size) break;
        }

        // Parçalanma yoksa (tek ada bulunduysa) eski grubu güncelle ve çık.
        if (components.size() <= 1) {
            originalGroup.lastAppliedPower = -1;
            rebuildSourceAdjacency(level, originalGroup, originalGroup.groupId);
            updateNeighborGroupRelationsForGroup(originalGroup);
            scheduleEvaluation(originalGroup.groupId);
            return;
        }

        // --- PARÇALANMA DURUMU (MERGE/SPLIT MANTIĞI) ---


        // Orijinal grubu ilk parçaya (en büyük olması gerekmez, ilk bulunan yeterli) düşür.
        LongOpenHashSet first = components.get(0);
        removeFromSpatialIndex(originalGroup.centerPosLong, originalGroup.groupId);

        // Verileri temizle ama yapıyı (ID) koru
        originalGroup.blockPositions.clear();
        originalGroup.sourceAdjacentBlocks.clear();
        originalGroup.neighborGroupIds.clear();
        originalGroup.powerLevel = 0;
        originalGroup.distanceToSource = Integer.MAX_VALUE;
        originalGroup.lastAppliedPower = -1;

        originalGroup.blockPositions.addAll(first);
        originalGroup.centerPosLong = first.iterator().nextLong();
        addToSpatialIndex(originalGroup.centerPosLong, originalGroup.groupId);

        // İlk parçadaki tüm blokların "Bayrak" ID sini onayla.
        for (long packed : first) {
            blockToGroup.put(packed, originalGroup.groupId);
        }

        rebuildSourceAdjacency(level, originalGroup, originalGroup.groupId);
        updateNeighborGroupRelationsForGroup(originalGroup);
        scheduleEvaluation(originalGroup.groupId);

        // Diğer parçalar için yeni "Bayraklar" (Gruplar) oluştur.
        for (int j = 1; j < components.size(); j++) {
            LongOpenHashSet component = components.get(j);
            long firstBlock = component.iterator().nextLong();

            int newGroupId = createGroup(-1, firstBlock); // Bayrak ID üret
            FlagGroup newGroup = groups.get(newGroupId);

            if (newGroup != null) {
                newGroup.blockPositions.clear(); // Temiz başla
                for (long packed : component) {
                    newGroup.addBlock(packed);
                    blockToGroup.put(packed, newGroupId);
                }
                newGroup.lastAppliedPower = -1;
                newGroup.powerLevel = 0;
                newGroup.distanceToSource = Integer.MAX_VALUE;

                rebuildSourceAdjacency(level, newGroup, newGroupId);
                updateNeighborGroupRelationsForGroup(newGroup);
                scheduleEvaluation(newGroupId);
            }
        }
    }

    // -------------------------------------------------------------------
    // SPATIAL INDEX
    // -------------------------------------------------------------------

    private List<Integer> getGroupsCoveringPos(BlockPos pos) {
        List<Integer> result = new ArrayList<>();
        int chunkRadius = (EnergyCarrierBlock.MAX_POWER / 16) + 1;

        int centerChunkX = SectionPos.blockToSectionCoord(pos.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long chunkKey = chunkKey(centerChunkX + dx, centerChunkZ + dz);
                List<Integer> groupsInChunk = chunkToGroups.get(chunkKey);
                if (groupsInChunk == null) continue;

                for (int groupId : groupsInChunk) {
                    FlagGroup group = groups.get(groupId);
                    if (group != null && group.isInArea(pos)) {
                        result.add(groupId);
                    }
                }
            }
        }
        return result;
    }

    private void addToSpatialIndex(long centerPosLong, int groupId) {
        BlockPos center = BlockPos.of(centerPosLong);
        long chunkKey = chunkKey(
                SectionPos.blockToSectionCoord(center.getX()),
                SectionPos.blockToSectionCoord(center.getZ())
        );
        chunkToGroups.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(groupId);
    }

    private void removeFromSpatialIndex(long centerPosLong, int groupId) {
        BlockPos center = BlockPos.of(centerPosLong);
        long chunkKey = chunkKey(
                SectionPos.blockToSectionCoord(center.getX()),
                SectionPos.blockToSectionCoord(center.getZ())
        );
        List<Integer> list = chunkToGroups.get(chunkKey);
        if (list != null) {
            list.remove(Integer.valueOf(groupId));
            if (list.isEmpty()) chunkToGroups.remove(chunkKey);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    // -------------------------------------------------------------------
    // YARDIMCI METODLAR
    // -------------------------------------------------------------------

    private boolean isAdjacentToGroup(BlockPos pos, FlagGroup group) {
        for (Direction dir : Direction.values()) {
            if (group.blockPositions.contains(pos.relative(dir).asLong())) {
                return true;
            }
        }
        return false;
    }

    private int createGroup(int parentGroupId, long centerPosLong) {
        int id = nextGroupId++;
        groups.put(id, new FlagGroup(id, parentGroupId, centerPosLong));
        addToSpatialIndex(centerPosLong, id);
        return id;
    }

    /**
     *  dissolveGroup  gruba ait blockToGroup kayıtlarını  temizliyor.
     * Bu metod sadece grubun kendi bloklarını temizlemeden doğrudan groups haritasından siler.
     * Çağıran taraf blockToGroup güncellemesini önceden yapmış olmalıdır (merge durumu).
     */
    private void dissolveGroupSafe(int groupId) {
        FlagGroup group = groups.remove(groupId);
        if (group == null) return;
        removeFromSpatialIndex(group.centerPosLong, groupId);
        for (int neighborId : group.neighborGroupIds) {
            FlagGroup neighbor = groups.get(neighborId);
            if (neighbor != null) {
                neighbor.neighborGroupIds.remove(groupId);
                scheduleEvaluation(neighborId);
            }
        }
    }

    /**
     * dissolveGroup: kendi bloklarının blockToGroup kayıtlarını da siler.
     * onBlockRemoved gibi dışarıdan blockToGroup güncellemesi yapılmayan durumlarda kullanmalı.
     */
    private void dissolveGroup(int groupId) {
        FlagGroup group = groups.get(groupId);
        if (group != null) {
            // Bu grubun bloklarını blockToGroup'tan temizle
            for (long packed : group.blockPositions) {
                // Sadece bu gruba ait olanları sil (başka grup tarafından üzerine yazılmamış)
                if (blockToGroup.getOrDefault(packed, Integer.MIN_VALUE) == groupId) {
                    blockToGroup.remove(packed);
                }
            }
        }
        dissolveGroupSafe(groupId);
    }

    private void scheduleEvaluation(int groupId) {
        if (groups.containsKey(groupId)) {
            pendingGroups.add(groupId);
        }
    }

    private void recheckNeighborRelations(FlagGroup group, List<Integer> otherGroups) {
        for (int otherId : otherGroups) {
            if (otherId == group.groupId) continue;
            FlagGroup other = groups.get(otherId);
            if (other == null) continue;

            boolean stillConnected = false;
            outer:
            for (long packed : group.blockPositions) {
                BlockPos pos = BlockPos.of(packed);
                for (Direction dir : Direction.values()) {
                    if (other.blockPositions.contains(pos.relative(dir).asLong())) {
                        stillConnected = true;
                        break outer;
                    }
                }
            }

            if (!stillConnected) {
                group.neighborGroupIds.remove(otherId);
                other.neighborGroupIds.remove(group.groupId);
            }
        }
    }

    private int getExternalPowerAtBlock(Level level, BlockPos pos, int ownGroupId) {
        int maxPower = 0;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            // Kendi türümüzü (borulari) dis kaynak sayma
            if (neighborState.getBlock() instanceof EnergyCarrierBlock) {
                continue;
            }

            // Redstone teli özel hesaplanir
            if (neighborState.getBlock() instanceof RedStoneWireBlock) {
                continue;
            }

            int signal = neighborState.getSignal(level, neighborPos, dir);
            int directSignal = neighborState.getDirectSignal(level, neighborPos, dir);

            int best = Math.max(signal, directSignal);
            if (best > maxPower) maxPower = best;
        }

        if (maxPower == 0) return 0;

        // 0-15 -> 0-20 ölçekleme
        return Math.round((maxPower / 15.0f) * EnergyCarrierBlock.MAX_POWER);
    }

    private boolean hasDirectSource(Level level, BlockPos pos, int ownGroupId) {
        return getExternalPowerAtBlock(level, pos, ownGroupId) > 0;
    }

    private void rebuildSourceAdjacency(Level level, FlagGroup group, int groupId) {
        // 1. Temizlik: Mevcut kaynak komşularını sıfırla
        group.sourceAdjacentBlocks.clear();
        int count = 0;

        // [OPTIMIZASYON]: Iterator (foreach) yerine primitive long array kullan.
        // Bu, binlerce blokluk gruplarda CPU cache hit oranını artırır ve hızı katlar.
        long[] rawPositions = group.blockPositions.toLongArray();

        for (long packed : rawPositions) {
            // [KRİTİK]: hasDirectSource kontrolü sadece gerektiğinde BlockPos objesi oluşturmalı
            // Eğer hasDirectSource metodun overloaded ise (packed alabiliyorsa) daha da hızlanır.
            if (hasDirectSource(level, BlockPos.of(packed), groupId)) {
                group.sourceAdjacentBlocks.add(packed);
                count++;
            }
        }

        // 2. Güncelleme: Tek seferde yazarak bellek görünürlüğünü sağla
        group.sourceCount = count;
    }

    private void updateNeighborGroupRelationsForGroup(FlagGroup group) {
        // Mevcut neighbor'ları temizle
        group.neighborGroupIds.clear();

        // local reference ile iterasyon (JVM için daha hızlı)
        LongOpenHashSet positions = group.blockPositions;

        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);

            for (Direction dir : Direction.values()) {
                long neighborPacked = pos.relative(dir).asLong();
                int nGroup = blockToGroup.getOrDefault(neighborPacked, Integer.MIN_VALUE);

                // Köprüler üzerinden komşu ekleme
                if (nGroup == BRIDGE_ID) {
                    List<Integer> groupsOnBridge = getGroupsCoveringPos(BlockPos.of(neighborPacked));
                    for (int otherId : groupsOnBridge) {
                        if (otherId == group.groupId) continue;
                        if (!groups.containsKey(otherId)) continue;

                        // karşılıklı ekleme
                        group.neighborGroupIds.add(otherId);
                        FlagGroup otherGroup = groups.get(otherId);
                        if (otherGroup != null) otherGroup.neighborGroupIds.add(group.groupId);
                    }
                    continue;
                }

                // normal neighbor
                if (nGroup == Integer.MIN_VALUE || nGroup == group.groupId) continue;
                if (!groups.containsKey(nGroup)) continue;

                group.neighborGroupIds.add(nGroup);
                FlagGroup neighborGroup = groups.get(nGroup);
                if (neighborGroup != null) neighborGroup.neighborGroupIds.add(group.groupId);
            }
        }
    }

    private void updateNeighborGroupRelations(BlockPos pos, int ownGroupId) {
        FlagGroup ownGroup = groups.get(ownGroupId);
        if (ownGroup == null) return;
        for (Direction dir : Direction.values()) {
            long neighborPacked = pos.relative(dir).asLong();
            int nGroup = blockToGroup.getOrDefault(neighborPacked, Integer.MIN_VALUE);
            if (nGroup == Integer.MIN_VALUE || nGroup == ownGroupId || nGroup == BRIDGE_ID) continue;
            if (!groups.containsKey(nGroup)) continue;
            ownGroup.neighborGroupIds.add(nGroup);
            FlagGroup neighborGroup = groups.get(nGroup);
            if (neighborGroup != null) neighborGroup.neighborGroupIds.add(ownGroupId);
        }
    }

    /**
     * Hayalet flag koruması: kayıtlı dış alıcıların (piston, lamp vs.) gördüğü güç
     * ile komşu boru bloğunun güncel gücünü karşılaştırır.
     * Uyuşmuyorsa neighborChanged gönderir.
     * Tick başına O(receiver sayısı) — tipik yapıda 1-10 blok.
     */
    private void syncReceivers(Level level) {
        if (receiverToBorderBlock.isEmpty()) return;

        LongArrayList toRemove = new LongArrayList();

        for (Long2LongMap.Entry entry : receiverToBorderBlock.long2LongEntrySet()) {
            long receiverPacked = entry.getLongKey();
            long borderPacked   = entry.getLongValue();

            BlockPos borderPos   = BlockPos.of(borderPacked);
            BlockPos receiverPos = BlockPos.of(receiverPacked);

            BlockState borderState = level.getBlockState(borderPos);
            if (!(borderState.getBlock() instanceof EnergyCarrierBlock)) {
                toRemove.add(receiverPacked);
                continue;
            }

            // Receiver hâlâ dış blok mu (boru değil)?
            BlockState receiverState = level.getBlockState(receiverPos);
            if (receiverState.getBlock() instanceof EnergyCarrierBlock) {
                toRemove.add(receiverPacked);
                continue;
            }
            // Hava veya başka geçici blok — kayıttan çıkar
            if (receiverState.isAir()) {
                toRemove.add(receiverPacked);
                continue;
            }

            // Boru bloğunun güncel power'ı
            int borderPower = borderState.getValue(EnergyCarrierBlock.POWER);

            // Receiver'ın borumuzdan aldığı sinyali sorgula
            Direction toReceiver = Direction.fromDelta(
                    receiverPos.getX() - borderPos.getX(),
                    receiverPos.getY() - borderPos.getY(),
                    receiverPos.getZ() - borderPos.getZ());
            if (toReceiver == null) { toRemove.add(receiverPacked); continue; }

            int currentSignal  = borderState.getSignal(level, borderPos, toReceiver.getOpposite());
            int expectedSignal = Math.min(borderPower, 15);

            // Sinyal uyuşmuyorsa receiver'ı uyar
            if (currentSignal != expectedSignal) {
                receiverState.neighborChanged(level, receiverPos,
                        borderState.getBlock(), borderPos, false);
            }
        }

        for (long packed : toRemove) {
            receiverToBorderBlock.remove(packed);
        }
    }

    /**
     * Tek bir bloğun dış komşularına (EnergyCarrierBlock olmayan) neighborChanged gönderir.
     * Topoloji değişimlerinde (blok yerleşimi, merge) power değişmese bile
     * piston gibi komşu blokların yeni durumu fark etmesi için çağrılır.
     * O(1) — sadece 6 komşuya bakar.
     */
    private void notifyExternalNeighborsOf(Level level, BlockPos pos) {
        BlockState placedState = level.getBlockState(pos);
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof EnergyCarrierBlock) continue;
            neighborState.neighborChanged(level, neighborPos, placedState.getBlock(), pos, false);
        }
    }

    private void applyGroupPower(Level level, FlagGroup group) {
        if (group == null) return;

        int power = group.powerLevel;
        boolean powered = power > 0;

        if (group.lastAppliedPower == power) return;
        group.lastAppliedPower = power;

        // AŞAMA 1: Tüm setBlock'ları applyingPower=true altında yap.
        // Flag 146 = SEND_TO_CLIENT(2) | NO_OBSERVER(16) | NO_NEIGHBOR_DROPS(128)
        // neighborChanged TETİKLEMEZ → cascade yok → lag yok.
        //
        // AŞAMA 2: applyingPower=false olduktan sonra sadece GERÇEKTEN değişen
        // bloklara updateNeighborsAt gönder. Piston ve diğer komşu bloklar
        // bu sayede "sinyal değişti" bildirimini alır.
        // EnergyCarrierBlock'ların kendi arası: onNeighborChanged guard'ından geçse de
        // lastAppliedPower dirty check applyGroupPower'ın yeniden çalışmasını engeller.
        List<BlockPos> changed = new ArrayList<>();

        applyingPower = true;
        try {
            for (long packed : group.blockPositions) {
                BlockPos pos = BlockPos.of(packed);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof EnergyCarrierBlock)) continue;

                int oldPower   = state.getValue(EnergyCarrierBlock.POWER);
                boolean oldPowered = state.getValue(EnergyCarrierBlock.POWERED);

                if (oldPower == power && oldPowered == powered) continue;

                level.setBlock(pos, state
                        .setValue(EnergyCarrierBlock.POWER, power)
                        .setValue(EnergyCarrierBlock.POWERED, powered), 146);
                changed.add(pos);
            }
        } finally {
            applyingPower = false;
        }

        // AŞAMA 2: Guard kalktıktan sonra SADECE DIŞ komşulara bildirim gönder.
        // Aynı zamanda dış alıcıları receiverToBorderBlock a kaydet (hayalet flag koruması).
        for (BlockPos pos : changed) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof EnergyCarrierBlock) continue;
                neighborState.neighborChanged(level, neighborPos, level.getBlockState(pos).getBlock(), pos, false);
                // Receiver kaydı: bu dış blok + komşu boru bloğunu takibe al
                receiverToBorderBlock.put(neighborPos.asLong(), pos.asLong());
            }
        }

        // forceNotify: changed boş ama topoloji değişmişse (merge/split sonrası lastAppliedPower=-1 idi)
        // grubun sınır bloklarından dış komşulara bildirim gönder.
        // "Sınır bloğu" = en az bir komşusu EnergyCarrierBlock olmayan boru bloğu.
        // Tüm grubu değil sadece sınırı tara — iç blokların dış komşusu zaten yok.

    }

    private void applyPowerToSingleBlock(Level level, BlockPos pos, int power) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EnergyCarrierBlock)) return;

        boolean isPowered = power > 0;

        // Değişim yoksa zaten çık (Dirty Check)
        if (state.getValue(EnergyCarrierBlock.POWER) == power &&
                state.getValue(EnergyCarrierBlock.POWERED) == isPowered) {
            return;
        }

        // [ÇÖZÜM]: 146 Flag'i (2 | 16 | 128)
        // Hem 'Glow' modeline geçerken, hem normal modele dönerken
        // Işık motoruna "Sakın uyanma" talimatı ver.
        level.setBlock(pos, state
                .setValue(EnergyCarrierBlock.POWER, power)
                .setValue(EnergyCarrierBlock.POWERED, isPowered), 146);
    }

    // Debug
    public int getGroupCount() { return groups.size(); }
    public int getGroupIdAt(BlockPos pos) { return blockToGroup.getOrDefault(pos.asLong(), -1); }
    public boolean isBridge(BlockPos pos) { return blockToGroup.getOrDefault(pos.asLong(), Integer.MIN_VALUE) == BRIDGE_ID; }

    // -------------------------------------------------------------------
    // WORLD LOAD - Grup yeniden inşası
    // -------------------------------------------------------------------


    public void onChunkLoaded(Level level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (level.isClientSide) return;
        List<BlockPos> carrierPositions = new ArrayList<>();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int secY = chunk.getMinSection(); secY < chunk.getMaxSection(); secY++) {
            net.minecraft.world.level.chunk.LevelChunkSection section =
                    chunk.getSection(chunk.getSectionIndexFromSectionY(secY));
            if (section.hasOnlyAir()) continue;
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            int baseY = secY << 4;
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        if (section.getBlockState(lx, ly, lz).getBlock() instanceof EnergyCarrierBlock) {
                            carrierPositions.add(new BlockPos(baseX + lx, baseY + ly, baseZ + lz));
                        }
                    }
                }
            }
        }

        if (carrierPositions.isEmpty()) return;

        // Grup yapısını kur - sadece kayıt, setBlock/neighborUpdate YOK.
        // Chunk load sırasında setBlock çağırmak deadlock/takılmaya neden olur.
        // Güç hesaplaması tick() döngüsüne bırak.
        for (BlockPos pos : carrierPositions) {
            if (blockToGroup.containsKey(pos.asLong())) continue;
            registerBlockIntoGroup(level, pos);
        }

        // Etkilenen grupları bir sonraki tick'e schedule et
        for (BlockPos pos : carrierPositions) {
            int gid = blockToGroup.getOrDefault(pos.asLong(), Integer.MIN_VALUE);
            if (gid != Integer.MIN_VALUE && gid != BRIDGE_ID) {
                FlagGroup g = groups.get(gid);
                if (g != null) {
                    g.lastAppliedPower = -1;
                    scheduleEvaluation(gid);
                }
            }
        }
    }

    /**
     * Bloğu gruba kaydeder ama setBlock/neighborUpdate yapmaz.
     * Chunk load gibi güvenli olmayan bağlamlarda kullanılır.
     */
    /**
     * getGroupsCoveringPos (spatial index taraması) yerine direkt
     * 6 komşuya bakarak O(1) grup tespiti yapar.
     * setBlock / neighborUpdate YOK
     */
    private void registerBlockIntoGroup(Level level, BlockPos pos) {
        long packed = pos.asLong();

        // 6 komşudan hangi gruplara fiziksel bağlantı var?
        // SADECE blockToGroup'a bakıyoruz — level.getBlockState YOK.
        // Chunk load sırasında level.getBlockState komşu chunk ı yüklemeye
        // tetikler → yeni ChunkEvent.Load → sonsuz döngü/takılma olur (başka yol).
        List<Integer> physicallyConnected = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            long neighborPacked = pos.relative(dir).asLong();
            int nid = blockToGroup.getOrDefault(neighborPacked, Integer.MIN_VALUE);
            if (nid == Integer.MIN_VALUE || nid == BRIDGE_ID) continue;
            if (!physicallyConnected.contains(nid)) physicallyConnected.add(nid);
        }

        if (physicallyConnected.isEmpty()) {
            int newId = createGroup(-1, packed);
            FlagGroup group = groups.get(newId);
            group.addBlock(packed);
            blockToGroup.put(packed, newId);
            // hasDirectSource / updateNeighborGroupRelations çağrısı YOK.
            // tick() içinde rebuildSourceAdjacency + evaluateGroup halleder.
        } else if (physicallyConnected.size() == 1) {
            int groupId = physicallyConnected.get(0);
            FlagGroup group = groups.get(groupId);
            if (group == null) return;
            group.addBlock(packed);
            blockToGroup.put(packed, groupId);
            // Aynı şekilde kaynak ve komşu tespiti tick()'e bırakılıyor.
        } else {
            // Birden fazla gruba bağlanıyor: en büyüğüne merge et
            int mainGroupId = physicallyConnected.get(0);
            int maxSize = 0;
            for (int gid : physicallyConnected) {
                FlagGroup g = groups.get(gid);
                int sz = g == null ? 0 : g.blockPositions.size();
                if (sz > maxSize) { maxSize = sz; mainGroupId = gid; }
            }
            FlagGroup mainGroup = groups.get(mainGroupId);
            if (mainGroup == null) return;
            for (int otherId : physicallyConnected) {
                if (otherId == mainGroupId) continue;
                FlagGroup smallGroup = groups.get(otherId);
                if (smallGroup == null) continue;
                for (long p : smallGroup.blockPositions) {
                    mainGroup.addBlock(p);
                    blockToGroup.put(p, mainGroupId);
                }
                mainGroup.sourceAdjacentBlocks.addAll(smallGroup.sourceAdjacentBlocks);
                dissolveGroupSafe(otherId);
            }
            mainGroup.addBlock(packed);
            blockToGroup.put(packed, mainGroupId);
            // Kaynak/komşu tespiti YOK — tick()e bırak.
        }
    }

    /**
     * Chunk unload olduğunda o chunk'taki blokları gruplardan temizle.
     * Bu sayede re-load sırasında çift kayıt oluşmaz.
     */
    public void onChunkUnloaded(Level level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (level.isClientSide) return;
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int secY = chunk.getMinSection(); secY < chunk.getMaxSection(); secY++) {
            net.minecraft.world.level.chunk.LevelChunkSection section =
                    chunk.getSection(chunk.getSectionIndexFromSectionY(secY));
            if (section.hasOnlyAir()) continue;
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            int baseY = secY << 4;
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        if (section.getBlockState(lx, ly, lz).getBlock() instanceof EnergyCarrierBlock) {
                            BlockPos pos = new BlockPos(baseX + lx, baseY + ly, baseZ + lz);
                            long packed = pos.asLong();
                            int gid = blockToGroup.getOrDefault(packed, Integer.MIN_VALUE);
                            if (gid == Integer.MIN_VALUE) continue;
                            blockToGroup.remove(packed);
                            if (gid == BRIDGE_ID) { bridgeInputSourceGroup.remove(packed); continue; }
                            FlagGroup g = groups.get(gid);
                            if (g != null) {
                                g.removeBlock(packed);
                                if (g.isEmpty()) dissolveGroupSafe(gid);
                            }
                        }
                    }
                }
            }
        }
    }
}