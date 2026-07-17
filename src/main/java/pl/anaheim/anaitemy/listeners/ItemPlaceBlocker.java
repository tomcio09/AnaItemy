package pl.anaheim.anaitemy.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.*;

public class ItemPlaceBlocker implements Listener {

    private final AnaItemy plugin;

    public ItemPlaceBlocker(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (shouldBlockPlacement(item)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockPlacement(ItemStack item) {
        // ✅ Blokuj stawianie wszystkich naszych customowych itemow
        if (KostkaRubikaItem.isKostkaRubika(item)) return true;
        if (BalonikItem.isBalonik(item)) return true;
        if (OlafItem.isOlaf(item)) return true;
        if (ZlamaneSerceItem.isZlamaneSerce(item)) return true;
        if (KamienKowalskiItem.isKamienKowalski(item)) return true;
        if (PrzepustkaNeteruItem.isPrzepustkaNetheru(item)) return true;
        if (SplesnialaKanapkaItem.isSplesnialaKanapka(item)) return true;
        if (DynamitItem.isDynamit(item)) return true;
        if (PrzeterminowanyTrunekItem.isPrzeterminowanyTrunek(item)) return true;
        if (CiepleMlekoItem.isCiepleMleko(item)) return true;
        if (ParawanItem.isParawan(item)) return true;
        if (WataCukrowaItem.isWataCukrowa(item)) return true;
        if (PiernikItem.isPiernik(item)) return true;
        if (KrewWampiraItem.isKrewWampira(item)) return true;
        if (RozaKupidynaItem.isRozaKupidyna(item)) return true;
        if (LizakItem.isLizak(item)) return true;
        if (RozszerzenieECItem.isRozszerzenieEC(item)) return true;

        return false;
    }
}
