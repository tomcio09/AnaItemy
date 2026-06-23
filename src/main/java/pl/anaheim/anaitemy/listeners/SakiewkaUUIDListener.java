package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.SakiewkaDropu;

/**
 * ✅ Listener który automatycznie regeneruje UUID sakiewki gdy dotknie jej nie-OP.
 * 
 * Dzięki temu OP może:
 * - Wziąć sakiewkę z GUI
 * - Wrzucić do skrzynki/lootboxa
 * - Gdy gracz ją wylosuje → automatycznie staje się jego unikalną sakiewką
 */
public class SakiewkaUUIDListener implements Listener {

    private final AnaItemy plugin;

    public SakiewkaUUIDListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Gdy gracz KLIKNIE w sakiewkę w inventory (bierze z skrzynki/GUI).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.isOp()) return; // OP może brać sakiewki bez zmiany UUID

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        // Sprawdź czy to sakiewka
        if (!SakiewkaDropu.isSakiewka(clicked)) return;

        // ✅ REGENERUJ UUID z opóźnieniem (1 tick) aby item zdążył trafić do eq
        new BukkitRunnable() {
            @Override
            public void run() {
                regenerateAllSakiewkiInInventory(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * ✅ Gdy gracz PODNOSI sakiewkę z ziemi.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isOp()) return;

        ItemStack item = event.getItem().getItemStack();
        if (!SakiewkaDropu.isSakiewka(item)) return;

        // ✅ REGENERUJ UUID z opóźnieniem (1 tick)
        new BukkitRunnable() {
            @Override
            public void run() {
                regenerateAllSakiewkiInInventory(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * ✅ Gdy gracz DOŁĄCZA do serwera - skanuj eq na sakiewki.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        // Skanuj eq po 5 tickach (aby eq się załadował)
        new BukkitRunnable() {
            @Override
            public void run() {
                regenerateAllSakiewkiInInventory(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    /**
     * Regeneruje UUID wszystkich sakiewek w ekwipunku gracza.
     */
    private void regenerateAllSakiewkiInInventory(Player player) {
        if (!player.isOnline()) return;

        ItemStack[] contents = player.getInventory().getContents();
        boolean modified = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            if (SakiewkaDropu.isSakiewka(item)) {
                // ✅ Zamień na nową sakiewkę z tym samym contentem
                ItemStack newSakiewka = SakiewkaDropu.regenerateUUID(item);
                contents[i] = newSakiewka;
                modified = true;
            }
        }

        if (modified) {
            player.getInventory().setContents(contents);
            plugin.getLogger().info("[Sakiewka] Zregenerowano UUID sakiewek dla gracza: " + player.getName());
        }
    }
}
