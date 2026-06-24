package pl.anaheim.anaitemy.listeners;

import org.bukkit.Bukkit;
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
import pl.anaheim.anaitemy.managers.CombatIntegrationManager;
import pl.anaheim.anaitemy.utils.SakiewkaData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SakiewkaListener implements Listener {

    private final AnaItemy plugin;

    public SakiewkaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // ✅ SPRAWDZENIE 1: Czy keepInventory jest aktywne
        if (event.getKeepInventory()) {
            return;
        }

        // ✅ SPRAWDZENIE 2: Czy gracz był chroniony przez nasz Totem Ułaskawienia
        if (plugin.getTotemListener().isTotemProtected(victim.getUniqueId())) {
            return;
        }

        // ✅ SPRAWDZENIE 3: Czy ofiara miała Totem Ułaskawienia w ręku/offhand
        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand) ||
                TotemUlaskawienia.isTotemUlaskawienia(offHand)) {
            return;
        }

        // ✅ SPRAWDZENIE 4: Czy drops są puste
        if (event.getDrops().isEmpty()) {
            return;
        }

        // ✅ SPRAWDZENIE 5: Czy gracz uciekł z walki (wylogował się)
        CombatIntegrationManager combat = plugin.getCombatIntegrationManager();
        if (combat.wasLogoutDeath(victim.getUniqueId())) {
            // Gracz UCIEKŁ - itemy NIE trafiają do sakiewki, wypadają normalnie
            return;
        }

        // ✅ SPRAWDZENIE 6: Ustal killera (z combat pluginu lub z eventu)
        Player realKiller = killer;

        if (realKiller == null && combat.isEnabled()) {
            // Śmierć w walce bez bezpośredniego killera (void, dripstone itp.)
            UUID killerUUID = combat.getKillerOf(victim.getUniqueId());
            if (killerUUID != null) {
                realKiller = Bukkit.getPlayer(killerUUID);
            }
        }

        // Sprawdź czy mamy killera
        if (realKiller == null) return;

        // ✅ SPRAWDZENIE 7: Czy killer ma sakiewkę w ekwipunku
        List<ItemStack> sakiewki = findAllSakiewki(realKiller);
        if (sakiewki.isEmpty()) return;

        // ✅ SPRAWDZENIE 8: Zablokowane regiony
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getSakiewkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(victim.getLocation(), blockedRegions)) {
            return;
        }

        // ✅ Zbierz wszystkie itemy ofiary
        List<ItemStack> dropsToCollect = new ArrayList<>(event.getDrops());

        if (dropsToCollect.isEmpty()) return;

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
            plugin.getLogger().info("[Sakiewka] " + realKiller.getName() +
                    " zebral " + collected + " itemow po zabiciu " + victim.getName());
        }
    }

    private List<ItemStack> findAllSakiewki(Player player) {
        List<ItemStack> sakiewki = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (SakiewkaDropu.isSakiewka(item)) {
                sakiewki.add(item);
            }
        }

        return sakiewki;
    }
}
