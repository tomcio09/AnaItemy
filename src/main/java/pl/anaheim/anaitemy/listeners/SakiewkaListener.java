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
     * 
     * EventPriority.LOWEST = wykonuje się OSTATNI (po TotemListener który ma HIGHEST)
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Sprawdź czy zabójcą jest gracz
        if (killer == null) return;

        // ✅ SPRAWDZENIE 1: Czy keepInventory jest aktywne (totem, region, gamerule)
        if (event.getKeepInventory()) {
            return; // Keep inventory aktywne - nie zbieraj itemów
        }

        // ✅ SPRAWDZENIE 2: Czy drops są puste (totem już wyczyścił)
        if (event.getDrops().isEmpty()) {
            return; // Brak itemów do zebrania
        }

        // ✅ SPRAWDZENIE 3: Czy ofiara miała Totem Ułaskawienia w ręku/offhand
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand) || 
            TotemUlaskawienia.isTotemUlaskawienia(offHand)) {
            // Totem w użyciu - sakiewka NIE zbiera itemów
            return;
        }

        // ✅ SPRAWDZENIE 4: Czy killer ma sakiewkę w ekwipunku
        List<ItemStack> sakiewki = findAllSakiewki(killer);
        if (sakiewki.isEmpty()) return;

        // ✅ SPRAWDZENIE 5: Zablokowane regiony
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getSakiewkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(victim.getLocation(), blockedRegions)) {
            return;
        }

        // ✅ Zbierz wszystkie itemy ofiary (TYLKO z event.getDrops())
        List<ItemStack> dropsToCollect = new ArrayList<>(event.getDrops());

        if (dropsToCollect.isEmpty()) {
            return; // Nie ma nic do zebrania
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

        // ✅ DEBUG LOG
        int collected = dropsToCollect.size() - overflow.size();
        if (collected > 0) {
            plugin.getLogger().info("[Sakiewka] " + killer.getName() + 
                    " zebral " + collected + " itemow po zabiciu " + victim.getName());
        }
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
