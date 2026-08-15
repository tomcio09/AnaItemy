package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
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
import pl.anaheim.anaitemy.utils.ArmorReductionHelper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HydroTrojzabManager {

    private static final String META_HYDRO_TRIDENT = "anaitemy_hydro_trident";
    private static final String META_HYDRO_TRIDENT_OWNER = "anaitemy_hydro_trident_owner";

    private final AnaItemy plugin;
    private final NamespacedKey ghostArrowKey;

    private final Map<UUID, Long> shotCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> launchCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, GhostArrowState> ghostArrows = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> launchActionBarTasks = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;
    private BukkitTask ghostMonitorTask;

    public HydroTrojzabManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.ghostArrowKey = new NamespacedKey(plugin, "hydro_trojzab_ghost_arrow");
        startCleanupTask();
        startGhostMonitorTask();
    }

    // ==================== TASKS ====================

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                shotCooldowns.entrySet().removeIf(e -> now >= e.getValue());
                launchCooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
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

                    if (!HydroTrojzabItem.isHydroTrojzab(mainHand)) {
                        restoreGhostArrow(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 5L);
    }

    // ==================== COOLDOWNS ====================

    public boolean isShotOnCooldown(Player player) {
        Long end = shotCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getShotCooldownRemaining(Player player) {
        Long end = shotCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setShotCooldown(Player player) {
        long seconds = plugin.getItemsConfig().getHydroTrojzabShotCooldown();
        shotCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean isLaunchOnCooldown(Player player) {
        Long end = launchCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getLaunchCooldownRemaining(Player player) {
        Long end = launchCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setLaunchCooldown(Player player) {
        long seconds = plugin.getItemsConfig().getHydroTrojzabLaunchCooldown();
        launchCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        startLaunchActionBarDisplay(player);
    }

    public void resetCooldowns(Player player) {
        shotCooldowns.remove(player.getUniqueId());
        launchCooldowns.remove(player.getUniqueId());
        stopLaunchActionBarDisplay(player);
    }

    public void setPostResetCooldowns(Player player, int seconds) {
        long end = System.currentTimeMillis() + (seconds * 1000L);
        shotCooldowns.put(player.getUniqueId(), end);
        launchCooldowns.put(player.getUniqueId(), end);
        startLaunchActionBarDisplay(player);
    }

    // ==================== ACTION BAR DLA LAUNCHA ====================

    private void startLaunchActionBarDisplay(Player player) {
        stopLaunchActionBarDisplay(player);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    launchActionBarTasks.remove(player.getUniqueId());
                    plugin.getActionBarManager().removeActionBar(player, "hydrotrojzab");
                    return;
                }

                long rem = getLaunchCooldownRemaining(player);
                if (rem <= 0) {
                    cancel();
                    launchActionBarTasks.remove(player.getUniqueId());
                    plugin.getActionBarManager().removeActionBar(player, "hydrotrojzab");
                    return;
                }

                String format = plugin.getItemsConfig().getHydroTrojzabLaunchActionBarFormat()
                        .replace("{time}", rem + "s");
                plugin.getActionBarManager().setActionBar(player, "hydrotrojzab", format);
            }
        }.runTaskTimer(plugin, 0L, 20L);

        launchActionBarTasks.put(player.getUniqueId(), task);
    }

    private void stopLaunchActionBarDisplay(Player player) {
        BukkitTask task = launchActionBarTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        plugin.getActionBarManager().removeActionBar(player, "hydrotrojzab");
    }

    // ==================== GHOST ARROW ====================

    public boolean prepareGhostArrow(Player player) {
        if (ghostArrows.containsKey(player.getUniqueId())) {
            return true;
        }

        if (hasAnyUsableArrow(player)) {
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        int heldSlot = inventory.getHeldItemSlot();

        int emptySlot = findRightmostEmptySlot(inventory, heldSlot);
        if (emptySlot != -1) {
            ItemStack ghostArrow = createGhostArrow();
            inventory.setItem(emptySlot, ghostArrow);
            ghostArrows.put(player.getUniqueId(), new GhostArrowState(emptySlot, null));
            player.updateInventory();
            return true;
        }

        int replaceSlot = findRightmostReplaceableSlot(inventory, heldSlot);
        if (replaceSlot == -1) {
            return false;
        }

        ItemStack original = inventory.getItem(replaceSlot);
        ItemStack ghostArrow = createGhostArrow();
        inventory.setItem(replaceSlot, ghostArrow);
        ghostArrows.put(player.getUniqueId(), new GhostArrowState(replaceSlot,
                original == null ? null : original.clone()));
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

    public boolean hasGhostArrow(Player player) {
        return ghostArrows.containsKey(player.getUniqueId());
    }

    public int getGhostArrowSlot(Player player) {
        GhostArrowState state = ghostArrows.get(player.getUniqueId());
        return state == null ? -1 : state.slot;
    }

    public boolean isGhostArrow(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Byte value = meta.getPersistentDataContainer().get(ghostArrowKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
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

    private int findRightmostEmptySlot(PlayerInventory inventory, int heldSlot) {
        for (int slot = 35; slot >= 0; slot--) {
            if (slot == heldSlot) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private int findRightmostReplaceableSlot(PlayerInventory inventory, int heldSlot) {
        for (int slot = 35; slot >= 0; slot--) {
            if (slot == heldSlot) continue;

            ItemStack item = inventory.getItem(slot);
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

        return true;
    }

    // ==================== SUBTITLES ====================

    public void sendShotCooldownSubtitle(Player player) {
        long remaining = getShotCooldownRemaining(player);
        String subtitle = plugin.getItemsConfig().getHydroTrojzabShotCooldownSubtitle()
                .replace("{seconds_left}", remaining + "s");

        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(
                        Duration.ofMillis(150),
                        Duration.ofMillis(1800),
                        Duration.ofMillis(150)
                )
        ));
    }

    public void sendLaunchCooldownSubtitle(Player player) {
        long remaining = getLaunchCooldownRemaining(player);
        String subtitle = plugin.getItemsConfig().getHydroTrojzabLaunchCooldownSubtitle()
                .replace("{seconds_left}", remaining + "s");

        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(
                        Duration.ofMillis(150),
                        Duration.ofMillis(1800),
                        Duration.ofMillis(150)
                )
        ));
    }

    // ==================== REGIONS ====================

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInBlockedRegion(
                location,
                plugin.getItemsConfig().getHydroTrojzabBlockedRegions()
        );
    }

    // ==================== ABILITY: SHOT ====================

    public void fireHydroTrident(Player shooter, float force) {
        Location eye = shooter.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Location spawnLoc = eye.clone().add(direction.clone().multiply(1.0));

        Trident trident = shooter.getWorld().spawn(spawnLoc, Trident.class, t -> {
            t.setShooter(shooter);
            t.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            t.setGravity(true);
            t.setMetadata(META_HYDRO_TRIDENT,
                    new FixedMetadataValue(plugin, true));
            t.setMetadata(META_HYDRO_TRIDENT_OWNER,
                    new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));

            double speed = force * 3.0;
            t.setVelocity(direction.multiply(speed));
        });

        shooter.playSound(shooter.getLocation(),
                Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.2f, 1.0f);

        setShotCooldown(shooter);
        monitorGroundRemoval(trident);
    }

    private void monitorGroundRemoval(Trident trident) {
        new BukkitRunnable() {
            boolean scheduled = false;

            @Override
            public void run() {
                if (!trident.isValid() || trident.isDead()) {
                    cancel();
                    return;
                }

                if (trident.isOnGround()) {
                    if (!scheduled) {
                        scheduled = true;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (trident.isValid() && !trident.isDead()) {
                                trident.remove();
                            }
                        }, 20L);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void handleImpact(Trident trident) {
        if (!trident.hasMetadata(META_HYDRO_TRIDENT)) return;

        ItemsConfig config = plugin.getItemsConfig();
        Location impact = trident.getLocation();
        World world = impact.getWorld();

        Player shooter = getOwner(trident);

        world.strikeLightningEffect(impact);
        world.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, impact, 40, 0.8, 1.0, 0.8, 0.3);
        world.spawnParticle(Particle.WATER_SPLASH, impact, 30, 1.2, 0.6, 1.2, 0.05);

        double radius = config.getHydroTrojzabImpactRadius();
        double baseDamage = config.getHydroTrojzabImpactDamage();
        double kbHorizontal = config.getHydroTrojzabKnockbackHorizontal();
        double kbUp = config.getHydroTrojzabKnockbackUpward();

        for (Player target : world.getNearbyPlayers(impact, radius, radius, radius)) {
            if (shooter != null && target.getUniqueId().equals(shooter.getUniqueId())) continue;

            if (isInBlockedRegion(target.getLocation())) continue;

            if (plugin.getItemProtectionManager().isProtected(target, "hydro-trojzab")) {
                continue;
            }

            if (shooter != null && plugin.getCombatIntegrationManager().isEnabled()) {
                plugin.getCombatIntegrationManager().tagPlayer(target, shooter);
                plugin.getCombatIntegrationManager().tagPlayer(shooter, target);
            }

            // ✅ Zastosuj redukcję zbroi eventówek
            double damage = ArmorReductionHelper.applyArmorReduction(baseDamage, target);

            double currentHealth = target.getHealth();
            double newHealth = currentHealth - damage;

            if (newHealth <= 0) {
                target.setHealth(0.0);
            } else {
                target.setHealth(newHealth);
            }

            plugin.getItemProtectionManager().applyProtection(target, "hydro-trojzab");

            Vector knockback = target.getLocation().toVector().subtract(impact.toVector());
            knockback.setY(0);

            if (knockback.lengthSquared() <= 0.0001) {
                if (shooter != null) {
                    knockback = target.getLocation().toVector()
                            .subtract(shooter.getLocation().toVector());
                    knockback.setY(0);
                }
            }

            if (knockback.lengthSquared() <= 0.0001) {
                knockback = new Vector(1, 0, 0);
            }

            knockback.normalize().multiply(kbHorizontal).setY(kbUp);
            target.setVelocity(target.getVelocity().add(knockback));
        }

        trident.remove();
    }

    private Player getOwner(Trident trident) {
        if (!trident.hasMetadata(META_HYDRO_TRIDENT_OWNER)) return null;
        try {
            String raw = trident.getMetadata(META_HYDRO_TRIDENT_OWNER).get(0).asString();
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== ABILITY: LAUNCH ====================

    public void useLaunch(Player player) {
        double launchPower = plugin.getItemsConfig().getHydroTrojzabLaunchPower();

        Vector direction = player.getLocation().getDirection().normalize().multiply(launchPower);
        if (direction.getY() < 0.2) {
            direction.setY(0.2);
        }
        direction.setY(direction.getY() + 0.35);

        player.setVelocity(direction);
        player.getWorld().playSound(player.getLocation(),
                Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.3f, 1.0f);
        player.getWorld().spawnParticle(Particle.WATER_SPLASH,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);

        setLaunchCooldown(player);
    }

    // ==================== CLEANUP ====================

    public void cleanupPlayer(Player player) {
        restoreGhostArrow(player);
        stopLaunchActionBarDisplay(player);
    }

    public void cleanup() {
        if (cleanupTask != null) cleanupTask.cancel();
        if (ghostMonitorTask != null) ghostMonitorTask.cancel();

        for (UUID uuid : new HashSet<>(ghostArrows.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreGhostArrow(player);
            } else {
                ghostArrows.remove(uuid);
            }
        }

        for (BukkitTask task : launchActionBarTasks.values()) {
            task.cancel();
        }

        shotCooldowns.clear();
        launchCooldowns.clear();
        ghostArrows.clear();
        launchActionBarTasks.clear();
    }

    // ==================== INNER CLASS ====================

    private static class GhostArrowState {
        private final int slot;
        private final ItemStack originalItem;

        private GhostArrowState(int slot, ItemStack originalItem) {
            this.slot = slot;
            this.originalItem = originalItem;
        }
    }
}
