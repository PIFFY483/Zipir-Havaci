================================================================
  ZİPİRHAVACI MOD — TEKNİK OKUMA KILAVUZU (TÜRKÇE)
  Mekanik Açıklamaları, Optimizasyon Notları ve Kod Mimarisi
================================================================

────────────────────────────────────────────────────────────────
1. GENEL MİMARİ
────────────────────────────────────────────────────────────────

Mod, birbirinden bağımsız ama birlikte çalışan dört ana katmandan oluşur:

  • Hareket Sistemi   → MovementHandler.java
  • Kalkan Mekaniği  → ThrownShieldEntity.java + SoulBondHandler.java
  • Aura / Ram        → ShieldRamMechanicHandler.java + AuraSkillPacket.java
  • Enerji Ağı        → EnergyNetworkManager.java + EnergyCarrierBlock.java + FlagGroup.java

Her katman kendi durumunu ConcurrentHashMap yapıları içinde tutar.
Bu, çok iş parçacıklı (multi-threaded) Forge olaylarının eş zamanlı
harita erişiminden kaynaklanan yarış koşullarına (race condition)
karşı temel güvencedir.


────────────────────────────────────────────────────────────────
2. HAREKET SİSTEMİ — MovementHandler.java
────────────────────────────────────────────────────────────────

AĞIRLIK HESABI
--------------
Her fırlatmada oyuncunun Zırh + Zırh Sertliği (Armor + Armor Toughness)
değerleri toplanır ve 60'a kelepçelenir (Mth.clamp):

  totalWeight  = clamp(armor + toughness, 0, 60)
  weightRatio  = totalWeight / 60.0

Bu oran üç çarpana dönüştürülür:

  mobilityPenalty = 1.0 − (weightRatio × 0.20)   → Daha ağır = daha yavaş
  powerBoost      = 1.0 + (weightRatio × 0.20)   → Daha ağır = çevreye daha fazla itme
  damageBoost     = 1.0 + (weightRatio × 0.05)   → Daha ağır = hafif ekstra hasar

Neden 60 sınırı?
  Oyunda ulaşılabilecek maksimum Armor + Toughness değeri ~30 + 12 = 42'dir.
  60 sınırı, mod zırhlarının da hesaba katılabilmesi için bilinçli
  şekilde geniş bırakılmıştır.

PULL MODU (Çekme)
-----------------
Oyuncu bakış yönüne doğru fırlatılır. Y bileşenine ×1.1 çarpanı
eklenir çünkü Minecraft'ın yerçekimi fırlatmanın hemen ardından Y'yi
düşürür; bu düzeltme olmadan hedef noktalara yatay fırlatma geri
düşüşe dönüşürdü.

Ek olarak tek tick boyunca player.noPhysics = true yapılır ve
"NoPhysicsResetNextTick" bayrağı PersistentData'ya kaydedilir.
Böylece fırlatma anındaki kare sürtünme (friction) hesabı atlanır;
bir sonraki tick'te bayrak kontrol edilerek noPhysics tekrar false'a çekilir.
Bu pattern single-tick friction bypass olarak adlandırılır.

PUSH MODU (İtme)
----------------
Geri tepme vektörü hesaplanırken eğer oyuncu yukarı bakıyorsa (look.y > 0.7)
Meteor Çakılışı tetiklenir: hareket (0, −2.5, 0) olarak sabitlenir ve
AnimationManager::triggerSquash çağrısıyla sıkışma animasyonu oynatılır.

Normal PUSH'ta kapılar kontrol edilir:
  - Demir kapı → sadece titretme (DoorShakeBlockEntity)
  - Ahşap kapı → patlatarak koparma (DoorBlastHandler.blast)

SUPER SKILL (SEVİYELİ YETENEK)
-------------------------------
executeSuperSkill() 1–5 arası seviye alır. Her seviyede:

  Mermi maliyeti = seviye + 1   (minimum 2 mermi gerekir)
  Taban güç     = 3.0 + (seviye × 1.5)   [Seviye 5 = 16.0 sabit]
  Menzil        = 4.5 + (seviye × 1.6) blok

SuperSkill'in PULL versiyonu vakum etkisi yaratır: oyuncunun önündeki
varlıklar çekilirken arkasındakiler itilir (dot > 0 ise VAKUM, değilse İTİŞ).

PUSH versiyonunda "Ground Blast" koşulu devreye girer:
  - XRot ≥ 80° VEYA önde 3 blok içinde bir yüzey varsa
  - VE seviye ≥ 3 ise
  → Alan patlaması tetiklenir, tüm yönlere hasar ve fırlatma uygulanır.

BLOK ÇATLAK EFEKTİ (spawnSuperCracks)
--------------------------------------
Çatlaklar sunucu taraflı destroyBlockProgress() API'siyle gösterilir.
Bloklar gerçekten kırılmaz; sadece kırılma animasyonu oynatılır ve
10 saniyede kademeli olarak geri alınır:

  0–4  sn  → Tam kırık görüntüsü (crackLevel 4–9)
  4–7  sn  → %60'a düşürülmüş
  7–10 sn  → %30'a düşürülmüş
  10.  sn  → Tamamen kaldırılır (−1 ile sıfırlanır)

