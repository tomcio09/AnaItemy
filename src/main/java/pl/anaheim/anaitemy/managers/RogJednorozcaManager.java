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
    private final Map<UUID, Boolean> stunnedGravity = new ConcurrentHashMap<>();

    private static final Set<Material> FULLY_INDESTRUCTIBLE = Set.of(
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_VOID, Material.JIGSAW,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.NETHER_PORTAL
    );

    private static final Set<Material> DEATH_ONLY_DESTRUCTIBLE = Set.of(
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN
    );

    private BukkitTask stunTask;

    public RogJednorozcaManager(AnaItemy plugin) {
        this.plugin = plugin;
        startStunTask();
        startCooldownCleanup();
    }

    // ==================== STUN TASK ====================

    private void startStunTask() {
        stunTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(stunnedPlayers.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) {
                        // Stun skończony
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            // Przywróć grawitację
                            Boolean hadGravity = stunnedGravity.remove(uuid);
                            if (hadGravity != null) {
                                player.setGravity(true);
                            }
                        }
                        stunnedPlayers.remove(uuid);
                        stunnedLocations.remove(uuid);
                        continue;
                    }

                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        stunnedPlayers.remove(uuid);
                        stunnedLocations.remove(uuid);
                        stunnedGravity.remove(uuid);
                        continue;
                    }

                    // Trzymaj gracza w miejscu
                    Location stunLoc = stunnedLocations.get(uuid);
                    if (stunLoc != null && player.getLocation().getWorld().equals(stunLoc.getWorld())) {
                        Location tp = stunLoc.clone();
                        tp.setYaw(player.getLocation().getYaw());
                        tp.setPitch(player.getLocation().getPitch());
                        player.teleport(tp);
                        player.setVelocity(new Vector(0, 0, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startCooldownCleanup() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
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

        // ✅ Niszcz bloki 3x3x3 przy spawnie
        boolean canBuild = canDestroyInRegion(spawnLoc);
        if (canBuild) {
            destroyArea(spawnLoc, false);
        }

        // Spawn konia
        Horse horse = spawnLoc.getWorld().spawn(spawnLoc, Horse.class, h -> {
            h.setColor(Horse.Color.WHITE);
            h.setStyle(Horse.Style.NONE);
            h.setTamed(true);
            h.setOwner(player);
            h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            h.setAdult();

            // Najszybszy koń w MC
            if (h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
                h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3375);
            }
            if (h.getAttribute(Attribute.HORSE_JUMP_STRENGTH) != null) {
                h.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(1.0);
            }
            if (h.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                h.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30.0);
                h.setHealth(30.0);
            }
        });

        horse.addPassenger(player);

        // Dźwięk rogu
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ITEM_GOAT_HORN_SOUND_0,
                SoundCategory.PLAYERS, 2.0f, 1.0f);

        // Particle
        spawnLoc.getWorld().spawnParticle(Particle.HEART, spawnLoc.clone().add(0, 2, 0),
                15, 1, 0.5, 1, 0.1);

        // Cooldown
        setCooldown(player);

        // Zapisz i uruchom task
        int duration = config.getRogJednorozcaDuration();
        int maxBlocks = config.getRogJednorozcaMaxBlocks();

        ActiveUnicorn unicorn = new ActiveUnicorn(
                player.getUniqueId(),
                horse,
                System.currentTimeMillis() + (duration * 1000L),
                maxBlocks
        );
        activeUnicorns.put(player.getUniqueId(), unicorn);

        // ✅ OSOBNY TASK DLA KONIA - wzorowany na starym kodzie
        startHorseTask(player, horse, unicorn);
    }

    // ==================== HORSE TASK ====================

    /**
     * ✅ Główny task konia - co tick sprawdza ruch i niszczy bloki.
     * Kierunek niszczenia = RZECZYWISTY KIERUNEK RUCHU (nie patrzenia).
     */
    private void startHorseTask(Player player, Horse horse, ActiveUnicorn unicorn) {
        new BukkitRunnable() {
            Location lastLocation = horse.getLocation().clone();

            @Override
            public void run() {
                // Koń nie istnieje
                if (!horse.isValid() || horse.isDead()) {
                    activeUnicorns.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                // Gracz nie jedzie
                if (!horse.getPassengers().contains(player)) {
                    removeUnicorn(unicorn, true);
                    cancel();
                    return;
                }

                // Czas wygasł
                if (unicorn.isExpired()) {
                    removeUnicorn(unicorn, true);
                    cancel();
                    return;
                }

                Location horseLoc = horse.getLocation();

                // Przejechany dystans
                double moved = horseLoc.distance(lastLocation);
                unicorn.addRawDistance(moved);

                if (unicorn.getTotalDistance() >= unicorn.getMaxDistance()) {
                    removeUnicorn(unicorn, true);
                    cancel();
                    return;
                }

                // Sprawdź region
                List<String> blockedRegions = plugin.getItemsConfig().getRogJednorozcaBlockedRegions();
                if (plugin.getWorldGuardManager().isInBlockedRegion(horseLoc, blockedRegions)) {
                    removeUnicorn(unicorn, true);
                    cancel();
                    return;
                }

                // ✅ KIERUNEK RUCHU (nie patrzenia!)
                double dx = horseLoc.getX() - lastLocation.getX();
                double dz = horseLoc.getZ() - lastLocation.getZ();
                double horizontalLength = Math.sqrt(dx * dx + dz * dz);

                boolean isMoving = horizontalLength > 0.05;

                if (isMoving) {
                    Vector moveDirection = new Vector(dx / horizontalLength, 0, dz / horizontalLength);

                    // ✅ Niszcz bloki przed koniem
                    boolean canBuild = canDestroyInRegion(horseLoc);
                    if (canBuild) {
                        destroyBlocksInFront(horse, moveDirection);
                    }

                    // ✅ Ogłuszaj graczy
                    stunNearbyPlayers(horse, player);
                }

                lastLocation = horseLoc.clone();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ==================== USUWANIE ====================

    private void removeUnicorn(ActiveUnicorn unicorn, boolean destroyOnDeath) {
        activeUnicorns.remove(unicorn.getOwnerId());

        Horse horse = unicorn.getHorse();
        if (horse != null && horse.isValid() && !horse.isDead()) {
            Location deathLoc = horse.getLocation();

            boolean canBuild = canDestroyInRegion(deathLoc);
            if (canBuild && destroyOnDeath) {
                destroyArea(deathLoc, true);
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
     * ✅ Niszczy bloki 3x3 PRZED koniem w kierunku RUCHU.
     * Sprawdza od 1.0 do 2.5 bloka przed koniem (co 0.5)
     * żeby przy pełnej prędkości nie przeskakiwał bloków.
     */
    private void destroyBlocksInFront(Horse horse, Vector direction) {
        Location horseLoc = horse.getLocation();
        World world = horseLoc.getWorld();
        int baseY = horseLoc.getBlockY();

        // Wektor prostopadły (do boku)
        Vector perp = new Vector(-direction.getZ(), 0, direction.getX());

        for (double dist = 1.0; dist <= 2.5; dist += 0.5) {
            double frontX = horseLoc.getX() + direction.getX() * dist;
            double frontZ = horseLoc.getZ() + direction.getZ() * dist;

            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h <= 2; h++) {
                    int blockX = (int) Math.floor(frontX + perp.getX() * w);
                    int blockZ = (int) Math.floor(frontZ + perp.getZ() * w);
                    int blockY = baseY + h;

                    Block block = world.getBlockAt(blockX, blockY, blockZ);

                    if (canDestroyBlock(block, false)) {
                        // ✅ breakNaturally() = drop itemy jak przy normalnym kopaniu
                        block.breakNaturally();
                    }
                }
            }
        }
    }

    /**
     * ✅ Niszczy bloki w obszarze 3x3x3 (spawn/śmierć).
     */
    private void destroyArea(Location center, boolean deathExplosion) {
        World world = center.getWorld();
        int baseY = center.getBlockY();

        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
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

    private boolean canDestroyBlock(Block block, boolean deathExplosion) {
        Material type = block.getType();
        if (type.isAir()) return false;
        if (FULLY_INDESTRUCTIBLE.contains(type)) return false;
        if (DEATH_ONLY_DESTRUCTIBLE.contains(type)) return deathExplosion;
        return true;
    }

    // ==================== OGŁUSZANIE ====================

    private void stunNearbyPlayers(Horse horse, Player owner) {
        ItemsConfig config = plugin.getItemsConfig();
        Location horseLoc = horse.getLocation();

        for (Player target : horseLoc.getWorld().getNearbyPlayers(horseLoc, 1.5, 1.5, 1.5)) {
            if (target.equals(owner)) continue;
            if (isStunned(target)) continue;

            List<String> blockedRegions = config.getRogJednorozcaBlockedRegions();
            if (plugin.getWorldGuardManager().isInBlockedRegion(target.getLocation(), blockedRegions)) {
                continue;
            }

            if (plugin.getItemProtectionManager().isProtected(target, "rog-jednorozca")) {
                plugin.getItemProtectionManager()
                        .notifyAttacker(owner, "rog-jednorozca",
                                plugin.getItemProtectionManager().getRemainingSeconds(target, "rog-jednorozca"));
                continue;
            }

            applyStun(target, config.getRogJednorozcaStunDuration());

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

        // ✅ Wyłącz grawitację (gracz nie spada jeśli był w powietrzu)
        stunnedGravity.put(target.getUniqueId(), target.hasGravity());
        target.setGravity(false);
        target.setVelocity(new Vector(0, 0, 0));

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
        Boolean hadGravity = stunnedGravity.remove(player.getUniqueId());
        if (hadGravity != null) {
            player.setGravity(true);
        }
    }

    // ==================== REGION CHECKS ====================

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInBlockedRegion(
                location,
                plugin.getItemsConfig().getRogJednorozcaBlockedRegions()
        );
    }

    private boolean canDestroyInRegion(Location location) {
        try {
            return plugin.getWorldGuardManager().canBreakBlock(null, location);
        } catch (Exception e) {
            // Jeśli WorldGuard nie obsługuje null gracza, domyślnie pozwól
            return true;
        }
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
        if (stunTask != null) stunTask.cancel();

        for (ActiveUnicorn unicorn : new ArrayList<>(activeUnicorns.values())) {
            Horse horse = unicorn.getHorse();
            if (horse != null && horse.isValid()) {
                horse.eject();
                horse.remove();
            }
        }

        // Przywróć grawitację ogłuszonym graczom
        for (UUID uuid : stunnedGravity.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGravity(true);
            }
        }

        activeUnicorns.clear();
        cooldowns.clear();
        stunnedPlayers.clear();
        stunnedLocations.clear();
        stunnedGravity.clear();
    }

    // ==================== INNER CLASS ====================

    public static class ActiveUnicorn {
        private final UUID ownerId;
        private final Horse horse;
        private final long expirationTime;
        private final int maxDistance;
        private double totalDistance;

        public ActiveUnicorn(UUID ownerId, Horse horse, long expirationTime, int maxDistance) {
            this.ownerId = ownerId;
            this.horse = horse;
            this.expirationTime = expirationTime;
            this.maxDistance = maxDistance;
            this.totalDistance = 0;
        }

        public UUID getOwnerId() { return ownerId; }
        public Horse getHorse() { return horse; }
        public int getMaxDistance() { return maxDistance; }
        public double getTotalDistance() { return totalDistance; }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public void addRawDistance(double distance) {
            this.totalDistance += distance;
        }
    }
}
