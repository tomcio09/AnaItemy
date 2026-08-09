package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CudownaLatarniaManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> chunkCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveLatarnia> activeLatarnie = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    public CudownaLatarniaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    // ==================== TICK TASK ====================

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                playerCooldowns.entrySet().removeIf(e -> now >= e.getValue());
                chunkCooldowns.entrySet().removeIf(e -> now >= e.getValue());

                for (ActiveLatarnia latarnia : new ArrayList<>(activeLatarnie.values())) {
                    if (latarnia.isExpired()) {
                        removeLatarnia(latarnia, false);
                        continue;
                    }

                    Block block = latarnia.getLocation().getBlock();
                    if (block.getType() != Material.BEACON) {
                        removeLatarnia(latarnia, true);
                        continue;
                    }

                    updateBossBar(latarnia);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==================== COOLDOWN ====================

    public boolean isOnCooldown(Player player) {
        Long end = playerCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getCooldownRemaining(Player player) {
        Long end = playerCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getCudownaLatarniaCooldown();
        playerCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (cooldownSeconds * 1000));
        player.setCooldown(Material.BEACON, (int) (cooldownSeconds * 20));
    }

    public void resetCooldown(Player player) {
        playerCooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.BEACON, 0);
    }
    
    public void setPostResetCooldown(Player player, int seconds) {
        playerCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (seconds * 1000L));
        player.setCooldown(Material.BEACON, seconds * 20);
    }

    // ==================== CHUNK COOLDOWN ====================

    public boolean isChunkBlocked(Location location) {
        long now = System.currentTimeMillis();
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        String worldName = location.getWorld().getName();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String key = worldName + ":" + (cx + dx) + ":" + (cz + dz);
                Long end = chunkCooldowns.get(key);
                if (end != null && now < end) return true;
            }
        }
        return false;
    }

    private void setChunkCooldown(Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getCudownaLatarniaChunkCooldown();
        long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000);

        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        String worldName = location.getWorld().getName();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String key = worldName + ":" + (cx + dx) + ":" + (cz + dz);
                chunkCooldowns.put(key, cooldownEnd);
            }
        }
    }

    // ==================== BLOCKED REGION ====================

    public boolean isInBlockedRegion(Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getCudownaLatarniaBlockedRegions();
        return plugin.getWorldGuardManager().isInBlockedRegion(location, blockedRegions);
    }

    // ==================== AKTYWACJA ====================

    public void activate(Player player, Location blockLocation) {
        ItemsConfig config = plugin.getItemsConfig();
        int beaconDuration = config.getCudownaLatarniaDuration();

        Block block = blockLocation.getBlock();
        Material originalType = block.getType();
        block.setType(Material.BEACON);

        setCooldown(player);
        setChunkCooldown(blockLocation);

        World world = blockLocation.getWorld();
        for (Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distance(blockLocation) <= 50) {
                nearby.playSound(blockLocation, Sound.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.PLAYERS, 1.0f, 1.5f);
                nearby.playSound(blockLocation, Sound.BLOCK_BEACON_ACTIVATE,
                        SoundCategory.BLOCKS, 1.5f, 1.0f);
            }
        }

        world.spawnParticle(Particle.END_ROD,
                blockLocation.clone().add(0.5, 1, 0.5), 60, 0.5, 2, 0.5, 0.05);
        world.spawnParticle(Particle.WITCH,
                blockLocation.clone().add(0.5, 1, 0.5), 40, 1, 1, 1, 0.05);

        String subtitle = config.getCudownaLatarniaActivatedSubtitle();
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(3000),
                        Duration.ofMillis(500)
                )
        ));

        applyEffects(player, config);

        long expirationTime = System.currentTimeMillis() + (beaconDuration * 1000L);
        ActiveLatarnia latarnia = new ActiveLatarnia(
                UUID.randomUUID(),
                player.getUniqueId(),
                blockLocation.clone(),
                originalType,
                expirationTime,
                beaconDuration
        );
        activeLatarnie.put(latarnia.getId(), latarnia);

        createBossBar(player, latarnia);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeLatarnie.containsKey(latarnia.getId())) {
                    removeLatarnia(latarnia, false);
                }
            }
        }.runTaskLater(plugin, beaconDuration * 20L);
    }

    // ==================== EFEKTY ====================

    private void applyEffects(Player player, ItemsConfig config) {
        int regenDuration = config.getCudownaLatarniaRegenDuration() * 20;
        int regenLevel = config.getCudownaLatarniaRegenLevel() - 1;
        int absorptionDuration = config.getCudownaLatarniaAbsorptionDuration() * 20;
        int absorptionLevel = config.getCudownaLatarniaAbsorptionLevel() - 1;
        int strengthDuration = config.getCudownaLatarniaStrengthDuration() * 20;
        int strengthLevel = config.getCudownaLatarniaStrengthLevel() - 1;

        PotionEffect currentRegen = player.getPotionEffect(PotionEffectType.REGENERATION);
        if (currentRegen == null || currentRegen.getAmplifier() <= regenLevel) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, regenDuration, regenLevel,
                    false, true, true));
        }

        PotionEffect currentAbsorption = player.getPotionEffect(PotionEffectType.ABSORPTION);
        if (currentAbsorption == null || currentAbsorption.getAmplifier() <= absorptionLevel) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.ABSORPTION, absorptionDuration, absorptionLevel,
                    false, true, true));
        }

        // ✅ 1.21.4 - STRENGTH zamiast INCREASE_DAMAGE
        PotionEffect currentStrength = player.getPotionEffect(PotionEffectType.STRENGTH);
        if (currentStrength == null || currentStrength.getAmplifier() <= strengthLevel) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH, strengthDuration, strengthLevel,
                    false, true, true));
        }
    }

    // ✅ POPRAWKA: Usunięta duplikacja - tylko jedna metoda removeEffects
    private void removeEffects(Player player) {
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        // ✅ 1.21.4 - STRENGTH zamiast INCREASE_DAMAGE
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    // ==================== USUWANIE ====================

    public void removeLatarnia(ActiveLatarnia latarnia, boolean destroyed) {
        if (!activeLatarnie.containsKey(latarnia.getId())) return;
        activeLatarnie.remove(latarnia.getId());

        Block block = latarnia.getLocation().getBlock();
        if (block.getType() == Material.BEACON) {
            block.setType(latarnia.getOriginalType());
        }

        Player owner = Bukkit.getPlayer(latarnia.getOwnerId());
        World world = latarnia.getLocation().getWorld();
        ItemsConfig config = plugin.getItemsConfig();

        if (destroyed) {
            if (owner != null && owner.isOnline()) {
                removeEffects(owner);

                owner.showTitle(Title.title(
                        Component.empty(),
                        LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(config.getCudownaLatarniaDestroyedSubtitle()),
                        Title.Times.times(
                                Duration.ofMillis(250),
                                Duration.ofMillis(2500),
                                Duration.ofMillis(250)
                        )
                ));
            }

            for (Player nearby : world.getPlayers()) {
                if (nearby.getLocation().distance(latarnia.getLocation()) <= 50) {
                    nearby.playSound(latarnia.getLocation(),
                            Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 1.0f);
                }
            }
        }

        world.spawnParticle(Particle.CLOUD,
                latarnia.getLocation().clone().add(0.5, 0.5, 0.5),
                30, 0.3, 0.3, 0.3, 0.05);

        BossBar bossBar = latarnia.getBossBar();
        if (bossBar != null && owner != null && owner.isOnline()) {
            owner.hideBossBar(bossBar);
        }
    }

    // ==================== BOSSBAR ====================

    private void createBossBar(Player player, ActiveLatarnia latarnia) {
        ItemsConfig config = plugin.getItemsConfig();

        String title = config.getCudownaLatarniaBossBarTitle()
                .replace("{seconds_left}", String.valueOf(latarnia.getRemainingSeconds()));

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(config.getCudownaLatarniaBossBarColor());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.PURPLE;
        }

        BossBar bossBar = BossBar.bossBar(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );

        latarnia.setBossBar(bossBar);
        player.showBossBar(bossBar);
    }

    private void updateBossBar(ActiveLatarnia latarnia) {
        BossBar bossBar = latarnia.getBossBar();
        if (bossBar == null) return;

        Player owner = Bukkit.getPlayer(latarnia.getOwnerId());
        if (owner == null || !owner.isOnline()) return;

        ItemsConfig config = plugin.getItemsConfig();
        int remaining = latarnia.getRemainingSeconds();
        int total = latarnia.getTotalDuration();

        String title = config.getCudownaLatarniaBossBarTitle()
                .replace("{seconds_left}", String.valueOf(remaining));
        bossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        float progress = total > 0
                ? Math.max(0.01f, Math.min(1.0f, (float) remaining / total))
                : 0.01f;
        bossBar.progress(progress);
    }

    // ==================== BEACON BREAK CHECK ====================

    public boolean isLatarniaBlock(Location location) {
        for (ActiveLatarnia latarnia : activeLatarnie.values()) {
            Location loc = latarnia.getLocation();
            if (loc.getBlockX() == location.getBlockX()
                    && loc.getBlockY() == location.getBlockY()
                    && loc.getBlockZ() == location.getBlockZ()
                    && loc.getWorld().equals(location.getWorld())) {
                return true;
            }
        }
        return false;
    }

    public ActiveLatarnia getLatarniaAt(Location location) {
        for (ActiveLatarnia latarnia : activeLatarnie.values()) {
            Location loc = latarnia.getLocation();
            if (loc.getBlockX() == location.getBlockX()
                    && loc.getBlockY() == location.getBlockY()
                    && loc.getBlockZ() == location.getBlockZ()
                    && loc.getWorld().equals(location.getWorld())) {
                return latarnia;
            }
        }
        return null;
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();

        for (ActiveLatarnia latarnia : new ArrayList<>(activeLatarnie.values())) {
            Block block = latarnia.getLocation().getBlock();
            if (block.getType() == Material.BEACON) {
                block.setType(latarnia.getOriginalType());
            }

            BossBar bossBar = latarnia.getBossBar();
            if (bossBar != null) {
                Player owner = Bukkit.getPlayer(latarnia.getOwnerId());
                if (owner != null && owner.isOnline()) {
                    owner.hideBossBar(bossBar);
                }
            }
        }

        activeLatarnie.clear();
        playerCooldowns.clear();
        chunkCooldowns.clear();
    }

    // ==================== INNER CLASS ====================

    public static class ActiveLatarnia {
        private final UUID id;
        private final UUID ownerId;
        private final Location location;
        private final Material originalType;
        private final long expirationTime;
        private final int totalDuration;
        private BossBar bossBar;

        public ActiveLatarnia(UUID id, UUID ownerId, Location location,
                              Material originalType, long expirationTime, int totalDuration) {
            this.id = id;
            this.ownerId = ownerId;
            this.location = location;
            this.originalType = originalType;
            this.expirationTime = expirationTime;
            this.totalDuration = totalDuration;
        }

        public UUID getId() { return id; }
        public UUID getOwnerId() { return ownerId; }
        public Location getLocation() { return location.clone(); }
        public Material getOriginalType() { return originalType; }
        public int getTotalDuration() { return totalDuration; }

        public int getRemainingSeconds() {
            return (int) Math.max(0, (expirationTime - System.currentTimeMillis()) / 1000);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public BossBar getBossBar() { return bossBar; }
        public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }
    }
}
