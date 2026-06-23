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

        // ✅ NAJWAŻNIEJSZE - sprawdź czy keepInventory jest aktywne
        // (totem, region WorldGuard, gamerule, itp.)
        if (event.getKeepInventory()) {
            return; // Keep inventory aktywne - nie zbieraj itemów
        }

        // ✅ Dodatkowe sprawdzenie - czy ofiara miała Totem Ułaskawienia w ręku/offhand
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

        // ✅ Zbierz wszystkie itemy ofiary (TYLKO z event.getDrops())
        // NIE zbieraj zbroi ani offhand ręcznie - są już w drops jeśli keepInventory=false
        List<ItemStack> dropsToCollect = new ArrayList<>(event.getDrops());

        // ✅ Wypełnij sakiewki po kolei
        List<ItemStack> overflow = dropsToCollect;
        for (ItemStack sakiewka : sakiewki) {
            if (overflow.isEmpty()) break;
            overflow = SakiewkaData.addItems(sakiewka, overflow);
        }

        // ✅ Usuń zebrane itemy z dropu (zostaw tylko overflow)
        event.getDrops().clear();
        event.getDrops().addAll(overflow);
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
