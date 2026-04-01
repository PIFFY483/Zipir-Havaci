================================================================
  ZİPİRHAVACI MOD — TECHNICAL README (ENGLISH)
  Mechanic Explanations, Optimization Notes & Code Architecture
================================================================

────────────────────────────────────────────────────────────────
1. OVERALL ARCHITECTURE
────────────────────────────────────────────────────────────────

The mod is divided into four independent but cooperating layers:

  • Movement System  → MovementHandler.java
  • Shield Mechanics → ThrownShieldEntity.java + SoulBondHandler.java
  • Aura / Ram       → ShieldRamMechanicHandler.java + AuraSkillPacket.java
  • Energy Network   → EnergyNetworkManager.java + EnergyCarrierBlock.java + FlagGroup.java

Each layer maintains its own state inside ConcurrentHashMap structures.
This is the primary guard against race conditions caused by concurrent
map access across Forge's multi-threaded event system.


────────────────────────────────────────────────────────────────
2. MOVEMENT SYSTEM — MovementHandler.java
────────────────────────────────────────────────────────────────

WEIGHT CALCULATION
------------------
On every launch the player's Armor + Armor Toughness values are summed
and clamped to 60 (Mth.clamp):

  totalWeight  = clamp(armor + toughness, 0, 60)
  weightRatio  = totalWeight / 60.0

This ratio is converted into three multipliers:

  mobilityPenalty = 1.0 − (weightRatio × 0.20)   → heavier = slower self-movement
  powerBoost      = 1.0 + (weightRatio × 0.20)   → heavier = more knockback on others
  damageBoost     = 1.0 + (weightRatio × 0.05)   → heavier = slightly more damage

Why a cap of 60?
  The maximum realistic Armor + Toughness in vanilla is roughly 30 + 12 = 42.
  The 60 cap is intentionally wide to leave room for modded armors.

PULL MODE
---------
The player is launched in the direction they are looking.
The Y component receives a ×1.1 multiplier because Minecraft's gravity
starts reducing Y velocity immediately after launch; without this correction
a horizontal shot becomes a nosedive.

Additionally, player.noPhysics is set to true for exactly one tick.
The flag "NoPhysicsResetNextTick" is written to PersistentData so that
the next tick's living-update handler resets it. This is referred to as
a single-tick friction bypass: it prevents the ground friction calculation
from killing the launch impulse on the very tick it is applied.

PUSH MODE
---------
When computing the recoil vector, if the player is looking upward
(look.y > 0.7) the Meteor Crash is triggered: movement is set to
(0, −2.5, 0) and AnimationManager::triggerSquash is called.

In normal PUSH mode, doors in front of the player are evaluated:
  - Iron door  → shake only (DoorShakeBlockEntity)
  - Wood door  → blast and destroy (DoorBlastHandler.blast)

SUPER SKILL (LEVELED ABILITY)
------------------------------
executeSuperSkill() accepts a level from 1 to 5. Per level:

  Ammo cost  = level + 1   (minimum 2 ammo required)
  Base power = 3.0 + (level × 1.5)   [Level 5 = flat 16.0]
   Range     = 4.5 + (level × 1.6) blocks

The PULL version of SuperSkill creates a vacuum effect:
entities in front of the player are pulled toward them (dot > 0 → VACUUM),
while those behind are pushed away (dot ≤ 0 → PUSH).

The PUSH version triggers a Ground Blast when:
  - XRot ≥ 80° OR a surface exists within 3 blocks
  - AND level ≥ 3
  → An omnidirectional area blast is triggered.

BLOCK CRACK EFFECT (spawnSuperCracks)
--------------------------------------
Cracks are displayed server-side via destroyBlockProgress().
No blocks are actually broken; only the crack animation plays.
It fades out in three steps over 10 seconds:

  0–4  s  → Full crack display (crackLevel 4–9)
  4–7  s  → Reduced to 60 %
  7–10 s  → Reduced to 30 %
  10.  s  → Completely cleared (sent −1)

