package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
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
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarchewkowaKuszaManager {

    private static final String META_KUSZA_ARROW = "anaitemy_kusza_arrow";

    private final AnaItemy plugin;
    private final NamespacedKey ghostArrowKey;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, GhostArrowState> ghostArrows = new ConcurrentHashMap<>();
    private final Map<UUID, PullData> activePulls = new ConcurrentHashMap<>();

    private BukkitTask ghostMonitorTask;
    private BukkitTask pullTask;

    public MarchewkowaKuszaManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.ghostArrowKey = new NamespacedKey(plugin, "marchewkowa_kusza_ghost");
        startGhostMonitorTask();
        startPullTask();

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    // ==================== GHOST ARROW ====================

    private void startGhostMonitorTask() {
        ghostMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(ghostArrows.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) { ghostArrows.remove(uuid); continue; }
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    if (!MarchewkowaKuszaItem.isMarchewkowaKusza(mainHand) || !player.isHandRaised()) {
                        restoreGhostArrow(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public boolean prepareGhostArrow(Player player) {
        if (ghostArrows.containsKey(player.getUniqueId())) return true;
        if (hasAnyUsableArrow(player)) return true;

        PlayerInventory inv = player.getInventory();
        int heldSlot = inv.getHeldItemSlot();
        int emptySlot = findRightmostEmptySlot(inv, heldSlot);
        if (emptySlot != -1) {
            inv.setItem(emptySlot, createGhostArrow());
            ghostArrows.put(player.getUniqueId(), new GhostArrowState(emptySlot, null));
            player.updateInventory();
            return true;
        }
        int replaceSlot = findRightmostReplaceableSlot(inv, heldSlot);
        if (replaceSlot == -1) return false;
        ItemStack original = inv.getItem(replaceSlot);
        inv.setItem(replaceSlot, createGhostArrow());
        ghostArrows.put(player.getUniqueId(), new GhostArrowState(replaceSlot, original == null ? null : original.clone()));
        player.updateInventory();
        return true;
    }

    public void restoreGhostArrow(Player player) {
        GhostArrowState state = ghostArrows.remove(player.getUniqueId());
        if (state == null) return;
        PlayerInventory inv = player.getInventory();
        inv.setItem(state.slot, state.originalItem);
        player.updateInventory();
    }

    public boolean isGhostArrow(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return false;
        if (!item.hasItemMeta()) return false;
        Byte v = item.getItemMeta().getPersistentDataContainer().get(ghostArrowKey, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    public int getGhostArrowSlot(Player player) {
        GhostArrowState s = ghostArrows.get(player.getUniqueId());
        return s == null ? -1 : s.slot;
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
            if (item.getType() == Material.ARROW || item.getType() == Material.SPECTRAL_ARROW
                    || item.getType().name().endsWith("TIPPED_ARROW")) return true;
        }
        return false;
    }

    private int findRightmostEmptySlot(PlayerInventory inv, int heldSlot) {
        for (int i = 35; i >= 0; i--) { if (i == heldSlot) continue; ItemStack it = inv.getItem(i); if (it == null || it.getType().isAir()) return i; }
        return -1;
    }

    private int findRightmostReplaceableSlot(PlayerInventory inv, int heldSlot) {
        for (int i = 35; i >= 0; i--) {
            if (i == heldSlot) continue;
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            Material t = it.getType();
            if (t == Material.ENCHANTED_GOLDEN_APPLE || t == Material.ELYTRA) continue;
            if (MarchewkowaKuszaItem.isMarchewkowaKusza(it)) continue;
            return i;
        }
        return -1;
    }

    // ==================== COOLDOWN ====================

    public boolean isOnCooldown(Player player) {
        Long end = cooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getCooldownRemaining(Player player) {
        Long end = cooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        long seconds = plugin.getItemsConfig().getMarchewkowaKuszaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.CROSSBOW, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.CROSSBOW, 0);
    }

    // ==================== STRZAŁ ====================

    public boolean isKuszaArrow(Arrow arrow) { return arrow.hasMetadata(META_KUSZA_ARROW); }

    public Player getArrowShooter(Arrow arrow) {
        if (!arrow.hasMetadata(META_KUSZA_ARROW)) return null;
        try { return Bukkit.getPlayer(UUID.fromString(arrow.getMetadata(META_KUSZA_ARROW).get(0).asString())); }
        catch (Exception e) { return null; }
    }

    public void markArrow(Arrow arrow, Player shooter) {
        arrow.setMetadata(META_KUSZA_ARROW, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
    }

    // ==================== PRZYCIĄGANIE ====================

    private void startPullTask() {
        pullTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, PullData> entry : new ArrayList<>(activePulls.entrySet())) {
                    UUID victimId = entry.getKey();
                    PullData data = entry.getValue();

                    if (now >= data.endTime) { activePulls.remove(victimId); continue; }

                    Player victim = Bukkit.getPlayer(victimId);
                    if (victim == null || !victim.isOnline() || victim.isDead()) { activePulls.remove(victimId); continue; }

                    Location target = data.pullTarget;
                    Location victimLoc = victim.getLocation();

                    if (!victimLoc.getWorld().equals(target.getWorld())) { activePulls.remove(victimId); continue; }

                    double distance = victimLoc.distance(target);

                    // Dotarł lub blisko
                    if (distance < 1.5) { activePulls.remove(victimId); continue; }

                    // Gracz rusza się w innym kierunku - szybciej zakończ
                    if (data.lastLocation != null) {
                        double victimMoved = victimLoc.distance(data.lastLocation);
                        Vector toTarget = target.toVector().subtract(data.lastLocation.toVector()).normalize();
                        Vector actualMove = victimLoc.toVector().subtract(data.lastLocation.toVector());
                        if (actualMove.length() > 0.1) {
                            double dot = actualMove.normalize().dot(toTarget);
                            if (dot < -0.3) {
                                // Gracz rusza się od celu - zakończ wcześniej
                                activePulls.remove(victimId);
                                continue;
                            }
                        }
                    }

                    // Ciągnij gracza
                    Vector direction = target.toVector().subtract(victimLoc.toVector());
                    direction.setY(0);
                    if (direction.lengthSquared() < 0.01) { activePulls.remove(victimId); continue; }
                    direction.normalize().multiply(0.4);

                    // Sprawdź czy nie przeniknie przez blok
                    Location nextLoc = victimLoc.clone().add(direction);
                    if (nextLoc.getBlock().getType().isSolid()) {
                        // Blok na drodze - zatrzymaj
                        activePulls.remove(victimId);
                        continue;
                    }

                    // Blokuj latanie
                    if (victim.isGliding()) {
                        victim.setGliding(false);
                    }

                    victim.setVelocity(direction);
                    data.lastLocation = victimLoc.clone();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void startPull(Player shooter, Player victim, Location shooterLocation) {
        ItemsConfig config = plugin.getItemsConfig();

        // Sprawdź dystans
        if (victim.getLocation().distance(shooterLocation) > 100) return;

        // Region
        if (isInBlockedRegion(victim.getLocation())) return;

        // Subtitle
        String victimSub = config.getMarchewkowaKuszaVictimSubtitle()
                .replace("{attacker}", shooter.getName());
        victim.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(victimSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(200))));

        String attackerSub = config.getMarchewkowaKuszaAttackerSubtitle()
                .replace("{victim_name}", victim.getName());
        shooter.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(200))));

        // Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, shooter);
            plugin.getCombatIntegrationManager().tagPlayer(shooter, victim);
        }

        // Dźwięk
        victim.playSound(victim.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
                SoundCategory.PLAYERS, 1.5f, 0.8f);

        // Rozpocznij przyciąganie (3 sekundy)
        long endTime = System.currentTimeMillis() + 3000L;
        activePulls.put(victim.getUniqueId(), new PullData(shooterLocation.clone(), endTime));

        setCooldown(shooter);
    }

    private boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(location,
                plugin.getItemsConfig().getMarchewkowaKuszaBlockedRegions());
    }

    // ==================== CLEANUP ====================

    public void cleanupPlayer(Player player) {
        restoreGhostArrow(player);
        activePulls.remove(player.getUniqueId());
    }

    public void cleanup() {
        if (ghostMonitorTask != null) ghostMonitorTask.cancel();
        if (pullTask != null) pullTask.cancel();
        for (UUID uuid : new HashSet<>(ghostArrows.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) restoreGhostArrow(p);
        }
        ghostArrows.clear();
        activePulls.clear();
        cooldowns.clear();
    }

    // ==================== INNER CLASSES ====================

    private static class GhostArrowState {
        final int slot; final ItemStack originalItem;
        GhostArrowState(int slot, ItemStack originalItem) { this.slot = slot; this.originalItem = originalItem; }
    }

    private static class PullData {
        final Location pullTarget;
        final long endTime;
        Location lastLocation;
        PullData(Location pullTarget, long endTime) { this.pullTarget = pullTarget; this.endTime = endTime; }
    }
}
