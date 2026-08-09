package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LopataGrinchaManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public LopataGrinchaManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
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
        long seconds = config.getLopataGrinchaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.DIAMOND_SHOVEL, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.DIAMOND_SHOVEL, 0);
    }
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    // ==================== ATAK ====================

    public boolean attack(Player attacker, Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        // Cooldown
        if (isOnCooldown(attacker)) {
            long remaining = getCooldownRemaining(attacker);
            String subtitle = config.getLopataGrinchaCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");

            attacker.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(
                            Duration.ofMillis(200),
                            Duration.ofMillis(2000),
                            Duration.ofMillis(200)
                    )
            ));
            return false;
        }

        // Region
        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) {
            return false;
        }

        // 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "lopata-grincha")) {
            int secondsLeft = plugin.getItemProtectionManager()
                    .getRemainingSeconds(victim, "lopata-grincha");
            plugin.getItemProtectionManager()
                    .notifyAttacker(attacker, "lopata-grincha", secondsLeft);
            return false;
        }

        // ✅ 1. Zamrożenie wizualne (pół sekundy)
        victim.setFreezeTicks(30); // 30 ticków = 1.5s wizualnie, ale efekt trwa ~0.5s

        // ✅ 2. Dźwięk zamrożenia
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        attacker.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // ✅ 3. Losowa rotacja kamery
        float randomYaw = ThreadLocalRandom.current().nextFloat() * 360.0f - 180.0f;
        float randomPitch = ThreadLocalRandom.current().nextFloat() * 120.0f - 60.0f;

        Location rotatedLoc = victim.getLocation().clone();
        rotatedLoc.setYaw(randomYaw);
        rotatedLoc.setPitch(randomPitch);
        victim.teleport(rotatedLoc);

        // ✅ 4. Subtitle dla atakującego
        String attackerSubtitle = config.getLopataGrinchaAttackerSubtitle()
                .replace("{nick}", victim.getName());
        attacker.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSubtitle),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));

        // ✅ 5. Subtitle dla ofiary
        String victimSubtitle = config.getLopataGrinchaVictimSubtitle();
        victim.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(victimSubtitle),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));

        // ✅ 6. Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        // ✅ 7. Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "lopata-grincha");

        // ✅ 8. Cooldown
        setCooldown(attacker);

        // ✅ 9. Particle
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);

        return true;
    }

    // ==================== REGION ====================

    private boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getLopataGrinchaBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        cooldowns.clear();
    }
}
