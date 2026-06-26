package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OslepienieManager {

    private final AnaItemy plugin;

    // Osobne cooldowny dla kosy i łuku kupidyna
    private final Map<UUID, Long> kosaCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lukCooldowns = new ConcurrentHashMap<>();

    public OslepienieManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                kosaCooldowns.entrySet().removeIf(e -> now >= e.getValue());
                lukCooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    // ==================== KOSA COOLDOWN ====================

    public boolean isKosaOnCooldown(Player player) {
        Long end = kosaCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getKosaCooldownRemaining(Player player) {
        Long end = kosaCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setKosaCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long seconds = config.getKosaCooldown();
        kosaCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.NETHERITE_HOE, (int) (seconds * 20));
    }

    public void resetKosaCooldown(Player player) {
        kosaCooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.NETHERITE_HOE, 0);
    }

    // ==================== ŁUK KUPIDYNA COOLDOWN ====================

    public boolean isLukOnCooldown(Player player) {
        Long end = lukCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getLukCooldownRemaining(Player player) {
        Long end = lukCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setLukCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long seconds = config.getLukKupidynaCooldown();
        lukCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void resetLukCooldown(Player player) {
        lukCooldowns.remove(player.getUniqueId());
    }

    // ==================== OŚLEPIENIE ====================

    /**
     * ✅ Nakłada oślepienie na gracza.
     * Używane zarówno przez kosę jak i łuk kupidyna.
     */
    public boolean applyBlindness(Player attacker, Player victim, boolean isKosa) {
        ItemsConfig config = plugin.getItemsConfig();

        // Region check
        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) {
            return false;
        }

        // ✅ Nałóż blindness
        int blindnessDuration = config.getOslepienieDuration();
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, blindnessDuration * 20, 0, false, false, true));

        // ✅ Dźwięk
        victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_AMBIENT,
                SoundCategory.PLAYERS, 0.5f, 0.5f);

        // ✅ Subtitle dla ofiary
        String victimSubtitle = config.getOslepienieVictimSubtitle();
        victim.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(victimSubtitle),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));

        // ✅ Subtitle dla atakującego
        String attackerSubtitle = config.getOslepienieAttackerSubtitle()
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

        // ✅ Particle
        victim.getWorld().spawnParticle(Particle.SMOKE_LARGE,
                victim.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);

        // ✅ Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        // ✅ Cooldown
        if (isKosa) {
            setKosaCooldown(attacker);
        } else {
            setLukCooldown(attacker);
        }

        return true;
    }

    // ==================== REGION ====================

    private boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getOslepienieBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        kosaCooldowns.clear();
        lukCooldowns.clear();
    }
}
