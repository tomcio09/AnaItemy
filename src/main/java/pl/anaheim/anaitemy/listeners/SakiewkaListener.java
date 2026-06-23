package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.SakiewkaDropu;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;
import pl.anaheim.anaitemy.utils.SakiewkaData;

import java.util.ArrayList;
import java.util.List;

public class SakiewkaListener implements Listener {

    private final AnaItemy plugin;

    public SakiewkaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Zbiera itemy po zabiciu gracza do sakiewki.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Sprawdź czy zabójcą jest gracz
        if (killer == null) return;

        // ✅ Sprawdź czy ofiara miała Totem Ułaskawienia w ręku/offhand
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand) || 
            TotemUlaskawienia.isTotemUlaskawienia(offHand)) {
            // Totem w użyciu - sakiewka NIE zbiera itemów
            return;
        }

        // ✅ Sprawdź czy killer ma sakiewkę w ekwipunku
        List<ItemStack> sakiewki = findAllSakiewki(killer);
        if (sakiewki.isEmpty()) return;

        // ✅ Sprawdź zablokowane regiony
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getSakiewkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(victim.getLocation(), blockedRegions)) {
            return;
        }

        // ✅ Zbierz wszystkie itemy ofiary
        List<ItemStack> dropsToCollect = new ArrayList<>(event.getDrops());
        
        // Dodaj zbroję
        if (victim.getInventory().getHelmet() != null) {
            dropsToCollect.add(victim.getInventory().getHelmet());
        }
        if (victim.getInventory().getChestplate() != null) {
            dropsToCollect.add(victim.getInventory().getChestplate());
        }
        if (victim.getInventory().getLeggings() != null) {
            dropsToCollect.add(victim.getInventory().getLeggings());
        }
        if (victim.getInventory().getBoots() != null) {
            dropsToCollect.add(victim.getInventory().getBoots());
        }
        
        // Dodaj offhand (jeśli nie był totem)
        if (offHand != null && !offHand.getType().isAir()) {
            dropsToCollect.add(offHand);
        }

        // ✅ Wypełnij sakiewki po kolei
        List<ItemStack> overflow = dropsToCollect;
        for (ItemStack sakiewka : sakiewki) {
            if (overflow.isEmpty()) break;
            overflow = SakiewkaData.addItems(sakiewka, overflow);
        }

        // ✅ Usuń zebrane itemy z dropu (zostaw tylko overflow)
        event.getDrops().clear();
        event.getDrops().addAll(overflow);

        // Nie dropuj zbroi ani offhand (już zebrane)
        victim.getInventory().setHelmet(null);
        victim.getInventory().setChestplate(null);
        victim.getInventory().setLeggings(null);
        victim.getInventory().setBoots(null);
    }

    /**
     * Znajduje wszystkie sakiewki w ekwipunku gracza (w kolejności slotów).
     */
    private List<ItemStack> findAllSakiewki(Player player) {
        List<ItemStack> sakiewki = new ArrayList<>();
        
        // Sprawdź wszystkie sloty (0-35 storage + 36-39 armor + 40 offhand)
        for (ItemStack item : player.getInventory().getContents()) {
            if (SakiewkaDropu.isSakiewka(item)) {
                sakiewki.add(item);
            }
        }
        
        return sakiewki;
    }
}
