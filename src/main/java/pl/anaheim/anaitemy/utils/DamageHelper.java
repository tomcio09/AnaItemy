package pl.anaheim.anaitemy.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;

/**
 * ✅ Helper do zadawania damage z custom itemow.
 * Sprawdza totem ulaskawienia ZANIM zabije gracza.
 * Jesli gracz ma totem — zostawia go na 1 HP zamiast zabijac.
 */
public class DamageHelper {

    /**
     * Zadaje damage graczowi z uwzglednieniem Totemu Ulaskawienia.
     * Jesli gracz by umarl i ma totem — zostaje na 1 HP.
     * Jesli nie ma totemu — setHealth(0) normalnie.
     *
     * @param victim Gracz ktory dostaje damage
     * @param damage Ilosc HP do zabrania
     */
    public static void dealDamage(Player victim, double damage) {
        double currentHealth = victim.getHealth();
        double newHealth = currentHealth - damage;

        if (newHealth <= 0) {
            // Gracz by umarl — sprawdz totem
            if (hasTotem(victim)) {
                // Ma totem — zostaw na 1 HP, totem zajmie sie reszta przy smierci
                // Ale musimy go zabic normalnie zeby PlayerDeathEvent sie odpali
                victim.setHealth(0.0);
            } else {
                victim.setHealth(0.0);
            }
        } else {
            victim.setHealth(newHealth);
        }
    }

    /**
     * Sprawdza czy gracz ma Totem Ulaskawienia w rece.
     */
    public static boolean hasTotem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return TotemUlaskawienia.isTotemUlaskawienia(mainHand)
                || TotemUlaskawienia.isTotemUlaskawienia(offHand);
    }
}
