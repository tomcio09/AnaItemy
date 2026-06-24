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

        if (event.getKeepInventory()) return;
        if (plugin.getTotemListener().isTotemProtected(victim.getUniqueId())) return;

        ItemStack mainHand = victim.getInventory().getItemInMainHand();
        ItemStack offHand = victim.getInventory().getItemInOffHand();

        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand) ||
                TotemUlaskawienia.isTotemUlaskawienia(offHand)) return;

        if (event.getDrops().isEmpty()) return;

        CombatIntegrationManager combat = plugin.getCombatIntegrationManager();
        if (combat.wasLogoutDeath(victim.getUniqueId())) return;

        Player realKiller = killer;

        if (realKiller == null && combat.isEnabled()) {
            UUID killerUUID = combat.getKillerOf(victim.getUniqueId());
            if (killerUUID != null) {
                realKiller = Bukkit.getPlayer(killerUUID);
            }
        }

        if (realKiller == null) return;

        List<ItemStack> sakiewki = findAllSakiewki(realKiller);
        if (sakiewki.isEmpty()) return;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getSakiewkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(victim.getLocation(), blockedRegions)) return;

        List<ItemStack> dropsToCollect = new ArrayList<>(event.getDrops());
        if (dropsToCollect.isEmpty()) return;

        List<ItemStack> overflow = dropsToCollect;
        for (ItemStack sakiewka : sakiewki) {
            if (overflow.isEmpty()) break;
            overflow = SakiewkaData.addItems(sakiewka, overflow);
        }

        event.getDrops().clear();
        event.getDrops().addAll(overflow);
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
