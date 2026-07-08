package pl.anaheim.anaitemy.listeners;

import org.bukkit.*;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PrzeterminowanyTrunekItem;

public class PrzeterminowanyTrunekListener implements Listener {

    private final AnaItemy plugin;

    public PrzeterminowanyTrunekListener(AnaItemy plugin) { this.plugin = plugin; }

    // ✅ Blokuj vanilla picie
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
        ThrownPotion potion = player.launchProjectile(ThrownPotion.class);

        ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
        meta.setColor(Color.YELLOW);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.SLOW, 400, 3, false, true, true), true);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 400, 0, false, true, true), true);
        potionItem.setItemMeta(meta);
        potion.setItem(potionItem);

        // Zużyj
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }
}
