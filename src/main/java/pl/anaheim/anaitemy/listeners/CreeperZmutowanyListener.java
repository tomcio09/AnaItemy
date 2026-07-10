package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.CreeperZmutowanyItem;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CreeperZmutowanyListener implements Listener {

    private static final String META_CREEPER = "anaitemy_zmutowany_creeper";
    private final AnaItemy plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public CreeperZmutowanyListener(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CreeperZmutowanyItem.isCreeperZmutowany(item)) return;

        event.setCancelled(true);

        // Cooldown
        Long end = cooldowns.get(player.getUniqueId());
        if (end != null && System.currentTimeMillis() < end) {
            long remaining = Math.max(0, (end - System.currentTimeMillis()) / 1000);
            String subtitle = plugin.getItemsConfig().getCreeperCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");
            player.showTitle(Title.title(Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));
            return;
        }

        // Region
        if (plugin.getWorldGuardManager().isInNamedRegion(player.getLocation(),
                plugin.getItemsConfig().getCreeperBlockedRegions())) return;

        // 4s protection
        // (creeper zadaje damage więc podlega)

        // Spawn creepera
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(2));
        Creeper creeper = player.getWorld().spawn(spawnLoc, Creeper.class, c -> {
            c.setPowered(true);
            c.setMetadata(META_CREEPER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            c.setExplosionRadius(0); // Nie niszczy terenu
        });

        // Wymuś natychmiastowy wybuch
        creeper.ignite();

        // Zużyj item
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        // Cooldown 60s
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 60000);
    }

    /**
     * ✅ Blokuj niszczenie terenu przez zmutowanego creepera.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (!creeper.hasMetadata(META_CREEPER)) return;

        // Nie niszcz bloków
        event.blockList().clear();

        // ✅ Zadaj 6 serc (12 HP) graczom w zasięgu
        Location center = creeper.getLocation();
        double damage = 12.0; // 6 serc

        String ownerUUID = creeper.getMetadata(META_CREEPER).get(0).asString();

        for (Player target : center.getWorld().getNearbyPlayers(center, 5.0)) {
            if (target.getUniqueId().toString().equals(ownerUUID)) continue;

            // 4s protection
            if (plugin.getItemProtectionManager().isProtected(target, "creeper-zmutowany")) {
                continue;
            }

            double newHealth = target.getHealth() - damage;
            if (newHealth <= 0) target.setHealth(0.0);
            else target.setHealth(newHealth);

            plugin.getItemProtectionManager().applyProtection(target, "creeper-zmutowany");
        }
    }

    /**
     * ✅ Blokuj vanilla damage od wybuchu creepera (robimy własny damage).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Creeper creeper)) return;
        if (creeper.hasMetadata(META_CREEPER)) {
            event.setCancelled(true);
        }
    }
}
