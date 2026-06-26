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

public class KroliczyMieczManager {

    private final AnaItemy plugin;

    // ✅ Cooldown per-item: przechowujemy UUID ItemStacka (przez NBT marker)
    // Ale ponieważ to ten sam item type, używamy UUID gracza
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> jumpBlocked = new ConcurrentHashMap<>();

    public KroliczyMieczManager(AnaItemy plugin) {
        this.plugin = plugin;

        // Cleanup + utrzymywanie jump block
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(jumpBlocked.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) {
                        // Klątwa wygasła
                        jumpBlocked.remove(uuid);

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            player.removePotionEffect(PotionEffectType.JUMP);

                            // ✅ Nałóż 4s protection od KOŃCA klątwy
                            plugin.getItemProtectionManager().applyProtection(player, "kroliczy-miecz");
                        }
                        continue;
                    }

                    // Utrzymuj efekt jump block
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        jumpBlocked.remove(uuid);
                        continue;
                    }

                    // ✅ Odnawiaj efekt jump boost -128 co sekundę
                    if (!player.hasPotionEffect(PotionEffectType.JUMP)) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.JUMP, 100, 128, false, false, false));
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

        // ✅ Cooldown TYLKO na tym typie miecza - używamy setCooldown per-material
        // Ale netherite_sword jest współdzielony z Excaliburem
        // Więc NIE ustawiamy material cooldown - gracz widzi cooldown przez efekt
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    // ==================== ATAK ====================

    public boolean attack(Player attacker, Player victim) {
        ItemsConfig config = plugin.getItemsConfig();

        // ✅ Cooldown - cicho, gracz dalej bije normalnie
        if (isOnCooldown(attacker)) {
            return false;
        }

        // Region
        if (isInBlockedRegion(attacker.getLocation()) || isInBlockedRegion(victim.getLocation())) {
            return false;
        }

        // 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "kroliczy-miecz")) {
            int secondsLeft = plugin.getItemProtectionManager()
                    .getRemainingSeconds(victim, "kroliczy-miecz");
            plugin.getItemProtectionManager()
                    .notifyAttacker(attacker, "kroliczy-miecz", secondsLeft);
            return false;
        }

        // ✅ 1. Zablokuj skakanie na 4 sekundy
        int curseDuration = config.getKroliczyMieczCurseDuration();
        jumpBlocked.put(victim.getUniqueId(),
                System.currentTimeMillis() + (curseDuration * 1000L));

        // ✅ Jump boost level 128 = nie można skakać (ujemny skok)
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP, curseDuration * 20 + 10, 128, false, false, false));

        // ✅ 2. Subtitle dla atakującego
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

        // ✅ 3. Title/subtitle dla ofiary
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

        // ✅ 4. Dźwięk
        victim.playSound(victim.getLocation(), Sound.ENTITY_RABBIT_HURT,
                SoundCategory.PLAYERS, 1.0f, 0.5f);

        // ✅ 5. Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        // ✅ 6. Cooldown
        setCooldown(attacker);

        // ✅ 7. Particle
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                victim.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

        return true;
    }

    // ==================== CHECKS ====================

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
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    public void cleanup() {
        for (UUID uuid : jumpBlocked.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.removePotionEffect(PotionEffectType.JUMP);
            }
        }
        cooldowns.clear();
        jumpBlocked.clear();
    }

    public void cleanupPlayer(Player player) {
        removeJumpBlock(player);
    }
}
