// src/main/java/pl/anaheim/anaitemy/listeners/ZlamaneSerceListener.java
package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.ZlamaneSerceItem;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZlamaneSerceListener implements Listener {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000L;

    public ZlamaneSerceListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== PRAWY KLIK NA GRACZA ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof Player victim)) return;

        Player attacker = event.getPlayer();
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();

        if (!ZlamaneSerceItem.isZlamaneSerce(mainHand)) return;
        if (attacker.equals(victim)) return;

        event.setCancelled(true);
        applyZlamaneSerce(attacker, victim, mainHand);
    }

    // ==================== LEWY KLIK (UDERZENIE) NA GRACZA ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!ZlamaneSerceItem.isZlamaneSerce(mainHand)) return;

        // Anuluj obrażenia - złamane serce nie zadaje dmg, tylko daje efekt
        event.setCancelled(true);
        applyZlamaneSerce(attacker, victim, mainHand);
    }

    // ==================== GŁÓWNA LOGIKA ====================

    private void applyZlamaneSerce(Player attacker, Player victim, ItemStack mainHand) {
        if (attacker.equals(victim)) return;

        // Cooldown
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(attacker.getUniqueId());
        if (lastUse != null && now - lastUse < COOLDOWN_MS) return;
        cooldowns.put(attacker.getUniqueId(), now);

        // 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "zlamane-serce")) {
            int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "zlamane-serce");
            plugin.getItemProtectionManager().notifyAttacker(attacker, "zlamane-serce", sl);
            return;
        }

        int duration = plugin.getItemsConfig().getZlamaneSerceSlowFallingDuration() * 20;

        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, duration, 0, false, true, true));

        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT,
                SoundCategory.PLAYERS, 0.5f, 2.0f);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT,
                SoundCategory.PLAYERS, 0.5f, 2.0f);

        String attackerSub = plugin.getItemsConfig().getZlamaneSerceAttackerSubtitle()
                .replace("{victim}", victim.getName())
                .replace("{nick}", victim.getName());
        attacker.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        String victimSub = plugin.getItemsConfig().getZlamaneSerceVictimSubtitle()
                .replace("{attacker}", attacker.getName())
                .replace("{nick}", attacker.getName());
        victim.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(victimSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        // Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "zlamane-serce");

        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        // Zużyj przedmiot
        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            attacker.getInventory().setItemInMainHand(null);
        }
    }

    // ==================== BLOKUJ STAWIANIE ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (ZlamaneSerceItem.isZlamaneSerce(item)) {
            // ✅ Nie anuluj całego eventu - tylko zablokuj użycie na bloku
            // żeby PlayerInteractEntityEvent dalej się odpalił
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }
}
