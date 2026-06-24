package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.BlokWidmoItem;
import pl.anaheim.anaitemy.managers.BlokWidmoManager;

public class BlokWidmoListener implements Listener {

    private final AnaItemy plugin;

    public BlokWidmoListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // ✅ Tylko PPM na blok
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // ✅ Ignoruj off-hand (zapobiega podwójnej aktywacji)
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!BlokWidmoItem.isBlokWidmo(item)) return;

        // ✅ ANULUJ postawienie bloku
        event.setCancelled(true);

        BlokWidmoManager manager = plugin.getBlokWidmoManager();
        ItemsConfig config = plugin.getItemsConfig();

        // ✅ Sprawdź cooldown
        if (manager.isOnCooldown(player)) {
            long remaining = manager.getCooldownRemaining(player);
            String timeLeft = formatTime(remaining);

            String message = config.getBlokWidmoCooldownMessage()
                    .replace("{time_left}", timeLeft);

            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(message));

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        // ✅ Sprawdź region
        if (manager.isInBlockedRegion(player.getLocation())) {
            return;
        }

        // ✅ Aktywuj blok widmo na lokacji klikniętego bloku
        Location activationLoc = event.getClickedBlock().getLocation().clone().add(0.5, 1.0, 0.5);
        manager.activate(player, activationLoc);
    }

    /**
     * ✅ KLUCZOWE: Przy śmierci gracza - zdejmij efekt bloku widmo NATYCHMIAST.
     * 
     * Priorytet LOWEST = uruchamia się PIERWSZY, ZANIM inne pluginy (serca, kostiumy)
     * przetworzą śmierć. Dzięki temu plugin na serca widzi prawdziwy max health
     * gracza i może poprawnie zabrać serce.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        BlokWidmoManager manager = plugin.getBlokWidmoManager();

        if (manager.isAffected(player)) {
            // ✅ Przywróć max health ZANIM inne pluginy przetworzą śmierć
            manager.forceRemoveEffect(player);
            
            plugin.getLogger().info("[BlokWidmo] Zdjęto efekt z gracza " + player.getName() + 
                    " z powodu śmierci (przed innymi pluginami)");
        }
    }

    /**
     * ✅ Przy wylogowaniu - usuń efekt bloku widmo.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BlokWidmoManager manager = plugin.getBlokWidmoManager();

        if (manager.isAffected(player)) {
            manager.forceRemoveEffect(player);
        }
    }

    /**
     * ✅ Przy zalogowaniu - upewnij się że nie ma resztek efektu.
     * Czyści modifier który mógł zostać (np. crash serwera).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BlokWidmoManager manager = plugin.getBlokWidmoManager();

        // Na wszelki wypadek - wyczyść efekt który mógł zostać
        if (manager.isAffected(player)) {
            manager.forceRemoveEffect(player);
        }
        
        // ✅ Dodatkowo wyczyść modifier nawet jeśli nie ma danych w mapie
        // (zabezpieczenie przed crashem serwera)
        manager.cleanupStaleModifier(player);
    }

    private String formatTime(long totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m" + String.format("%02d", seconds) + "s";
    }
}
