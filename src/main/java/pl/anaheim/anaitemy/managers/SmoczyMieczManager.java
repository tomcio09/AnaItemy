package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SmoczyMieczManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // ✅ NamespacedKey do oznaczania naszych pereł
    private final Set<UUID> smoczyPearls = ConcurrentHashMap.newKeySet();

    public SmoczyMieczManager(AnaItemy plugin) {
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
        long seconds = config.getSmoczyMieczCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));

        // ✅ Osobny cooldown widoczny na slocie
        // Nie możemy użyć setCooldown(NETHERITE_SWORD) bo koliduje z innymi mieczami
        // Zamiast tego wyświetlamy subtitle
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    // ==================== RZUCANIE PERŁY ====================

    public void throwPearl(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isOnCooldown(player)) {
            long remaining = getCooldownRemaining(player);
            String subtitle = config.getSmoczyMieczCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");

            player.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(
                            Duration.ofMillis(200),
                            Duration.ofMillis(2000),
                            Duration.ofMillis(200)
                    )
            ));
            return;
        }

        if (isInBlockedRegion(player.getLocation())) {
            return;
        }

        // ✅ Rzuć perłę (nie zabiera z eq gracza)
        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        pearl.setShooter(player);

        // Oznacz jako nasza perła
        smoczyPearls.add(pearl.getUniqueId());

        // Dźwięk
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Particle
        player.getWorld().spawnParticle(Particle.PORTAL,
                player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);

        setCooldown(player);
    }

    public boolean isSmoczyPearl(UUID pearlUUID) {
        return smoczyPearls.remove(pearlUUID);
    }

    // ==================== REGION ====================

    private boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getSmoczyMieczBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        cooldowns.clear();
        smoczyPearls.clear();
    }
}
