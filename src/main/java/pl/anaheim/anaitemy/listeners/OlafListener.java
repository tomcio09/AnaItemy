package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.OlafItem;
import pl.anaheim.anaitemy.managers.OlafManager;

import java.time.Duration;

public class OlafListener implements Listener {

    private static final String META_OLAF = "anaitemy_olaf_egg";
    private final AnaItemy plugin;

    public OlafListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!(egg.getShooter() instanceof Player shooter)) return;

        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        if (!OlafItem.isOlaf(mainHand)) return;

        OlafManager manager = plugin.getOlafManager();

        if (manager.isShooterOnCooldown(shooter)) {
            long remaining = manager.getShooterCooldownRemaining(shooter);
            String subtitle = plugin.getItemsConfig().getOlafCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");
            shooter.showTitle(Title.title(Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));
            event.setCancelled(true);
            return;
        }

        if (plugin.getWorldGuardManager().isInNamedRegion(shooter.getLocation(),
                plugin.getItemsConfig().getOlafBlockedRegions())) {
            event.setCancelled(true);
            return;
        }

        egg.setMetadata(META_OLAF, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!egg.hasMetadata(META_OLAF)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        String shooterUUID = egg.getMetadata(META_OLAF).get(0).asString();
        Player shooter = plugin.getServer().getPlayer(java.util.UUID.fromString(shooterUUID));

        if (shooter == null || !shooter.isOnline()) return;
        if (shooter.equals(victim)) return;

        OlafManager manager = plugin.getOlafManager();

        if (manager.isVictimOnCooldown(victim)) return;

        if (plugin.getWorldGuardManager().isInNamedRegion(victim.getLocation(),
                plugin.getItemsConfig().getOlafBlockedRegions())) return;

        // ✅ 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "olaf")) {
            int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "olaf");
            plugin.getItemProtectionManager().notifyAttacker(shooter, "olaf", sl);
            egg.remove();
            return;
        }

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "olaf");

        manager.activateOlaf(shooter, victim);

        egg.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        OlafManager manager = plugin.getOlafManager();

        if (!manager.hasActiveOlaf(player)) return;

        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            manager.onVictimHit(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        OlafManager manager = plugin.getOlafManager();
        if (manager.hasActiveOlaf(player)) {
            manager.removeOlaf(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        OlafManager manager = plugin.getOlafManager();
        if (manager.hasActiveOlaf(player)) {
            manager.removeOlaf(player);
        }
    }
}