Bu zamanlayıcılar ZipirHavaci.SCHEDULER (ScheduledExecutorService)
ile tetiklenir ve sonuçlar server.execute() içinde ana iş parçacığına
iletilir — thread güvenliği için zorunludur.

MOMENTUM KAYIT SİSTEMİ
-----------------------
Her fırlatmadan sonra üç eksen bağımsız olarak kaydedilir:
  "MomX", "MomY", "MomZ" → PersistentData CompoundTag içine

Her tick'te bu değerler blended yön vektörü üretmek için kullanılır:
  blendedDir = lockDir × 0.80 + currentLook × 0.20

"DashDirX/Y/Z" kilitli yönü tutar. Oyuncu havadayken sönümleme
katsayısı 0.985'tir (çok yavaş düşüş). Yere değdiğinde 0.82'ye
iner ve ilk temas tick'inde tüm momentum silinir.

DÜŞME HASARI KORUMASI
----------------------
onLivingFall olayında:
  0 ağırlık  → %95 koruma
  60 ağırlık → %55 koruma
  (aralarında doğrusal interpolasyon — Mth.lerp)

Limit hızının altına düşülmüşse (deltaY < −1.9) %15 ek ceza alınır.

RELOAD MEKANİĞİ
---------------
Aşırı ısınma sonrası (uses ≥ 5):
  1. WaitingForOverheat = true
  2. Cooldown biter → uses = 0, HasReloadCredit = true
  3. Oyuncu Shift + Sol Tık → IsRecharging = true
  4. 70 tick (~3.5 sn) boyunca çömelmeli tutulursa → 3 mermi yüklenir
  5. İptal: çömelmek bırakılırsa uses tekrar 6 yapılır (boş gösterim)

İlerleme çubuğu action bar'da 15 bloklu bar olarak görünür.


────────────────────────────────────────────────────────────────
3. KALKAN MEKANİĞİ — ThrownShieldEntity.java
────────────────────────────────────────────────────────────────

ThrownShieldEntity, ThrowableItemProjectile sınıfını genişletir.
Ağ üzerinden senkronize edilen iki SynchedEntityData alanı vardır:

  STUCK       → boolean  (Kalkana saplanmış mı?)
  JITTER_TICKS → int    (Titreşim tick sayacı)

Kalkan suya girdiğinde su sıçrama efekti kapalıdır (doWaterSplashEffect boş).
preventAutoWaterLogic bayrağı true yapıldığında isInWater() her zaman
false döndürür — suda saplanmayı önlemek için kullanılan bir koruma katmanıdır.

Kalkan bloka çarptığında stuckPos kaydedilir ve STUCK = true yapılır.
Bu noktadan sonra SoulBondHandler tarafından işaretlenir.


────────────────────────────────────────────────────────────────
4. SOUL BOND — SoulBondHandler.java
────────────────────────────────────────────────────────────────

Soul Bond, oyuncunun duvara saplanmış kalkana ruhsal bir ip atarak
kendini o noktaya çektiği bir skill'dir.

ÇALIŞMA AKIŞI
-------------
  1. El kontrol      → Elde kalkan varsa skill çalışmaz
  2. Öğrenme kontrol → Capability (SoulBondDataProvider) ile kontrol edilir
  3. Cooldown        → 8 saniye
  4. Soul Sand tüket → Envanterde en az 1 adet gerekir
  5. Ruhsal bedel    → 0.5 can + 3 saniye Wither etkisi
  6. ACTIVE_PULLS'a ekle → Sunucu tick'lerinde hareket başlar

HAREKET DÖNGÜSÜ (onServerTick)
-------------------------------
Her sunucu tick'inde:
  • Kalkan kaldırılmışsa veya 2 blok içindeyse dur
  • Önde kırılamaz blok varsa (destroySpeed < 0 veya ≥ 50) dur
  • Oyuncu geçici olarak SPECTATOR moduna alınır (çarpışma bypass)
  • Hareket vektörü: kalkan yönüne normalize × 1.5
  • Her iki tick'te bir SOUL partikülü ve rastgele ses oynatılır

GEÇİCİ SPECTATOR MODE
----------------------
Duvar içinden geçişi simüle etmek için oyuncu SPECTATOR'a alınır.
Önceki mod ORIGINAL_GAMEMODE haritasına kaydedilir ve çekim
bitince geri yüklenir.

Eğer oyuncu çekimden çıktığında bir bloğun içindeyse handleSafeExit()
5 blok genişleyen küp taramasıyla güvenli bir konuma ışınlar.
Lav, geçirimsiz blok kontrolleri de bu aşamada yapılır.

PACKET HIZI KORUMASI
--------------------
İki paket arası minimum 500 ms zorunludur (LAST_PACKET_TIME).
Bu, client'tan gelen hızlı tıklamaların sunucu üzerindeki yükünü sınırlar.


────────────────────────────────────────────────────────────────
5. AURA & RAM MEKANİĞİ — ShieldRamMechanicHandler.java
────────────────────────────────────────────────────────────────

