package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RogJednorozcaManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveUnicorn> activeUnicorns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stunnedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Location> stunnedLocations = new ConcurrentHashMap<>();

    private static final Set<Material> FULLY_INDESTRUCTIBLE = Set.of(
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_VOID, Material.JIGSAW,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.NETHER_PORTAL
    );

    private static final Set<Material> DEATH_ONLY_DESTRUCTIBLE = Set.of(
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN
    );

    private BukkitTask tickTask;
    private BukkitTask stunTask;

    public RogJednorozcaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
        startStunTask();
    }

    // ==================== TASKS ====================

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());

                for (ActiveUnicorn unicorn : new ArrayList<>(activeUnicorns.values())) {
                    Player owner = Bukkit.getPlayer(unicorn.getOwnerId());

                    if (owner == null || !owner.isOnline()) {
                        removeUnicorn(unicorn, false);
                        continue;
                    }

                    Horse horse = unicorn.getHorse();
                    if (horse == null || horse.isDead() || !horse.isValid()) {
                        removeUnicorn(unicorn, false);
                        continue;
                    }

                    // Gracz nie jedzie koniem
                    if (!horse.getPassengers().contains(owner)) {
                        removeUnicorn(unicorn, true);
                        continue;
                    }

                    // Czas wygasł
                    if (unicorn.isExpired()) {
                        removeUnicorn(unicorn, true);
                        continue;
                    }

                    // Sprawdź przejechane bloki
                    Location current = horse.getLocation();
                    double distance = unicorn.addDistance(current);
                    if (distance >= unicorn.getMaxDistance()) {
                        removeUnicorn(unicorn, true);
                        continue;
                    }

                    // Sprawdź region - jeśli wjeżdża w zablokowany region, znika
                    List<String> blockedRegions = plugin.getItemsConfig().getRogJednorozcaBlockedRegions();
                    if (plugin.getWorldGuardManager().isInBlockedRegion(current, blockedRegions)) {
                        removeUnicorn(unicorn, true);
                        continue;
                    }

                    // ✅ Niszcz bloki przed koniem (KAŻDY TICK)
                    boolean canBuild = canDestroyInRegion(current);
                    if (canBuild) {
                        destroyBlocksInFront(horse);
                    }

                    // Ogłuszaj graczy
                    stunNearbyPlayers(horse, owner);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // ✅ CO TICK (nie co 2 ticki)
    }

    private void startStunTask() {
        stunTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(stunnedPlayers.entrySet())) {
                    if (now >= entry.getValue()) {
                        stunnedPlayers.remove(entry.getKey());
                        stunnedLocations.remove(entry.getKey());
                        continue;
                    }

                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        stunnedPlayers.remove(entry.getKey());
                        stunnedLocations.remove(entry.getKey());
                        continue;
                    }

                    // Trzymaj gracza w miejscu
                    Location stunLoc = stunnedLocations.get(entry.getKey());
                    if (stunLoc != null) {
                        Location playerLoc = player.getLocation();
                        if (playerLoc.getWorld().equals(stunLoc.getWorld())) {
                            double dist = playerLoc.distance(stunLoc);
                            if (dist > 0.3) {
                                Location tp = stunLoc.clone();
                                tp.setYaw(playerLoc.getYaw());
                                tp.setPitch(playerLoc.getPitch());
                                player.teleport(tp);
                            }
                            player.setVelocity(new Vector(0, 0, 0));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== COOLDOWN ====================

    public boolean isOnCooldown(Player player) {
        Long end = cooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getCooldownRemaining(Player player) {
        Long end = cooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long seconds = config.getRogJednorozcaCooldown();
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
        player.setCooldown(Material.GOAT_HORN, (int) (seconds * 20));
    }

    public void resetCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.setCooldown(Material.GOAT_HORN, 0);
    }

    // ==================== SPAWN ====================

    public void summonUnicorn(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        Location spawnLoc = player.getLocation();

        // ✅ Niszcz bloki 3x3x3 przy spawnie (bedrock = skip, obsydian = skip)
        boolean canBuild = canDestroyInRegion(spawnLoc);
        if (canBuild) {
            destroyArea(spawnLoc, 3, false);
        }

        // Spawn konia
        Horse horse = spawnLoc.getWorld().spawn(spawnLoc, Horse.class, h -> {
            h.setColor(Horse.Color.WHITE);
            h.setStyle(Horse.Style.NONE);
            h.setTamed(true);
            h.setOwner(player);
            h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            h.setAdult();

            // Najszybszy koń w MC: ~0.3375 movement speed
            if (h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3375);
            }
            // Skok
            if (h.getAttribute(Attribute.HORSE_JUMP_STRENGTH) != null) {
                h.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(1.0);
            }
        });

        // Gracz wsiada
        horse.addPassenger(player);

        // Dźwięk rogu
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ITEM_GOAT_HORN_SOUND_0,
                SoundCategory.PLAYERS, 2.0f, 1.0f);

        // Particle
        spawnLoc.getWorld().spawnParticle(Particle.HEART, spawnLoc.clone().add(0, 2, 0),
                15, 1, 0.5, 1, 0.1);

        // Cooldown
        setCooldown(player);

        // Zapisz aktywnego jednorożca
        int duration = config.getRogJednorozcaDuration();
        int maxBlocks = config.getRogJednorozcaMaxBlocks();

        ActiveUnicorn unicorn = new ActiveUnicorn(
                player.getUniqueId(),
                horse,
                System.currentTimeMillis() + (duration * 1000L),
                maxBlocks
        );
        activeUnicorns.put(player.getUniqueId(), unicorn);
    }

    // ==================== USUWANIE ====================

    private void removeUnicorn(ActiveUnicorn unicorn, boolean destroyOnDeath) {
        activeUnicorns.remove(unicorn.getOwnerId());

        Horse horse = unicorn.getHorse();
        if (horse != null && horse.isValid() && !horse.isDead()) {
            Location deathLoc = horse.getLocation();

            // ✅ Niszcz bloki 3x3x3 przy śmierci (obsydian = TAK, bedrock = NIE)
            boolean canBuild = canDestroyInRegion(deathLoc);
            if (canBuild && destroyOnDeath) {
                destroyArea(deathLoc, 3, true);
            }

            horse.eject();
            horse.remove();
        }
    }

    public void forceRemove(Player player) {
        ActiveUnicorn unicorn = activeUnicorns.get(player.getUniqueId());
        if (unicorn != null) {
            removeUnicorn(unicorn, false);
        }
    }

    public boolean hasActiveUnicorn(Player player) {
        return activeUnicorns.containsKey(player.getUniqueId());
    }

    // ==================== NISZCZENIE BLOKÓW ====================

    /**
     * ✅ Niszczy bloki 3x3 PRZED koniem w kierunku patrzenia.
     * Tunel 3 wysoki (Y+0, Y+1, Y+2 od poziomu konia) i 3 szeroki.
     * Wywołane CO TICK - koń przejeżdża przez ściany bez zatrzymywania.
     */
    private void destroyBlocksInFront(Horse horse) {
        Location loc = horse.getLocation();
        Vector direction = loc.getDirection().clone();
        direction.setY(0).normalize();

        // Jeśli koń stoi w miejscu - nie niszcz
        if (direction.lengthSquared() < 0.01) return;

        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        int baseY = loc.getBlockY();
        World world = loc.getWorld();

        // ✅ Sprawdź 2 bloki przed koniem (żeby przy pełnej prędkości nie przeskakiwał)
        for (int forward = 1; forward <= 2; forward++) {
            Location frontCenter = loc.clone().add(direction.clone().multiply(forward));

            for (int dy = 0; dy <= 2; dy++) {
                for (int side = -1; side <= 1; side++) {
                    Location blockLoc = new Location(world,
                            frontCenter.getBlockX() + (int) Math.round(right.getX() * side),
                            baseY + dy,
                            frontCenter.getBlockZ() + (int) Math.round(right.getZ() * side)
                    );

                    Block block = blockLoc.getBlock();
                    if (canDestroyBlock(block, false)) {
                        block.breakNaturally();
                    }
                }
            }
        }
    }

    /**
     * ✅ Niszczy bloki w obszarze 3x3x3 (spawn/śmierć).
     * @param deathExplosion true = niszczy też obsydian
     */
    private void destroyArea(Location center, int size, boolean deathExplosion) {
        int half = size / 2;
        World world = center.getWorld();
        int baseY = center.getBlockY();

        for (int x = -half; x <= half; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = -half; z <= half; z++) {
                    Block block = world.getBlockAt(
                            center.getBlockX() + x,
                            baseY + y,
                            center.getBlockZ() + z
                    );
                    if (canDestroyBlock(block, deathExplosion)) {
                        block.breakNaturally();
                    }
                }
            }
        }
    }

    /**
     * ✅ Sprawdza czy blok może być zniszczony.
     * - Bedrock, barrier itp. = NIGDY
     * - Obsydian = TYLKO przy śmierci konia (deathExplosion = true)
     * - Reszta = TAK
     */
    private boolean canDestroyBlock(Block block, boolean deathExplosion) {
        Material type = block.getType();

        if (type.isAir()) return false;
        if (FULLY_INDESTRUCTIBLE.contains(type)) return false;

        // Obsydian tylko przy śmierci/spawnie z deathExplosion
        if (DEATH_ONLY_DESTRUCTIBLE.contains(type)) {
            return deathExplosion;
        }

        return true;
    }

    // ==================== OGŁUSZANIE ====================

    private void stunNearbyPlayers(Horse horse, Player owner) {
        ItemsConfig config = plugin.getItemsConfig();
        Location horseLoc = horse.getLocation();
        double stunRadius = 1.5;

        for (Player target : horseLoc.getWorld().getNearbyPlayers(horseLoc, stunRadius)) {
            if (target.equals(owner)) continue;
            if (isStunned(target)) continue;

            // Region check
            List<String> blockedRegions = config.getRogJednorozcaBlockedRegions();
            if (plugin.getWorldGuardManager().isInBlockedRegion(target.getLocation(), blockedRegions)) {
                continue;
            }

            // 4s protection (od końca ogłuszenia)
            if (plugin.getItemProtectionManager().isProtected(target, "rog-jednorozca")) {
                plugin.getItemProtectionManager()
                        .notifyAttacker(owner, "rog-jednorozca",
                                plugin.getItemProtectionManager().getRemainingSeconds(target, "rog-jednorozca"));
                continue;
            }

            applyStun(target, config.getRogJednorozcaStunDuration());

            // Combat tag
            if (plugin.getCombatIntegrationManager().isEnabled()) {
                plugin.getCombatIntegrationManager().tagPlayer(target, owner);
                plugin.getCombatIntegrationManager().tagPlayer(owner, target);
            }
        }
    }

    private void applyStun(Player target, int durationSeconds) {
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        stunnedPlayers.put(target.getUniqueId(), endTime);
        stunnedLocations.put(target.getUniqueId(), target.getLocation().clone());

        // Subtitle
        target.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(plugin.getItemsConfig().getRogJednorozcaStunSubtitle()),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2500),
                        Duration.ofMillis(200)
                )
        ));

        // Nałóż protection od KOŃCA ogłuszenia
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getItemProtectionManager().applyProtection(target, "rog-jednorozca");
        }, durationSeconds * 20L);
    }

    public boolean isStunned(Player player) {
        Long end = stunnedPlayers.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public void removeStun(Player player) {
        stunnedPlayers.remove(player.getUniqueId());
        stunnedLocations.remove(player.getUniqueId());
    }

    // ==================== REGION CHECKS ====================

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInBlockedRegion(
                location,
                plugin.getItemsConfig().getRogJednorozcaBlockedRegions()
        );
    }

    /**
     * ✅ Sprawdza czy w danym regionie można niszczyć bloki.
     * Jeśli build/block-break jest off - nie niszczymy bloków ale koń nadal jeździ.
     */
    private boolean canDestroyInRegion(Location location) {
        // Sprawdź czy WorldGuard pozwala na block-break
        // Używamy null gracza - sprawdzamy ogólną flagę regionu
        return plugin.getWorldGuardManager().canBreakBlock(null, location);
    }

    // ==================== HORSE CHECKS ====================

    public boolean isUnicornHorse(Horse horse) {
        for (ActiveUnicorn unicorn : activeUnicorns.values()) {
            if (unicorn.getHorse() != null && unicorn.getHorse().equals(horse)) {
                return true;
            }
        }
        return false;
    }

    public ActiveUnicorn getUnicornByHorse(Horse horse) {
        for (ActiveUnicorn unicorn : activeUnicorns.values()) {
            if (unicorn.getHorse() != null && unicorn.getHorse().equals(horse)) {
                return unicorn;
            }
        }
        return null;
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();
        if (stunTask != null) stunTask.cancel();

        for (ActiveUnicorn unicorn : new ArrayList<>(activeUnicorns.values())) {
            Horse horse = unicorn.getHorse();
            if (horse != null && horse.isValid()) {
                horse.eject();
                horse.remove();
            }
        }

        activeUnicorns.clear();
        cooldowns.clear();
        stunnedPlayers.clear();
        stunnedLocations.clear();
    }

    // ==================== INNER CLASS ====================

    public static class ActiveUnicorn {
        private final UUID ownerId;
        private final Horse horse;
        private final long expirationTime;
        private final int maxDistance;
        private Location lastLocation;
        private double totalDistance;

        public ActiveUnicorn(UUID ownerId, Horse horse, long expirationTime, int maxDistance) {
            this.ownerId = ownerId;
            this.horse = horse;
            this.expirationTime = expirationTime;
            this.maxDistance = maxDistance;
            this.lastLocation = horse.getLocation().clone();
            this.totalDistance = 0;
        }

        public UUID getOwnerId() { return ownerId; }
        public Horse getHorse() { return horse; }
        public int getMaxDistance() { return maxDistance; }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public double addDistance(Location current) {
            if (lastLocation != null && lastLocation.getWorld().equals(current.getWorld())) {
                totalDistance += lastLocation.distance(current);
            }
            lastLocation = current.clone();
            return totalDistance;
        }
    }
}
