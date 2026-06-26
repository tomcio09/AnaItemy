package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArcusMagnusManager {

    private static final String META_ARCUS_ARROW = "anaitemy_arcus_arrow";
    private static final String HEART = "❤";

    // WARTOŚCI W SERCACH, nie HP
    private static final double[] COMBO_DAMAGE = {0.5, 1.0, 2.0, 4.0, 8.0};
    private static final long COMBO_TIMEOUT_MS = 4000L;

    private final AnaItemy plugin;
    private final NamespacedKey ghostArrowKey;

    // shooter UUID -> combo
    private final Map<UUID, ComboData> activeCombos = new ConcurrentHashMap<>();

    // victim UUID -> shooter UUID
    private final Map<UUID, UUID> victimLocks = new ConcurrentHashMap<>();

    // ghost arrows
    private final Map<UUID, GhostArrowState> ghostArrows = new ConcurrentHashMap<>();

    // bossbary
    private final Map<UUID, BossBar> comboBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> bossBarTasks = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;
    private BukkitTask ghostMonitorTask;

    public ArcusMagnusManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.ghostArrowKey = new NamespacedKey(plugin, "arcus_magnus_ghost_arrow");
        startCleanupTask();
        startGhostMonitorTask();
    }

    // ==================== TASKS ====================

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (ComboData combo : new ArrayList<>(activeCombos.values())) {
                    if (now - combo.getLastHitTime() > COMBO_TIMEOUT_MS) {
                        endCombo(combo.getShooterId());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void startGhostMonitorTask() {
        ghostMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(ghostArrows.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        ghostArrows.remove(uuid);
                        continue;
                    }

                    ItemStack mainHand = player.getInventory().getItemInMainHand();

                    if (!ArcusMagnusItem.isArcusMagnus(mainHand) || !player.isHandRaised()) {
                        restoreGhostArrow(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================== GHOST ARROW ====================

    public boolean prepareGhostArrow(Player player) {
        if (ghostArrows.containsKey(player.getUniqueId())) return true;
        if (hasAnyUsableArrow(player)) return true;

        PlayerInventory inventory = player.getInventory();
        int heldSlot = inventory.getHeldItemSlot();

        int emptySlot = findRightmostEmptySlot(inventory, heldSlot);
        if (emptySlot != -1) {
            inventory.setItem(emptySlot, createGhostArrow());
            ghostArrows.put(player.getUniqueId(), new GhostArrowState(emptySlot, null));
            player.updateInventory();
            return true;
        }

        int replaceSlot = findRightmostReplaceableSlot(inventory, heldSlot);
        if (replaceSlot == -1) return false;

        ItemStack original = inventory.getItem(replaceSlot);
        inventory.setItem(replaceSlot, createGhostArrow());
        ghostArrows.put(
                player.getUniqueId(),
                new GhostArrowState(replaceSlot, original == null ? null : original.clone())
        );
        player.updateInventory();
        return true;
    }

    public void restoreGhostArrow(Player player) {
        GhostArrowState state = ghostArrows.remove(player.getUniqueId());
        if (state == null) return;

        PlayerInventory inventory = player.getInventory();
        if (state.originalItem == null) {
            inventory.setItem(state.slot, null);
        } else {
            inventory.setItem(state.slot, state.originalItem);
        }
        player.updateInventory();
    }

    public boolean isGhostArrow(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        Byte value = meta.getPersistentDataContainer().get(ghostArrowKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public int getGhostArrowSlot(Player player) {
        GhostArrowState state = ghostArrows.get(player.getUniqueId());
        return state == null ? -1 : state.slot;
    }

    private ItemStack createGhostArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.getPersistentDataContainer().set(ghostArrowKey, PersistentDataType.BYTE, (byte) 1);
        arrow.setItemMeta(meta);
        return arrow;
    }

    private boolean hasAnyUsableArrow(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (isGhostArrow(item)) continue;

            if (item.getType() == Material.ARROW
                    || item.getType() == Material.SPECTRAL_ARROW
                    || item.getType().name().endsWith("TIPPED_ARROW")) {
                return true;
            }
        }
        return false;
    }

    private int findRightmostEmptySlot(PlayerInventory inv, int heldSlot) {
        for (int slot = 35; slot >= 0; slot--) {
            if (slot == heldSlot) continue;
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) return slot;
        }
        return -1;
    }

    private int findRightmostReplaceableSlot(PlayerInventory inv, int heldSlot) {
        for (int slot = 35; slot >= 0; slot--) {
            if (slot == heldSlot) continue;

            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (!isReplaceableForGhostArrow(item)) continue;

            return slot;
        }
        return -1;
    }

    private boolean isReplaceableForGhostArrow(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        Material type = item.getType();
        if (type == Material.ENCHANTED_GOLDEN_APPLE) return false;
        if (type == Material.ELYTRA) return false;

        if (TotemUlaskawienia.isTotemUlaskawienia(item)) return false;
        if (Excalibur.isExcalibur(item)) return false;
        if (HydroKlatka.isHydroKlatka(item)) return false;
        if (RozdzkailuzjonistyItem.isRozdzkaIluzjonisty(item)) return false;
        if (WedkaNielotaItem.isWedkaNielota(item)) return false;
        if (SakiewkaDropu.isSakiewka(item)) return false;
        if (WzmocnianaElytra.isWzmocnianaElytra(item)) return false;
        if (BlokWidmoItem.isBlokWidmo(item)) return false;
        if (SiekieraGrinchaItem.isSiekieraGrincha(item)) return false;
        if (HydroTrojzabItem.isHydroTrojzab(item)) return false;
        if (CudownaLatarniaItem.isCudownaLatarnia(item)) return false;
        if (RogJednorozcaItem.isRogJednorozca(item)) return false;
        if (BoskiToporItem.isBoskiTopor(item)) return false;
        if (SuperMarchewkaItem.isSuperMarchewka(item)) return false;
        if (LopataGrinchaItem.isLopataGrincha(item)) return false;
        if (RozgaItem.isRozga(item)) return false;
        if (ArcusMagnusItem.isArcusMagnus(item)) return false;
        if (KroliczyMieczItem.isKroliczyMiecz(item)) return false;

        return true;
    }

    // ==================== STRZAŁ ====================

    public void fireArrow(Player shooter, float force) {
        restoreGhostArrow(shooter);

        // tylko full charge
        if (force < 0.9f) return;

        Location eye = shooter.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        shooter.getWorld().spawn(
                eye.add(direction.clone().multiply(0.6)),
                Arrow.class,
                arrow -> {
                    arrow.setShooter(shooter);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.setVelocity(direction.multiply(force * 3.0));
                    arrow.setMetadata(META_ARCUS_ARROW,
                            new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
                }
        );

        shooter.playSound(shooter.getLocation(),
                Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public boolean isArcusArrow(Arrow arrow) {
        return arrow.hasMetadata(META_ARCUS_ARROW);
    }

    public Player getArrowShooter(Arrow arrow) {
        if (!arrow.hasMetadata(META_ARCUS_ARROW)) return null;

        try {
            String raw = arrow.getMetadata(META_ARCUS_ARROW).get(0).asString();
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== HIT ====================

    public void handleHit(Player shooter, Player victim) {
        UUID shooterId = shooter.getUniqueId();
        UUID victimId = victim.getUniqueId();

        if (isInBlockedRegion(victim.getLocation())) return;

        UUID currentLocker = victimLocks.get(victimId);
        if (currentLocker != null && !currentLocker.equals(shooterId)) {
            return;
        }

        ComboData combo = activeCombos.get(shooterId);

        if (combo == null
                || !combo.getVictimId().equals(victimId)
                || System.currentTimeMillis() - combo.getLastHitTime() > COMBO_TIMEOUT_MS) {
            endCombo(shooterId);
            combo = new ComboData(shooterId, victimId);
            activeCombos.put(shooterId, combo);
            victimLocks.put(victimId, shooterId);
        }

        int hitIndex = combo.getHitCount() % COMBO_DAMAGE.length;

        // ✅ damage w SERCACH
        double damageHearts = COMBO_DAMAGE[hitIndex];
        double damageHP = damageHearts * 2.0;

        double currentHealth = victim.getHealth();
        double newHealth = currentHealth - damageHP;

        if (newHealth <= 0) {
            victim.setHealth(0.0);
        } else {
            victim.setHealth(newHealth);
        }

        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, shooter);
            plugin.getCombatIntegrationManager().tagPlayer(shooter, victim);
        }

        String victimSubtitle = "&aZostałeś trafiony przez &eArcusa Magnusa&7! &c-"
                + formatDamage(damageHearts) + HEART;
        victim.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(victimSubtitle),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));

        combo.registerHit();

        victim.getWorld().spawnParticle(
                Particle.CRIT,
                victim.getLocation().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0.2
        );

        int nextHitIndex = combo.getHitCount() % COMBO_DAMAGE.length;
        double nextDamageHearts = COMBO_DAMAGE[nextHitIndex];
        startComboBossBar(shooter, nextDamageHearts);
    }

    // ==================== BOSSBAR ====================

    private void startComboBossBar(Player shooter, double nextDamageHearts) {
        UUID shooterId = shooter.getUniqueId();

        BukkitTask oldTask = bossBarTasks.remove(shooterId);
        if (oldTask != null) oldTask.cancel();

        BossBar bossBar = comboBossBars.get(shooterId);
        if (bossBar == null) {
            bossBar = BossBar.bossBar(
                    Component.empty(),
                    1.0f,
                    BossBar.Color.RED,
                    BossBar.Overlay.PROGRESS
            );
            comboBossBars.put(shooterId, bossBar);
            shooter.showBossBar(bossBar);
        }

        final BossBar bar = bossBar;
        final long startTime = System.currentTimeMillis();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shooter.isOnline()) {
                    endCombo(shooterId);
                    cancel();
                    return;
                }

                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = COMBO_TIMEOUT_MS - elapsed;

                if (remaining <= 0) {
                    endCombo(shooterId);
                    cancel();
                    return;
                }

                float progress = Math.max(0.01f, Math.min(1.0f, (float) remaining / COMBO_TIMEOUT_MS));
                bar.progress(progress);

                String title = "&aArcus Magnus &7ma aktywne &eCOMBO&7! Traf przeciwnika w czasie &a"
                        + remaining + "ms &7aby zadać mu &c"
                        + formatDamage(nextDamageHearts) + HEART;
                bar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(title));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        bossBarTasks.put(shooterId, task);
    }

    private void endCombo(UUID shooterId) {
        ComboData combo = activeCombos.remove(shooterId);
        if (combo != null) {
            victimLocks.remove(combo.getVictimId());
        }

        BukkitTask task = bossBarTasks.remove(shooterId);
        if (task != null) {
            task.cancel();
        }

        BossBar bossBar = comboBossBars.remove(shooterId);
        if (bossBar != null) {
            Player shooter = Bukkit.getPlayer(shooterId);
            if (shooter != null && shooter.isOnline()) {
                shooter.hideBossBar(bossBar);
            }
        }
    }

    // ==================== UTILS ====================

    private String formatDamage(double damage) {
        if (damage == (int) damage) {
            return ((int) damage) + ".0";
        }
        return String.valueOf(damage);
    }

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getArcusMagnusBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void cleanupPlayer(Player player) {
        restoreGhostArrow(player);
        endCombo(player.getUniqueId());

        // usuń lock jeśli gracz był ofiarą jakiegoś combo
        victimLocks.entrySet().removeIf(entry -> entry.getValue().equals(player.getUniqueId())
                || entry.getKey().equals(player.getUniqueId()));
    }

    public void cleanup() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (ghostMonitorTask != null) ghostMonitorTask.cancel();

        for (UUID uuid : new HashSet<>(ghostArrows.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreGhostArrow(player);
            }
        }

        for (UUID shooterId : new HashSet<>(activeCombos.keySet())) {
            endCombo(shooterId);
        }

        ghostArrows.clear();
        activeCombos.clear();
        victimLocks.clear();
        comboBossBars.clear();
        bossBarTasks.clear();
    }

    // ==================== INNER CLASSES ====================

    private static class ComboData {
        private final UUID shooterId;
        private final UUID victimId;
        private int hitCount;
        private long lastHitTime;

        public ComboData(UUID shooterId, UUID victimId) {
            this.shooterId = shooterId;
            this.victimId = victimId;
            this.hitCount = 0;
            this.lastHitTime = System.currentTimeMillis();
        }

        public UUID getShooterId() { return shooterId; }
        public UUID getVictimId() { return victimId; }
        public int getHitCount() { return hitCount; }
        public long getLastHitTime() { return lastHitTime; }

        public void registerHit() {
            hitCount++;
            lastHitTime = System.currentTimeMillis();
        }
    }

    private static class GhostArrowState {
        private final int slot;
        private final ItemStack originalItem;

        private GhostArrowState(int slot, ItemStack originalItem) {
            this.slot = slot;
            this.originalItem = originalItem;
        }
    }
}
