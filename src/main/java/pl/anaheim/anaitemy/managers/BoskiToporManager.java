package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BoskiToporManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> invinciblePlayers = new ConcurrentHashMap<>();

    public BoskiToporManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
                invinciblePlayers.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
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
        long seconds = config.getBoskiToporCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.IRON_AXE, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.IRON_AXE, 0);
    }
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    // ==================== INVINCIBILITY ====================

    public boolean isInvincible(Player player) {
        Long end = invinciblePlayers.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    // ==================== AKTYWACJA ====================

    public void activate(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        // ✅ 1. Cooldown
        setCooldown(player);

        // ✅ 2. Nieśmiertelność
        int invincDuration = config.getBoskiToporInvincibilityDuration();
        invinciblePlayers.put(player.getUniqueId(),
                System.currentTimeMillis() + (invincDuration * 1000L));

        // ✅ 3. Dźwięk smoka dla wszystkich wokół
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.0f, 1.0f);

        // ✅ 4. Subtitle dla gracza
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getBoskiToporActivatedSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2500),
                        Duration.ofMillis(200)
                )
        ));

        // ✅ 5. Glowing na 2 sekundy
        int glowDuration = config.getBoskiToporGlowDuration();
        player.setGlowing(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setGlowing(false);
            }
        }, glowDuration * 20L);

        // ✅ 6. Odpychanie graczy wokół + combat tag TYLKO jeśli kogoś odepchnął
        double knockbackRadius = config.getBoskiToporKnockbackRadius();
        double knockbackPower = config.getBoskiToporKnockbackPower();

        Location center = player.getLocation();
        List<String> blockedRegions = config.getBoskiToporBlockedRegions();

        boolean pushedAnyone = false;

        for (Player target : center.getWorld().getNearbyPlayers(center, knockbackRadius)) {
            if (target.equals(player)) continue;

            if (plugin.getWorldGuardManager().isInNamedRegion(target.getLocation(), blockedRegions)) {
                continue;
            }

            Vector knockback = target.getLocation().toVector()
                    .subtract(center.toVector());
            knockback.setY(0);

            if (knockback.lengthSquared() < 0.0001) {
                knockback = new Vector(1, 0, 0);
            }

            knockback.normalize().multiply(knockbackPower).setY(0.4);
            target.setVelocity(target.getVelocity().add(knockback));

            pushedAnyone = true;

            // ✅ Combat tag dla odepchniętego gracza
            if (plugin.getCombatIntegrationManager().isEnabled()) {
                plugin.getCombatIntegrationManager().tagPlayer(target, player);
            }
        }

        // ✅ Combat tag dla użytkownika TYLKO jeśli kogoś odepchnął
        if (pushedAnyone && plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(player, player);
        }

        // ✅ 7. Particle
        center.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center.clone().add(0, 1, 0),
                10, 2, 1, 2, 0.1);
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1, 0),
                40, 2, 1, 2, 0.3);
    }

    // ==================== REGION CHECK ====================

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getBoskiToporBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (UUID uuid : invinciblePlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGlowing(false);
            }
        }

        cooldowns.clear();
        invinciblePlayers.clear();
    }
}
