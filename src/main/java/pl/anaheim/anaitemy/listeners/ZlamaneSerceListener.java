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

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof Player victim)) return;

        Player attacker = event.getPlayer();
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();

        if (!ZlamaneSerceItem.isZlamaneSerce(mainHand)) return;
        if (attacker.equals(victim)) return;

        event.setCancelled(true);

        // ✅ Cooldown
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(attacker.getUniqueId());
        if (lastUse != null && now - lastUse < COOLDOWN_MS) return;
        cooldowns.put(attacker.getUniqueId(), now);

        // ✅ 4s protection
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
                .replace("{nick}", victim.getName());
        attacker.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        victim.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        plugin.getItemsConfig().getZlamaneSerceVictimSubtitle()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "zlamane-serce");

        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }

        if (mainHand.getAmount() > 1) mainHand.setAmount(mainHand.getAmount() - 1);
        else attacker.getInventory().setItemInMainHand(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (ZlamaneSerceItem.isZlamaneSerce(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
