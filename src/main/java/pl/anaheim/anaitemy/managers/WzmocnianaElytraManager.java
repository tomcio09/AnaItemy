package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.WzmocnianaElytra;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WzmocnianaElytraManager {

    private final AnaItemy plugin;

    // Gracze którzy aktualnie latają elytrą (UUID -> taska actionbara)
    private final Map<UUID, BukkitTask> flyingPlayers = new ConcurrentHashMap<>();

    // Gracze którzy są w locie (do sprawdzenia czy naładować)
    private final Map<UUID, Double> playerFlightDistance = new ConcurrentHashMap<>();

    // Shift clicks (UUID -> liczba kliknięć)
    private final Map<UUID, Integer> shiftClicks = new ConcurrentHashMap<>();

    private static final double DISTANCE_PER_1_PERCENT = 5.0; // 500 bloków = 100%
    private static final int MAX_CHARGE = 100;
    private static final int DAMAGE_RADIUS = 5;
    private static final int DAMAGE = 20; // 10 serc
    private static final int SHIFT_CLICKS_NEEDED = 1; // do configu

    public WzmocnianaElytraManager(AnaItemy plugin) {
        this.plugin = plugin;
        startFlightMonitor();
    }

    /**
     * ✅ Monitoruj lot graczy z elytrą.
     */
    private void startFlightMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    ItemStack chestplate = player.getInventory().getChestplate();

                    // Gracz leci z wzmocnianą elytrą
                    if (player.isGliding() && WzmocnianaElytra.isWzmocnianaElytra(chestplate)) {
                        startFlying(player, chestplate);
                    } else {
                        // Gracz przestał lecieć lub zdje elytrę
                        stopFlying(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Co tick
    }

    private void startFlying(Player player, ItemStack elytra) {
        UUID uuid = player.getUniqueId();

        // Jeśli już leci - aktualizuj dystans
        if (playerFlightDistance.containsKey(uuid)) {
            Vector vel = player.getVelocity();
            double distance = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ() + vel.getY() * vel.getY());
            playerFlightDistance.put(uuid, playerFlightDistance.get(uuid) + distance);
        } else {
            // Nowy lot - resetuj procent i dystans
            WzmocnianaElytra.resetCharge(elytra);
            playerFlightDistance.put(uuid, 0.0);
            startActionBarDisplay(player);
        }

        // Załaduj elytrę
        double totalDistance = playerFlightDistance.get(uuid);
        double charge = Math.min(100.0, (totalDistance / DISTANCE_PER_1_PERCENT) * 100.0);
        WzmocnianaElytra.setCharge(elytra, charge);

        // Resetuj shift clicks
        shiftClicks.remove(uuid);
    }

    private void stopFlying(Player player) {
        UUID uuid = player.getUniqueId();
        playerFlightDistance.remove(uuid);
        shiftClicks.remove(uuid);

        BukkitTask task = flyingPlayers.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        // Wyczyść action bar tylko jeśli hydroklatka nie wysyła
        if (!plugin.getHydroKlatkaManager().isPlayerOnCooldown(player)) {
            player.sendActionBar(Component.empty());
        }
    }

    private void startActionBarDisplay(Player player) {
        UUID uuid = player.getUniqueId();

        // Jeśli już wyświetla - nie zamawiaj drugiego
        if (flyingPlayers.containsKey(uuid)) return;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.isGliding()) {
                    cancel();
                    flyingPlayers.remove(uuid);
                    return;
                }

                ItemStack chestplate = player.getInventory().getChestplate();
                if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) {
                    cancel();
                    flyingPlayers.remove(uuid);
                    return;
                }

                double charge = WzmocnianaElytra.getCharge(chestplate);
                String chargeStr = String.format("%.2f", charge);

                // ✅ Główny action bar elytra
                String elytraBar = "&bWzmocniana elytra &fzaladowana w &3" + chargeStr + "%";

                // Sprawdź czy hydroklatka wysyła
                if (plugin.getHydroKlatkaManager().isPlayerOnCooldown(player)) {
                    long remaining = plugin.getHydroKlatkaManager().getPlayerCooldownRemaining(player);
                    String hydroBar = plugin.getItemsConfig().getHydroKlatkaActionBarFormat()
                            .replace("{time}", String.valueOf(remaining));
                    // ✅ Połącz oba action bary
                    String combined = elytraBar + " &8| " + hydroBar;
                    player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(combined));
                } else {
                    // Tylko elytra
                    player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(elytraBar));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        flyingPlayers.put(uuid, task);
    }

    /**
     * ✅ Gracz kliknął shift - resetuj ładowanie.
     */
    public void onShiftClick(Player player) {
        UUID uuid = player.getUniqueId();

        if (!player.isGliding()) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        // Zwiększ licznik
        int clicks = shiftClicks.getOrDefault(uuid, 0) + 1;
        shiftClicks.put(uuid, clicks);

        if (clicks >= SHIFT_CLICKS_NEEDED) {
            // ✅ Resetuj ładowanie
            WzmocnianaElytra.resetCharge(chestplate);
            playerFlightDistance.put(uuid, 0.0);
            shiftClicks.remove(uuid);

            // Wyślij wiadomość
            player.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Kliknąłeś &bSHIFT&7! Procenty zostały wyczyszczone."),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
            ));
        }
    }

    /**
     * ✅ Gracz wpadł w ziemię z 100% elytrą.
     */
    public void triggerLightningStrike(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        double charge = WzmocnianaElytra.getCharge(chestplate);
        if (charge < 100.0) return;

        Location center = player.getLocation();
        center.setY(player.getLocation().getY() - 1); // Gdzie gracz uderzył

        // ✅ PIORUN - visual only
        center.getWorld().strikeLightningEffect(center);

        // ✅ DŹWIĘK
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 2.0f, 1.0f);

        // ✅ CZĄSTECZKI
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 50, 1, 1, 1, 0.5);

        // ✅ DAMAGE - wszyscy w 5x5x5
        for (Player nearPlayer : center.getWorld().getNearbyPlayers(center, DAMAGE_RADIUS)) {
            if (nearPlayer == null || !nearPlayer.isOnline()) continue;

            // Odejmij serca bezpośrednio
            double newHealth = nearPlayer.getHealth() - DAMAGE;
            if (newHealth <= 0) {
                nearPlayer.setHealth(0);
            } else {
                nearPlayer.setHealth(newHealth);
            }

            // ✅ TAG COMBATU - wszyscy w obrębie + strzelec
            if (plugin.getCombatIntegrationManager().isEnabled()) {
                plugin.getCombatIntegrationManager().tagPlayer(nearPlayer, player);
            }
        }

        // ✅ TAG COMBATU - strzelec
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(player, player);
        }

        // ✅ RESETUJ CHARGE
        WzmocnianaElytra.resetCharge(chestplate);
        playerFlightDistance.remove(player.getUniqueId());
    }

    public void cleanup() {
        for (BukkitTask task : flyingPlayers.values()) {
            task.cancel();
        }
        flyingPlayers.clear();
        playerFlightDistance.clear();
        shiftClicks.clear();
    }
}
