package pl.anaheim.anaitemy.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ✅ Debug listener do wychwytywania przyczyn "network protocol error".
 * Loguje ostatnie akcje gracza przed rozłączeniem.
 */
public class ProtocolDebugListener implements Listener {

    private final AnaItemy plugin;

    // Ostatnie akcje gracza (max 10)
    private final Map<UUID, LinkedList<String>> playerActions = new ConcurrentHashMap<>();

    public ProtocolDebugListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    private void logAction(Player player, String action) {
        LinkedList<String> actions = playerActions.computeIfAbsent(
                player.getUniqueId(), k -> new LinkedList<>());
        actions.addLast(System.currentTimeMillis() + " " + action);
        while (actions.size() > 15) actions.removeFirst();
    }

    // ✅ Loguj każde uderzenie eventówką
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            String itemName = "FIST";
            if (hand != null && hand.hasItemMeta() && hand.getItemMeta().displayName() != null) {
                itemName = PlainTextComponentSerializer.plainText()
                        .serialize(hand.getItemMeta().displayName());
            }
            logAction(attacker, "HIT_ENTITY item=" + itemName
                    + " target=" + event.getEntity().getType()
                    + " cancelled=" + event.isCancelled()
                    + " damage=" + String.format("%.1f", event.getFinalDamage()));
        }

        if (event.getEntity() instanceof Player victim) {
            String damagerType = event.getDamager().getType().name();
            if (event.getDamager() instanceof Player p) damagerType = "PLAYER:" + p.getName();
            logAction(victim, "TOOK_DAMAGE from=" + damagerType
                    + " cancelled=" + event.isCancelled()
                    + " health=" + String.format("%.1f", victim.getHealth())
                    + " damage=" + String.format("%.1f", event.getFinalDamage()));
        }
    }

    // ✅ Loguj teleportacje/ruch (tylko znaczące)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        // Tylko loguj jeśli event został zmieniony (setTo)
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            logAction(event.getPlayer(), "WORLD_CHANGE from=" + event.getFrom().getWorld().getName()
                    + " to=" + event.getTo().getWorld().getName());
        }
    }

    // ✅ Przechwytuj KICK — to jest moment gdy gracz dostaje "network protocol error"
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        String reason = PlainTextComponentSerializer.plainText()
                .serialize(event.reason());

        plugin.getLogger().warning("========== KICK DEBUG ==========");
        plugin.getLogger().warning("Gracz: " + player.getName());
        plugin.getLogger().warning("Powod: " + reason);
        plugin.getLogger().warning("Lokacja: " + player.getLocation().getBlockX() + ","
                + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ()
                + " swiat=" + player.getLocation().getWorld().getName());
        plugin.getLogger().warning("HP: " + String.format("%.1f", player.getHealth()));
        plugin.getLogger().warning("Gliding: " + player.isGliding());
        plugin.getLogger().warning("Swimming: " + player.isSwimming());
        plugin.getLogger().warning("Vehicle: " + (player.getVehicle() != null ? player.getVehicle().getType() : "none"));
        plugin.getLogger().warning("Gravity: " + player.hasGravity());
        plugin.getLogger().warning("Glowing: " + player.isGlowing());

        // Loguj aktywne efekty naszego pluginu
        if (plugin.getWedkaNielotaManager().hasCurse(player))
            plugin.getLogger().warning("AKTYWNE: WedkaNielota curse");
        if (plugin.getKroliczyMieczManager().isJumpBlocked(player))
            plugin.getLogger().warning("AKTYWNE: KroliczyMiecz jumpBlock");
        if (plugin.getMarchewkowyMieczManager().isFrozen(player))
            plugin.getLogger().warning("AKTYWNE: MarchewkowyMiecz frozen");
        if (plugin.getRogJednorozcaManager().isStunned(player))
            plugin.getLogger().warning("AKTYWNE: RogJednorozca stunned");
        if (plugin.getRogJednorozcaManager().hasActiveUnicorn(player))
            plugin.getLogger().warning("AKTYWNE: RogJednorozca unicorn");
        if (plugin.getRozdzkailuzjonistyManager().isVanished(player))
            plugin.getLogger().warning("AKTYWNE: Rozdzka vanished");
        if (plugin.getBoskiToporManager().isInvincible(player))
            plugin.getLogger().warning("AKTYWNE: BoskiTopor invincible");
        if (plugin.getBlokWidmoManager().isAffected(player))
            plugin.getLogger().warning("AKTYWNE: BlokWidmo affected");
        if (plugin.getSuperMarchewkaManager().hasActiveEffect(player))
            plugin.getLogger().warning("AKTYWNE: SuperMarchewka effect");
        if (plugin.getHydroKlatkaManager().getKlatkaForPlayer(player) != null)
            plugin.getLogger().warning("AKTYWNE: HydroKlatka trapped");
        if (plugin.getOlafManager().hasActiveOlaf(player))
            plugin.getLogger().warning("AKTYWNE: Olaf active");

        // Item w ręce
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.hasItemMeta() && hand.getItemMeta().displayName() != null) {
            plugin.getLogger().warning("Item w rece: " + PlainTextComponentSerializer.plainText()
                    .serialize(hand.getItemMeta().displayName()));
        }

        // Ostatnie akcje
        LinkedList<String> actions = playerActions.get(player.getUniqueId());
        if (actions != null && !actions.isEmpty()) {
            plugin.getLogger().warning("Ostatnie akcje:");
            for (String action : actions) {
                plugin.getLogger().warning("  " + action);
            }
        }

        plugin.getLogger().warning("=================================");
    }

    // ✅ Cleanup przy wylogowaniu
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playerActions.remove(event.getPlayer().getUniqueId());
    }
}
