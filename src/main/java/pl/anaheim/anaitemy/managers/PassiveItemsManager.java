package pl.anaheim.anaitemy.managers;

import org.bukkit.Bukkit;
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

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PassiveItemsManager {

    private final AnaItemy plugin;
    private BukkitTask tickTask;

    private static final UUID ROZA_HEALTH_UUID = UUID.fromString("D4E5F6A7-B8C9-0123-DEF0-123456789ABC");
    private static final String ROZA_HEALTH_NAME = "roza_kupidyna_health";

    private final Set<UUID> hadRozaLastTick = ConcurrentHashMap.newKeySet();

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

                    // ✅ Korona - działa tylko gdy założona na głowie
                    if (hasKorona) {
                        applyEffect(player, PotionEffectType.SPEED, 1);             // Speed II
                        applyEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);   // Fire Resistance I
                        applyEffect(player, PotionEffectType.INCREASE_DAMAGE, 1);   // Strength II
                        applyEffect(player, PotionEffectType.DAMAGE_RESISTANCE, 2); // Resistance III
                        applyEffect(player, PotionEffectType.LUCK, 0);              // Luck I
                    }

                    // ✅ Róża kupidyna - ręka/offhand
                    if (hasRoza) {
                        applyEffect(player, PotionEffectType.DAMAGE_RESISTANCE, 0); // Resistance I
                        applyEffect(player, PotionEffectType.REGENERATION, 1);      // Regeneration II
                        applyRozaHealth(player);
                    } else {
                        if (hadRozaLastTick.contains(player.getUniqueId())) {
                            removeRozaHealth(player);
                        }
                    }

                    if (hasRoza) {
                        hadRozaLastTick.add(player.getUniqueId());
                    } else {
                        hadRozaLastTick.remove(player.getUniqueId());
                    }

                    // ✅ Lizak - ręka/offhand
                    if (hasLizak) {
                        applyEffect(player, PotionEffectType.INCREASE_DAMAGE, 0);   // Strength I
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 16L); // co 0.8 sekundy
    }

    /**
     * Nadaj efekt tylko jeśli gracz nie ma silniejszego.
     * Jeśli ma taki sam lub słabszy - odnawiamy.
     */
    private void applyEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);

        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }

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

        for (AttributeModifier mod : maxHealth.getModifiers()) {
            if (mod.getUniqueId().equals(ROZA_HEALTH_UUID)) return;
        }

        maxHealth.addModifier(new AttributeModifier(
                ROZA_HEALTH_UUID,
                ROZA_HEALTH_NAME,
                10.0, // +5 serc = +10 HP
                AttributeModifier.Operation.ADD_NUMBER
        ));
    }

    private void removeRozaHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) return;

        for (AttributeModifier mod : new ArrayList<>(maxHealth.getModifiers())) {
            if (mod.getUniqueId().equals(ROZA_HEALTH_UUID) || ROZA_HEALTH_NAME.equals(mod.getName())) {
                maxHealth.removeModifier(mod);
            }
        }

        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    public void cleanupPlayer(Player player) {
        removeRozaHealth(player);
        hadRozaLastTick.remove(player.getUniqueId());
    }

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();

        for (Player player : Bukkit.getOnlinePlayers()) {
            removeRozaHealth(player);
        }

        hadRozaLastTick.clear();
    }
}
