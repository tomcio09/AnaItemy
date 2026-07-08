package pl.anaheim.anaitemy.listeners;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.DynamitItem;

public class DynamitListener implements Listener {

    private final AnaItemy plugin;

    public DynamitListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!DynamitItem.isDynamit(item)) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        if (clickedBlock.getType() != Material.BEDROCK) return;

        event.setCancelled(true);

        // ✅ Sprawdź czy to nie chroniony bedrock
        World world = clickedBlock.getWorld();
        int blockY = clickedBlock.getY();
        World.Environment env = world.getEnvironment();

        if (env == World.Environment.NORMAL) {
            // Overworld - nie niszcz najniższej warstwy (Y = world.getMinHeight())
            if (blockY <= world.getMinHeight()) return;
        } else if (env == World.Environment.NETHER) {
            // Nether - nie niszcz najniższej warstwy i dachu (Y=0 i Y=127)
            if (blockY <= 0) return;
            if (blockY >= 127) return;
        } else if (env == World.Environment.THE_END) {
            // End - nie niszcz portalu (obsidian + bedrock w centrum)
            // Sprawdź czy to bedrock portalu (Y=0 w centrum 0,0)
            int bx = clickedBlock.getX();
            int bz = clickedBlock.getZ();
            if (blockY == 0 && Math.abs(bx) <= 3 && Math.abs(bz) <= 3) return;
        }

        // ✅ Zniszcz bedrock
        clickedBlock.setType(Material.AIR);

        // Efekty
        world.spawnParticle(Particle.EXPLOSION_LARGE,
                clickedBlock.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
        world.playSound(clickedBlock.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 1.0f, 1.5f);

        // Zużyj
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }
}
