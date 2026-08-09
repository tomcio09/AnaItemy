package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.World;
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
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
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

                    // ✅ Oblicz dystans tylko w poziomie (X/Z)
                    double horizontalDistance = Math.sqrt(
                            Math.pow(victimLoc.getX() - target.getX(), 2) +
                            Math.pow(victimLoc.getZ() - target.getZ(), 2)
                    );

                    // Dotarł
                    if (horizontalDistance < 1.5) { activePulls.remove(victimId); continue; }

                    // Gracz rusza się w innym kierunku - szybciej zakończ
                    if (data.lastLocation != null) {
                        Vector toTarget2D = new Vector(
                                target.getX() - data.lastLocation.getX(),
                                0,
                                target.getZ() - data.lastLocation.getZ()
                        ).normalize();

                        Vector actualMove = new Vector(
                                victimLoc.getX() - data.lastLocation.getX(),
                                0,
                                victimLoc.getZ() - data.lastLocation.getZ()
                        );

                        if (actualMove.length() > 0.1) {
                            double dot = actualMove.normalize().dot(toTarget2D);
                            if (dot < -0.3) {
                                activePulls.remove(victimId);
                                continue;
                            }
                        }
                    }

                    // ✅ Kierunek ciągnięcia - TYLKO poziomo (X/Z), bez Y
                    Vector direction = new Vector(
                            target.getX() - victimLoc.getX(),
                            0,
                            target.getZ() - victimLoc.getZ()
                    );

                    if (direction.lengthSquared() < 0.01) { activePulls.remove(victimId); continue; }
                    direction.normalize().multiply(0.4);

                    // ✅ Sprawdź czy następna pozycja nie jest w bloku
                    Location nextLoc = victimLoc.clone().add(direction);

                    // ✅ Znajdź ziemię pod następną pozycją
                    Location groundLoc = findGround(nextLoc);

                    if (groundLoc == null) {
                        // Brak ziemi (void?) - zatrzymaj
                        activePulls.remove(victimId);
                        continue;
                    }

                    // ✅ Sprawdź czy ściana nie blokuje
                    int groundY = groundLoc.getBlockY();
                    org.bukkit.block.Block feetBlock = victim.getWorld().getBlockAt(
                            groundLoc.getBlockX(), groundY, groundLoc.getBlockZ());
                    org.bukkit.block.Block headBlock = victim.getWorld().getBlockAt(
                            groundLoc.getBlockX(), groundY + 1, groundLoc.getBlockZ());

                    if (feetBlock.getType().isSolid() && headBlock.getType().isSolid()) {
                        // Ściana 2 bloki wysoka - nie da się przejść
                        activePulls.remove(victimId);
                        continue;
                    }

                    // ✅ Blokuj latanie
                    if (victim.isGliding()) {
                        victim.setGliding(false);
                    }

                    // ✅ Ustaw velocity - tylko poziomo, lekko w dół żeby trzymać na ziemi
                    direction.setY(-0.1);
                    victim.setVelocity(direction);

                    data.lastLocation = victimLoc.clone();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * ✅ Znajduje poziom ziemi pod daną lokalizacją.
     * Zwraca lokalizację na szczycie solid bloku, lub null jeśli void.
     */
    private Location findGround(Location loc) {
        World world = loc.getWorld();
        int startY = loc.getBlockY();

        // Sprawdź w dół (max 10 bloków)
        for (int y = startY; y >= startY - 10 && y >= world.getMinHeight(); y--) {
            org.bukkit.block.Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            org.bukkit.block.Block above = world.getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ());

            if (block.getType().isSolid() && !above.getType().isSolid()) {
                return new Location(world, loc.getX(), y + 1, loc.getZ());
            }
        }

        // Sprawdź w górę (max 5 bloków - schody/góra)
        for (int y = startY + 1; y <= startY + 5 && y <= world.getMaxHeight(); y++) {
            org.bukkit.block.Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            org.bukkit.block.Block above = world.getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ());

            if (block.getType().isSolid() && !above.getType().isSolid()) {
                return new Location(world, loc.getX(), y + 1, loc.getZ());
            }
        }

        return null;
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
