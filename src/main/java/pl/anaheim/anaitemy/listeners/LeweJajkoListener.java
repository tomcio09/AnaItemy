package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.LeweJajkoItem;

import java.time.Duration;

public class LeweJajkoListener implements Listener {

    private static final String META_LEWE = "anaitemy_lewe_jajko";
    private final AnaItemy plugin;

    public LeweJajkoListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!(egg.getShooter() instanceof Player shooter)) return;

        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        if (!LeweJajkoItem.isLeweJajko(mainHand)) return;

        egg.setMetadata(META_LEWE, new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!egg.hasMetadata(META_LEWE)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        // ✅ Wyrzuć gracza 50 bloków w górę
        victim.setVelocity(new Vector(0, 10.0, 0));

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&eLewe Jajko"),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&7zostałeś wystrzelony..."),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500))));

        egg.remove();
    }
}
