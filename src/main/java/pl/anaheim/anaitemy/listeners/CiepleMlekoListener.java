package pl.anaheim.anaitemy.listeners;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.CiepleMlekoItem;

import java.util.Set;

public class CiepleMlekoListener implements Listener {

    private final AnaItemy plugin;

    // ✅ 1.21.4 - nowe nazwy PotionEffectType
    private static final Set<PotionEffectType> NEGATIVE_EFFECTS = Set.of(
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA,        // było CONFUSION
            PotionEffectType.HUNGER,
            PotionEffectType.POISON,
            PotionEffectType.SLOWNESS,      // było SLOW
            PotionEffectType.MINING_FATIGUE, // było SLOW_DIGGING
            PotionEffectType.WEAKNESS,
            PotionEffectType.WITHER,
            PotionEffectType.UNLUCK,
            PotionEffectType.BAD_OMEN,
            PotionEffectType.DARKNESS,
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.LEVITATION
    );

    public CiepleMlekoListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CiepleMlekoItem.isCiepleMleko(item)) return;

        event.setCancelled(true);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (NEGATIVE_EFFECTS.contains(effect.getType())) {
                player.removePotionEffect(effect.getType());
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }
}
