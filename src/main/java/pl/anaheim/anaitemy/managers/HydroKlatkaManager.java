// src/main/java/pl/anaheim/anaitemy/managers/HydroKlatkaManager.java
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

    private static final Set<Material> PROTECTED_BLOCKS = Set.of(
            Material.BEDROCK,
            Material.BEACON
    );

    /**
     * ✅ NOWE: Zbiór materiałów które klatka może ustawić.
     * Używany w removeKlatka() do sprawdzenia czy blok jest "nasz".
     */
    private Set<Material> cageMaterials = null;

    public HydroKlatkaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * ✅ NOWE: Zwraca zbiór materiałów które klatka może ustawić (shell + mapped bloki).
     * Używane do sprawdzenia czy blok jest "nasz" przed przywróceniem.
     */
    private Set<Material> getCageMaterials() {
        if (cageMaterials == null) {
            cageMaterials = new HashSet<>();
            cageMaterials.add(SHELL);

            // Dodaj wszystkie materiały docelowe z mapowania
            Map<Material, Material> mapping = plugin.getItemsConfig().getHydroKlatkaBlockMapping();
            cageMaterials.addAll(mapping.values());

            // Dodaj domyślny fallback
            cageMaterials.add(Material.LIGHT_BLUE_CONCRETE);
        }
        return cageMaterials;
    }

    /**
     * ✅ NOWE: Resetuje cache materiałów klatki (po reload configu).
     */
    public void resetCageMaterialsCache() {
        cageMaterials = null;
    }

    // ==================== CLEANUP TASK ====================

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
        if (player == null) return false;
        Long end = playerCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getPlayerCooldownRemaining(Player player) {
        if (player == null) return 0;
        Long end = playerCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        if (player == null) return;
        ItemsConfig config = plugin.getItemsConfig();
        long seconds = config.getHydroKlatkaCooldown();
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
        player.setCooldown(Material.BLAZE_ROD, (int) (seconds * 20));
        startCooldownDisplay(player);
    }

    public void setExternalCooldown(Player player, long seconds) {
        if (player == null) return;
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
        player.setCooldown(Material.BLAZE_ROD, (int) (seconds * 20));
        startCooldownDisplay(player);
    }

    public void resetCooldown(Player player) {
        if (player == null) return;
        playerCooldowns.remove(player.getUniqueId());
    }

    public boolean isChunkBlocked(Location location) {
        if (location == null) return false;
        Long end = chunkCooldowns.get(getChunkKey(location));
        return end != null && System.currentTimeMillis() < end;
    }

    private void setChunkCooldown(Location center) {
        if (center == null || center.getWorld() == null) return;
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

    private void clearChunkCooldownsForKlatka(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;

        int radius = klatka.getRadius();
        String world = center.getWorld().getName();
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int cr = (radius >> 4) + 1;

        for (int x = cx - cr; x <= cx + cr; x++)
            for (int z = cz - cr; z <= cz + cr; z++)
                chunkCooldowns.remove(world + ":" + x + ":" + z);
    }

    private String getChunkKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private String formatTime(long s) {
        if (s < 60) return s + "s";
        return s / 60 + "m" + String.format("%02d", s % 60) + "s";
    }

    // ==================== COOLDOWN DISPLAY ====================

    public void startCooldownDisplay(Player player) {
        if (player == null) return;
        stopCooldownDisplay(player);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    cooldownTasks.remove(player.getUniqueId());
                    plugin.getActionBarManager().removeActionBar(player, "hydroklatka");
                    return;
                }
                long rem = getPlayerCooldownRemaining(player);
                if (rem <= 0) {
                    cancel();
                    cooldownTasks.remove(player.getUniqueId());
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
        if (player == null) return;
        BukkitTask task = cooldownTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        plugin.getActionBarManager().removeActionBar(player, "hydroklatka");
    }

    // ==================== MESSAGES ====================

    public void sendMessage(Player player, String message) {
        if (player == null || message == null) return;
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    // ==================== KLATKA CREATION ====================

    public void createKlatka(Location center, Player creator) {
        if (center == null || creator == null || center.getWorld() == null) return;

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

        startBuildAnimation(klatka);
        scheduleRemoval(klatka);
    }

    private Set<Location> calculateShellPositions(Location center, int radius, World world) {
        Set<Location> positions = new HashSet<>();
        if (center == null || world == null) return positions;

        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    Location loc = new Location(world,
                            center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    double dist = loc.clone().add(0.5, 0.5, 0.5).distance(center);
                    if (dist > radius - 1.0 && dist <= radius) {
                        positions.add(loc);
                    }
                }
        return positions;
    }

    // ==================== TRAP PLAYERS ====================

    private void trapPlayers(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;

        Player creator = Bukkit.getPlayer(klatka.getCreatorId());
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blocked = config.getHydroKlatkaBlockedRegions();

        for (Player p : center.getWorld().getPlayers()) {
            if (p == null || !p.isOnline()) continue;
            Location pLoc = p.getLocation();
            if (pLoc == null || pLoc.getWorld() == null) continue;
            if (!pLoc.getWorld().equals(center.getWorld())) continue;

            if (pLoc.distance(center) <= klatka.getRadius()) {
                if (plugin.getWorldGuardManager().isInBlockedRegion(pLoc, blocked))
                    continue;
                klatka.addTrappedPlayer(p.getUniqueId());
                if (config.isHydroKlatkaTagPlayers()
                        && plugin.getCombatIntegrationManager().isEnabled()
                        && plugin.getCombatIntegrationManager().hasTagPlayerMethod()) {
                    plugin.getCombatIntegrationManager().tagPlayer(p, creator);
                }
            }
        }
    }

    private void checkTrappedPlayers(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blocked = config.getHydroKlatkaBlockedRegions();

        for (UUID id : new ArrayList<>(klatka.getTrappedPlayers())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            Location pLoc = p.getLocation();
            if (pLoc == null) continue;

            if (plugin.getWorldGuardManager().isInBlockedRegion(pLoc, blocked)) {
                klatka.removeTrappedPlayer(id);
                BossBar bb = playerBossBars.remove(id);
                if (bb != null) p.hideBossBar(bb);
            }
        }
    }

    // ==================== BOSS BAR ====================

    private void createBossBar(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
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
        if (klatka == null) return;
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
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;
        ItemsConfig config = plugin.getItemsConfig();

        try {
            Sound s = Sound.valueOf(config.getHydroKlatkaExplodeSound());
            world.playSound(center, s, SoundCategory.BLOCKS,
                    config.getHydroKlatkaExplodeVolume(), config.getHydroKlatkaExplodePitch());
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Sound s = Sound.valueOf(config.getHydroKlatkaSplashSound());
            world.playSound(center, s, SoundCategory.BLOCKS,
                    config.getHydroKlatkaSplashVolume(), config.getHydroKlatkaSplashPitch());
        } catch (IllegalArgumentException ignored) {
        }

        int animDur = config.getHydroKlatkaAnimationDuration();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Sound s = Sound.valueOf(config.getHydroKlatkaAmbientSound());
                for (Player p : world.getPlayers()) {
                    if (p != null && p.isOnline() && p.getLocation() != null
                            && p.getLocation().distance(center) <= 50) {
                        p.playSound(center, s, SoundCategory.BLOCKS,
                                config.getHydroKlatkaAmbientVolume(),
                                config.getHydroKlatkaAmbientPitch());
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }, Math.max(1L, animDur - 10L));
    }

    // ==================== BUILD ANIMATION ====================

    private void startBuildAnimation(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
        ItemsConfig config = plugin.getItemsConfig();
        Location center = klatka.getCenter();
        if (center == null) return;

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
                if (!activeKlatki.containsKey(klatka.getId())) {
                    cancel();
                    return;
                }
                if (y < minY) {
                    klatka.setAnimationComplete(true);
                    cancel();
                    return;
                }
                buildLayer(klatka, y);
                y--;
            }
        }.runTaskTimer(plugin, 0L, ticksPer);
    }

    private void buildLayer(ActiveHydroKlatka klatka, int y) {
        if (klatka == null) return;
        Location center = klatka.getCenter();
        if (center == null) return;

        int radius = klatka.getRadius();
        World world = center.getWorld();
        if (world == null) return;

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

                if (!klatka.hasOriginalBlock(loc)) {
                    klatka.addOriginalBlock(loc, block.getBlockData());
                }

                if (dist > radius - 1.0) {
                    block.setType(SHELL);
                } else if (type != Material.AIR && type != Material.CAVE_AIR
                        && type != Material.VOID_AIR) {
                    block.setType(mapToWaterBlock(type));
                }
            }
        }
    }

    /**
     * Mapuje oryginalny blok na blok wodny używając konfiguracji.
     */
    private Material mapToWaterBlock(Material original) {
        Map<Material, Material> mapping = plugin.getItemsConfig().getHydroKlatkaBlockMapping();
        Material mapped = mapping.get(original);

        if (mapped != null) {
            return mapped;
        }

        return Material.LIGHT_BLUE_CONCRETE;
    }

    // ==================== ✅ REMOVAL - POPRAWIONA ====================

    private void scheduleRemoval(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                removeKlatka(klatka);
            }
        }.runTaskLater(plugin, 20L * klatka.getOriginalDuration());
    }

    /**
     * ✅ POPRAWIONA: removeKlatka
     * 
     * Logika przywracania bloków:
     * 1. Jeśli blok został zniszczony przez gracza → NIE przywracaj
     * 2. Jeśli blok został postawiony podczas klatki → NIE przywracaj
     * 3. Jeśli aktualny blok NIE jest blokiem klatki (shell/mapped) → NIE przywracaj
     *    (ktoś/coś zmieniło blok na coś innego - np. Cudowna Latarnia postawiła BEACON)
     * 4. W pozostałych przypadkach → przywróć oryginalny blok
     */
    public void removeKlatka(ActiveHydroKlatka klatka) {
        if (klatka == null) return;
        if (!activeKlatki.containsKey(klatka.getId())) return;

        Set<Material> knownCageMaterials = getCageMaterials();

        // Przywróć oryginalne bloki
        klatka.getOriginalBlocks().forEach((key, data) -> {
            // 1. Blok zniszczony przez gracza - nie przywracaj
            if (klatka.wasBlockDestroyed(key)) return;

            // 2. Blok postawiony podczas klatki - nie przywracaj
            if (klatka.wasBlockPlacedDuringCage(key)) return;

            Location loc = ActiveHydroKlatka.keyToLocation(key);
            if (loc == null || data == null) return;

            // 3. Sprawdź AKTUALNY blok w świecie
            Block currentBlock = loc.getBlock();
            Material currentType = currentBlock.getType();

            // Jeśli aktualny blok NIE jest blokiem klatki (shell ani mapped material)
            // to znaczy że coś go zmieniło (np. plugin postawił BEACON)
            // → NIE przywracaj, zostaw co jest
            if (!knownCageMaterials.contains(currentType)) {
                return;
            }

            // 4. Aktualny blok jest blokiem klatki → przywróć oryginalny
            currentBlock.setBlockData(data);
        });

        // Usuń boss bary
        for (UUID id : klatka.getTrappedPlayers()) {
            BossBar bb = playerBossBars.remove(id);
            if (bb != null) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) p.hideBossBar(bb);
            }
        }
        for (UUID id : klatka.getOfflinePlayers()) {
            BossBar bb = playerBossBars.remove(id);
            if (bb != null) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) p.hideBossBar(bb);
            }
        }

        Location center = klatka.getCenter();
        World world = center != null ? center.getWorld() : null;
        ItemsConfig config = plugin.getItemsConfig();

        if (world != null && center != null) {
            try {
                Sound s = Sound.valueOf(config.getHydroKlatkaRemoveSound());
                world.playSound(center, s, SoundCategory.BLOCKS,
                        config.getHydroKlatkaRemoveVolume(), config.getHydroKlatkaRemovePitch());
            } catch (IllegalArgumentException ignored) {
            }

            world.spawnParticle(Particle.SPLASH, center, 150, 4, 4, 4, 0.5);
            world.spawnParticle(Particle.CLOUD, center, 40, 3, 3, 3, 0.1);
        }

        activeKlatki.remove(klatka.getId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> clearChunkCooldownsForKlatka(klatka), 100L);
    }

    // ==================== BLOCK PROTECTION ====================

    public boolean isInBlockedRegion(Location location) {
        if (location == null) return false;
        return plugin.getWorldGuardManager().isInBlockedRegion(location,
                plugin.getItemsConfig().getHydroKlatkaBlockedRegions());
    }

    public boolean isShellBlock(Location location) {
        if (location == null || location.getBlock() == null) return false;
        if (location.getBlock().getType() != SHELL) return false;
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.hasOriginalBlock(location)) return true;
        }
        return false;
    }

    public boolean isKlatkaBlock(Location location) {
        if (location == null) return false;
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.hasOriginalBlock(location)) return true;
        }
        return false;
    }

    public boolean isProtectedByCage(Location loc) {
        if (loc == null) return false;
        if (isKlatkaBlock(loc)) return true;
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.isInsideCage(loc)) return true;
            if (!k.isAnimationComplete() && k.isPlannedShellLocation(loc)) return true;
        }
        return false;
    }

    public boolean canUseItem(Player player, Material material) {
        if (player == null || material == null) return true;
        Set<Material> blocked = new HashSet<>();
        for (String name : plugin.getItemsConfig().getHydroKlatkaBlockedItems()) {
            try {
                blocked.add(Material.valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.isPlayerTrapped(player.getUniqueId())) {
                return !blocked.contains(material);
            }
        }
        return true;
    }

    public boolean canBreakBlock(Player player, Location location) {
        if (player == null || location == null) return false;
        return plugin.getWorldGuardManager().canBreakBlock(player, location);
    }

    public void markBlockAsDestroyed(Location location) {
        if (location == null) return;
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            k.markBlockDestroyed(location);
        }
    }

    public Collection<ActiveHydroKlatka> getActiveKlatki() {
        return Collections.unmodifiableCollection(activeKlatki.values());
    }

    public ActiveHydroKlatka getKlatkaForPlayer(Player player) {
        if (player == null) return null;
        for (ActiveHydroKlatka k : activeKlatki.values()) {
            if (k.isPlayerTrapped(player.getUniqueId())) return k;
        }
        return null;
    }

    public void removePlayerFromKlatka(Player player) {
        if (player == null) return;
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
        for (ActiveHydroKlatka k : new ArrayList<>(activeKlatki.values())) {
            removeKlatka(k);
        }
        cooldownTasks.values().forEach(BukkitTask::cancel);
        cooldownTasks.clear();
        chunkCooldowns.clear();
    }
}
