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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZatrutyOlowekManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ZatrutyOlowekManager(AnaItemy plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override public void run() {
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
        long seconds = plugin.getItemsConfig().getZatrutyOlowekCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.LIME_CANDLE, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.LIME_CANDLE, 0);
    }
    public void setPostResetCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean attack(Player attacker, Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isOnCooldown(attacker)) {
            long remaining = getCooldownRemaining(attacker);
            String subtitle = config.getZatrutyOlowekCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");
            attacker.showTitle(Title.title(Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));
            return false;
        }

        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) return false;

        if (plugin.getItemProtectionManager().isProtected(victim, "zatruty-olowek")) {
            int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "zatruty-olowek");
            plugin.getItemProtectionManager().notifyAttacker(attacker, "zatruty-olowek", sl);
            return false;
        }

        int weakDuration = config.getZatrutyOlowekWeaknessDuration() * 20;
        int poisonDuration = config.getZatrutyOlowekPoisonDuration() * 20;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weakDuration, 0, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, 0, false, true, true));

        String attackerSub = config.getZatrutyOlowekAttackerSubtitle().replace("{victim}", victim.getName());
        attacker.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        victim.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(config.getZatrutyOlowekVictimSubtitle()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));


        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        plugin.getItemProtectionManager().applyProtection(victim, "zatruty-olowek");
        setCooldown(attacker);
        return true;
    }

    private boolean isInBlockedRegion(Location loc) {
        return plugin.getWorldGuardManager().isInNamedRegion(loc,
                plugin.getItemsConfig().getZatrutyOlowekBlockedRegions());
    }

    public void cleanup() { cooldowns.clear(); }
}
