package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SuperMarchewkaManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveEffect> activeEffects = new ConcurrentHashMap<>();

    private static final NamespacedKey SCALE_KEY = new NamespacedKey("anaitemy", "marchewka_scale");
    private static final NamespacedKey CRIT_KEY = new NamespacedKey("anaitemy", "marchewka_crit");

    public SuperMarchewkaManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());

                for (ActiveEffect effect : new ArrayList<>(activeEffects.values())) {
                    if (effect.isExpired()) {
                        Player player = Bukkit.getPlayer(effect.getPlayerId());
                        if (player != null && player.isOnline()) {
                            removeEffect(player);
                        } else {
                            activeEffects.remove(effect.getPlayerId());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
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
        ItemsConfig config = plugin.getItemsConfig();
        long seconds = config.getSuperMarchewkaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.GOLDEN_CARROT, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.GOLDEN_CARROT, 0);
    }

    public void activate(Player player, boolean inHydroKlatka) {
        ItemsConfig config = plugin.getItemsConfig();

        if (activeEffects.containsKey(player.getUniqueId())) {
            removeEffect(player);
        }

        setCooldown(player);

        int effectDuration = config.getSuperMarchewkaEffectDuration();
        int effectTicks = effectDuration * 20;

        if (inHydroKlatka) {
            applyScale(player, -0.5);
            // ✅ 1.21.4 - nowe nazwy PotionEffectType
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, effectTicks, 2, false, true, true));

            applyCritBoost(player);

            player.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaMiniTitle()),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaMiniSubtitle()),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500))
            ));
        } else {
            applyScale(player, 1.0);
            // ✅ 1.21.4 - nowe nazwy PotionEffectType
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, effectTicks, 2, false, true, true));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, effectTicks, 1, false, true, true));

            applyCritBoost(player);

            player.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaSuperTitle()),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaSuperSubtitle()),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500))
            ));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, inHydroKlatka ? 1.5f : 0.7f);

        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);

        long expirationTime = System.currentTimeMillis() + (effectDuration * 1000L);
        activeEffects.put(player.getUniqueId(),
                new ActiveEffect(player.getUniqueId(), expirationTime, inHydroKlatka));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeEffects.containsKey(player.getUniqueId())) {
                if (player.isOnline()) removeEffect(player);
                else activeEffects.remove(player.getUniqueId());
            }
        }, effectTicks);
    }

    private void applyScale(Player player, double scaleModifier) {
        // ✅ 1.21.4 - nowa nazwa atrybutu
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr == null) return;

        removeScaleModifier(scaleAttr);

        scaleAttr.addModifier(new AttributeModifier(
                SCALE_KEY,
                scaleModifier,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void removeScaleModifier(AttributeInstance attribute) {
        attribute.removeModifier(SCALE_KEY);
    }

    private void applyCritBoost(Player player) {
        // ✅ 1.21.4 - nowa nazwa atrybutu
        AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage == null) return;

        removeCritModifier(attackDamage);

        attackDamage.addModifier(new AttributeModifier(
                CRIT_KEY,
                0.2,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void removeCritModifier(AttributeInstance attribute) {
        attribute.removeModifier(CRIT_KEY);
    }

    public void removeEffect(Player player) {
        ActiveEffect effect = activeEffects.remove(player.getUniqueId());
        if (effect == null) return;

        // ✅ 1.21.4 - nowe nazwy atrybutów
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) removeScaleModifier(scaleAttr);

        AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) removeCritModifier(attackDamage);

        // ✅ 1.21.4 - nowe nazwy PotionEffectType
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&7Efekt marchewki &fwygasł&7!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
        ));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public boolean hasActiveEffect(Player player) {
        return activeEffects.containsKey(player.getUniqueId());
    }

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location, plugin.getItemsConfig().getSuperMarchewkaBlockedRegions());
    }

    public void cleanup() {
        for (ActiveEffect effect : new ArrayList<>(activeEffects.values())) {
            Player player = Bukkit.getPlayer(effect.getPlayerId());
            if (player != null && player.isOnline()) removeEffect(player);
        }
        activeEffects.clear();
        cooldowns.clear();
    }

    public void cleanupPlayer(Player player) {
        if (activeEffects.containsKey(player.getUniqueId())) removeEffect(player);

        // ✅ 1.21.4 - nowe nazwy atrybutów
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) removeScaleModifier(scaleAttr);

        AttributeInstance attackDamage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) removeCritModifier(attackDamage);
    }

    public static class ActiveEffect {
        private final UUID playerId;
        private final long expirationTime;
        private final boolean mini;

        public ActiveEffect(UUID playerId, long expirationTime, boolean mini) {
            this.playerId = playerId;
            this.expirationTime = expirationTime;
            this.mini = mini;
        }

        public UUID getPlayerId() { return playerId; }
        public boolean isMini() { return mini; }
        public boolean isExpired() { return System.currentTimeMillis() >= expirationTime; }
    }
}
