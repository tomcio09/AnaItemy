package pl.anaheim.anaitemy.managers;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
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

    public RozdzkailuzjonistyManager(AnaItemy plugin) {
        this.plugin = plugin;
    }

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
        fangsCooldowns.put(player.getUniqueId(), 
                System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    public void resetFangsCooldown(Player player) {
        fangsCooldowns.remove(player.getUniqueId());
    }

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
        vanishCooldowns.put(player.getUniqueId(), 
                System.currentTimeMillis() + (cooldownSeconds * 1000));
    }

    public void resetVanishCooldown(Player player) {
        vanishCooldowns.remove(player.getUniqueId());
    }

    public void activateFangs(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        
        if (isFangsOnCooldown(player)) {
            showCooldownTitle(player, getFangsCooldownRemaining(player), true);
            return;
        }

        if (isInBlockedRegion(player.getLocation())) {
            return;
        }

        setFangsCooldown(player);

        String message = config.getRozdzkailuzjonistyFangsMessageActivated();
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(message),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)
                )
        ));

        spawnFangs(player);
    }

    private void spawnFangs(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        
        int length = config.getRozdzkailuzjonistyFangsLength();
        int width = config.getRozdzkailuzjonistyFangsWidth();
        double spacing = config.getRozdzkailuzjonistyFangsSpacing();
        double speed = config.getRozdzkailuzjonistyFangsSpeed();
        
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        direction.setY(0);
        direction.normalize();
        
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        double centerOffset = (width - 1) * spacing / 2.0;
        
        for (int w = 0; w < width; w++) {
            double offset = (w * spacing) - centerOffset;
            Vector sideways = perpendicular.clone().multiply(offset);
            Location stripStart = start.clone().add(sideways);
            new FangStrip(stripStart, direction, length, speed, player).start();
        }
    }

    public void activateVanish(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        
        if (isVanishOnCooldown(player)) {
            showCooldownTitle(player, getVanishCooldownRemaining(player), false);
            return;
        }

        if (isInBlockedRegion(player.getLocation())) {
            return;
        }

        if (!plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
            player.sendMessage("§cCitizens nie jest załadowany!");
            return;
        }

        setVanishCooldown(player);

        String message = config.getRozdzkailuzjonistyVanishMessageActivated();
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(message),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)
                )
        ));

        try {
            Sound activateSound = Sound.valueOf(config.getRozdzkailuzjonistyVanishSoundActivate());
            player.playSound(player.getLocation(), activateSound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy dźwięk aktywacji: " + config.getRozdzkailuzjonistyVanishSoundActivate());
        }

        startVanish(player);
    }

    private void startVanish(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int duration = config.getRozdzkailuzjonistyVanishDuration();
        double npcSpeed = config.getRozdzkailuzjonistyVanishNpcSpeed();
        
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                org.bukkit.entity.EntityType.PLAYER,
                player.getName()
        );
        
        npc.getOrAddTrait(SkinTrait.class).setSkinName(player.getName());
        
        Location npcLoc = player.getLocation().clone();
        npc.spawn(npcLoc);
        
        if (npc.getEntity() instanceof Player npcPlayer) {
            npcPlayer.getInventory().setArmorContents(player.getInventory().getArmorContents());
            npcPlayer.getInventory().setItemInMainHand(player.getInventory().getItemInMainHand());
            npcPlayer.getInventory().setItemInOffHand(player.getInventory().getItemInOffHand());
            
            npcPlayer.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)
                    .setBaseValue(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            npcPlayer.setHealth(player.getHealth());
        }
        
        npc.setProtected(true);
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(plugin, player);
            }
        }
        
        VanishData data = new VanishData(player, npc, System.currentTimeMillis() + (duration * 1000L));
        activeVanishes.put(player.getUniqueId(), data);
        
        Vector direction = player.getLocation().getDirection().normalize();
        direction.setY(0);
        direction.normalize().multiply(npcSpeed);
        
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;
            
            @Override
            public void run() {
                if (!activeVanishes.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                
                if (ticks >= maxTicks) {
                    endVanish(player, false);
                    cancel();
                    return;
                }
                
                if (npc.isSpawned() && npc.getEntity() != null) {
                    Location current = npc.getEntity().getLocation();
                    Location next = current.clone().add(direction);
                    
                    if (next.getBlock().getType().isSolid()) {
                        return;
                    }
                    
                    npc.getEntity().teleport(next);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void endVanish(Player player, boolean early) {
        VanishData data = activeVanishes.remove(player.getUniqueId());
        if (data == null) return;
        
        ItemsConfig config = plugin.getItemsConfig();
        
        if (data.getNpc() != null && data.getNpc().isSpawned()) {
            data.getNpc().destroy();
        }
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
        
        try {
            Sound deactivateSound = Sound.valueOf(config.getRozdzkailuzjonistyVanishSoundDeactivate());
            player.playSound(player.getLocation(), deactivateSound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy dźwięk deaktywacji: " + config.getRozdzkailuzjonistyVanishSoundDeactivate());
        }
    }

    public boolean isVanished(Player player) {
        return activeVanishes.containsKey(player.getUniqueId());
    }

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
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(1500),
                        Duration.ofMillis(250)
                )
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

    public void cleanupFang(EvokerFangs fang) {
        fangsDamagedPlayers.remove(fang);
    }

    public boolean canFangDamageInRegion(Location location) {
        return !isInBlockedRegion(location);
    }

    public void cleanup() {
        for (UUID playerId : new HashSet<>(activeVanishes.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                endVanish(player, true);
            }
        }
        
        fangsCooldowns.clear();
        vanishCooldowns.clear();
        fangsDamagedPlayers.clear();
    }

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
                    if (distance >= length) {
                        cancel();
                        return;
                    }

                    Location spawnLoc = start.clone().add(direction.clone().multiply(distance));
                    
                    if (isInBlockedRegion(spawnLoc)) {
                        cancel();
                        return;
                    }

                    EvokerFangs fang = spawnLoc.getWorld().spawn(spawnLoc, EvokerFangs.class);
                    fang.setOwner(owner);

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
