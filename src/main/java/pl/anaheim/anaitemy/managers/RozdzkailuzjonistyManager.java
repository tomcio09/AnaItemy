package pl.anaheim.anaitemy.managers;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RozdzkailuzjonistyManager {

    private final AnaItemy plugin;

    private final Map<UUID, Long> fangsCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vanishCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, VanishData> activeVanishes = new ConcurrentHashMap<>();
    private final Map<EvokerFangs, Set<UUID>> fangsDamagedPlayers = new ConcurrentHashMap<>();
    private final Map<EvokerFangs, UUID> fangsOwners = new ConcurrentHashMap<>();

    public RozdzkailuzjonistyManager(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== FANGS COOLDOWN ====================

    public boolean isFangsOnCooldown(Player player) {
        Long cooldownEnd = fangsCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    public long getFangsCooldownRemaining(Player player) {
        Long cooldownEnd = fangsCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return 0;
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000);
    }

    public void setFangsCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getRozdzkailuzjonistyFangsCooldown();
        fangsCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    public void resetFangsCooldown(Player player) {
        fangsCooldowns.remove(player.getUniqueId());
    }

    // ==================== VANISH COOLDOWN ====================

    public boolean isVanishOnCooldown(Player player) {
        Long cooldownEnd = vanishCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    public long getVanishCooldownRemaining(Player player) {
        Long cooldownEnd = vanishCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return 0;
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000);
    }

    public void setVanishCooldown(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        long cooldownSeconds = config.getRozdzkailuzjonistyVanishCooldown();
        vanishCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    public void resetVanishCooldown(Player player) {
        vanishCooldowns.remove(player.getUniqueId());
    }

    // ==================== FANGS ====================

    public void activateFangs(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isFangsOnCooldown(player)) {
            showCooldownTitle(player, getFangsCooldownRemaining(player), true);
            return;
        }

        if (isInBlockedRegion(player.getLocation())) return;

        setFangsCooldown(player);

        String message = config.getRozdzkailuzjonistyFangsMessageActivated();
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(message),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));

        spawnFangs(player);
    }

    private void spawnFangs(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        int length = config.getRozdzkailuzjonistyFangsLength();
        int width = config.getRozdzkailuzjonistyFangsWidth();
        double speed = config.getRozdzkailuzjonistyFangsSpeed();

        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        direction.setY(0).normalize();

        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        double centerOffset = (width - 1) * 2.0 / 2.0;

        for (int w = 0; w < width; w++) {
            double offset = (w * 2.0) - centerOffset;
            Vector sideways = perpendicular.clone().multiply(offset);
            Location stripStart = start.clone().add(sideways);
            new FangStrip(stripStart, direction, length, speed, player).start();
        }
    }

    // ==================== VANISH ====================

    public void activateVanish(Player player) {
        ItemsConfig config = plugin.getItemsConfig();

        if (isVanishOnCooldown(player)) {
            showCooldownTitle(player, getVanishCooldownRemaining(player), false);
            return;
        }

        if (isInBlockedRegion(player.getLocation())) return;

        boolean citizensEnabled = plugin.getServer().getPluginManager().isPluginEnabled("Citizens");

        setVanishCooldown(player);

        String message = config.getRozdzkailuzjonistyVanishMessageActivated();
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(message),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));

        try {
            Sound activateSound = Sound.valueOf(config.getRozdzkailuzjonistyVanishSoundActivate());
            player.playSound(player.getLocation(), activateSound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidlowy dzwiek aktywacji: " + config.getRozdzkailuzjonistyVanishSoundActivate());
        }

        if (!citizensEnabled) {
            startVanishWithoutNPC(player);
        } else {
            startVanish(player);
        }
    }

    private void startVanishWithoutNPC(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int duration = config.getRozdzkailuzjonistyVanishDuration();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) online.hidePlayer(plugin, player);
        }

        VanishData data = new VanishData(player, null, System.currentTimeMillis() + (duration * 1000L));
        activeVanishes.put(player.getUniqueId(), data);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeVanishes.containsKey(player.getUniqueId())) endVanish(player, false);
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    private void startVanish(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int duration = config.getRozdzkailuzjonistyVanishDuration();
        double npcSpeed = config.getRozdzkailuzjonistyVanishNpcSpeed();

        boolean isGliding = player.isGliding();

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                org.bukkit.entity.EntityType.PLAYER,
                player.getName()
        );

        // ✅ Skin gracza
        npc.getOrAddTrait(SkinTrait.class).setSkinName(player.getName());

        Location spawnLoc = isGliding ? player.getLocation().clone() : findGroundLocation(player.getLocation());
        npc.spawn(spawnLoc);

        if (npc.getEntity() instanceof LivingEntity npcEntity) {
            EntityEquipment equipment = npcEntity.getEquipment();
            if (equipment != null) {
                // ✅ Zbroja + ekwipunek gracza
                equipment.setHelmet(player.getInventory().getHelmet());
                equipment.setChestplate(player.getInventory().getChestplate());
                equipment.setLeggings(player.getInventory().getLeggings());
                equipment.setBoots(player.getInventory().getBoots());
                equipment.setItemInMainHand(player.getInventory().getItemInMainHand());
                equipment.setItemInOffHand(player.getInventory().getItemInOffHand());
            }

            // ✅ Maksymalne HP gracza
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null && npcEntity.getAttribute(Attribute.MAX_HEALTH) != null) {
                npcEntity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth.getValue());
                npcEntity.setHealth(maxHealth.getValue());
            }

            // ✅ Prefix / suffix przez display name (jeśli gracz ma custom name)
            if (player.customName() != null) {
                npcEntity.customName(player.customName());
                npcEntity.setCustomNameVisible(true);
            }
        }

        npc.setProtected(true);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) online.hidePlayer(plugin, player);
        }

        VanishData data = new VanishData(player, npc, System.currentTimeMillis() + (duration * 1000L));
        activeVanishes.put(player.getUniqueId(), data);

        Vector direction = isGliding
                ? player.getVelocity().clone().normalize().multiply(npcSpeed / 20.0)
                : player.getLocation().getDirection().normalize().setY(0).normalize().multiply(npcSpeed / 20.0);

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;
            boolean wasGliding = isGliding;

            @Override
            public void run() {
                if (!activeVanishes.containsKey(player.getUniqueId())) { cancel(); return; }
                if (ticks >= maxTicks) { endVanish(player, false); cancel(); return; }
                if (!npc.isSpawned() || npc.getEntity() == null) { cancel(); return; }

                if (npc.getEntity() instanceof LivingEntity le) {
                    le.setNoDamageTicks(20);
                    le.setFallDistance(0f);
                }

                Location current = npc.getEntity().getLocation();

                if (wasGliding) {
                    Location next = current.clone().add(direction);
                    Location ground = findGroundBelow(next, 2);
                    if (ground != null) {
                        wasGliding = false;
                        direction.setY(0).normalize().multiply(npcSpeed / 20.0);
                        next = ground;
                    }
                    next.setYaw(current.getYaw());
                    next.setPitch(current.getPitch());
                    npc.getEntity().teleport(next);
                } else {
                    Location nextHorizontal = current.clone().add(direction.getX(), 0, direction.getZ());
                    Location nextGround = findGroundForMovement(current, nextHorizontal);
                    if (nextGround == null) { ticks++; return; }
                    nextGround.setYaw(current.getYaw());
                    nextGround.setPitch(current.getPitch());
                    npc.getEntity().teleport(nextGround);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Location findGroundLocation(Location playerLoc) {
        World world = playerLoc.getWorld();
        int x = playerLoc.getBlockX();
        int z = playerLoc.getBlockZ();

        org.bukkit.block.Block below = playerLoc.clone().subtract(0, 0.1, 0).getBlock();
        if (below.getType().isSolid()) return playerLoc.clone();

        for (int y = playerLoc.getBlockY(); y >= world.getMinHeight(); y--) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            org.bukkit.block.Block above = world.getBlockAt(x, y + 1, z);
            org.bukkit.block.Block above2 = world.getBlockAt(x, y + 2, z);

            if (block.getType().isSolid() && !above.getType().isSolid() && !above2.getType().isSolid()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5, playerLoc.getYaw(), playerLoc.getPitch());
            }
        }

        return playerLoc.clone();
    }

    private Location findGroundBelow(Location location, int maxDepth) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = location.getBlockY();

        for (int y = startY; y >= startY - maxDepth && y >= world.getMinHeight(); y--) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            org.bukkit.block.Block above = world.getBlockAt(x, y + 1, z);

            if (block.getType().isSolid() && !above.getType().isSolid()) {
                return new Location(world, location.getX(), y + 1, location.getZ());
            }
        }
        return null;
    }

    private Location findGroundForMovement(Location current, Location nextHorizontal) {
        World world = current.getWorld();
        int nx = nextHorizontal.getBlockX();
        int nz = nextHorizontal.getBlockZ();
        int currentY = current.getBlockY();

        org.bukkit.block.Block targetBlock = world.getBlockAt(nx, currentY, nz);
        org.bukkit.block.Block targetBlockAbove = world.getBlockAt(nx, currentY + 1, nz);

        if (!targetBlock.getType().isSolid() && !targetBlockAbove.getType().isSolid()) {
            org.bukkit.block.Block groundBlock = world.getBlockAt(nx, currentY - 1, nz);
            if (groundBlock.getType().isSolid()) {
                return new Location(world, nextHorizontal.getX(), currentY, nextHorizontal.getZ());
            }

            for (int y = currentY - 1; y >= currentY - 4; y--) {
                org.bukkit.block.Block checkBlock = world.getBlockAt(nx, y, nz);
                org.bukkit.block.Block checkAbove = world.getBlockAt(nx, y + 1, nz);
                org.bukkit.block.Block checkAbove2 = world.getBlockAt(nx, y + 2, nz);

                if (checkBlock.getType().isSolid() && !checkAbove.getType().isSolid() && !checkAbove2.getType().isSolid()) {
                    return new Location(world, nextHorizontal.getX(), y + 1, nextHorizontal.getZ());
                }
            }
        }

        if (targetBlock.getType().isSolid()) {
            org.bukkit.block.Block jumpTarget = world.getBlockAt(nx, currentY + 1, nz);
            org.bukkit.block.Block jumpTargetAbove = world.getBlockAt(nx, currentY + 2, nz);

            if (!jumpTarget.getType().isSolid() && !jumpTargetAbove.getType().isSolid()) {
                return new Location(world, nextHorizontal.getX(), currentY + 1, nextHorizontal.getZ());
            }
            return null;
        }
        return null;
    }

    public void endVanish(Player player, boolean early) {
        VanishData data = activeVanishes.remove(player.getUniqueId());
        if (data == null) return;

        ItemsConfig config = plugin.getItemsConfig();

        // ✅ Zniszcz NPC bezpiecznie
        if (data.getNpc() != null) {
            try {
                if (data.getNpc().isSpawned()) {
                    data.getNpc().destroy();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Rozdzka] Blad niszczenia NPC: " + e.getMessage());
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }

        try {
            Sound deactivateSound = Sound.valueOf(config.getRozdzkailuzjonistyVanishSoundDeactivate());
            player.playSound(player.getLocation(), deactivateSound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidlowy dzwiek deaktywacji: " + config.getRozdzkailuzjonistyVanishSoundDeactivate());
        }

        if (!early) {
            player.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&cJestes znowu widoczny!"),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
            ));
        }
    }

    public boolean isVanished(Player player) {
        return activeVanishes.containsKey(player.getUniqueId());
    }

    // ==================== UTILITIES ====================

    private void showCooldownTitle(Player player, long seconds, boolean isFangs) {
        ItemsConfig config = plugin.getItemsConfig();

        String title = isFangs
                ? config.getRozdzkailuzjonistyFangsMessageCooldownTitle()
                : config.getRozdzkailuzjonistyVanishMessageCooldownTitle();

        String subtitle = isFangs
                ? config.getRozdzkailuzjonistyFangsMessageCooldownSubtitle()
                : config.getRozdzkailuzjonistyVanishMessageCooldownSubtitle();

        subtitle = subtitle.replace("{seconds}", String.valueOf(seconds));

        player.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(250))
        ));
    }

    private boolean isInBlockedRegion(Location location) {
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getRozdzkailuzjonistyBlockedRegions();
        return plugin.getWorldGuardManager().isInBlockedRegion(location, blockedRegions);
    }

    public void markFangDamaged(EvokerFangs fang, UUID playerId) {
        fangsDamagedPlayers.computeIfAbsent(fang, k -> new HashSet<>()).add(playerId);
    }

    public boolean hasFangDamaged(EvokerFangs fang, UUID playerId) {
        Set<UUID> damaged = fangsDamagedPlayers.get(fang);
        return damaged != null && damaged.contains(playerId);
    }

    public void setFangOwner(EvokerFangs fang, Player owner) {
        fangsOwners.put(fang, owner.getUniqueId());
    }

    public UUID getFangOwner(EvokerFangs fang) {
        return fangsOwners.get(fang);
    }

    public void cleanupFang(EvokerFangs fang) {
        fangsDamagedPlayers.remove(fang);
        fangsOwners.remove(fang);
    }

    public boolean canFangDamageInRegion(Location location) {
        return !isInBlockedRegion(location);
    }

    /**
     * ✅ Cleanup przy wyłączeniu pluginu / crashu serwera.
     * Usuwa wszystkie aktywne NPC PRZED wyłączeniem serwera.
     */
    public void cleanup() {
        plugin.getLogger().info("[Rozdzka] Czyszczenie " + activeVanishes.size() + " aktywnych znikniéć...");

        for (UUID playerId : new HashSet<>(activeVanishes.keySet())) {
            VanishData data = activeVanishes.remove(playerId);
            if (data == null) continue;

            // ✅ Zniszcz NPC nawet jeśli gracz jest offline
            if (data.getNpc() != null) {
                try {
                    if (data.getNpc().isSpawned()) {
                        data.getNpc().destroy();
                        plugin.getLogger().info("[Rozdzka] Usunieto NPC dla gracza " + playerId);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[Rozdzka] Blad usuwania NPC: " + e.getMessage());
                }
            }

            // Pokaż gracza z powrotem jeśli jest online
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(plugin, player);
                }
            }
        }

        fangsCooldowns.clear();
        vanishCooldowns.clear();
        fangsDamagedPlayers.clear();
        fangsOwners.clear();

        plugin.getLogger().info("[Rozdzka] Czyszczenie zakonczone.");
    }

    // ==================== INNER CLASSES ====================

    private class FangStrip {
        private final Location start;
        private final Vector direction;
        private final int length;
        private final double speed;
        private final Player owner;

        public FangStrip(Location start, Vector direction, int length, double speed, Player owner) {
            this.start = start;
            this.direction = direction;
            this.length = length;
            this.speed = speed;
            this.owner = owner;
        }

        public void start() {
            new BukkitRunnable() {
                int distance = 0;

                @Override
                public void run() {
                    if (distance >= length) { cancel(); return; }

                    Location spawnLoc = start.clone().add(direction.clone().multiply(distance * 1.5));

                    if (isInBlockedRegion(spawnLoc)) { cancel(); return; }

                    EvokerFangs fang = spawnLoc.getWorld().spawn(spawnLoc, EvokerFangs.class);
                    setFangOwner(fang, owner);

                    distance++;
                }
            }.runTaskTimer(plugin, 0L, (long) (1.0 / speed));
        }
    }

    private static class VanishData {
        private final Player player;
        private final NPC npc;
        private final long endTime;

        public VanishData(Player player, NPC npc, long endTime) {
            this.player = player;
            this.npc = npc;
            this.endTime = endTime;
        }

        public Player getPlayer() { return player; }
        public NPC getNpc() { return npc; }
        public long getEndTime() { return endTime; }
    }
}
