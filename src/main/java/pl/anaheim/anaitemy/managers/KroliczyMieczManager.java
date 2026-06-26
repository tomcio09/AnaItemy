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

public class KroliczyMieczManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> jumpBlocked = new ConcurrentHashMap<>();
    private final Map<UUID, Location> jumpBlockedLocations = new ConcurrentHashMap<>();

    private BukkitTask jumpBlockTask;

    public KroliczyMieczManager(AnaItemy plugin) {
        this.plugin = plugin;
        startJumpBlockTask();

        // Cooldown cleanup
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    // ==================== JUMP BLOCK TASK ====================

    /**
     * ✅ Co tick sprawdzaj czy gracze z blokadą skoku próbują skoczyć.
     * Zamiast efektu jump boost (który powoduje bugi z knockbackiem),
     * po prostu blokujemy ruch w górę.
     */
    private void startJumpBlockTask() {
        jumpBlockTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(jumpBlocked.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) {
                        // Klątwa wygasła
                        jumpBlocked.remove(uuid);
                        jumpBlockedLocations.remove(uuid);

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            // ✅ Nałóż 4s protection od KOŃCA klątwy
                            plugin.getItemProtectionManager().applyProtection(player, "kroliczy-miecz");
                        }
                        continue;
                    }

                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        jumpBlocked.remove(uuid);
                        jumpBlockedLocations.remove(uuid);
                        continue;
                    }

                    // ✅ Blokuj skok: jeśli gracz próbuje się wznieść ponad swoją bazową pozycję Y
                    // i jest na ziemi lub właśnie odskoczył - anuluj velocity Y
                    Vector vel = player.getVelocity();
                    if (vel.getY() > 0.1 && player.isOnGround()) {
                        // Gracz próbuje skoczyć - anuluj
                        vel.setY(-0.08); // Lekko w dół żeby nie "lewitował"
                        player.setVelocity(vel);
                    } else if (vel.getY() > 0.42) {
                        // Gracz jest w trakcie skoku (0.42 to vanilla jump velocity)
                        // Ale TYLKO jeśli to skok, nie knockback od miecza
                        // Knockback ma zazwyczaj mniejsze Y niż 0.42
                        // Więc blokujemy tylko czysty skok
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
        long seconds = config.getKroliczyMieczCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    // ==================== ATAK ====================

    public boolean attack(Player attacker, Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isOnCooldown(attacker)) {
            return false;
        }

        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) {
            return false;
        }

        if (plugin.getItemProtectionManager().isProtected(victim, "kroliczy-miecz")) {
            int secondsLeft = plugin.getItemProtectionManager()
                    .getRemainingSeconds(victim, "kroliczy-miecz");
            plugin.getItemProtectionManager()
                    .notifyAttacker(attacker, "kroliczy-miecz", secondsLeft);
            return false;
        }

        // ✅ 1. Zablokuj skakanie na 4 sekundy
        int curseDuration = config.getKroliczyMieczCurseDuration();
        jumpBlocked.put(victim.getUniqueId(),
                System.currentTimeMillis() + (curseDuration * 1000L));
        jumpBlockedLocations.put(victim.getUniqueId(), victim.getLocation().clone());

        // ✅ 2. Subtitle dla atakującego
        String attackerSubtitle = config.getKroliczyMieczAttackerSubtitle()
                .replace("{nick_victim}", victim.getName());
        attacker.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSubtitle),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));

        // ✅ 3. Title/subtitle dla ofiary
        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getKroliczyMieczVictimTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getKroliczyMieczVictimSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(3000),
                        Duration.ofMillis(250)
                )
        ));

        // ✅ 4. Dźwięk
        victim.playSound(victim.getLocation(), Sound.ENTITY_RABBIT_HURT,
                SoundCategory.PLAYERS, 1.0f, 0.5f);

        // ✅ 5. Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        // ✅ 6. Cooldown
        setCooldown(attacker);

        // ✅ 7. Particle
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                victim.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

        return true;
    }

    // ==================== CHECKS ====================

    public boolean isJumpBlocked(Player player) {
        Long end = jumpBlocked.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    private boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getKroliczyMieczBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void removeJumpBlock(Player player) {
        jumpBlocked.remove(player.getUniqueId());
        jumpBlockedLocations.remove(player.getUniqueId());
    }

    public void cleanup() {
        if (jumpBlockTask != null) jumpBlockTask.cancel();
        jumpBlocked.clear();
        jumpBlockedLocations.clear();
        cooldowns.clear();
    }

    public void cleanupPlayer(Player player) {
        removeJumpBlock(player);
    }
}
