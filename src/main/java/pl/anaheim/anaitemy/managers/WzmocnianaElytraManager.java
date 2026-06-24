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
import pl.anaheim.anaitemy.items.TotemUlaskawienia;
import pl.anaheim.anaitemy.items.WzmocnianaElytra;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WzmocnianaElytraManager {

    private final AnaItemy plugin;

    private final Map<UUID, BukkitTask> flyingPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerFlightDistance = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shiftClicks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> previousLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Double> distanceSinceLastUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> wasGliding = new ConcurrentHashMap<>();

    private static final double TOTAL_BLOCKS_FOR_100_PERCENT = 500.0;
    private static final int MAX_CHARGE = 100;
    private static final int DAMAGE_RADIUS = 5;
    private static final int DAMAGE = 20;
    private static final int SHIFT_CLICKS_NEEDED = 1;
    private static final int UPDATE_INTERVAL = 10;

    public WzmocnianaElytraManager(AnaItemy plugin) {
        this.plugin = plugin;
        startFlightMonitor();
    }

    private void startFlightMonitor() {
        new BukkitRunnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                tickCounter++;

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    ItemStack chestplate = player.getInventory().getChestplate();

                    if (player.isGliding() && WzmocnianaElytra.isWzmocnianaElytra(chestplate)) {
                        updateFlying(player, chestplate, tickCounter);
                        wasGliding.put(player.getUniqueId(), true);
                    } else {
                        if (wasGliding.getOrDefault(player.getUniqueId(), false)) {
                            handleLanding(player, chestplate);
                        }
                        stopFlying(player);
                        wasGliding.remove(player.getUniqueId());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updateFlying(Player player, ItemStack elytra, int tickCounter) {
        UUID uuid = player.getUniqueId();
        Location currentLoc = player.getLocation();

        if (!playerFlightDistance.containsKey(uuid)) {
            WzmocnianaElytra.resetCharge(elytra);
            playerFlightDistance.put(uuid, 0.0);
            distanceSinceLastUpdate.put(uuid, 0.0);
            previousLocations.put(uuid, currentLoc.clone());
            startActionBarDisplay(player);
            return;
        }

        Location prevLoc = previousLocations.get(uuid);
        if (prevLoc != null && prevLoc.getWorld().equals(currentLoc.getWorld())) {
            double distance = currentLoc.distance(prevLoc);
            double accumulated = distanceSinceLastUpdate.getOrDefault(uuid, 0.0) + distance;
            distanceSinceLastUpdate.put(uuid, accumulated);

            if (tickCounter % UPDATE_INTERVAL == 0) {
                double totalDistance = playerFlightDistance.get(uuid) + accumulated;
                playerFlightDistance.put(uuid, totalDistance);
                distanceSinceLastUpdate.put(uuid, 0.0);

                double charge = Math.min(100.0, (totalDistance / TOTAL_BLOCKS_FOR_100_PERCENT) * 100.0);
                WzmocnianaElytra.setCharge(elytra, charge);
            }
        }

        previousLocations.put(uuid, currentLoc.clone());
    }

    private void handleLanding(Player player, ItemStack elytra) {
        if (!WzmocnianaElytra.isWzmocnianaElytra(elytra)) return;

        double charge = WzmocnianaElytra.getCharge(elytra);
        if (charge < 100.0) return;

        if (!player.isOnGround()) return;

        Location landingLoc = player.getLocation().clone();
        landingLoc.setY(landingLoc.getY() - 0.5);

        triggerLightningStrike(player, landingLoc);
    }

    private void stopFlying(Player player) {
        UUID uuid = player.getUniqueId();
        playerFlightDistance.remove(uuid);
        previousLocations.remove(uuid);
        distanceSinceLastUpdate.remove(uuid);
        shiftClicks.remove(uuid);

        BukkitTask task = flyingPlayers.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        plugin.getActionBarManager().removeActionBar(player, "elytra");
    }

    private void startActionBarDisplay(Player player) {
        UUID uuid = player.getUniqueId();

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

                String elytraBar = "&bWzmocniana elytra &fzaladowana w &3" + chargeStr + "%";

                plugin.getActionBarManager().setActionBar(player, "elytra", elytraBar);
            }
        }.runTaskTimer(plugin, 0L, UPDATE_INTERVAL);

        flyingPlayers.put(uuid, task);
    }

    public void onShiftClick(Player player) {
        UUID uuid = player.getUniqueId();

        if (!player.isGliding()) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        int clicks = shiftClicks.getOrDefault(uuid, 0) + 1;
        shiftClicks.put(uuid, clicks);

        if (clicks >= SHIFT_CLICKS_NEEDED) {
            WzmocnianaElytra.resetCharge(chestplate);
            playerFlightDistance.put(uuid, 0.0);
            distanceSinceLastUpdate.put(uuid, 0.0);
            previousLocations.put(uuid, player.getLocation().clone());
            shiftClicks.remove(uuid);

            player.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Kliknąłeś &bSHIFT&7! Procenty zostały wyczyszczone."),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
            ));
        }
    }

    public void triggerLightningStrike(Player player, Location landingLocation) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        double charge = WzmocnianaElytra.getCharge(chestplate);
        if (charge < 100.0) return;

        Location center = landingLocation.clone();

        // ✅ NAJPIERW TAG COMBATU - POTEM DAMAGE
        // (żeby gracz który zginie miał combat tag zanim umrze)

        // ✅ 1. TAG COMBATU - strzelec
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(player, player);
        }

        // ✅ 2. TAG COMBATU - wszyscy w obrębie (PRZED damage)
        for (Player nearPlayer : center.getWorld().getNearbyPlayers(center, DAMAGE_RADIUS)) {
            if (nearPlayer == null || !nearPlayer.isOnline()) continue;
            if (nearPlayer.equals(player)) continue;

            if (plugin.getCombatIntegrationManager().isEnabled()) {
                plugin.getCombatIntegrationManager().tagPlayer(nearPlayer, player);
            }
        }

        // ✅ 3. PIORUN - visual only
        center.getWorld().strikeLightningEffect(center);

        // ✅ 4. DŹWIĘK
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 2.0f, 1.0f);

        // ✅ 5. CZĄSTECZKI
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 50, 1, 1, 1, 0.5);

        // ✅ 6. DAMAGE - wszyscy w 5x5x5 OPRÓCZ STRZELCA (Z TOTEMEM)
        for (Player nearPlayer : center.getWorld().getNearbyPlayers(center, DAMAGE_RADIUS)) {
            if (nearPlayer == null || !nearPlayer.isOnline()) continue;
            if (nearPlayer.equals(player)) continue;

            double newHealth = nearPlayer.getHealth() - DAMAGE;

            if (newHealth <= 0) {
                // ✅ Sprawdź totem ułaskawienia
                ItemStack mainHand = nearPlayer.getInventory().getItemInMainHand();
                ItemStack offHand = nearPlayer.getInventory().getItemInOffHand();

                if (TotemUlaskawienia.isTotemUlaskawienia(mainHand) ||
                        TotemUlaskawienia.isTotemUlaskawienia(offHand)) {
                    // Totem ratuje - ustaw 1 HP
                    nearPlayer.setHealth(1.0);
                } else {
                    nearPlayer.setHealth(0);
                }
            } else {
                nearPlayer.setHealth(newHealth);
            }
        }

        // ✅ 7. RESETUJ CHARGE
        WzmocnianaElytra.resetCharge(chestplate);
        playerFlightDistance.remove(player.getUniqueId());
        distanceSinceLastUpdate.remove(player.getUniqueId());
        previousLocations.remove(player.getUniqueId());
    }

    public void cleanup() {
        for (BukkitTask task : flyingPlayers.values()) {
            task.cancel();
        }
        flyingPlayers.clear();
        playerFlightDistance.clear();
        previousLocations.clear();
        distanceSinceLastUpdate.clear();
        shiftClicks.clear();
        wasGliding.clear();
    }
}
