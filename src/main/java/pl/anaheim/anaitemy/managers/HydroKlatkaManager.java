package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> chunkCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveHydroKlatka> activeKlatki = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> cooldownTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    
    // Materiały klatki
    private static final Material SHELL = Material.BLUE_GLAZED_TERRACOTTA;
    private static final Material INNER = Material.LIGHT_BLUE_CONCRETE;
    private static final Material INNER_POWDER = Material.LIGHT_BLUE_CONCRETE_POWDER;
    
    // Zablokowane przedmioty w klatce
    private static final Set<Material> BLOCKED_ITEMS = Set.of(
            Material.ENDER_PEARL, Material.EGG, Material.FIRE_CHARGE,
            Material.WATER_BUCKET, Material.LAVA_BUCKET
    );

    public HydroKlatkaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                
                // Cleanup player cooldowns
                playerCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
                
                // Cleanup chunk cooldowns  
                chunkCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
                
                // Update boss bars
                for (ActiveHydroKlatka klatka : activeKlatki.values()) {
                    updateBossBar(klatka);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public boolean isPlayerOnCooldown(Player player) {
        Long cooldownEnd = playerCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    public long getPlayerCooldownRemaining(Player player) {
        Long cooldownEnd = playerCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return 0;
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        long cooldownSeconds = plugin.getConfig().getLong("hydroklatka.cooldown", 180);
        playerCooldowns.put(player.getUniqueId(), 
                System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    public boolean isChunkBlocked(Location location) {
        String chunkKey = getChunkKey(location);
        Long cooldownEnd = chunkCooldowns.get(chunkKey);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    private void setChunkCooldown(Location center) {
        int radius = plugin.getConfig().getInt("hydroklatka.radius", 7);
        long cooldownSeconds = plugin.getConfig().getLong("hydroklatka.cooldown", 180);
        long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000);
        
        World world = center.getWorld();
        int centerX = center.getBlockX() >> 4;
        int centerZ = center.getBlockZ() >> 4;
        int chunkRadius = (radius >> 4) + 1;
        
        for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
            for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                String chunkKey = world.getName() + ":" + x + ":" + z;
                chunkCooldowns.put(chunkKey, cooldownEnd);
            }
        }
    }

    private String getChunkKey(Location location) {
        return location.getWorld().getName() + ":" + 
               (location.getBlockX() >> 4) + ":" + 
               (location.getBlockZ() >> 4);
    }

    public void createKlatka(Location center, Player creator) {
        int radius = plugin.getConfig().getInt("hydroklatka.radius", 7);
        int duration = plugin.getConfig().getInt("hydroklatka.duration", 15);
        
        ActiveHydroKlatka klatka = new ActiveHydroKlatka(center, radius, duration, creator.getUniqueId());
        activeKlatki.put(klatka.getId(), klatka);

        setChunkCooldown(center);
        trapPlayers(klatka);
        createBossBar(klatka);
        playCustomSound(center);
        startBuildAnimation(klatka);
        scheduleRemoval(klatka);
    }

    private void trapPlayers(ActiveHydroKlatka klatka) {
        Location center = klatka.getCenter();
        World world = center.getWorld();
        
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(center) <= klatka.getRadius()) {
                if (!isInBlockedRegion(player.getLocation())) {
                    klatka.addTrappedPlayer(player.getUniqueId());
                }
            }
        }
    }

    private void createBossBar(ActiveHydroKlatka klatka) {
        for (UUID playerId : klatka.getTrappedPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                
                BossBar bossBar = BossBar.bossBar(
                        LegacyComponentSerializer.legacyAmpersand().deserialize("&bHydroklatka " + klatka.getRemainingSeconds() + "s"),
                        1.0f,
                        BossBar.Color.BLUE,
                        BossBar.Overlay.PROGRESS
                );
                
                player.showBossBar(bossBar);
                playerBossBars.put(playerId, bossBar);
            }
        }
    }

    private void updateBossBar(ActiveHydroKlatka klatka) {
        int remaining = klatka.getRemainingSeconds();
        float progress = Math.max(0.0f, (float) remaining / klatka.getOriginalDuration());
        
        for (UUID playerId : klatka.getTrappedPlayers()) {
            BossBar bossBar = playerBossBars.get(playerId);
            if (bossBar != null) {
                bossBar.name(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&bHydroklatka " + remaining + "s"));
                bossBar.progress(progress);
            }
        }
    }

    private void playCustomSound(Location center) {
        World world = center.getWorld();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(center) <= 50) {
                player.playSound(center, "custom.hydroklatka", 
                        SoundCategory.MASTER, 3.0f, 1.0f);
            }
        }
    }

    private void startBuildAnimation(ActiveHydroKlatka klatka) {
        Location center = klatka.getCenter();
        int radius = klatka.getRadius();
        int animationDuration = plugin.getConfig().getInt("hydroklatka.animation-duration", 60);
        
        int maxY = center.getBlockY() + radius;
        int minY = center.getBlockY() - radius;
        int totalLayers = maxY - minY + 1;
        int ticksPerLayer = Math.max(1, animationDuration / totalLayers);
        
        new BukkitRunnable() {
            int currentY = maxY;
            
            @Override
            public void run() {
                if (!activeKlatki.containsKey(klatka.getId())) {
                    cancel();
                    return;
                }
                
                if (currentY < minY) {
                    klatka.setAnimationComplete(true);
                    cancel();
                    return;
                }
                
                buildLayer(klatka, currentY);
                currentY--;
            }
        }.runTaskTimer(plugin, 0L, ticksPerLayer);
    }

    private void buildLayer(ActiveHydroKlatka klatka, int y) {
        Location center = klatka.getCenter();
        int radius = klatka.getRadius();
        World world = center.getWorld();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location blockLoc = new Location(world, 
                        center.getBlockX() + x, y, center.getBlockZ() + z);
                
                double distance = blockLoc.distance(center);
                if (distance > radius) continue;
                
                if (isInBlockedRegion(blockLoc)) continue;
                
                Block block = blockLoc.getBlock();
                Material originalType = block.getType();
                
                klatka.addOriginalBlock(blockLoc, block.getBlockData());
                
                if (distance > radius - 1.0) {
                    block.setType(SHELL);
                } else if (originalType != Material.AIR && 
                          originalType != Material.CAVE_AIR && 
                          originalType != Material.VOID_AIR) {
                    Material replacement = mapToWaterBlock(originalType);
                    block.setType(replacement);
                }
            }
        }
    }

    private Material mapToWaterBlock(Material original) {
        String name = original.name();
        
        if (original == Material.DIRT || original == Material.GRASS_BLOCK || 
            original == Material.COARSE_DIRT || original == Material.ROOTED_DIRT) {
            return Material.LIGHT_GRAY_TERRACOTTA;
        }
        
        if (original == Material.ANDESITE || original == Material.DIORITE || 
            original == Material.POLISHED_ANDESITE || original == Material.POLISHED_DIORITE) {
            return Material.SEA_LANTERN;
        }
        
        if (original == Material.STONE) {
            return Material.PRISMARINE;
        }
        
        if (name.contains("BRICKS")) {
            return Material.PRISMARINE_BRICKS;
        }
        
        if (original == Material.SPRUCE_LOG || original == Material.SPRUCE_WOOD || 
            original == Material.STRIPPED_SPRUCE_LOG) {
            return Material.BRAIN_CORAL_BLOCK;
        }
        
        if (name.contains("LEAVES")) {
            return Material.PURPLE_TERRACOTTA;
        }
        
        if (original == Material.SAND || original == Material.RED_SAND || 
            original == Material.GRAVEL) {
            return INNER_POWDER;
        }
        
        return INNER;
    }

    private void scheduleRemoval(ActiveHydroKlatka klatka) {
        int duration = klatka.getOriginalDuration();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                removeKlatka(klatka);
            }
        }.runTaskLater(plugin, 20L * duration);
    }

    private void removeKlatka(ActiveHydroKlatka klatka) {
        if (!activeKlatki.containsKey(klatka.getId())) return;
        
        klatka.getOriginalBlocks().forEach((location, blockData) -> {
            if (!klatka.wasBlockDestroyed(location)) {
                location.getBlock().setBlockData(blockData);
            }
        });
        
        for (UUID playerId : klatka.getTrappedPlayers()) {
            BossBar bossBar = playerBossBars.remove(playerId);
            if (bossBar != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.hideBossBar(bossBar);
                }
            }
        }
        
        Location center = klatka.getCenter();
        World world = center.getWorld();
        
        world.playSound(center, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 1.2f);
        world.spawnParticle(Particle.WATER_SPLASH, center, 150, 4, 4, 4, 0.5);
        world.spawnParticle(Particle.CLOUD, center, 40, 3, 3, 3, 0.1);
        
        activeKlatki.remove(klatka.getId());
    }

    public void startCooldownDisplay(Player player) {
        if (!isPlayerOnCooldown(player)) return;
        
        stopCooldownDisplay(player);
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                long remaining = getPlayerCooldownRemaining(player);
                if (remaining <= 0) {
                    resetItemCooldown(player);
                    cancel();
                    return;
                }
                
                String message = "&bHydro Klatka: &f" + remaining + "s";
                player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
                
                updateItemCooldown(player, remaining);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        
        cooldownTasks.put(player.getUniqueId(), task);
    }

    public void stopCooldownDisplay(Player player) {
        BukkitTask task = cooldownTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        resetItemCooldown(player);
    }

    private void updateItemCooldown(Player player, long remainingSeconds) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!pl.anaheim.anaitemy.items.HydroKlatka.isHydroKlatka(item)) return;
        
        long totalCooldown = plugin.getConfig().getLong("hydroklatka.cooldown", 180);
        int maxDurability = item.getType().getMaxDurability();
        
        if (maxDurability > 0) {
            int damage = (int) ((totalCooldown - remainingSeconds) * maxDurability / totalCooldown);
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable) {
                ((Damageable) meta).setDamage(Math.min(damage, maxDurability - 1));
                item.setItemMeta(meta);
            }
        }
    }

    private void resetItemCooldown(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!pl.anaheim.anaitemy.items.HydroKlatka.isHydroKlatka(item)) return;
        
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(0);
            item.setItemMeta(meta);
        }
    }

    public void sendCooldownMessage(Player player) {
        long remaining = getPlayerCooldownRemaining(player);
        sendMessage(player, "&cNie możesz używać tego tak szybko! Pozostało: " + remaining + "s");
        
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 
                SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    public boolean isInBlockedRegion(Location location) {
        // TODO: WorldGuard integration
        return false;
    }

    public boolean isShellBlock(Location location) {
        return location.getBlock().getType() == SHELL && 
               activeKlatki.values().stream().anyMatch(k -> k.hasOriginalBlock(location));
    }

    public boolean isKlatkaBlock(Location location) {
        return activeKlatki.values().stream().anyMatch(k -> k.hasOriginalBlock(location));
    }

    public boolean canUseItem(Player player, Material material) {
        for (ActiveHydroKlatka klatka : activeKlatki.values()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                return !BLOCKED_ITEMS.contains(material);
            }
        }
        return true;
    }

    public boolean canBreakBlock(Player player, Location location) {
        // TODO: WorldGuard integration
        return true;
    }

    public void markBlockAsDestroyed(Location location) {
        activeKlatki.values().forEach(k -> k.markBlockDestroyed(location));
    }

    public Collection<ActiveHydroKlatka> getActiveKlatki() {
        return new ArrayList<>(activeKlatki.values());
    }

    public void cleanup() {
        for (ActiveHydroKlatka klatka : new ArrayList<>(activeKlatki.values())) {
            removeKlatka(klatka);
        }
        
        cooldownTasks.values().forEach(BukkitTask::cancel);
        cooldownTasks.clear();
        playerCooldowns.clear();
        chunkCooldowns.clear();
    }
}
