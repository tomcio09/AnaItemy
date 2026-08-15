package pl.anaheim.anaitemy.listeners;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.BombardaMaximaItem;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

import java.util.List;

public class BombardaMaximaListener implements Listener {

    private static final String META_BOMBARDA = "anaitemy_bombarda";
    private final AnaItemy plugin;

    public BombardaMaximaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!BombardaMaximaItem.isBombardaMaxima(item)) return;

        event.setCancelled(true);

        // ✅ Zablokuj w klatce
        HydroKlatkaManager klatkaManager = plugin.getHydroKlatkaManager();
        if (klatkaManager.getKlatkaForPlayer(player) != null) {
            klatkaManager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
            return;
        }

        // ✅ Zablokuj w world_the_end
        if (isInBlockedWorld(player.getLocation())) return;

        // ✅ Zablokuj w regionach z items.yml
        if (plugin.getWorldGuardManager().isInNamedRegion(player.getLocation(),
                plugin.getItemsConfig().getBombardaBlockedRegions())) return;

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Fireball fireball = player.getWorld().spawn(eye.add(direction), Fireball.class);
        fireball.setShooter(player);
        fireball.setDirection(direction);
        fireball.setYield(0f);
        fireball.setIsIncendiary(false);
        fireball.setMetadata(META_BOMBARDA, new FixedMetadataValue(plugin, true));

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }

    /**
     * ✅ Sprawdza czy lokacja jest w zablokowanym wymiarze lub chroniona przez klatkę
     */
    private boolean isProtectedByCage(Location blockLoc, HydroKlatkaManager klatkaManager) {
        if (klatkaManager.isKlatkaBlock(blockLoc)) {
            return true;
        }

        for (ActiveHydroKlatka klatka : klatkaManager.getActiveKlatki()) {
            if (klatka.isInsideCage(blockLoc)) {
                return true;
            }

            if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc)) {
                return true;
            }
        }

        return false;
    }

    /**
     * ✅ Sprawdza czy lokacja jest w zablokowanym wymiarze
     */
    private boolean isInBlockedWorld(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = location.getWorld().getName();
        List<String> blockedWorlds = plugin.getItemsConfig().getBombardaBlockedWorlds();
        for (String blocked : blockedWorlds) {
            if (blocked.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!fireball.hasMetadata(META_BOMBARDA)) return;

        event.setCancelled(true);

        Location impact = fireball.getLocation();
        World world = impact.getWorld();
        int radius = plugin.getItemsConfig().getBombardaRadius();

        // ✅ Zablokuj w world_the_end przy trafieniu
        if (isInBlockedWorld(impact)) {
            fireball.remove();
            return;
        }

        // ✅ Zablokuj w regionach przy trafieniu
        if (plugin.getWorldGuardManager().isInNamedRegion(impact,
                plugin.getItemsConfig().getBombardaBlockedRegions())) {
            fireball.remove();
            return;
        }

        HydroKlatkaManager klatkaManager = plugin.getHydroKlatkaManager();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location blockLoc = impact.clone().add(x, y, z);
                    if (blockLoc.distance(impact) > radius) continue;

                    Block block = blockLoc.getBlock();
                    if (block.getType() == Material.BEDROCK) continue;
                    if (block.getType().isAir()) continue;

                    if (isProtectedByCage(blockLoc, klatkaManager)) continue;

                    block.setType(Material.AIR, false);
                }
            }
        }

        world.spawnParticle(Particle.EXPLOSION_LARGE, impact, 10, 2, 2, 2, 0.1);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0f, 1.0f);

        fireball.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireballDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fireball fireball)) return;
        if (fireball.hasMetadata(META_BOMBARDA)) {
            event.setCancelled(true);
        }
    }
}
