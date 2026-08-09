package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.SniezkaZamianyItem;

public class SniezkaZamianyListener implements Listener {

    private static final String META_SNIEZKA = "anaitemy_sniezka_zamiany";
    private static final String META_SNIEZKA_LOC = "anaitemy_sniezka_loc";

    private final AnaItemy plugin;

    public SniezkaZamianyListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player shooter)) return;

        ItemStack item = shooter.getInventory().getItemInMainHand();
        if (!SniezkaZamianyItem.isSniezkaZamiany(item)) {
            item = shooter.getInventory().getItemInOffHand();
            if (!SniezkaZamianyItem.isSniezkaZamiany(item)) return;
        }

        snowball.setMetadata(META_SNIEZKA,
                new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));

        Location shooterLoc = shooter.getLocation().clone();
        snowball.setMetadata(META_SNIEZKA_LOC,
                new FixedMetadataValue(plugin,
                        shooterLoc.getWorld().getName() + ";"
                                + shooterLoc.getX() + ";"
                                + shooterLoc.getY() + ";"
                                + shooterLoc.getZ() + ";"
                                + shooterLoc.getYaw() + ";"
                                + shooterLoc.getPitch()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!snowball.hasMetadata(META_SNIEZKA)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        String shooterUUID = snowball.getMetadata(META_SNIEZKA).get(0).asString();
        Player shooter = plugin.getServer().getPlayer(java.util.UUID.fromString(shooterUUID));

        if (shooter == null || !shooter.isOnline()) return;
        if (shooter.equals(victim)) return;

        // ✅ 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "sniezka-zamiany")) {
            int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "sniezka-zamiany");
            plugin.getItemProtectionManager().notifyAttacker(shooter, "sniezka-zamiany", sl);
            return;
        }

        String locData = snowball.getMetadata(META_SNIEZKA_LOC).get(0).asString();
        String[] parts = locData.split(";");
        String originalWorld = parts[0];

        if (!shooter.getWorld().getName().equals(originalWorld)) return;

        double origX = Double.parseDouble(parts[1]);
        double origY = Double.parseDouble(parts[2]);
        double origZ = Double.parseDouble(parts[3]);
        Location originalLoc = new Location(shooter.getWorld(), origX, origY, origZ);

        if (shooter.getLocation().distance(originalLoc) > 100) return;
        if (shooter.getLocation().distance(victim.getLocation()) > 100) return;

        Location shooterLoc = shooter.getLocation().clone();
        Location victimLoc = victim.getLocation().clone();

        float tempYaw = shooterLoc.getYaw();
        float tempPitch = shooterLoc.getPitch();

        shooterLoc.setYaw(victimLoc.getYaw());
        shooterLoc.setPitch(victimLoc.getPitch());
        victimLoc.setYaw(tempYaw);
        victimLoc.setPitch(tempPitch);

        shooter.teleport(victimLoc);
        victim.teleport(shooterLoc);

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "sniezka-zamiany");

        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, shooter);
            plugin.getCombatIntegrationManager().tagPlayer(shooter, victim);
        }
    }
}
