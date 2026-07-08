package pl.anaheim.anaitemy.listeners;

import org.bukkit.*;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PrzeterminowanyTrunekItem;

public class PrzeterminowanyTrunekListener implements Listener {

    private static final String META_TRUNEK = "anaitemy_trunek";
    private final AnaItemy plugin;

    public PrzeterminowanyTrunekListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (PrzeterminowanyTrunekItem.isPrzeterminowanyTrunek(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PrzeterminowanyTrunekItem.isPrzeterminowanyTrunek(item)) return;

        event.setCancelled(true);

        // ✅ Rzuć splash potion
        ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
        meta.setColor(Color.YELLOW);
        potionItem.setItemMeta(meta);

        ThrownPotion potion = player.launchProjectile(ThrownPotion.class);
        potion.setItem(potionItem);
        potion.setMetadata(META_TRUNEK, new FixedMetadataValue(plugin, true));

        // Zużyj
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }

    /**
     * ✅ Gdy potka wyląduje — stwórz AreaEffectCloud (dragon breath).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        if (!potion.hasMetadata(META_TRUNEK)) return;

        event.setCancelled(true);

        Location impact = potion.getLocation();
        World world = impact.getWorld();

        // ✅ Stwórz AreaEffectCloud (jak dragon breath)
        AreaEffectCloud cloud = world.spawn(impact, AreaEffectCloud.class, c -> {
            c.setRadius(3.0f);
            c.setRadiusOnUse(0f);
            c.setRadiusPerTick(-0.005f);
            c.setDuration(400); // 20 sekund
            c.setDurationOnUse(0);
            c.setWaitTime(0);
            c.setReapplicationDelay(20); // co sekundę
            c.setColor(Color.YELLOW);
            c.setParticle(Particle.DRAGON_BREATH);

            c.addCustomEffect(new PotionEffect(
                    PotionEffectType.SLOW, 400, 3, false, true, true), true);
            c.addCustomEffect(new PotionEffect(
                    PotionEffectType.POISON, 400, 0, false, true, true), true);
        });

        potion.remove();
    }
}
