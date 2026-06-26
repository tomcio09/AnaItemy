package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KukurydzaManager {

    private static final String META_KUKURYDZA = "anaitemy_kukurydza_fireball";

    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public KukurydzaManager(AnaItemy plugin) {
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
        long seconds = plugin.getItemsConfig().getKukurydzaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void shoot(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isOnCooldown(player)) {
            long remaining = getCooldownRemaining(player);
            String subtitle = config.getKukurydzaCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");
            player.showTitle(Title.title(Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));
            return;
        }

        if (isInBlockedRegion(player.getLocation())) return;

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Fireball fireball = player.getWorld().spawn(eye.add(direction), Fireball.class);
        fireball.setShooter(player);
        fireball.setDirection(direction);
        fireball.setYield(0f);
        fireball.setIsIncendiary(false);
        fireball.setMetadata(META_KUKURYDZA, new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 1.0f);

        String attackerSub = config.getKukurydzaAttackerSubtitle();
        player.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        setCooldown(player);
    }

    public void handleImpact(Fireball fireball) {
        if (!fireball.hasMetadata(META_KUKURYDZA)) return;

        Location impact = fireball.getLocation();
        World world = impact.getWorld();
        double radius = plugin.getItemsConfig().getKukurydzaRadius();
        int durabilityDamage = plugin.getItemsConfig().getKukurydzaDurabilityDamage();

        Player shooter = null;
        try {
            String raw = fireball.getMetadata(META_KUKURYDZA).get(0).asString();
            shooter = Bukkit.getPlayer(UUID.fromString(raw));
        } catch (Exception ignored) {}

        // Particle
        world.spawnParticle(Particle.EXPLOSION_LARGE, impact, 5, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.FLAME, impact, 30, 1, 1, 1, 0.1);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.5f, 1.0f);

        for (Player target : world.getNearbyPlayers(impact, radius)) {
            if (shooter != null && target.equals(shooter)) continue;

            if (isInBlockedRegion(target.getLocation())) continue;

            // 4s protection
            if (plugin.getItemProtectionManager().isProtected(target, "kukurydza")) continue;

            // ✅ Zadaj durability damage na zbroi
            damageArmor(target, durabilityDamage);

            plugin.getItemProtectionManager().applyProtection(target, "kukurydza");

            if (shooter != null && plugin.getCombatIntegrationManager().isEnabled()) {
                plugin.getCombatIntegrationManager().tagPlayer(target, shooter);
                plugin.getCombatIntegrationManager().tagPlayer(shooter, target);
            }
        }

        fireball.remove();
    }

    private void damageArmor(Player player, int amount) {
        ItemStack[] armor = player.getInventory().getArmorContents();

        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType().isAir()) continue;

            ItemMeta meta = piece.getItemMeta();
            if (meta == null) continue;

            // Nie niszcz unbreakable
            if (meta.isUnbreakable()) continue;

            if (meta instanceof Damageable damageable) {
                int maxDurability = piece.getType().getMaxDurability();
                if (maxDurability <= 0) continue;

                int currentDamage = damageable.getDamage();
                int newDamage = currentDamage + amount;

                if (newDamage >= maxDurability) {
                    // Zbroja się zepsuła
                    armor[i] = null;
                } else {
                    damageable.setDamage(newDamage);
                    piece.setItemMeta(meta);
                }
            }
        }

        player.getInventory().setArmorContents(armor);
    }

    public boolean isKukurydzaFireball(Fireball fireball) {
        return fireball.hasMetadata(META_KUKURYDZA);
    }

    private boolean isInBlockedRegion(Location loc) {
        return plugin.getWorldGuardManager().isInNamedRegion(loc,
                plugin.getItemsConfig().getKukurydzaBlockedRegions());
    }

    public void cleanup() { cooldowns.clear(); }
}