These timers are fired through ZipirHavaci.SCHEDULER
(ScheduledExecutorService) and results are posted back to the
main server thread via server.execute() — required for thread safety.

MOMENTUM STORAGE SYSTEM
------------------------
After each launch, three axes are stored independently:
  "MomX", "MomY", "MomZ" → CompoundTag inside PersistentData

Each tick a blended direction is produced:
  blendedDir = lockDir × 0.80 + currentLook × 0.20

"DashDirX/Y/Z" holds the locked direction. While airborne the
damping factor is 0.985 (very slow decay). On ground contact
it drops to 0.82, and momentum is fully zeroed on the first
ground-contact tick.

FALL DAMAGE PROTECTION
-----------------------
In the onLivingFall event:
  0 weight  → 95 % protection
  60 weight → 55 % protection
  (linear interpolation between them — Mth.lerp)

A 15 % additional penalty applies when falling speed exceeds
the limit (deltaY < −1.9).

RELOAD MECHANIC
---------------
After overheating (uses ≥ 5):
  1. WaitingForOverheat = true
  2. Cooldown expires → uses = 0, HasReloadCredit = true
  3. Player holds Shift + Left Click → IsRecharging = true
  4. Crouch for 70 ticks (~3.5 s) → 3 ammo loaded
  5. Cancel: releasing crouch resets uses to 6 (empty display)

Progress is shown on the action bar as a 15-block bar.


────────────────────────────────────────────────────────────────
3. SHIELD MECHANICS — ThrownShieldEntity.java
────────────────────────────────────────────────────────────────

ThrownShieldEntity extends ThrowableItemProjectile.
Two SynchedEntityData fields are synchronized over the network:

  STUCK        → boolean  (Is the shield embedded in a block?)
  JITTER_TICKS → int     (Visual jitter tick counter)

Water splash is suppressed (doWaterSplashEffect is empty).
When preventAutoWaterLogic is true, isInWater() always returns false —
a guard layer to prevent unwanted water-embedding behavior.

When the shield hits a block, stuckPos is recorded and STUCK = true.
From this point SoulBondHandler tracks the entity.


────────────────────────────────────────────────────────────────
4. SOUL BOND — SoulBondHandler.java
────────────────────────────────────────────────────────────────

Soul Bond allows the player to launch a "spiritual tether" toward
their wall-embedded shield and be pulled to that location.

EXECUTION FLOW
--------------
  1. Hand check       → Skill blocked if a shield is in either hand
  2. Learning check   → Verified via Capability (SoulBondDataProvider)
  3. Cooldown         → 8 seconds
  4. Consume Soul Sand → At least 1 in inventory required
  5. Spiritual cost   → 0.5 HP + 3-second Wither effect
  6. Added to ACTIVE_PULLS → Movement begins in server ticks

MOVEMENT LOOP (onServerTick)
-----------------------------
Each server tick:
  • If the shield was removed or the player is within 2 blocks → stop
  • If an indestructible block is ahead (destroySpeed < 0 or ≥ 50) → stop
  • Player is temporarily set to SPECTATOR mode (collision bypass)
  • Movement vector: direction toward shield, normalized × 1.5
  • Every 2 ticks: SOUL particle spawned + random sound played

TEMPORARY SPECTATOR MODE
-------------------------
SPECTATOR mode is used to simulate passing through walls.
The prior game mode is saved in ORIGINAL_GAMEMODE and restored
when the pull ends.

If the player ends up inside a block after the pull, handleSafeExit()
scans an expanding cube up to 5 blocks wide to find a safe position.
Lava and solid-block checks are also performed at this stage.

PACKET RATE GUARD
-----------------
A minimum gap of 500 ms is enforced between two packets (LAST_PACKET_TIME).
This limits server load from rapid client clicks.


────────────────────────────────────────────────────────────────
5. AURA & RAM — ShieldRamMechanicHandler.java
────────────────────────────────────────────────────────────────

While Aura is active, a dynamic kinetic field surrounds the player.
The surrounding area is scanned every 2 ticks.

