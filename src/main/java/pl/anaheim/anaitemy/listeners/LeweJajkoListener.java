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

        egg.setMetadata(META_LEWE, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!egg.hasMetadata(META_LEWE)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        // ✅ Pobierz strzelca
        Player shooter = null;
        try {
            String uuid = egg.getMetadata(META_LEWE).get(0).asString();
            shooter = plugin.getServer().getPlayer(java.util.UUID.fromString(uuid));
        } catch (Exception ignored) {}

        // ✅ 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "lewe-jajko")) {
            if (shooter != null) {
                int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "lewe-jajko");
                plugin.getItemProtectionManager().notifyAttacker(shooter, "lewe-jajko", sl);
            }
            egg.remove();
            return;
        }

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "lewe-jajko");

        victim.setVelocity(new Vector(0, 10.0, 0));

        victim.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&eLewe Jajko"),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&7zostałeś wystrzelony..."),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500))));

        egg.remove();
    }
}
