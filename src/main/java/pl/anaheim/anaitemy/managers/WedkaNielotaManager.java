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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WedkaNielotaManager {

    private final AnaItemy plugin;

    // Cooldowny wędki
    private final Map<UUID, Long> wedkaCooldowns = new ConcurrentHashMap<>();

    // Aktywne klątwy
    private final Map<UUID, CurseData> activeCurses = new ConcurrentHashMap<>();

    // BossBary dla złapanych graczy
    private final Map<UUID, BossBar> curseBossBars = new ConcurrentHashMap<>();

    // Resetowanie bugowania po uderzeniu
    private final Map<UUID, Long> bugowanieResetTimers = new ConcurrentHashMap<>();

    // Licznik kliknięć spacji (do bugowania)
    private final Map<UUID, Integer> spaceClickCounts = new ConcurrentHashMap<>();

    public WedkaNielotaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // Cleanup cooldownów
                wedkaCooldowns.entrySet().removeIf(entry -> now >= entry.getValue());

                // Cleanup bugowanie reset timers
                bugowanieResetTimers.entrySet().removeIf(entry -> now >= entry.getValue());

                // Update BossBars
                for (CurseData curse : activeCurses.values()) {
                    updateBossBar(curse);

                    // Sprawdź czy klątwa wygasła
                    if (curse.isExpired()) {
                        handleCurseExpired(curse);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Co tick dla płynności
    }

    // ==================== COOLDOWN ====================

    public boolean isOnCooldown(Player player) {
        Long cooldownEnd = wedkaCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    public void setCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getWedkaNielotaCooldown();
        wedkaCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000));

        // Wizualny cooldown na slocie
        player.setCooldown(Material.FISHING_ROD, (int) (cooldownSeconds * 20));
    }

    public void resetCooldown(Player player) {
        wedkaCooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.FISHING_ROD, 0);
    }

    // ==================== KLĄTWA ====================

    public void applyCurse(Player victim, Player attacker) {
        ItemsConfig config = plugin.getItemsConfig();

        // Jeśli już ma klątwę - zresetuj czas
        if (activeCurses.containsKey(victim.getUniqueId())) {
            removeCurse(victim, false);
        }

        int duration = config.getWedkaNielotaCurseDuration();
        CurseData curse = new CurseData(
                victim.getUniqueId(),
                attacker.getUniqueId(),
                System.currentTimeMillis() + (duration * 1000L),
                duration
        );

        activeCurses.put(victim.getUniqueId(), curse);

        // Titles
        showCaughtTitles(victim, attacker);

        // BossBar
        createBossBar(victim, curse);

        // Cooldown dla atakującego
        setCooldown(attacker);
    }

    public void removeCurse(Player victim, boolean showMessage) {
        CurseData curse = activeCurses.remove(victim.getUniqueId());
        if (curse == null) return;

        // Usuń BossBar
        BossBar bossBar = curseBossBars.remove(victim.getUniqueId());
        if (bossBar != null) {
            victim.hideBossBar(bossBar);
        }

        // Wiadomość tylko jeśli gracz odleci
        if (showMessage && victim.isGliding()) {
            showReleasedTitle(victim);
        } else if (showMessage) {
            // Gracz jeszcze nie leci - ustaw flagę "czeka na odlot"
            curse.setWaitingForFlight(true);
            activeCurses.put(victim.getUniqueId(), curse);
        }
    }

    public boolean hasCurse(Player player) {
        return activeCurses.containsKey(player.getUniqueId());
    }

    public CurseData getCurse(Player player) {
        return activeCurses.get(player.getUniqueId());
    }

    // ==================== BUGOWANIE ====================

    public void handleSpaceClick(Player player) {
        CurseData curse = getCurse(player);
        if (curse == null) return;
        if (curse.isWaitingForFlight()) return; // Klątwa wygasła, czeka na odlot

        // Sprawdź czy bugowanie jest zablokowane (po uderzeniu)
        if (isBugowanieBlocked(player)) return;

        // Zwiększ licznik kliknięć
        int clicks = spaceClickCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        spaceClickCounts.put(player.getUniqueId(), clicks);

        // Reset licznika po 1 sekundzie
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spaceClickCounts.remove(player.getUniqueId());
        }, 20L);

        // Zastosuj wolne opadanie
        applySlowFall(player);
    }

    private void applySlowFall(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        double fallSpeed = config.getWedkaNielotaBugowanieFallSpeed();

        Vector velocity = player.getVelocity();

        // Ustaw Y velocity na ujemną wartość (opadanie)
        // fallSpeed = bloki/sekundę → podzielić przez 20 (ticki)
        double fallVelocity = -fallSpeed / 20.0;

        velocity.setY(fallVelocity);

        // Jeśli gracz trzyma W - lekki ruch do przodu
        if (player.isSneaking() || player.isFlying()) {
            // Nie robimy nic specjalnego - gracz sam kontroluje kierunek
        }

        player.setVelocity(velocity);
    }

    public void resetBugowanie(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int resetDuration = config.getWedkaNielotaBugowanieResetDuration();

        bugowanieResetTimers.put(player.getUniqueId(),
                System.currentTimeMillis() + (resetDuration * 50L)); // ticki → ms
    }

    public boolean isBugowanieBlocked(Player player) {
        Long resetEnd = bugowanieResetTimers.get(player.getUniqueId());
        return resetEnd != null && System.currentTimeMillis() < resetEnd;
    }

    // ==================== BOSSBAR ====================

    private void createBossBar(Player victim, CurseData curse) {
        ItemsConfig config = plugin.getItemsConfig();

        String title = config.getWedkaNielotaBossBarTitle()
                .replace("{seconds}", String.valueOf(curse.getRemainingSeconds()));

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
        int remaining = curse.getRemainingSeconds();
        int total = curse.getTotalDuration();

        if (curse.isWaitingForFlight()) {
            // Klątwa wygasła - pokaż "<1s"
            String titleWaiting = config.getWedkaNielotaBossBarTitleWaiting();
            bossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(titleWaiting));
            bossBar.progress(0.0f);
        } else {
            // Normalne odliczanie
            String title = config.getWedkaNielotaBossBarTitle()
                    .replace("{seconds}", String.valueOf(remaining));
            bossBar.name(LegacyComponentSerializer.legacyAmpersand().deserialize(title));

            float progress = total > 0 ? Math.max(0.0f, (float) remaining / total) : 0.0f;
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

        // Ustaw flagę "czeka na odlot"
        curse.setWaitingForFlight(true);

        // Jeśli gracz już leci - pokaż od razu wiadomość
        if (victim.isGliding()) {
            showFreedTitle(victim);
            removeCurse(victim, false);
        }
        // Jeśli nie leci - BossBar zamienia się na "<1s" i czeka
    }

    // ==================== TITLES ====================

    private void showCaughtTitles(Player victim, Player attacker) {
        ItemsConfig config = plugin.getItemsConfig();

        // Dla złapanego
        String caughtTitle = config.getWedkaNielotaCaughtTitle();
        String caughtSubtitle = config.getWedkaNielotaCaughtSubtitle()
                .replace("{attacker}", attacker.getName());

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(caughtTitle),
                LegacyComponentSerializer.legacyAmpersand().deserialize(caughtSubtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));

        // Dla łowiącego
        String catcherTitle = config.getWedkaNielotaCatcherTitle();
        String catcherSubtitle = config.getWedkaNielotaCatcherSubtitle()
                .replace("{victim}", victim.getName());

        attacker.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(catcherTitle),
                LegacyComponentSerializer.legacyAmpersand().deserialize(catcherSubtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    public void showReleasedTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        String title = config.getWedkaNielotaReleasedTitle();
        String subtitle = config.getWedkaNielotaReleasedSubtitle();

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    public void showFreedTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        String title = config.getWedkaNielotaFreedTitle();
        String subtitle = config.getWedkaNielotaFreedSubtitle();

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (UUID victimId : activeCurses.keySet()) {
            Player victim = Bukkit.getPlayer(victimId);
            if (victim != null) {
                removeCurse(victim, false);
            }
        }

        wedkaCooldowns.clear();
        activeCurses.clear();
        curseBossBars.clear();
        bugowanieResetTimers.clear();
        spaceClickCounts.clear();
    }

    // ==================== INNER CLASS ====================

    public static class CurseData {
        private final UUID victimId;
        private final UUID attackerId;
        private final long expirationTime;
        private final int totalDuration;
        private boolean waitingForFlight;

        public CurseData(UUID victimId, UUID attackerId, long expirationTime, int totalDuration) {
            this.victimId = victimId;
            this.attackerId = attackerId;
            this.expirationTime = expirationTime;
            this.totalDuration = totalDuration;
            this.waitingForFlight = false;
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
        public void setWaitingForFlight(boolean waiting) { this.waitingForFlight = waiting; }
    }
}
