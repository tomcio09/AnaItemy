package pl.anaheim.anaitemy.listeners;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.BombardaMaximaItem;

public class BombardaMaximaListener implements Listener {

    private static final String META_BOMBARDA = "anaitemy_bombarda";
    private final AnaItemy plugin;

    public BombardaMaximaListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!BombardaMaximaItem.isBombardaMaxima(item)) return;

        event.setCancelled(true);

        // Sprawdź region
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

        // Zużyj
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!fireball.hasMetadata(META_BOMBARDA)) return;

        event.setCancelled(true);

        Location impact = fireball.getLocation();
        World world = impact.getWorld();
        int radius = plugin.getItemsConfig().getBombardaRadius();

        // Sprawdź region
        if (plugin.getWorldGuardManager().isInNamedRegion(impact,
                plugin.getItemsConfig().getBombardaBlockedRegions())) {
            fireball.remove();
            return;
        }

        // Niszcz bloki
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location blockLoc = impact.clone().add(x, y, z);
                    if (blockLoc.distance(impact) > radius) continue;

                    Block block = blockLoc.getBlock();
                    if (block.getType() == Material.BEDROCK) continue;
                    if (block.getType().isAir()) continue;

                    block.breakNaturally();
                }
            }
        }

        world.spawnParticle(Particle.EXPLOSION_LARGE, impact, 10, 2, 2, 2, 0.1);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0f, 1.0f);

        fireball.remove();
    }
}
