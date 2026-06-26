package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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

    private BukkitTask expirationTask;

    public KroliczyMieczManager(AnaItemy plugin) {
        this.plugin = plugin;
        startExpirationTask();

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void startExpirationTask() {
        expirationTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(jumpBlocked.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) {
                        jumpBlocked.remove(uuid);

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            plugin.getItemProtectionManager().applyProtection(player, "kroliczy-miecz");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
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

        int curseDuration = config.getKroliczyMieczCurseDuration();
        jumpBlocked.put(victim.getUniqueId(),
                System.currentTimeMillis() + (curseDuration * 1000L));

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

        victim.playSound(victim.getLocation(), Sound.ENTITY_RABBIT_HURT,
                SoundCategory.PLAYERS, 1.0f, 0.5f);

        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        setCooldown(attacker);

        victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                victim.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

        return true;
    }

    // ==================== JUMP BLOCK CHECK ====================

    /**
     * ✅ Wywoływane z listenera PlayerMoveEvent.
     * Zwraca true jeśli ruch powinien być zablokowany (skok).
     */
    public boolean shouldBlockJump(Player player, Location from, Location to) {
        if (!isJumpBlocked(player)) return false;

        // ✅ Blokuj TYLKO ruch w górę (skok)
        // Nie blokuj: spadanie, chodzenie po płaskim, knockback w dół
        double deltaY = to.getY() - from.getY();

        if (deltaY <= 0) return false; // Spadanie lub płasko - OK

        // ✅ Sprawdź czy to skok (gracz był na ziemi w momencie ruchu w górę)
        // Vanilla skok daje deltaY ~0.42 w pierwszym ticku
        // Knockback w górę daje inne wartości ale nie możemy tego odróżnić
        // Więc blokujemy KAŻDY ruch w górę inicjowany przez gracza

        // Ale musimy pozwolić na:
        // - wchodzenie po schodkach (deltaY = 0.5 ale to nie skok)
        // - wchodzenie na pół-bloki

        // ✅ Schodki i pół-bloki mają deltaY dokładnie 0.5
        // Skok ma deltaY ~0.42 w pierwszym ticku
        // Knockback w górę ma różne wartości

        // Najprostsza metoda: sprawdź czy pod nogami gracza jest solid block
        // Jeśli tak = gracz próbuje skoczyć = blokuj
        // Jeśli nie = gracz spada/jest w powietrzu = pozwól (knockback)

        Location feetBelow = from.clone().subtract(0, 0.1, 0);
        boolean wasOnSolid = feetBelow.getBlock().getType().isSolid();

        if (!wasOnSolid) return false; // Gracz w powietrzu - nie blokuj (knockback OK)

        // ✅ Gracz na ziemi i próbuje się wznieść

        // Pozwól na schodki/pół-bloki (deltaY <= 0.5625 i blok docelowy jest solid)
        if (deltaY <= 0.5625) {
            Location targetFeet = to.clone().subtract(0, 0.1, 0);
            if (targetFeet.getBlock().getType().isSolid()) {
                return false; // Wchodzenie na schodek - OK
            }
        }

        // To jest skok - blokuj
        return true;
    }

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
    }

    public void cleanup() {
        if (expirationTask != null) expirationTask.cancel();
        jumpBlocked.clear();
        cooldowns.clear();
    }

    public void cleanupPlayer(Player player) {
        removeJumpBlock(player);
    }
}
