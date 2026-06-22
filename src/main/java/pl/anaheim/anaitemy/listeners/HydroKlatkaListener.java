package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.HydroKlatka;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;

public class HydroKlatkaListener implements Listener {

    private final AnaItemy plugin;

    public HydroKlatkaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!HydroKlatka.isHydroKlatka(item)) return;

        event.setCancelled(true);

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Sprawdź cooldown gracza
        if (manager.isPlayerOnCooldown(player)) {
            manager.sendCooldownMessage(player);
            return;
        }

        // Sprawdź region WorldGuard
        if (manager.isInBlockedRegion(player.getLocation())) {
            manager.sendMessage(player, "&cNie możesz użyć wyrzutni w tym regionie!");
            return;
        }

        // Wystrzał fireball
        shootFireball(player);

        // Ustaw cooldown
        manager.setCooldown(player);

        // Dźwięk strzału
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT,
                SoundCategory.PLAYERS, 2.0f, 1.0f);
    }

    private void shootFireball(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Fireball fireball = player.getWorld().spawn(eye.add(direction), Fireball.class);
        fireball.setShooter(player);
        fireball.setDirection(direction);
        fireball.setYield(0f);
        fireball.setIsIncendiary(false);

        // Oznacz jako Hydro Klatka pocisk
        fireball.setMetadata("hydro_klatka", new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        if (!(projectile instanceof Fireball)) return;
        if (!projectile.hasMetadata("hydro_klatka")) return;
        if (!(projectile.getShooter() instanceof Player)) return;

        // Usuń fireball
        projectile.remove();

        Player shooter = (Player) projectile.getShooter();
        Location hitLocation = projectile.getLocation();

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Sprawdź chunk cooldown - dźwięk szkła tylko tutaj
        if (manager.isChunkBlocked(hitLocation)) {
            manager.sendMessage(shooter, "&cNie możesz w tym miejscu stworzyć klatki!");
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 0.5f);
            return;
        }

        // Stwórz klatkę (wewnątrz są dźwięki wybuchu + custom)
        manager.createKlatka(hitLocation, shooter);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        if (HydroKlatka.isHydroKlatka(newItem)) {
            manager.startCooldownDisplay(player);
        } else {
            manager.stopCooldownDisplay(player);
        }
    }
}
