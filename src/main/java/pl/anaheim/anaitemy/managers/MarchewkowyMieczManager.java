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

public class MarchewkowyMieczManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> frozenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Location> frozenLocations = new ConcurrentHashMap<>();

    private BukkitTask freezeTask;

    public MarchewkowyMieczManager(AnaItemy plugin) {
        this.plugin = plugin;
        startFreezeTask();

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void startFreezeTask() {
        freezeTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(frozenPlayers.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) {
                        frozenPlayers.remove(uuid);
                        frozenLocations.remove(uuid);

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.setGravity(true);
                        }
                        continue;
                    }

                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        frozenPlayers.remove(uuid);
                        frozenLocations.remove(uuid);
                        continue;
                    }

                    Location freezeLoc = frozenLocations.get(uuid);
                    if (freezeLoc != null && player.getLocation().getWorld().equals(freezeLoc.getWorld())) {
                        Location tp = freezeLoc.clone();
                        tp.setYaw(player.getLocation().getYaw());
                        tp.setPitch(player.getLocation().getPitch());
                        player.teleport(tp);
                        player.setVelocity(new Vector(0, 0, 0));
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
        long seconds = config.getMarchewkowyMieczCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.GOLDEN_SWORD, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.GOLDEN_SWORD, 0);
    }
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    // ==================== ZAMROŻENIE ====================

    public boolean attack(Player attacker, Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isOnCooldown(attacker)) {
            long remaining = getCooldownRemaining(attacker);
            String subtitle = config.getMarchewkowyMieczCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");
            attacker.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
            ));
            return false;
        }

        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) {
            return false;
        }

        if (plugin.getItemProtectionManager().isProtected(victim, "marchewkowy-miecz")) {
            int secondsLeft = plugin.getItemProtectionManager()
                    .getRemainingSeconds(victim, "marchewkowy-miecz");
            plugin.getItemProtectionManager()
                    .notifyAttacker(attacker, "marchewkowy-miecz", secondsLeft);
            return false;
        }

        // ✅ Zamroź gracza
        int freezeDuration = config.getMarchewkowyMieczFreezeDuration();
        frozenPlayers.put(victim.getUniqueId(),
                System.currentTimeMillis() + (freezeDuration * 1000L));
        frozenLocations.put(victim.getUniqueId(), victim.getLocation().clone());
        victim.setGravity(false);
        victim.setVelocity(new Vector(0, 0, 0));

        // ✅ Wizualny efekt zamrożenia
        victim.setFreezeTicks(freezeDuration * 20 + 20);

        // ✅ Nałóż protection od POCZĄTKU zamrożenia
        plugin.getItemProtectionManager().applyProtection(victim, "marchewkowy-miecz");

        // Subtitle atakujący
        String attackerSubtitle = config.getMarchewkowyMieczAttackerSubtitle()
                .replace("{nick_victim}", victim.getName());
        attacker.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSubtitle),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
        ));

        // Subtitle ofiara
        victim.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(config.getMarchewkowyMieczVictimSubtitle()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
        ));

        // Dźwięk
        victim.playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 1.0f, 1.5f);

        // Particle
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                victim.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);

        // Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        setCooldown(attacker);
        return true;
    }

    public boolean isFrozen(Player player) {
        Long end = frozenPlayers.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    private boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(location,
                plugin.getItemsConfig().getMarchewkowyMieczBlockedRegions());
    }

    public void cleanupPlayer(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        frozenLocations.remove(player.getUniqueId());
        player.setGravity(true);
    }

    public void cleanup() {
        if (freezeTask != null) freezeTask.cancel();
        for (UUID uuid : frozenPlayers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.setGravity(true);
        }
        frozenPlayers.clear();
        frozenLocations.clear();
        cooldowns.clear();
    }
}