Aura aktifken oyuncu çevresinde dinamik bir kinetik alan oluşur.
Her 2 tick'te bir çevre taranır.

İKİ SENARYO:
  A) Hedefin de aurası varsa → processSmartCollision() (dengeli çarpışma)
  B) Hedefin aurası yoksa   → applyKineticField() (tek yönlü itme)

İç bölge (innerZone = collisionDist × 0.5) çok yakın mesafede
farklı davranış tetiklemek için kullanılır.

COMBO SİSTEMİ
-------------
  COMBO_TRACKER: UUID → (UUID → int) iç içe harita
  COMBO_TIMEOUT: 5 saniye — son isabet sonrası sayaç sıfırlanır
  RAM_COOLDOWN_MS: 800 ms — aynı hedefe tekrar vurma engeli

LANET KORUMASI (AuraSkillPacket)
---------------------------------
Oyuncu lanetliyse (data.isCursed()) normal aura yerine
DarkAuraHandler.handleDarkAuraSkill() yönlendirilir.
Lanet durumu StaticProgressionData'da tutulur.


────────────────────────────────────────────────────────────────
6. ENERJİ AĞI — EnergyNetworkManager + EnergyCarrierBlock + FlagGroup
────────────────────────────────────────────────────────────────

GENEL YAKLAŞIM
--------------
EnergyCarrierBlock, PipeBlock'u genişleterek altı yönde (NSEWUD)
bağlantı State'leri tutar. Her blok 0–20 arası POWER değeri taşır.

Blok yerleşince → EnergyNetworkManager.onBlockPlaced()
Blok kaldırılınca → EnergyNetworkManager.onBlockRemoved()
Komşu değişince → EnergyNetworkManager.onNeighborChanged()

FlagGroup
---------
Bir "enerji grubu"nu temsil eder. Merkez + Chebyshev mesafesi ≤ 20
olan tüm bloklar aynı gruptadır (41×41×41 blok küpü).

İki grup kesişiyorsa aralarındaki blok "köprü" (BRIDGE_ID = −999) olur.

SPATIAL INDEX
-------------
chunkToGroups: ChunkPos → grupId listesi
Bu yapı sayesinde yeni blok yerleşince sadece yakın chunklar taranır,
tüm dünya yerine küçük bir bölge kontrol edilir.

ENDİŞEYE KARŞI KILIF
---------------------
applyingPower = true bayrağı, güç uygulanırken neighborChanged'in
yanlışlıkla tetiklenmesini engeller.

processingBlockRemoval bayrağı da benzer şekilde blok kaldırma
sırasında zincirleme olayların oluşmasını önler.

ThreadLocal NEIGHBOR_CACHE
--------------------------
Komşu grupları her seferinde yeni dizi oluşturmak yerine
ThreadLocal<int[]> ile yeniden kullanılır — GC baskısını azaltır.


────────────────────────────────────────────────────────────────
7. IMPACT REACTION HANDLER — ImpactReactionHandler.java
────────────────────────────────────────────────────────────────

Meteor Çakılış yeteneğinin (Meteor Impact Scroll) sunucu tarafı
mekanizmasını yönetir. Java Reflection API kullanılarak
Display.BlockDisplay'in setBlockState ve setTransformation metodlarına
erişilir — bu metodlar normalde private'tır.

Reflection nesneleri static blokta bir kez hazırlanır ve
sınıf yükleme sırasında hata varsa hata ayıklama mesajı basılır.
Böylece her kullanımda yansıma araması yapılmaz.

PLAYER_CRATERS haritası: UUID → Varlık listesi
Craterlerdeki Display varlıkları burada takip edilir ve
belirli süre sonra temizlenir.


────────────────────────────────────────────────────────────────
8. LOOT TABLE — soul_bond_modifier.json
────────────────────────────────────────────────────────────────

Tip: zipirhavaci:add_item
Koşullar:
  • Sandık: minecraft:chests/nether_bridge (Nether Kalesi)
  • Şans  : %9 (0.09)
Öğe: zipirhavaci:soul_bond_scroll (Adet: 1)

Bu format Forge'un loot_table_id koşuluyla mevcut loot tablolarına
ekleme yapar; orijinal tablo değiştirilmez.


────────────────────────────────────────────────────────────────
9. GENEL OPTİMİZASYON NOKTALARI
────────────────────────────────────────────────────────────────

• ConcurrentHashMap    → Forge event iş parçacıkları arası güvenli erişim
• ThreadLocal diziler  → EnergyNetworkManager'da GC baskısını azaltma
• Bayrak guard'lar     → applyingPower / processingBlockRemoval
• LAST_PACKET_TIME     → Client paket spam koruması (500 ms kapı)
• PersistentData       → Oyuncu yeniden yüklendiğinde momentum/durum kaybını önler
• Scheduler+execute()  → Zamanlama ScheduledExecutorService'de, sonuç main thread'e iletilir
• Reflection static block → Metod araması sınıf yüklendiğinde tek seferlik yapılır
• Mth.clamp             → Sınır dışı değerlerin sistemi bozmasını engeller
• blockPositions LongOpenHashSet → FastUtil primitive seti, kutu açma (boxing) yok
================================================================
