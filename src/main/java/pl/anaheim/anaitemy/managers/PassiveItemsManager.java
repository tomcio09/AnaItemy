package pl.anaheim.anaitemy.managers;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KoronaAnarchiiItem;
import pl.anaheim.anaitemy.items.LizakItem;
import pl.anaheim.anaitemy.items.RozaKupidynaItem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PassiveItemsManager {

    private final AnaItemy plugin;
    private BukkitTask tickTask;

    private static final NamespacedKey ROZA_HEALTH_KEY = new NamespacedKey("anaitemy", "roza_kupidyna_health");
    private static final NamespacedKey LIZAK_SCALE_KEY = new NamespacedKey("anaitemy", "lizak_scale");

    private final Set<UUID> hadRozaLastTick = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hadLizakLastTick = ConcurrentHashMap.newKeySet();

    public PassiveItemsManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean hasKorona = checkKorona(player);
                    boolean hasRoza = checkRoza(player);
                    boolean hasLizak = checkLizak(player);

                    // ✅ Korona - efekty na głowie
                    if (hasKorona) {
                        applyEffect(player, PotionEffectType.SPEED, 1);
                        applyEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
                        applyEffect(player, PotionEffectType.INCREASE_DAMAGE, 1);
                        applyEffect(player, PotionEffectType.DAMAGE_RESISTANCE, 2);
                        applyEffect(player, PotionEffectType.LUCK, 0);
                    }

                    // ✅ Róża kupidyna - ręka/offhand
                    if (hasRoza) {
                        applyEffect(player, PotionEffectType.DAMAGE_RESISTANCE, 0);
                        applyEffect(player, PotionEffectType.REGENERATION, 1);
                        applyRozaHealth(player);
                    } else {
                        if (hadRozaLastTick.contains(player.getUniqueId())) {
                            removeRozaHealth(player);
                        }
                    }

                    if (hasRoza) hadRozaLastTick.add(player.getUniqueId());
                    else hadRozaLastTick.remove(player.getUniqueId());

                    // ✅ Lizak - ręka/offhand
                    if (hasLizak) {
                        applyEffect(player, PotionEffectType.INCREASE_DAMAGE, 0);
                        applyLizakScale(player);
                    } else {
                        if (hadLizakLastTick.contains(player.getUniqueId())) {
                            removeLizakScale(player);
                        }
                    }

                    if (hasLizak) hadLizakLastTick.add(player.getUniqueId());
                    else hadLizakLastTick.remove(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 0L, 16L); // co 0.8 sekundy
    }

    private void applyEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) return;
        player.addPotionEffect(new PotionEffect(type, 30, amplifier, false, false, true));
    }

    private boolean checkKorona(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return KoronaAnarchiiItem.isKoronaAnarchii(helmet);
    }

    private boolean checkRoza(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return RozaKupidynaItem.isRozaKupidyna(mainHand) || RozaKupidynaItem.isRozaKupidyna(offHand);
    }

    private boolean checkLizak(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return LizakItem.isLizak(mainHand) || LizakItem.isLizak(offHand);
    }

    // ==================== RÓŻA +5 SERC ====================

    private void applyRozaHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;

        // Sprawdź czy już ma modifier
        if (maxHealth.getModifier(ROZA_HEALTH_KEY) != null) return;

        maxHealth.addModifier(new AttributeModifier(
                ROZA_HEALTH_KEY,
                10.0, // +5 serc = +10 HP
                AttributeModifier.Operation.ADD_NUMBER
        ));
    }

    private void removeRozaHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;

        maxHealth.removeModifier(ROZA_HEALTH_KEY);

        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    // ==================== LIZAK POMNIEJSZENIE ====================

    private void applyLizakScale(Player player) {
        AttributeInstance scaleAttr = player.getAttribute(Attribute.GENERIC_SCALE);
        if (scaleAttr == null) return;

        if (scaleAttr.getModifier(LIZAK_SCALE_KEY) != null) return;

        double scaleMultiplier = plugin.getItemsConfig().getLizakScaleMultiplier();
        // scaleMultiplier = 0.9 oznacza 90% rozmiaru, więc modifier = -0.1
        double modifier = scaleMultiplier - 1.0;

        scaleAttr.addModifier(new AttributeModifier(
                LIZAK_SCALE_KEY,
                modifier,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void removeLizakScale(Player player) {
        AttributeInstance scaleAttr = player.getAttribute(Attribute.GENERIC_SCALE);
        if (scaleAttr == null) return;

        scaleAttr.removeModifier(LIZAK_SCALE_KEY);
    }

    // ==================== CLEANUP ====================

    public void cleanupPlayer(Player player) {
        removeRozaHealth(player);
        removeLizakScale(player);
        hadRozaLastTick.remove(player.getUniqueId());
        hadLizakLastTick.remove(player.getUniqueId());
    }

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();

        for (Player player : Bukkit.getOnlinePlayers()) {
            removeRozaHealth(player);
            removeLizakScale(player);
        }

        hadRozaLastTick.clear();
        hadLizakLastTick.clear();
    }
}