TWO SCENARIOS:
  A) Target also has Aura → processSmartCollision() (balanced clash)
  B) Target has no Aura  → applyKineticField() (one-way push)

The inner zone (innerZone = collisionDist × 0.5) triggers different
behavior for very close distances.

COMBO SYSTEM
------------
  COMBO_TRACKER:   UUID → (UUID → int) nested map
  COMBO_TIMEOUT:   5 seconds — counter resets after last hit
  RAM_COOLDOWN_MS: 800 ms — prevents hitting the same target repeatedly

CURSE GUARD (AuraSkillPacket)
-------------------------------
If the player is cursed (data.isCursed()), Aura activation is
redirected to DarkAuraHandler.handleDarkAuraSkill() instead.
Curse state is held in StaticProgressionData.


────────────────────────────────────────────────────────────────
6. ENERGY NETWORK — EnergyNetworkManager + EnergyCarrierBlock + FlagGroup
────────────────────────────────────────────────────────────────

GENERAL APPROACH
----------------
EnergyCarrierBlock extends PipeBlock and keeps six directional
(N/S/E/W/U/D) connection BlockState properties.
Each block carries a POWER value in the range 0–20.

Block placed  → EnergyNetworkManager.onBlockPlaced()
Block removed → EnergyNetworkManager.onBlockRemoved()
Neighbor changed → EnergyNetworkManager.onNeighborChanged()

FlagGroup
---------
Represents an "energy group". The center block plus all blocks within
Chebyshev distance ≤ 20 belong to the same group (a 41×41×41 cube).

If two groups intersect, the shared block becomes a "bridge" (BRIDGE_ID = −999).

SPATIAL INDEX
-------------
chunkToGroups: ChunkPos → List<groupId>
This means that when a new block is placed, only nearby chunks are
scanned — not the entire world.

GUARD FLAGS
-----------
applyingPower = true prevents neighborChanged from firing
spuriously while power is being applied.

processingBlockRemoval similarly prevents chain events during
block removal.

ThreadLocal NEIGHBOR_CACHE
--------------------------
Instead of allocating a new array on every neighbor lookup,
ThreadLocal<int[]> is reused — reduces GC pressure.


────────────────────────────────────────────────────────────────
7. IMPACT REACTION HANDLER — ImpactReactionHandler.java
────────────────────────────────────────────────────────────────

Manages the server-side logic of the Meteor Impact Scroll ability.
Java Reflection is used to access Display.BlockDisplay's private
setBlockState and setTransformation methods.

Reflection objects are prepared once in a static initializer block.
If preparation fails at class load time, a debug message is printed.
This avoids repeated reflection lookups at runtime.

PLAYER_CRATERS map: UUID → List<Entity>
Display entities inside craters are tracked here and cleaned up
after a set duration.


────────────────────────────────────────────────────────────────
8. LOOT TABLE — soul_bond_modifier.json
────────────────────────────────────────────────────────────────

Type: zipirhavaci:add_item
Conditions:
  • Chest:  minecraft:chests/nether_bridge (Nether Fortress)
  • Chance: 9 % (0.09)
Item: zipirhavaci:soul_bond_scroll (Count: 1)

This format uses Forge's loot_table_id condition to inject an item
into an existing loot table without modifying the original file.


────────────────────────────────────────────────────────────────
9. OPTIMIZATION HIGHLIGHTS
────────────────────────────────────────────────────────────────

• ConcurrentHashMap    → Thread-safe access across Forge event threads
• ThreadLocal arrays   → Reduces GC pressure in EnergyNetworkManager
• Guard flags          → applyingPower / processingBlockRemoval prevent re-entry
• LAST_PACKET_TIME     → Client packet spam protection (500 ms gate)
• PersistentData       → Preserves momentum/state across player reloads
• Scheduler+execute()  → Timing via ScheduledExecutorService, results
                         dispatched to the main thread via server.execute()
• Reflection static block → Method lookup done once at class load time
• Mth.clamp            → Prevents out-of-range values from corrupting the system
• LongOpenHashSet      → FastUtil primitive set, zero boxing overhead
================================================================
