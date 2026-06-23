package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WedkaNielotaManager {

    private final AnaItemy plugin;

    // Cooldowny wędki
    private final Map<UUID, Long> wedkaCooldowns = new ConcurrentHashMap<>();

    // Aktywne klątwy (key = victimId)
    private final Map<UUID, CurseData> activeCurses = new ConcurrentHashMap<>();

    // BossBary dla złapanych graczy
    private final Map<UUID, BossBar> curseBossBars = new ConcurrentHashMap<>();

    // Resetowanie bugowania po uderzeniu (key = victimId, value = czas końca resetu w ms)
    private final Map<UUID, Long> bugowanieResetTimers = new ConcurrentHashMap<>();

    public WedkaNielotaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
        startBugowanieTask();
    }

    // ==================== CLEANUP TASK ====================

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                wedkaCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());
                bugowanieResetTimers.entrySet().removeIf(entry -> now >= entry.getValue());

                for (CurseData curse : new ArrayList<>(activeCurses.values())) {
                    updateBossBar(curse);

                    if (!curse.isWaitingForFlight() && curse.isExpired()) {
                        handleCurseExpired(curse);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==================== BUGOWANIE TASK ====================

    /**
     * Co tick sprawdza graczy z klątwą w powietrzu
     * i aplikuje spowolnione opadanie jeśli oznaczono bugowanieTick
     */
    private void startBugowanieTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (CurseData curse : new ArrayList<>(activeCurses.values())) {
                    if (curse.isWaitingForFlight()) continue;

                    Player victim = Bukkit.getPlayer(curse.getVictimId());
                    if (victim == null || !victim.isOnline()) continue;

                    // Jeśli gracz leci elytrą - nie robimy nic
                    if (victim.isGliding()) continue;

                    // Jeśli gracz na ziemi lub w wodzie - nie robimy nic
                    if (victim.isOnGround() || victim.isInWater()) continue;

                    // Jeśli oznaczono bugowanieTick - zastosuj wolne opadanie
                    if (curse.isBugowanieTick()) {
                        if (!isBugowanieBlocked(victim)) {
                            applySlowFall(victim);
                        }
                        curse.resetBugowanieTick();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== COOLDOWN ====================

    public boolean isOnCooldown(Player player) {
        Long cooldownEnd = wedkaCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    public long getCooldownRemaining(Player player) {
        Long cooldownEnd = wedkaCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return 0;
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getWedkaNielotaCooldown();
        wedkaCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (cooldownSeconds * 1000));

        player.setCooldown(Material.FISHING_ROD, (int) (cooldownSeconds * 20));
    }

    public void resetCooldown(Player player) {
        wedkaCooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.FISHING_ROD, 0);
    }

    // ==================== KLĄTWA ====================

    public void applyCurse(Player victim, Player attacker) {
        ItemsConfig config = plugin.getItemsConfig();

        if (activeCurses.containsKey(victim.getUniqueId())) {
            forceRemoveCurse(victim);
        }

        int duration = config.getWedkaNielotaCurseDuration();
        CurseData curse = new CurseData(
                victim.getUniqueId(),
                attacker != null ? attacker.getUniqueId() : null,
                System.currentTimeMillis() + (duration * 1000L),
                duration
        );

        activeCurses.put(victim.getUniqueId(), curse);

        // Titles
        if (attacker != null) {
            showCaughtTitles(victim, attacker);
            setCooldown(attacker);
        } else {
            showCommandCurseTitle(victim);
        }

        // BossBar
        createBossBar(victim, curse);
    }

    /**
     * Usuwa klątwę i ustawia flagę "czeka na odlot"
     * Wiadomość pojawi się dopiero gdy gracz odleci
     */
    public void removeCurse(Player victim, boolean wasReleased) {
        CurseData curse = activeCurses.get(victim.getUniqueId());
        if (curse == null) return;

        // Jeśli gracz już leci - pokaż wiadomość natychmiast i usuń
        if (victim.isGliding()) {
            if (wasReleased) {
                showReleasedTitle(victim);
            } else {
                showFreedTitle(victim);
            }
            forceRemoveCurse(victim);
            return;
        }

        // Gracz nie leci - ustaw flagę i poczekaj na odlot
        curse.setWaitingForFlight(true);
        curse.setWasReleased(wasReleased);
        // BossBar zostaje z tekstem "<1s"
    }

    /**
     * Usuwa klątwę natychmiast bez żadnych wiadomości
     */
    public void forceRemoveCurse(Player victim) {
        activeCurses.remove(victim.getUniqueId());

        BossBar bossBar = curseBossBars.remove(victim.getUniqueId());
        if (bossBar != null) {
            victim.hideBossBar(bossBar);
        }
    }

    public boolean hasCurse(Player player) {
        return activeCurses.containsKey(player.getUniqueId());
    }

    public CurseData getCurse(Player player) {
        return activeCurses.get(player.getUniqueId());
    }

    public Collection<CurseData> getActiveCurses() {
        return new ArrayList<>(activeCurses.values());
    }

    // ==================== BUGOWANIE ====================

    /**
     * Wywoływane gdy gracz z klątwą klika spację w powietrzu (nie na ziemi)
     * Ustawia flagę że w następnym ticku ma być zastosowane wolne opadanie
     */
    public void handleSpaceClick(Player player) {
        CurseData curse = activeCurses.get(player.getUniqueId());
        if (curse == null) return;
        if (curse.isWaitingForFlight()) return;
        if (isBugowanieBlocked(player)) return;

        curse.markBugowanieTick();
    }

    /**
     * ✅ Aplikuje wolne opadanie gracza (bugowanie klątwy)
     * Prędkość z configu: bugowanie-fall-speed (bloki/sekundę)
     * Np. 1.0 = 1 blok/s (bardzo wolno), 2.0 = 2 bloki/s
     */
    private void applySlowFall(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        // ✅ Prędkość podzielona przez 20 (ticki) i przez 2 dla płynności
        double fallSpeed = config.getWedkaNielotaBugowanieFallSpeed();
        double fallVelocity = -(fallSpeed / 20.0);

        Vector velocity = player.getVelocity();

        // ✅ Zachowaj poziomy ruch (X i Z) - gracz może iść do przodu
        velocity.setY(fallVelocity);
        player.setVelocity(velocity);
    }

    /**
     * Resetuje bugowanie na krótki czas (po uderzeniu mieczem)
     * Przez resetDuration ticki gracz normalnie spada (bugowanie nie działa)
     */
    public void resetBugowanie(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int resetTicks = config.getWedkaNielotaBugowanieResetDuration();

        // Ticki → ms (1 tick = 50ms)
        bugowanieResetTimers.put(player.getUniqueId(),
                System.currentTimeMillis() + (resetTicks * 50L));
    }

    public boolean isBugowanieBlocked(Player player) {
        Long resetEnd = bugowanieResetTimers.get(player.getUniqueId());
        return resetEnd != null && System.currentTimeMillis() < resetEnd;
    }

    // ==================== BOSSBAR ====================

    private void createBossBar(Player victim, CurseData curse) {
        ItemsConfig config = plugin.getItemsConfig();

        int remaining = curse.getRemainingSeconds();
        String title = config.getWedkaNielotaBossBarTitle()
                .replace("{seconds}", String.valueOf(remaining));

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(config.getWedkaNielotaBossBarColor());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.RED;
        }

        BossBar bossBar = BossBar.bossBar(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );

        victim.showBossBar(bossBar);
        curseBossBars.put(victim.getUniqueId(), bossBar);
    }

    private void updateBossBar(CurseData curse) {
        Player victim = Bukkit.getPlayer(curse.getVictimId());
        if (victim == null || !victim.isOnline()) return;

        BossBar bossBar = curseBossBars.get(curse.getVictimId());
        if (bossBar == null) return;

        ItemsConfig config = plugin.getItemsConfig();

        if (curse.isWaitingForFlight()) {
            // Klątwa wygasła - pokaż "<1s"
            String titleWaiting = config.getWedkaNielotaBossBarTitleWaiting();
            bossBar.name(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(titleWaiting));
            bossBar.progress(0.01f);
        } else {
            int remaining = curse.getRemainingSeconds();
            int total = curse.getTotalDuration();

            String title = config.getWedkaNielotaBossBarTitle()
                    .replace("{seconds}", String.valueOf(remaining));
            bossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(title));

            float progress = total > 0
                    ? Math.max(0.01f, Math.min(1.0f, (float) remaining / total))
                    : 0.01f;
            bossBar.progress(progress);
        }
    }

    // ==================== WYGAŚNIĘCIE KLĄTWY ====================

    private void handleCurseExpired(CurseData curse) {
        Player victim = Bukkit.getPlayer(curse.getVictimId());

        if (victim == null || !victim.isOnline()) {
            activeCurses.remove(curse.getVictimId());
            curseBossBars.remove(curse.getVictimId());
            return;
        }

        // Jeśli gracz już leci - pokaż wiadomość i usuń od razu
        if (victim.isGliding()) {
            showFreedTitle(victim);
            forceRemoveCurse(victim);
            return;
        }

        // Gracz nie leci - BossBar zamienia się na "<1s" i czeka na odlot
        curse.setWaitingForFlight(true);
        curse.setWasReleased(false);
    }

    // ==================== TITLES ====================

    private void showCaughtTitles(Player victim, Player attacker) {
        ItemsConfig config = plugin.getItemsConfig();

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCaughtTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCaughtSubtitle()
                                .replace("{attacker}", attacker.getName())),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250))
        ));

        attacker.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCatcherTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCatcherSubtitle()
                                .replace("{victim}", victim.getName())),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250))
        ));
    }

    private void showCommandCurseTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCaughtTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&7Nałożono na Ciebie klątwę!"),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250))
        ));
    }

    public void showReleasedTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaReleasedTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaReleasedSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250))
        ));
    }

    public void showFreedTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaFreedTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaFreedSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250))
        ));
    }

    // ==================== MESSAGES ====================

    public void sendMessage(Player player, String message) {
        player.sendMessage(
                LegacyComponentSerializer.legacyAmpersand().deserialize(message)
        );
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (CurseData curse : new ArrayList<>(activeCurses.values())) {
            Player victim = Bukkit.getPlayer(curse.getVictimId());
            if (victim != null && victim.isOnline()) {
                forceRemoveCurse(victim);
            }
        }

        activeCurses.clear();
        curseBossBars.clear();
        wedkaCooldowns.clear();
        bugowanieResetTimers.clear();
    }

    // ==================== INNER CLASS ====================

    public static class CurseData {
        private final UUID victimId;
        private final UUID attackerId;
        private final long expirationTime;
        private final int totalDuration;
        private boolean waitingForFlight;
        private boolean wasReleased;
        private boolean bugowanieTick;

        // ✅ Flaga "był na ziemi" - żeby blokować bugowanie przy pierwszym skoku
        private boolean justOnGround;

        public CurseData(UUID victimId, UUID attackerId, long expirationTime, int totalDuration) {
            this.victimId = victimId;
            this.attackerId = attackerId;
            this.expirationTime = expirationTime;
            this.totalDuration = totalDuration;
            this.waitingForFlight = false;
            this.wasReleased = false;
            this.bugowanieTick = false;
            this.justOnGround = true; // Na początku gracz stoi na ziemi
        }

        public UUID getVictimId() { return victimId; }
        public UUID getAttackerId() { return attackerId; }
        public long getExpirationTime() { return expirationTime; }
        public int getTotalDuration() { return totalDuration; }

        public int getRemainingSeconds() {
            long remaining = expirationTime - System.currentTimeMillis();
            return (int) Math.max(0, remaining / 1000);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public boolean isWaitingForFlight() { return waitingForFlight; }
        public void setWaitingForFlight(boolean w) { this.waitingForFlight = w; }

        public boolean isWasReleased() { return wasReleased; }
        public void setWasReleased(boolean r) { this.wasReleased = r; }

        public boolean isBugowanieTick() { return bugowanieTick; }
        public void markBugowanieTick() { this.bugowanieTick = true; }
        public void resetBugowanieTick() { this.bugowanieTick = false; }

        // ✅ Flaga "był na ziemi" - zapobiega bugowaniu przy pierwszym skoku
        public boolean wasJustOnGround() { return justOnGround; }
        public void setJustOnGround(boolean v) { this.justOnGround = v; }
    }
}
