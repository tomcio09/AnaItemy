package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
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

import java.time.Duration;

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

        World world = clickedBlock.getWorld();
        int blockY = clickedBlock.getY();
        World.Environment env = world.getEnvironment();

        if (env == World.Environment.NORMAL) {
            if (blockY <= world.getMinHeight()) return;
        } else if (env == World.Environment.NETHER) {
            if (blockY <= 0) return;
            if (blockY >= 127) return;
        } else if (env == World.Environment.THE_END) {
            int bx = clickedBlock.getX();
            int bz = clickedBlock.getZ();
            if (blockY == 0 && Math.abs(bx) <= 3 && Math.abs(bz) <= 3) return;
        }

        clickedBlock.setType(Material.AIR);

        world.spawnParticle(Particle.EXPLOSION_LARGE,
                clickedBlock.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
        world.playSound(clickedBlock.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 1.0f, 1.5f);

        // ✅ Subtitle po zniszczeniu bedrocka
        String subtitleText = plugin.getItemsConfig().getDynamitSuccessSubtitle();
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitleText),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2500),
                        Duration.ofMillis(250)
                )
        ));

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }
}
