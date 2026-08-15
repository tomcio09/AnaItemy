package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.utils.ArmorReductionHelper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SiekieraGrinchaManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SiekieraGrinchaManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 600L, 600L);
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
        long cooldownSeconds = config.getSiekieraGrinchaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000));
        player.setCooldown(Material.GOLDEN_AXE, (int) (cooldownSeconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.GOLDEN_AXE, 0);
    }

    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    // ==================== ATAK ====================

    public boolean attack(Player attacker, Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isOnCooldown(attacker)) {
            long remaining = getCooldownRemaining(attacker);

            String subtitle = config.getSiekieraGrinchaCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");

            attacker.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofMillis(2000),
                            Duration.ofMillis(250)
                    )
            ));
            return false;
        }

        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) {
            return false;
        }

        if (plugin.getItemProtectionManager().isProtected(victim, "siekiera-grincha")) {
            int secondsLeft = plugin.getItemProtectionManager()
                    .getRemainingSeconds(victim, "siekiera-grincha");

            plugin.getItemProtectionManager()
                    .notifyAttacker(attacker, "siekiera-grincha", secondsLeft);
            return false;
        }

        // ✅ Oblicz 30% AKTUALNEGO zdrowia
        double currentHealth = victim.getHealth();
        double damagePercent = config.getSiekieraGrinchaDamagePercent();
        double damage = currentHealth * (damagePercent / 100.0);

        // ✅ Minimum 1 HP damage
        damage = Math.max(1.0, damage);

        // ✅ Zastosuj redukcję zbroi eventówek
        damage = ArmorReductionHelper.applyArmorReduction(damage, victim);

        // ✅ Nie zabijaj - zostaw minimum 1 HP
        double newHealth = Math.max(1.0, currentHealth - damage);

        // ✅ 1. Piorun wizualny
        victim.getWorld().strikeLightningEffect(victim.getLocation());

        // ✅ 2. Dźwięk pioruna
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 2.0f, 1.0f);

        // ✅ 3. Cząsteczki
        victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0),
                30, 0.5, 1, 0.5, 0.3);

        // ✅ 4. Ustaw zdrowie
        victim.setHealth(newHealth);

        // ✅ 5. Tag combatu
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        // ✅ 6. Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "siekiera-grincha");

        // ✅ 7. Subtitle dla atakującego
        String attackerSubtitle = config.getSiekieraGrinchaAttackerSubtitle()
                .replace("{nick_victim}", victim.getName());

        attacker.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSubtitle),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)
                )
        ));

        // ✅ 8. Subtitle dla ofiary
        String victimSubtitle = config.getSiekieraGrinchaVictimSubtitle();

        victim.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(victimSubtitle),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)
                )
        ));

        // ✅ 9. Cooldown
        setCooldown(attacker);

        return true;
    }

    // ==================== REGION ====================

    private boolean isInBlockedRegion(org.bukkit.Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        java.util.List<String> blockedRegions = config.getSiekieraGrinchaBlockedRegions();
        return plugin.getWorldGuardManager().isInBlockedRegion(location, blockedRegions);
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        cooldowns.clear();
    }
}
