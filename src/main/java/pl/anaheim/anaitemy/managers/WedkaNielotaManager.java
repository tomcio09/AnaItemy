package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WedkaNielotaManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> wedkaCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, CurseData> activeCurses = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> curseBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bugowanieResetTimers = new ConcurrentHashMap<>();

    public WedkaNielotaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
        startFallLimitTask();
    }

    // ==================== CLEANUP TASK ====================

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                wedkaCooldowns.entrySet().removeIf(e -> now >= e.getValue());
                bugowanieResetTimers.entrySet().removeIf(e -> now >= e.getValue());

                for (CurseData curse : new ArrayList<>(activeCurses.values())) {
                    updateBossBar(curse);

                    if (!curse.isWaitingForFlight() && curse.isExpired()) {
                        handleCurseExpired(curse);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==================== FALL LIMIT TASK ====================

    /**
     * ✅ GŁÓWNA LOGIKA BUGOWANIA:
     *
     * Co tick sprawdzamy każdego gracza z klątwą który:
     * - Jest w powietrzu (nie na ziemi, nie w wodzie)
     * - Nie leci elytrą
     * - Ma elytrę założoną
     *
     * Jeśli gracz spada szybciej niż limit (fallSpeed z configu) ORAZ
     * bugowanie nie jest zablokowane (po uderzeniu) ORAZ
     * gracz klikał spację (oznaczony przez spaceClicked) →
     * → ustaw velocity Y na -fallSpeed/20
     *
     * Dzięki temu:
     * - Gracz który NIE klika spacji spada normalnie (vanilla)
     * - Gracz który klika spację zwalnia do max fallSpeed bloków/s
     * - Im częściej klika tym lepiej utrzymuje limit (do max 2-3 bloków/s)
     */
    private void startFallLimitTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (CurseData curse : new ArrayList<>(activeCurses.values())) {
                    if (curse.isWaitingForFlight()) continue;

                    Player victim = Bukkit.getPlayer(curse.getVictimId());
                    if (victim == null || !victim.isOnline()) continue;

                    if (victim.isOnGround()) continue;
                    if (victim.isInWater()) continue;
                    if (victim.isGliding()) continue;

                    ItemStack chestplate = victim.getInventory().getChestplate();
                    boolean hasElytra = chestplate != null &&
                            chestplate.getType() == Material.ELYTRA;
                    if (!hasElytra) continue;

                    double currentVelY = victim.getVelocity().getY();

                    if (currentVelY < 0 && curse.isSpaceClicked()) {
                        if (!isBugowanieBlocked(victim)) {
                            ItemsConfig config = plugin.getItemsConfig();
                            double fallSpeed = config.getWedkaNielotaBugowanieFallSpeed();
                            double maxVelY = -(fallSpeed / 20.0);

                            if (currentVelY < maxVelY) {
                                Vector vel = victim.getVelocity();
                                vel.setY(maxVelY);

                                // ✅ RUCH DO PRZODU - prędkość chodzenia (~4.3 bloków/s = 0.215/tick)
                                // Kierunek patrzenia gracza (tylko X i Z, bez Y)
                                Vector direction = victim.getLocation().getDirection();
                                direction.setY(0).normalize();

                                // Szybkość chodzenia (bez sprintu)
                                double walkSpeed = 0.215;

                                vel.setX(direction.getX() * walkSpeed);
                                vel.setZ(direction.getZ() * walkSpeed);

                                victim.setVelocity(vel);
                            }
                        }
                        curse.resetSpaceClicked();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== COOLDOWN ====================

    public boolean isOnCooldown(Player player) {
        Long end = wedkaCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getCooldownRemaining(Player player) {
        Long end = wedkaCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long secs = config.getWedkaNielotaCooldown();
        wedkaCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + secs * 1000);
        player.setCooldown(Material.FISHING_ROD, (int) (secs * 20));
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

        if (attacker != null) {
            showCaughtTitles(victim, attacker);
            setCooldown(attacker);
        } else {
            showCommandCurseTitle(victim);
        }

        createBossBar(victim, curse);
    }

    /**
     * Usuwa klątwę gracefully - jeśli gracz leci → natychmiast,
     * jeśli nie leci → czeka na odlot (BossBar "<1s")
     */
    public void removeCurse(Player victim, boolean wasReleased) {
        CurseData curse = activeCurses.get(victim.getUniqueId());
        if (curse == null) return;

        if (victim.isGliding()) {
            if (wasReleased) {
                showReleasedTitle(victim);
            } else {
                showFreedTitle(victim);
            }
            forceRemoveCurse(victim);
            return;
        }

        // Nie leci - czeka na odlot
        curse.setWaitingForFlight(true);
        curse.setWasReleased(wasReleased);
    }

    /**
     * Usuwa klątwę natychmiast - bez wiadomości
     */
    public void forceRemoveCurse(Player victim) {
        activeCurses.remove(victim.getUniqueId());
        BossBar bb = curseBossBars.remove(victim.getUniqueId());
        if (bb != null) victim.hideBossBar(bb);
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
     * ✅ Wywoływane gdy EntityToggleGlideEvent zostaje ANULOWANY
     * (gracz próbował wejść w glide ale klątwa to zablokowała)
     * = gracz kliknął spację w powietrzu
     * Oznaczamy że w tym ticku kliknął spację → fall limit task zastosuje ograniczenie
     */
    public void markSpaceClicked(Player player) {
        CurseData curse = activeCurses.get(player.getUniqueId());
        if (curse == null) return;
        if (curse.isWaitingForFlight()) return;
        curse.markSpaceClicked();
    }

    public void resetBugowanie(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int resetTicks = config.getWedkaNielotaBugowanieResetDuration();
        bugowanieResetTimers.put(player.getUniqueId(),
                System.currentTimeMillis() + (resetTicks * 50L));
    }

    public boolean isBugowanieBlocked(Player player) {
        Long end = bugowanieResetTimers.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
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

        BossBar bb = BossBar.bossBar(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                1.0f, color, BossBar.Overlay.PROGRESS
        );

        victim.showBossBar(bb);
        curseBossBars.put(victim.getUniqueId(), bb);
    }

    private void updateBossBar(CurseData curse) {
        Player victim = Bukkit.getPlayer(curse.getVictimId());
        if (victim == null || !victim.isOnline()) return;

        BossBar bb = curseBossBars.get(curse.getVictimId());
        if (bb == null) return;

        ItemsConfig config = plugin.getItemsConfig();

        if (curse.isWaitingForFlight()) {
            bb.name(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(config.getWedkaNielotaBossBarTitleWaiting()));
            bb.progress(0.01f);
        } else {
            int remaining = curse.getRemainingSeconds();
            int total = curse.getTotalDuration();
            String title = config.getWedkaNielotaBossBarTitle()
                    .replace("{seconds}", String.valueOf(remaining));
            bb.name(LegacyComponentSerializer.legacyAmpersand().deserialize(title));
            float progress = total > 0
                    ? Math.max(0.01f, Math.min(1.0f, (float) remaining / total))
                    : 0.01f;
            bb.progress(progress);
        }
    }

    // ==================== WYGAŚNIĘCIE ====================

    private void handleCurseExpired(CurseData curse) {
        Player victim = Bukkit.getPlayer(curse.getVictimId());
        if (victim == null || !victim.isOnline()) {
            activeCurses.remove(curse.getVictimId());
            curseBossBars.remove(curse.getVictimId());
            return;
        }

        if (victim.isGliding()) {
            showFreedTitle(victim);
            forceRemoveCurse(victim);
            return;
        }

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
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
        attacker.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCatcherTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCatcherSubtitle()
                                .replace("{victim}", victim.getName())),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    private void showCommandCurseTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();
        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaCaughtTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&7Nałożono na Ciebie klątwę!"),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    public void showReleasedTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();
        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaReleasedTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaReleasedSubtitle()),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    public void showFreedTitle(Player victim) {
        ItemsConfig config = plugin.getItemsConfig();
        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaFreedTitle()),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getWedkaNielotaFreedSubtitle()),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
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

        // ✅ Flaga: gracz kliknął spację w tym ticku
        private volatile boolean spaceClicked;

        public CurseData(UUID victimId, UUID attackerId, long expirationTime, int totalDuration) {
            this.victimId = victimId;
            this.attackerId = attackerId;
            this.expirationTime = expirationTime;
            this.totalDuration = totalDuration;
            this.waitingForFlight = false;
            this.wasReleased = false;
            this.spaceClicked = false;
        }

        public UUID getVictimId() { return victimId; }
        public UUID getAttackerId() { return attackerId; }
        public int getTotalDuration() { return totalDuration; }

        public int getRemainingSeconds() {
            return (int) Math.max(0, (expirationTime - System.currentTimeMillis()) / 1000);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public boolean isWaitingForFlight() { return waitingForFlight; }
        public void setWaitingForFlight(boolean w) { this.waitingForFlight = w; }

        public boolean isWasReleased() { return wasReleased; }
        public void setWasReleased(boolean r) { this.wasReleased = r; }

        // ✅ SpaceClicked - wykrywanie kliknięcia spacji
        public boolean isSpaceClicked() { return spaceClicked; }
        public void markSpaceClicked() { this.spaceClicked = true; }
        public void resetSpaceClicked() { this.spaceClicked = false; }
    }
}
