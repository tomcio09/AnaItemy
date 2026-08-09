package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlokWidmoManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, AffectedData> affectedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    // ✅ 1.21.4 - NamespacedKey zamiast UUID dla AttributeModifier
    private static final NamespacedKey MODIFIER_KEY =
            new NamespacedKey("anaitemy", "blok_widmo_reduction");

    private BukkitTask tickTask;

    public BlokWidmoManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());

                for (AffectedData data : new ArrayList<>(affectedPlayers.values())) {
                    Player player = Bukkit.getPlayer(data.getVictimId());

                    if (player == null || !player.isOnline()) {
                        removeEffect(data.getVictimId());
                        continue;
                    }

                    if (data.isExpired()) {
                        restoreHealth(player);
                        removeEffect(data.getVictimId());

                        try {
                            Sound deactivateSound = Sound.valueOf(
                                    plugin.getItemsConfig().getBlokWidmoDeactivateSound());
                            player.playSound(player.getLocation(), deactivateSound, 1.0f, 1.5f);
                        } catch (IllegalArgumentException ignored) {}

                        continue;
                    }

                    updateBossBar(player, data);
                    ensureModifierExists(player, data);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
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
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getBlokWidmoCooldown();
        cooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (cooldownSeconds * 1000));
        player.setCooldown(Material.STRUCTURE_BLOCK, (int) (cooldownSeconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.STRUCTURE_BLOCK, 0);
    }

    // ✅ Post-reset cooldown
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (seconds * 1000L));
        player.setCooldown(Material.STRUCTURE_BLOCK, seconds * 20);
    }

    // ==================== AKTYWACJA ====================

    public void activate(Player activator, Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        int radius = config.getBlokWidmoRadius();
        int effectDuration = config.getBlokWidmoEffectDuration();
        double healthReduction = config.getBlokWidmoHealthReduction();
        double minimumHealth = config.getBlokWidmoMinimumHealth();

        setCooldown(activator);

        List<Player> infectedPlayers = new ArrayList<>();
        World world = location.getWorld();

        for (Player victim : world.getPlayers()) {
            if (victim.equals(activator)) continue;
            if (victim.getLocation().distance(location) > radius) continue;

            List<String> blockedRegions = config.getBlokWidmoBlockedRegions();
            if (plugin.getWorldGuardManager().isInBlockedRegion(
                    victim.getLocation(), blockedRegions)) {
                continue;
            }

            boolean applied = applyEffect(victim, effectDuration, healthReduction, minimumHealth);
            if (applied) infectedPlayers.add(victim);
        }

        try {
            Sound activateSound = Sound.valueOf(config.getBlokWidmoActivateSound());
            activator.playSound(activator.getLocation(), activateSound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy dźwięk aktywacji: "
                    + config.getBlokWidmoActivateSound());
        }

        activator.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getBlokWidmoPlacedSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)
                )
        ));

        location.getWorld().spawnParticle(Particle.WITCH, location, 100, 2, 2, 2, 0.1);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 50, 1, 1, 1, 0.05);

        for (Player victim : infectedPlayers) {
            victim.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getBlokWidmoAffectedTitle()),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getBlokWidmoAffectedSubtitle()),
                    Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofMillis(3000),
                            Duration.ofMillis(500)
                    )
            ));
        }
    }

    // ==================== EFEKT ====================

    private boolean applyEffect(Player victim, int durationSeconds,
                                 double healthReduction, double minimumHealth) {
        UUID victimId = victim.getUniqueId();

        if (affectedPlayers.containsKey(victimId)) {
            restoreHealth(victim);
            removeEffect(victimId);
        }

        // ✅ 1.21.4 - nowa nazwa atrybutu
        AttributeInstance maxHealthAttr = victim.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return false;

        double currentMaxHealth = maxHealthAttr.getValue();
        double targetMaxHealth = Math.max(minimumHealth, currentMaxHealth - healthReduction);
        double actualReduction = currentMaxHealth - targetMaxHealth;

        if (actualReduction <= 0) return false;

        // ✅ 1.21.4 - nowe API AttributeModifier z NamespacedKey
        AttributeModifier modifier = new AttributeModifier(
                MODIFIER_KEY,
                -actualReduction,
                AttributeModifier.Operation.ADD_NUMBER
        );

        removeModifier(maxHealthAttr);
        maxHealthAttr.addModifier(modifier);

        double newMaxHealth = maxHealthAttr.getValue();
        if (victim.getHealth() > newMaxHealth) {
            victim.setHealth(Math.max(1.0, newMaxHealth));
        }

        long expirationTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        AffectedData data = new AffectedData(
                victimId, expirationTime, durationSeconds, actualReduction);
        affectedPlayers.put(victimId, data);

        createBossBar(victim, data);

        try {
            Sound sound = Sound.valueOf(plugin.getItemsConfig().getBlokWidmoDeactivateSound());
            victim.playSound(victim.getLocation(), sound, 1.0f, 0.5f);
        } catch (IllegalArgumentException ignored) {}

        return true;
    }

    private void restoreHealth(Player player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;
        removeModifier(maxHealthAttr);
    }

    private void removeModifier(AttributeInstance attribute) {
        // ✅ 1.21.4 - usuń po NamespacedKey
        attribute.removeModifier(MODIFIER_KEY);
    }

    private void removeEffect(UUID playerId) {
        affectedPlayers.remove(playerId);

        BossBar bossBar = bossBars.remove(playerId);
        if (bossBar != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.hideBossBar(bossBar);
            }
        }
    }

    private void ensureModifierExists(Player player, AffectedData data) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;

        // ✅ 1.21.4 - sprawdź po NamespacedKey
        boolean hasModifier = maxHealthAttr.getModifier(MODIFIER_KEY) != null;

        if (!hasModifier && !data.isExpired()) {
            plugin.getLogger().info("[BlokWidmo] Modifier usunięty przez zewnętrzny plugin dla gracza "
                    + player.getName() + " - kończę efekt.");
            removeEffect(player.getUniqueId());
        }
    }

    public void cleanupStaleModifier(Player player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;

        if (!affectedPlayers.containsKey(player.getUniqueId())) {
            removeModifier(maxHealthAttr);
        }
    }

    // ==================== BOSSBAR ====================

    private void createBossBar(Player player, AffectedData data) {
        ItemsConfig config = plugin.getItemsConfig();

        String title = config.getBlokWidmoBossBarTitle()
                .replace("{time_left}", formatTime(data.getRemainingSeconds()));

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(config.getBlokWidmoBossBarColor());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.PURPLE;
        }

        BossBar bossBar = BossBar.bossBar(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );

        BossBar old = bossBars.remove(player.getUniqueId());
        if (old != null) player.hideBossBar(old);

        player.showBossBar(bossBar);
        bossBars.put(player.getUniqueId(), bossBar);
    }

    private void updateBossBar(Player player, AffectedData data) {
        BossBar bossBar = bossBars.get(player.getUniqueId());
        if (bossBar == null) return;

        ItemsConfig config = plugin.getItemsConfig();
        int remaining = data.getRemainingSeconds();
        int total = data.getTotalDuration();

        String title = config.getBlokWidmoBossBarTitle()
                .replace("{time_left}", formatTime(remaining));

        bossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        float progress = total > 0
                ? Math.max(0.01f, Math.min(1.0f, (float) remaining / total))
                : 0.01f;
        bossBar.progress(progress);
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "s";
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m" + String.format("%02d", seconds) + "s";
    }

    // ==================== API ====================

    public boolean isAffected(Player player) {
        return affectedPlayers.containsKey(player.getUniqueId());
    }

    public int getRemainingSeconds(Player player) {
        AffectedData data = affectedPlayers.get(player.getUniqueId());
        if (data == null) return 0;
        return data.getRemainingSeconds();
    }

    public double getReducedHealth(Player player) {
        AffectedData data = affectedPlayers.get(player.getUniqueId());
        if (data == null) return 0;
        return data.getReduction();
    }

    public void forceRemoveEffect(Player player) {
        if (!affectedPlayers.containsKey(player.getUniqueId())) {
            cleanupStaleModifier(player);
            return;
        }
        restoreHealth(player);
        removeEffect(player.getUniqueId());
    }

    public boolean isInBlockedRegion(Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getBlokWidmoBlockedRegions();
        return plugin.getWorldGuardManager().isInBlockedRegion(location, blockedRegions);
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();

        for (AffectedData data : new ArrayList<>(affectedPlayers.values())) {
            Player player = Bukkit.getPlayer(data.getVictimId());
            if (player != null && player.isOnline()) {
                restoreHealth(player);
            }
        }

        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(entry.getValue());
            }
        }

        affectedPlayers.clear();
        bossBars.clear();
        cooldowns.clear();
    }

    // ==================== INNER CLASS ====================

    public static class AffectedData {
        private final UUID victimId;
        private final long expirationTime;
        private final int totalDuration;
        private final double reduction;

        public AffectedData(UUID victimId, long expirationTime,
                            int totalDuration, double reduction) {
            this.victimId = victimId;
            this.expirationTime = expirationTime;
            this.totalDuration = totalDuration;
            this.reduction = reduction;
        }

        public UUID getVictimId() { return victimId; }
        public int getTotalDuration() { return totalDuration; }
        public double getReduction() { return reduction; }

        public int getRemainingSeconds() {
            return (int) Math.max(0,
                    (expirationTime - System.currentTimeMillis()) / 1000);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
    }
}
