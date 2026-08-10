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

    private static final Set<Material> PROTECTED_BLOCKS = Set.of(
            Material.BEDROCK,
            Material.BEACON
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
                playerCooldowns.entrySet().removeIf(e -> now >= e.getValue());
                chunkCooldowns.entrySet().removeIf(e -> now >= e.getValue());

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

    // ==================== COOLDOWN ====================

    public boolean isPlayerOnCooldown(Player player) {
        Long end = playerCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getPlayerCooldownRemaining(Player player) {
        Long end = playerCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long seconds = config.getHydroKlatkaCooldown();
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
        player.setCooldown(Material.BLAZE_ROD, (int) (seconds * 20));
        startCooldownDisplay(player);
    }

    public void setExternalCooldown(Player player, long seconds) {
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
        player.setCooldown(Material.BLAZE_ROD, (int) (seconds * 20));
        startCooldownDisplay(player);
    }

    public void resetCooldown(Player player) {
        playerCooldowns.remove(player.getUniqueId());
    }

    public boolean isChunkBlocked(Location location) {
        Long end = chunkCooldowns.get(getChunkKey(location));
        return end != null && System.currentTimeMillis() < end;
    }

    private void setChunkCooldown(Location center) {
        ItemsConfig config = plugin.getItemsConfig();
        int radius = config.getHydroKlatkaRadius();
        long seconds = config.getHydroKlatkaCooldown();
        long end = System.currentTimeMillis() + seconds * 1000;
        String world = center.getWorld().getName();
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int cr = (radius >> 4) + 1;

        for (int x = cx - cr; x <= cx + cr; x++)
            for (int z = cz - cr; z <= cz + cr; z++)
                chunkCooldowns.put(world + ":" + x + ":" + z, end);
    }

    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private String formatTime(long s) {
        if (s < 60) return s + "s";
        return s / 60 + "m" + String.format("%02d", s % 60) + "s";
    }

    // ==================== COOLDOWN DISPLAY ====================

    public void startCooldownDisplay(Player player) {
        stopCooldownDisplay(player);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel(); cooldownTasks.remove(player.getUniqueId());
                    plugin.getActionBarManager().removeActionBar(player, "hydroklatka");
                    return;
                }
                long rem = getPlayerCooldownRemaining(player);
                if (rem <= 0) {
                    cancel(); cooldownTasks.remove(player.getUniqueId());
                    plugin.getActionBarManager().removeActionBar(player, "hydroklatka");
                    return;
                }
                plugin.getActionBarManager().setActionBar(player, "hydroklatka",
                        plugin.getItemsConfig().getHydroKlatkaActionBarFormat()
                                .replace("{time}", formatTime(rem)));
            }
        }.runTaskTimer(plugin, 0L, 20L);
        cooldownTasks.put(player.getUniqueId(), task);
    }

    public void stopCooldownDisplay(Player player) {
        BukkitTask task = cooldownTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        plugin.getActionBarManager().removeActionBar(player, "hydroklatka");
    }

    // ==================== MESSAGES ====================

    public void sendMessage(Player player, String message) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    // ==================== KLATKA CREATION ====================

    public void createKlatka(Location center, Player creator) {
        ItemsConfig config = plugin.getItemsConfig();
        int radius = config.getHydroKlatkaRadius();
        int duration = config.getHydroKlatkaDuration();

        ActiveHydroKlatka klatka = new ActiveHydroKlatka(center, radius, duration, creator.getUniqueId());

        Set<Location> shellPositions = calculateShellPositions(center, radius, center.getWorld());
        klatka.setPlannedShellLocations(shellPositions);

        activeKlatki.put(klatka.getId(), klatka);

        setChunkCooldown(center);
        trapPlayers(klatka);
        createBossBar(klatka);
        playCreationSounds(center);

        // ✅ BEZ barrier bloków — listener softwarowo blokuje ruch trapped graczy
        startBuildAnimation(klatka);
        scheduleRemoval(klatka);
    }

    private Set<Location> calculateShellPositions(Location center, int radius, World world) {
        Set<Location> positions = new HashSet<>();
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    Location loc = new Location(world,
                            center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    double dist = loc.clone().add(0.5, 0.5, 0.5).distance(center);
                    if (dist > radius - 1.0 && dist <= radius) positions.add(loc);
                }
        return positions;
    }

    // ==================== TRAP PLAYERS ====================

    private void trapPlayers(ActiveHydroKlatka klatka) {
        Location center = klatka.getCenter();
        Player creator = Bukkit.getPlayer(klatka.getCreatorId());
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blocked = config.getHydroKlatkaBlockedRegions();

        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distance(center) <= klatka.getRadius()) {
                if (plugin.getWorldGuardManager().isInBlockedRegion(p.getLocation(), blocked))
                    continue;
                klatka.addTrappedPlayer(p.getUniqueId());
                if (config.isHydroKlatkaTagPlayers()
                        && plugin.getCombatIntegrationManager().isEnabled()
                        && plugin.getCombatIntegrationManager().hasTagPlayerMethod())
                    plugin.getCombatIntegrationManager().tagPlayer(p, creator);
            }
        }
    }

    private void checkTrappedPlayers(ActiveHydroKlatka klatka) {
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blocked = config.getHydroKlatkaBlockedRegions();

        for (UUID id : new ArrayList<>(klatka.getTrappedPlayers())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (plugin.getWorldGuardManager().isInBlockedRegion(p.getLocation(), blocked)) {
                klatka.removeTrappedPlayer(id);
                BossBar bb = playerBossBars.remove(id);
                if (bb != null) p.hideBossBar(bb);
            }
        }
    }

    // ==================== BOSS BAR ====================

    private void createBossBar(ActiveHydroKlatka klatka) {
        for (UUID id : klatka.getTrappedPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            BossBar bb = BossBar.bossBar(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&bHydroklatka"),
                    1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            p.showBossBar(bb);
            playerBossBars.put(id, bb);
        }
    }

    private void updateBossBar(ActiveHydroKlatka klatka) {
        int rem = klatka.getRemainingSeconds();
        int total = klatka.getOriginalDuration();
        float prog = total > 0 ? Math.max(0f, Math.min(1f, (float) rem / total)) : 0f;
        for (UUID id : klatka.getTrappedPlayers()) {
            BossBar bb = playerBossBars.get(id);
            if (bb != null) bb.progress(prog);
        }
    }

    // ==================== SOUNDS ====================

    private void playCreationSounds(Location center) {
        World world = center.getWorld();
        ItemsConfig config = plugin.getItemsConfig();

        try {
            Sound s = Sound.valueOf(config.getHydroKlatkaExplodeSound());
            world.playSound(center, s, SoundCategory.BLOCKS,
                    config.getHydroKlatkaExplodeVolume(), config.getHydroKlatkaExplodePitch());
        } catch (IllegalArgumentException ignored) {}

        try {
            Sound s = Sound.valueOf(config.getHydroKlatkaSplashSound());
            world.playSound(center, s, SoundCategory.BLOCKS,
                    config.getHydroKlatkaSplashVolume(), config.getHydroKlatkaSplashPitch());
        } catch (IllegalArgumentException ignored) {}

        int animDur = config.getHydroKlatkaAnimationDuration();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Sound s = Sound.valueOf(config.getHydroKlatkaAmbientSound());
                for (Player p : world.getPlayers())
                    if (p.getLocation().distance(center) <= 50)
                        p.playSound(center, s, SoundCategory.BLOCKS,
                                config.getHydroKlatkaAmbientVolume(),
                                config.getHydroKlatkaAmbientPitch());
            } catch (IllegalArgumentException ignored) {}
        }, animDur - 10L);
    }

    // ==================== BUILD ANIMATION ====================

    private void startBuildAnimation(ActiveHydroKlatka klatka) {
        ItemsConfig config = plugin.getItemsConfig();
        Location center = klatka.getCenter();
        int radius = klatka.getRadius();
        int animDur = config.getHydroKlatkaAnimationDuration();
        int maxY = center.getBlockY() + radius;
        int minY = center.getBlockY() - radius;
        int layers = maxY - minY + 1;
        int ticksPer = Math.max(1, animDur / layers);

        new BukkitRunnable() {
            int y = maxY;
            @Override
            public void run() {
                if (!activeKlatki.containsKey(klatka.getId())) { cancel(); return; }
                if (y < minY) { klatka.setAnimationComplete(true); cancel(); return; }
                buildLayer(klatka, y);
                y--;
            }
        }.runTaskTimer(plugin, 0L, ticksPer);
    }

    private void buildLayer(ActiveHydroKlatka klatka, int y) {
        Location center = klatka.getCenter();
        int radius = klatka.getRadius();
        World world = center.getWorld();
        List<String> blocked = plugin.getItemsConfig().getHydroKlatkaBlockedRegions();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location loc = new Location(world, center.getBlockX() + x, y, center.getBlockZ() + z);
                double dist = loc.clone().add(0.5, 0.5, 0.5).distance(center);
                if (dist > radius) continue;
                if (plugin.getWorldGuardManager().isInBlockedRegion(loc, blocked)) continue;

                Block block = loc.getBlock();
                Material type = block.getType();
                if (PROTECTED_BLOCKS.contains(type)) continue;

                if (!klatka.hasOriginalBlock(loc))
                    klatka.addOriginalBlock(loc, block.getBlockData());

                if (dist > radius - 1.0) {
                    block.setType(SHELL);
                } else if (type != Material.AIR && type != Material.CAVE_AIR
                        && type != Material.VOID_AIR) {
                    block.setType(mapToWaterBlock(type));
                }
            }
        }
    }

    private Material mapToWaterBlock(Material m) {
        String name = m.name();
        if (m == Material.BEDROCK) return Material.BEDROCK;
        if (m == Material.DIRT || m == Material.GRASS_BLOCK
                || m == Material.COARSE_DIRT || m == Material.ROOTED_DIRT)
            return Material.LIGHT_GRAY_TERRACOTTA;
        if (m == Material.ANDESITE || m == Material.DIORITE
                || m == Material.POLISHED_ANDESITE || m == Material.POLISHED_DIORITE)
            return Material.SEA_LANTERN;
        if (m == Material.STONE) return Material.PRISMARINE;
        if (name.contains("BRICKS")) return Material.PRISMARINE_BRICKS;
        if (m == Material.SPRUCE_LOG || m == Material.SPRUCE_WOOD
                || m == Material.STRIPPED_SPRUCE_LOG)
            return Material.BRAIN_CORAL_BLOCK;
        if (name.contains("LEAVES")) return Material.PURPLE_TERRACOTTA;
        if (m == Material.SAND || m == Material.RED_SAND || m == Material.GRAVEL)
            return INNER_POWDER;
        return INNER;
    }

    // ==================== REMOVAL ====================

    private void scheduleRemoval(ActiveHydroKlatka klatka) {
        new BukkitRunnable() {
            @Override
            public void run() {
                removeKlatka(klatka);
                Bukkit.getScheduler().runTaskLater(plugin, () -> chunkCooldowns.clear(), 100L);
            }
        }.runTaskLater(plugin, 20L * klatka.getOriginalDuration());
    }

    public void removeKlatka(ActiveHydroKlatka klatka) {
        if (!activeKlatki.containsKey(klatka.getId())) return;

        klatka.getOriginalBlocks().forEach((loc, data) -> {
            if (!klatka.wasBlockDestroyed(loc))
                loc.getBlock().setBlockData(data);
        });

        for (UUID id : klatka.getTrappedPlayers()) {
            BossBar bb = playerBossBars.remove(id);
            if (bb != null) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) p.hideBossBar(bb);
            }
        }
        for (UUID id : klatka.getOfflinePlayers())
            playerBossBars.remove(id);

        Location center = klatka.getCenter();
        World world = center.getWorld();
        ItemsConfig config = plugin.getItemsConfig();

        try {
            Sound s = Sound.valueOf(config.getHydroKlatkaRemoveSound());
            world.playSound(center, s, SoundCategory.BLOCKS,
                    config.getHydroKlatkaRemoveVolume(), config.getHydroKlatkaRemovePitch());
        } catch (IllegalArgumentException ignored) {}

        world.spawnParticle(Particle.SPLASH, center, 150, 4, 4, 4, 0.5);
        world.spawnParticle(Particle.CLOUD, center, 40, 3, 3, 3, 0.1);

        activeKlatki.remove(klatka.getId());
    }

    // ==================== BLOCK PROTECTION ====================

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInBlockedRegion(location,
                plugin.getItemsConfig().getHydroKlatkaBlockedRegions());
    }

    public boolean isShellBlock(Location location) {
        if (location.getBlock().getType() != SHELL) return false;
        return activeKlatki.values().stream().anyMatch(k -> k.hasOriginalBlock(location));
    }

    public boolean isKlatkaBlock(Location location) {
        return activeKlatki.values().stream().anyMatch(k -> k.hasOriginalBlock(location));
    }

    public boolean isProtectedByCage(Location loc) {
        if (isKlatkaBlock(loc)) return true;
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.isInsideCage(loc)) return true;
            if (!k.isAnimationComplete() && k.isPlannedShellLocation(loc)) return true;
        }
        return false;
    }

    public boolean canUseItem(Player player, Material material) {
        Set<Material> blocked = new HashSet<>();
        for (String name : plugin.getItemsConfig().getHydroKlatkaBlockedItems()) {
            try { blocked.add(Material.valueOf(name)); }
            catch (IllegalArgumentException ignored) {}
        }
        for (ActiveHydroKlatka k : activeKlatki.values())
            if (k.isPlayerTrapped(player.getUniqueId()))
                return !blocked.contains(material);
        return true;
    }

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
        for (ActiveHydroKlatka k : activeKlatki.values())
            if (k.isPlayerTrapped(player.getUniqueId())) return k;
        return null;
    }

    public void removePlayerFromKlatka(Player player) {
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.isPlayerTrapped(player.getUniqueId())) {
                k.removeTrappedPlayer(player.getUniqueId());
                BossBar bb = playerBossBars.remove(player.getUniqueId());
                if (bb != null) player.hideBossBar(bb);
                return;
            }
        }
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (ActiveHydroKlatka k : new ArrayList<>(activeKlatki.values()))
            removeKlatka(k);
        cooldownTasks.values().forEach(BukkitTask::cancel);
        cooldownTasks.clear();
        chunkCooldowns.clear();
    }
}
