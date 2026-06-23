package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
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

    private static final Material SHELL = Material.BLUE_GLAZED_TERRACOTTA;
    private static final Material INNER = Material.LIGHT_BLUE_CONCRETE;
    private static final Material INNER_POWDER = Material.LIGHT_BLUE_CONCRETE_POWDER;

    public HydroKlatkaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // ✅ Cleanup cooldownów (nie usuwamy przy wylogowaniu, tylko gdy upłynął czas)
                playerCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
                chunkCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());

                // ✅ Cleanup expired klatek automatycznie
                for (ActiveHydroKlatka klatka : new ArrayList<>(activeKlatki.values())) {
                    if (klatka.isExpired()) {
                        removeKlatka(klatka);
                    } else {
                        updateBossBar(klatka);
                        checkTrappedPlayers(klatka);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==================== COOLDOWN METHODS ====================

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
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getHydroKlatkaCooldown();
        long cooldownMillis = cooldownSeconds * 1000;

        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMillis);

        int cooldownTicks = (int) (cooldownSeconds * 20);
        player.setCooldown(Material.BLAZE_ROD, cooldownTicks);

        startCooldownDisplay(player);
    }

    public void resetCooldown(Player player) {
        playerCooldowns.remove(player.getUniqueId());
    }

    public boolean isChunkBlocked(Location location) {
        String chunkKey = getChunkKey(location);
        Long cooldownEnd = chunkCooldowns.get(chunkKey);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    private void setChunkCooldown(Location center) {
        ItemsConfig config = plugin.getItemsConfig();
        int radius = config.getHydroKlatkaRadius();
        long cooldownSeconds = config.getHydroKlatkaCooldown();
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
        return location.getWorld().getName() + ":"
                + (location.getBlockX() >> 4) + ":"
                + (location.getBlockZ() >> 4);
    }

    // ==================== COOLDOWN DISPLAY (ACTION BAR) ====================

    public void startCooldownDisplay(Player player) {
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
                    cancel();
                    return;
                }

                ItemsConfig config = plugin.getItemsConfig();
                String message = config.getHydroKlatkaActionBarFormat()
                        .replace("{time}", String.valueOf(remaining));

                player.sendActionBar(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(message)
                );
            }
        }.runTaskTimer(plugin, 0L, 20L);

        cooldownTasks.put(player.getUniqueId(), task);
    }

    public void stopCooldownDisplay(Player player) {
        BukkitTask task = cooldownTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
    }

    // ==================== MESSAGES ====================

    public void sendCooldownMessage(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long remaining = getPlayerCooldownRemaining(player);
        String message = config.getHydroKlatkaMessageCooldown()
                .replace("{time}", String.valueOf(remaining));
        sendMessage(player, message);
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize(message)
        );
    }

    // ==================== KLATKA CREATION ====================

    public void createKlatka(Location center, Player creator) {
        ItemsConfig config = plugin.getItemsConfig();
        int radius = config.getHydroKlatkaRadius();
        int duration = config.getHydroKlatkaDuration();

        ActiveHydroKlatka klatka = new ActiveHydroKlatka(center, radius, duration, creator.getUniqueId());
        activeKlatki.put(klatka.getId(), klatka);

        setChunkCooldown(center);
        trapPlayers(klatka);
        createBossBar(klatka);
        playCreationSounds(center);
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

    private void checkTrappedPlayers(ActiveHydroKlatka klatka) {
        Location center = klatka.getCenter();

        for (UUID playerId : new ArrayList<>(klatka.getTrappedPlayers())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            Location loc = player.getLocation();
            Block feet = loc.getBlock();
            Block head = loc.clone().add(0, 1, 0).getBlock();

            boolean feetInWall = feet.getType() == SHELL && klatka.hasOriginalBlock(feet.getLocation());
            boolean headInWall = head.getType() == SHELL && klatka.hasOriginalBlock(head.getLocation());

            if (feetInWall || headInWall) {
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(loc.getYaw());
                teleportLoc.setPitch(loc.getPitch());
                player.teleport(teleportLoc);
            }
        }
    }

    // ==================== BOSS BAR ====================

    private void createBossBar(ActiveHydroKlatka klatka) {
        for (UUID playerId : klatka.getTrappedPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            BossBar bossBar = BossBar.bossBar(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&bHydroklatka"),
                    1.0f,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.PROGRESS
            );

            player.showBossBar(bossBar);
            playerBossBars.put(playerId, bossBar);
        }
    }

    private void updateBossBar(ActiveHydroKlatka klatka) {
        int remaining = klatka.getRemainingSeconds();
        int totalDuration = klatka.getOriginalDuration();

        float progress = totalDuration > 0
                ? Math.max(0.0f, Math.min(1.0f, (float) remaining / totalDuration))
                : 0.0f;

        for (UUID playerId : klatka.getTrappedPlayers()) {
            BossBar bossBar = playerBossBars.get(playerId);
            if (bossBar == null) continue;

            bossBar.progress(progress);
        }
    }

    // ==================== SOUNDS ====================

    private void playCreationSounds(Location center) {
        World world = center.getWorld();
        ItemsConfig config = plugin.getItemsConfig();

        try {
            Sound explodeSound = Sound.valueOf(config.getHydroKlatkaExplodeSound());
            world.playSound(center, explodeSound, SoundCategory.BLOCKS,
                    config.getHydroKlatkaExplodeVolume(),
                    config.getHydroKlatkaExplodePitch());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy dźwięk wybuchu: " + config.getHydroKlatkaExplodeSound());
        }

        // ✅ Custom dźwięk z większą głośnością (10.0 zamiast 3.0)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String customSound = config.getHydroKlatkaCustomSound();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distance(center) <= 100) { // Zasięg zwiększony do 100 bloków
                    player.playSound(center, customSound, SoundCategory.MASTER,
                            10.0f, // ✅ Głośność x3 większa
                            config.getHydroKlatkaCustomSoundPitch());
                }
            }
        }, 1L);
    }

    // ==================== BUILD ANIMATION ====================

    private void startBuildAnimation(ActiveHydroKlatka klatka) {
        ItemsConfig config = plugin.getItemsConfig();
        Location center = klatka.getCenter();
        int radius = klatka.getRadius();
        int animationDuration = config.getHydroKlatkaAnimationDuration();

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
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location blockLoc = new Location(world,
                        center.getBlockX() + x, y, center.getBlockZ() + z);

                double distance = blockLoc.distance(center);
                if (distance > radius) continue;

                if (plugin.getWorldGuardManager().isInBlockedRegion(blockLoc, blockedRegions)) {
                    continue;
                }

                Block block = blockLoc.getBlock();
                Material originalType = block.getType();

                klatka.addOriginalBlock(blockLoc, block.getBlockData());

                if (distance > radius - 1.0) {
                    block.setType(SHELL);
                } else if (originalType != Material.AIR &&
                        originalType != Material.CAVE_AIR &&
                        originalType != Material.VOID_AIR) {
                    block.setType(mapToWaterBlock(originalType));
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

    // ==================== KLATKA REMOVAL ====================

    private void scheduleRemoval(ActiveHydroKlatka klatka) {
        new BukkitRunnable() {
            @Override
            public void run() {
                removeKlatka(klatka);
            }
        }.runTaskLater(plugin, 20L * klatka.getOriginalDuration());
    }

    public void removeKlatka(ActiveHydroKlatka klatka) {
        if (!activeKlatki.containsKey(klatka.getId())) return;

        klatka.getOriginalBlocks().forEach((location, blockData) -> {
            if (!klatka.wasBlockDestroyed(location)) {
                location.getBlock().setBlockData(blockData);
            }
        });

        // ✅ Cleanup BossBar dla wszystkich graczy (także offline)
        for (UUID playerId : klatka.getTrappedPlayers()) {
            BossBar bossBar = playerBossBars.remove(playerId);
            if (bossBar != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.hideBossBar(bossBar);
                }
            }
        }

        // ✅ Cleanup BossBar dla graczy offline
        for (UUID playerId : klatka.getOfflinePlayers()) {
            playerBossBars.remove(playerId);
        }

        Location center = klatka.getCenter();
        World world = center.getWorld();
        ItemsConfig config = plugin.getItemsConfig();

        try {
            Sound removeSound = Sound.valueOf(config.getHydroKlatkaRemoveSound());
            world.playSound(center, removeSound, SoundCategory.BLOCKS,
                    config.getHydroKlatkaRemoveVolume(),
                    config.getHydroKlatkaRemovePitch());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy dźwięk usunięcia: " + config.getHydroKlatkaRemoveSound());
        }

        world.spawnParticle(Particle.WATER_SPLASH, center, 150, 4, 4, 4, 0.5);
        world.spawnParticle(Particle.CLOUD, center, 40, 3, 3, 3, 0.1);

        activeKlatki.remove(klatka.getId());
    }

    // ==================== BLOCK PROTECTION ====================

    public boolean isInBlockedRegion(Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        return plugin.getWorldGuardManager().isInBlockedRegion(location, blockedRegions);
    }

    public boolean isShellBlock(Location location) {
        if (location.getBlock().getType() != SHELL) return false;
        return activeKlatki.values().stream().anyMatch(k -> k.hasOriginalBlock(location));
    }

    public boolean isKlatkaBlock(Location location) {
        return activeKlatki.values().stream().anyMatch(k -> k.hasOriginalBlock(location));
    }

    public boolean canUseItem(Player player, Material material) {
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedItems = config.getHydroKlatkaBlockedItems();

        Set<Material> blockedMaterials = new HashSet<>();
        for (String itemName : blockedItems) {
            try {
                blockedMaterials.add(Material.valueOf(itemName));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Nieprawidłowy materiał w blocked-items: " + itemName);
            }
        }

        for (ActiveHydroKlatka klatka : activeKlatki.values()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                return !blockedMaterials.contains(material);
            }
        }
        return true;
    }

    // ✅ POPRAWIONE - sprawdzanie flag WorldGuard (pvp + block-break)
    public boolean canBreakBlock(Player player, Location location) {
        return plugin.getWorldGuardManager().canBreakBlock(player, location);
    }

    public void markBlockAsDestroyed(Location location) {
        activeKlatki.values().forEach(k -> k.markBlockDestroyed(location));
    }

    public Collection<ActiveHydroKlatka> getActiveKlatki() {
        return new ArrayList<>(activeKlatki.values());
    }

    public ActiveHydroKlatka getKlatkaForPlayer(Player player) {
        for (ActiveHydroKlatka klatka : activeKlatki.values()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                return klatka;
            }
        }
        return null;
    }

    // ✅ Usuń gracza z klatki przy śmierci
    public void removePlayerFromKlatka(Player player) {
        for (ActiveHydroKlatka klatka : activeKlatki.values()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                klatka.removeTrappedPlayer(player.getUniqueId());
                
                // Ukryj BossBar
                BossBar bossBar = playerBossBars.remove(player.getUniqueId());
                if (bossBar != null) {
                    player.hideBossBar(bossBar);
                }
                return;
            }
        }
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (ActiveHydroKlatka klatka : new ArrayList<>(activeKlatki.values())) {
            removeKlatka(klatka);
        }

        cooldownTasks.values().forEach(BukkitTask::cancel);
        cooldownTasks.clear();
        
        // ✅ NIE usuwamy playerCooldowns - mają przetrwać restart
        // playerCooldowns.clear();
        
        chunkCooldowns.clear();
    }
}
