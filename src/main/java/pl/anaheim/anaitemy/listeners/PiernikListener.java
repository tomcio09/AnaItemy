package pl.anaheim.anaitemy.listeners;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiernikItem;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PiernikListener implements Listener {

    private final AnaItemy plugin;
    private final Set<UUID> eatingPlayers = ConcurrentHashMap.newKeySet();

    public PiernikListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj vanilla jedzenie cookie.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (PiernikItem.isPiernik(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * ✅ PPM = rozpocznij jedzenie (animacja + dźwięk + efekt po 1.5s).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PiernikItem.isPiernik(item)) return;

        event.setCancelled(true);

        // ✅ Zapobiegnij spamowaniu
        if (eatingPlayers.contains(player.getUniqueId())) return;
        eatingPlayers.add(player.getUniqueId());

        // ✅ Dźwięki jedzenia (3 razy w trakcie)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 30) {
                    cancel();
                    return;
                }

                // Sprawdź czy gracz nadal trzyma piernika
                if (!PiernikItem.isPiernik(player.getInventory().getItemInMainHand())) {
                    eatingPlayers.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (ticks % 8 == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT,
                            SoundCategory.PLAYERS, 1.0f, 1.0f);
                }

                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // ✅ Po 1.5 sekundy — efekt + zużycie
        new BukkitRunnable() {
            @Override
            public void run() {
                eatingPlayers.remove(player.getUniqueId());

                if (!player.isOnline()) return;

                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (!PiernikItem.isPiernik(currentItem)) return;

                // ✅ Daj efekt haste
                int duration = plugin.getItemsConfig().getPiernikHasteDuration() * 20;
                int level = plugin.getItemsConfig().getPiernikHasteLevel() - 1;
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.FAST_DIGGING, duration, level, false, true, true));

                // ✅ Dźwięk zjedzenia
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                        SoundCategory.PLAYERS, 1.0f, 1.0f);

                // ✅ Zużyj
                if (currentItem.getAmount() > 1) {
                    currentItem.setAmount(currentItem.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
        }.runTaskLater(plugin, 30L); // 1.5 sekundy
    }
}
