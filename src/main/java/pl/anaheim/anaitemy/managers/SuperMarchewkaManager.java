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

    private static final UUID SCALE_MODIFIER_UUID = UUID.fromString("B2C3D4E5-F6A7-8901-BCDE-F12345678901");
    private static final String SCALE_MODIFIER_NAME = "super_marchewka_scale";

    private static final UUID CRIT_MODIFIER_UUID = UUID.fromString("C3D4E5F6-A7B8-9012-CDEF-123456789012");
    private static final String CRIT_MODIFIER_NAME = "super_marchewka_crit";

    public SuperMarchewkaManager(AnaItemy plugin) {
        this.plugin = plugin;

        // Cleanup task
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
        long seconds = config.getSuperMarchewkaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.GOLDEN_CARROT, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.GOLDEN_CARROT, 0);
    }

    // ==================== AKTYWACJA ====================

    public void activate(Player player, boolean inHydroKlatka) {
        ItemsConfig config = plugin.getItemsConfig();

        // Jeśli już ma aktywny efekt - usuń stary
        if (activeEffects.containsKey(player.getUniqueId())) {
            removeEffect(player);
        }

        setCooldown(player);

        int effectDuration = config.getSuperMarchewkaEffectDuration();
        int effectTicks = effectDuration * 20;

        if (inHydroKlatka) {
            // ✅ MINI MARCHEWKA - pomniejszenie o 50%
            applyScale(player, 0.5);

            // Odporność III
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DAMAGE_RESISTANCE, effectTicks, 2, false, true, true));

            // Zwiększone obrażenia krytyczne 1.2x
            applyCritBoost(player);

            // Title/subtitle
            player.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaMiniTitle()),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaMiniSubtitle()),
                    Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofMillis(3000),
                            Duration.ofMillis(500)
                    )
            ));
        } else {
            // ✅ SUPER MARCHEWKA - powiększenie x2
            applyScale(player, 2.0);

            // Odporność III
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DAMAGE_RESISTANCE, effectTicks, 2, false, true, true));

            // Spowolnienie II
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW, effectTicks, 1, false, true, true));

            // Zwiększone obrażenia krytyczne 1.2x
            applyCritBoost(player);

            // Title/subtitle
            player.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaSuperTitle()),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(config.getSuperMarchewkaSuperSubtitle()),
                    Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofMillis(3000),
                            Duration.ofMillis(500)
                    )
            ));
        }

        // Dźwięk
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, inHydroKlatka ? 1.5f : 0.7f);

        // Particle
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);

        // Zapisz aktywny efekt
        long expirationTime = System.currentTimeMillis() + (effectDuration * 1000L);
        activeEffects.put(player.getUniqueId(),
                new ActiveEffect(player.getUniqueId(), expirationTime, inHydroKlatka));

        // Zaplanuj automatyczne usunięcie
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeEffects.containsKey(player.getUniqueId())) {
                if (player.isOnline()) {
                    removeEffect(player);
                } else {
                    activeEffects.remove(player.getUniqueId());
                }
            }
        }, effectTicks);
    }

    // ==================== SKALOWANIE ====================

    private void applyScale(Player player, double scale) {
        try {
            // ✅ Paper 1.20.1+ ma GENERIC_SCALE
            AttributeInstance scaleAttr = player.getAttribute(Attribute.valueOf("GENERIC_SCALE"));
            if (scaleAttr != null) {
                removeScaleModifier(scaleAttr);

                double modifier = scale - 1.0; // 0.5 -> -0.5, 2.0 -> 1.0
                scaleAttr.addModifier(new AttributeModifier(
                        SCALE_MODIFIER_UUID,
                        SCALE_MODIFIER_NAME,
                        modifier,
                        AttributeModifier.Operation.ADD_SCALAR
                ));
            }
        } catch (IllegalArgumentException e) {
            // GENERIC_SCALE nie istnieje w tej wersji - ignoruj
            plugin.getLogger().warning("[SuperMarchewka] Atrybut GENERIC_SCALE niedostępny - skalowanie wyłączone.");
        }
    }

    private void removeScaleModifier(AttributeInstance attribute) {
        for (AttributeModifier mod : new ArrayList<>(attribute.getModifiers())) {
            if (mod.getUniqueId().equals(SCALE_MODIFIER_UUID)
                    || SCALE_MODIFIER_NAME.equals(mod.getName())) {
                attribute.removeModifier(mod);
            }
        }
    }

    // ==================== CRIT BOOST ====================

    private void applyCritBoost(Player player) {
        AttributeInstance attackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamage == null) return;

        removeCritModifier(attackDamage);

        // 1.2x = +20% obrażeń
        attackDamage.addModifier(new AttributeModifier(
                CRIT_MODIFIER_UUID,
                CRIT_MODIFIER_NAME,
                0.2,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void removeCritModifier(AttributeInstance attribute) {
        for (AttributeModifier mod : new ArrayList<>(attribute.getModifiers())) {
            if (mod.getUniqueId().equals(CRIT_MODIFIER_UUID)
                    || CRIT_MODIFIER_NAME.equals(mod.getName())) {
                attribute.removeModifier(mod);
            }
        }
    }

    // ==================== USUWANIE EFEKTU ====================

    public void removeEffect(Player player) {
        ActiveEffect effect = activeEffects.remove(player.getUniqueId());
        if (effect == null) return;

        // Usuń skalowanie
        try {
            AttributeInstance scaleAttr = player.getAttribute(Attribute.valueOf("GENERIC_SCALE"));
            if (scaleAttr != null) {
                removeScaleModifier(scaleAttr);
            }
        } catch (IllegalArgumentException ignored) {}

        // Usuń crit boost
        AttributeInstance attackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamage != null) {
            removeCritModifier(attackDamage);
        }

        // Usuń efekty mikstur
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.SLOW);

        // Subtitle powrotu
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&7Wróciłeś do &fnormalnego rozmiaru&7!"),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // ==================== CHECKS ====================

    public boolean hasActiveEffect(Player player) {
        return activeEffects.containsKey(player.getUniqueId());
    }

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(
                location,
                plugin.getItemsConfig().getSuperMarchewkaBlockedRegions()
        );
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (ActiveEffect effect : new ArrayList<>(activeEffects.values())) {
            Player player = Bukkit.getPlayer(effect.getPlayerId());
            if (player != null && player.isOnline()) {
                removeEffect(player);
            }
        }

        activeEffects.clear();
        cooldowns.clear();
    }

    // ✅ Usuwanie modifierów przy śmierci/wylogowaniu
    public void cleanupPlayer(Player player) {
        if (activeEffects.containsKey(player.getUniqueId())) {
            removeEffect(player);
        }

        // Na wszelki wypadek - wyczyść modifery nawet bez aktywnego efektu
        try {
            AttributeInstance scaleAttr = player.getAttribute(Attribute.valueOf("GENERIC_SCALE"));
            if (scaleAttr != null) removeScaleModifier(scaleAttr);
        } catch (IllegalArgumentException ignored) {}

        AttributeInstance attackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamage != null) removeCritModifier(attackDamage);
    }

    // ==================== INNER CLASS ====================

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

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
    }
}
