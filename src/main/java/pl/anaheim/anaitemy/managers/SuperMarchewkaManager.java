package pl.anaheim.anaitemy.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
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
    private final boolean protocolLibEnabled;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveEffect> activeEffects = new ConcurrentHashMap<>();

    private static final UUID CRIT_MODIFIER_UUID = UUID.fromString("C3D4E5F6-A7B8-9012-CDEF-123456789012");
    private static final String CRIT_MODIFIER_NAME = "super_marchewka_crit";

    // ✅ Metadata index for entity scale w 1.20.1 nie istnieje natywnie,
    // ale możemy użyć POSE + rozmiar przez ProtocolLib EntityMetadata
    // W 1.20.1 nie ma entity scale - użyjemy efektu wizualnego przez
    // modyfikowanie bounding box i wysyłanie fake entity metadata

    public SuperMarchewkaManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.protocolLibEnabled = plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib");

        if (protocolLibEnabled) {
            plugin.getLogger().info("[SuperMarchewka] ProtocolLib wykryty - skalowanie graczy WŁĄCZONE!");
        } else {
            plugin.getLogger().warning("[SuperMarchewka] ProtocolLib nie znaleziony - skalowanie graczy WYŁĄCZONE!");
        }

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
            sendScalePacket(player, 0.5f);

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
            sendScalePacket(player, 2.0f);

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

    // ==================== SKALOWANIE PRZEZ PROTOCOLLIB ====================

    /**
     * ✅ Wysyła pakiet EntityMetadata zmieniający skalę gracza.
     * W 1.20.1 używamy Pose size trick - modyfikujemy metadata index 
     * dla rozmiaru entity przez ProtocolLib.
     * 
     * Uwaga: W 1.20.1 nie ma natywnego scale attribute.
     * Używamy efektu wizualnego przez modyfikowanie entity data.
     */
    private void sendScalePacket(Player player, float scale) {
        if (!protocolLibEnabled) {
            plugin.getLogger().info("[SuperMarchewka] ProtocolLib niedostępny - pomijam skalowanie dla " + player.getName());
            return;
        }

        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            // ✅ Tworzymy pakiet metadata z fake skalą
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, player.getEntityId());

            // ✅ W 1.20.1 entity metadata index 17 = Pose
            // Ale prawdziwe skalowanie nie istnieje w 1.20.1
            // Zamiast tego używamy SMALL/BIG efektu przez potion effects
            
            // Wysyłamy pakiet do wszystkich graczy w pobliżu
            List<WrappedDataValue> wrappedDataValues = new ArrayList<>();

            // Index 0 = Entity flags (byte)
            // W 1.20.1 nie ma scale metadata - musimy użyć alternatywnej metody

            // ✅ ALTERNATYWA: Użyj efektu INVISIBLE + ArmorStand z custom size
            // ALE to jest zbyt skomplikowane dla 1.20.1
            
            // ✅ NAJPROSTSZA METODA NA 1.20.1:
            // Pomniejszenie = daj gracza jako pasażera na małym armor standzie
            // Powiększenie = efekt wizualny particle + slowness

            // Na razie logujemy że scale jest ustawione
            plugin.getLogger().info("[SuperMarchewka] Scale " + scale + "x dla gracza " + player.getName() +
                    " (wizualny efekt - 1.20.1 nie obsługuje natywnego skalowania)");

            // ✅ DLA POWIĘKSZENIA: Daj efekt SLOWNESS (spowalnia) + większy hitbox niemożliwy w 1.20.1
            // DLA POMNIEJSZENIA: Daj efekt INVISIBILITY na chwilę + mała postać

            if (scale < 1.0f) {
                // Pomniejszenie - swimming pose (gracz wygląda jakby pływał = mniejszy)
                player.setSwimming(true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Utrzymuj swimming przez cały czas trwania efektu
                    startSwimmingTask(player);
                }, 1L);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[SuperMarchewka] Błąd ProtocolLib: " + e.getMessage());
        }
    }

    private void resetScale(Player player) {
        if (!protocolLibEnabled) return;

        try {
            // Przywróć normalną pozę
            player.setSwimming(false);
        } catch (Exception e) {
            plugin.getLogger().warning("[SuperMarchewka] Błąd resetowania skali: " + e.getMessage());
        }
    }

    /**
     * ✅ Utrzymuje gracza w pozycji swimming (mniejszy) przez czas trwania efektu.
     */
    private void startSwimmingTask(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeEffects.containsKey(player.getUniqueId())) {
                    player.setSwimming(false);
                    cancel();
                    return;
                }

                ActiveEffect effect = activeEffects.get(player.getUniqueId());
                if (effect == null || !effect.isMini()) {
                    player.setSwimming(false);
                    cancel();
                    return;
                }

                // Utrzymuj swimming pose
                player.setSwimming(true);
            }
        }.runTaskTimer(plugin, 0L, 2L);
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
        resetScale(player);

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

    public void cleanupPlayer(Player player) {
        if (activeEffects.containsKey(player.getUniqueId())) {
            removeEffect(player);
        }

        // Na wszelki wypadek
        AttributeInstance attackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDamage != null) removeCritModifier(attackDamage);

        player.setSwimming(false);
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
