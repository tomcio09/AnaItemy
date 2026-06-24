package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WzmocnianaElytra;

public class WzmocnianaElytraListener implements Listener {

    private final AnaItemy plugin;

    public WzmocnianaElytraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj fall damage gdy elytra ma 100% (piorun blokuje damage).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        double charge = WzmocnianaElytra.getCharge(chestplate);
        if (charge >= 100.0) {
            event.setCancelled(true);
        }
    }

    /**
     * ✅ Wykryj lądowanie na górnej części bloku (nie z boku ani od dołu).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLand(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Sprawdź czy gracz ma wzmocnianą elytrę
        ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        // Sprawdź czy gracz ma 100%
        double charge = WzmocnianaElytra.getCharge(chestplate);
        if (charge < 100.0) return;

        // Sprawdź czy gracz był w locie i teraz wylądował
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // ✅ Gracz był w powietrzu (gliding lub falling) i teraz jest na ziemi
        boolean wasFlying = player.isGliding() || !player.isOnGround();
        boolean nowOnGround = player.isOnGround();

        if (!wasFlying || !nowOnGround) return;

        // ✅ Sprawdź czy gracz wylądował NA GÓRZE bloku (nie z boku)
        Block blockBelow = to.getBlock().getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.AIR) return;

        // ✅ Sprawdź czy gracz uderzył w górną powierzchnię
        // (różnica Y między graczem a blokiem powinna być ~1)
        double playerY = to.getY();
        double blockTopY = blockBelow.getY() + 1.0;

        if (Math.abs(playerY - blockTopY) > 0.5) return; // Nie wylądował na górze

        // ✅ WYWOŁAJ PIORUN
        plugin.getWzmocnianaElytraManager().triggerLightningStrike(player, blockBelow.getLocation().add(0.5, 1, 0.5));
    }

    /**
     * ✅ Shift click - resetuj ładowanie.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShiftClick(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        if (!player.isGliding()) return;

        plugin.getWzmocnianaElytraManager().onShiftClick(player);
    }
}
