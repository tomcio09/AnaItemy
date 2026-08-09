package pl.anaheim.anaitemy.managers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WedkaSurferkaManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public WedkaSurferkaManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

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
        long seconds = plugin.getItemsConfig().getWedkaSurferkaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public void launchTowards(Player player, Location hookLocation) {
        if (isOnCooldown(player)) return;

        Location playerLoc = player.getLocation();
        if (!playerLoc.getWorld().equals(hookLocation.getWorld())) return;

        double power = plugin.getItemsConfig().getWedkaSurferkaPower();

        Vector direction = hookLocation.toVector().subtract(playerLoc.toVector());

        if (direction.lengthSquared() < 1.0) return;

        direction.normalize().multiply(power);

        if (direction.getY() < 0.3) direction.setY(0.3);

        player.setVelocity(direction);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
                SoundCategory.PLAYERS, 1.0f, 1.2f);

        setCooldown(player);
    }

    public void cleanup() { cooldowns.clear(); }
}
