package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.CudownaLatarniaItem;
import pl.anaheim.anaitemy.managers.CudownaLatarniaManager;

public class CudownaLatarniaListener implements Listener {

    private final AnaItemy plugin;

    public CudownaLatarniaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== POSTAWIENIE LATARNI ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!CudownaLatarniaItem.isCudownaLatarnia(item)) return;

        // ✅ ZAWSZE anuluj postawienie bloku (item zostaje w eq)
        event.setCancelled(true);

        CudownaLatarniaManager manager = plugin.getCudownaLatarniaManager();

        // ✅ Sprawdź cooldown - po cichu
        if (manager.isOnCooldown(player)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // ✅ Lokalizacja gdzie postawić beacon (blok nad klikniętym)
        Location placeLoc = clickedBlock.getRelative(event.getBlockFace()).getLocation();

        // ✅ Sprawdź blocked region
        if (manager.isInBlockedRegion(placeLoc)) {
            return;
        }

        // ✅ Sprawdź chunk cooldown
        if (manager.isChunkBlocked(placeLoc)) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&cNie możesz tu tego zrobić!"),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2000),
                            java.time.Duration.ofMillis(200)
                    )
            ));
            return;
        }

        // ✅ Sprawdź czy miejsce nie jest zajęte przez solid block
        Block placeBlock = placeLoc.getBlock();
        if (placeBlock.getType().isSolid()) {
            return;
        }

        // ✅ Aktywuj latarnię
        manager.activate(player, placeLoc);
    }

    // ==================== BLOKADA GUI BEACONA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBeaconRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON) return;

        CudownaLatarniaManager manager = plugin.getCudownaLatarniaManager();
        if (manager.isLatarniaBlock(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== NISZCZENIE BEACONA ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BEACON) return;

        CudownaLatarniaManager manager = plugin.getCudownaLatarniaManager();
        CudownaLatarniaManager.ActiveLatarnia latarnia = manager.getLatarniaAt(block.getLocation());

        if (latarnia == null) return;

        // ✅ Anuluj vanilla break (nie wypada nic)
        event.setCancelled(true);

        // ✅ Zniszcz latarnię
        manager.removeLatarnia(latarnia, true);
    }

    // ==================== WYLOGOWANIE ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Latarnie zostają na mapie - nie usuwamy ich przy wylogowaniu
        // Bossbar automatycznie zniknie bo gracz jest offline
    }
}
