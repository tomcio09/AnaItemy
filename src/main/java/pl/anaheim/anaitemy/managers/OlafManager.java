package pl.anaheim.anaitemy.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.OlafItem;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OlafManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> shooterCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> victimCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveOlaf> activeOlafs = new ConcurrentHashMap<>();

    public OlafManager(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                shooterCooldowns.entrySet().removeIf(e -> now >= e.getValue());
                victimCooldowns.entrySet().removeIf(e -> now >= e.getValue());
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    // ==================== COOLDOWN ====================

    public boolean isShooterOnCooldown(Player player) {
        Long end = shooterCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public long getShooterCooldownRemaining(Player player) {
        Long end = shooterCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000);
    }

    public boolean isVictimOnCooldown(Player player) {
        Long end = victimCooldowns.get(player.getUniqueId());
        return end != null && System.currentTimeMillis() < end;
    }

    public void setShooterCooldown(Player player) {
        long seconds = plugin.getItemsConfig().getOlafCooldown();
        shooterCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void setVictimCooldown(Player player) {
        long seconds = plugin.getItemsConfig().getOlafCooldown();
        victimCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void resetCooldowns(Player player) {
        shooterCooldowns.remove(player.getUniqueId());
        victimCooldowns.remove(player.getUniqueId());
    }

    // ==================== OLAF AKTYWACJA ====================

    public void activateOlaf(Player shooter, Player victim) {
        // Cooldowny
        setShooterCooldown(shooter);
        setVictimCooldown(victim);

        // Aktywny olaf
        ActiveOlaf active = new ActiveOlaf(shooter.getUniqueId(), victim.getUniqueId());
        activeOlafs.put(victim.getUniqueId(), active);

        // ✅ Dla INNYCH graczy — zmień hełm ofiary na głowę bałwana (packet)
        sendSnowmanHead(victim, true);

        // ✅ Dla OFIARY — pokaż ArmorStand z głową bałwana tuż przed twarzą
        spawnFakeSnowmanForVictim(victim, active);

        // Pokaż subtitle
        showOlafSubtitle(victim, 3);

        // ✅ Auto-usunięcie po 5s
        active.setTask(new BukkitRunnable() {
            @Override public void run() {
                if (activeOlafs.containsKey(victim.getUniqueId())) {
                    removeOlaf(victim);
                }
            }
        }.runTaskLater(plugin, 100L));
    }

    private void sendSnowmanHead(Player victim, boolean snowmanHead) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();

            ItemStack headItem;
            if (snowmanHead) {
                // Głowa z teksturą bałwana
                headItem = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) headItem.getItemMeta();
                PlayerProfile profile = Bukkit.createProfile(OlafItem.getProfileUUID(), "Olaf");
                profile.setProperty(new ProfileProperty("textures", OlafItem.getSnowmanTexture()));
                meta.setPlayerProfile(profile);
                headItem.setItemMeta(meta);
            } else {
                // Oryginalny hełm ofiary
                headItem = victim.getInventory().getHelmet();
                if (headItem == null) headItem = new ItemStack(Material.AIR);
            }

            final ItemStack finalHead = headItem;

            // Wyślij pakiet zmiany ekwipunku do wszystkich POZA ofiarą
            PacketContainer equipPacket = pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, victim.getEntityId());

            List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
            equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.HEAD, finalHead));
            equipPacket.getSlotStackPairLists().write(0, equipment);

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(victim)) continue;
                try {
                    pm.sendServerPacket(online, equipPacket);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Olaf] Błąd wysyłania pakietu hełmu: " + e.getMessage());
        }
    }

    private void spawnFakeSnowmanForVictim(Player victim, ActiveOlaf active) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();

            int fakeEntityId = 999999 + victim.getEntityId();
            active.setFakeEntityId(fakeEntityId);

            // ✅ Spawn ArmorStand jako fake entity - tylko dla ofiary
            Location victimLoc = victim.getLocation().clone().add(0, 1.5, 0);

            // Spawn entity packet
            PacketContainer spawnPacket = pm.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawnPacket.getIntegers().write(0, fakeEntityId);
            spawnPacket.getUUIDs().write(0, UUID.randomUUID());
            spawnPacket.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.ARMOR_STAND);
            spawnPacket.getDoubles().write(0, victimLoc.getX());
            spawnPacket.getDoubles().write(1, victimLoc.getY());
            spawnPacket.getDoubles().write(2, victimLoc.getZ());

            pm.sendServerPacket(victim, spawnPacket);

            // ✅ Wyślij głowę bałwana jako hełm fake armorstand
            ItemStack snowmanHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) snowmanHead.getItemMeta();
            PlayerProfile profile = Bukkit.createProfile(OlafItem.getProfileUUID(), "Olaf");
            profile.setProperty(new ProfileProperty("textures", OlafItem.getSnowmanTexture()));
            meta.setPlayerProfile(profile);
            snowmanHead.setItemMeta(meta);

            PacketContainer equipPacket = pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, fakeEntityId);
            List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
            equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.HEAD, snowmanHead));
            equipPacket.getSlotStackPairLists().write(0, equipment);
            pm.sendServerPacket(victim, equipPacket);

            // ✅ Task — przesuwa fake entity razem z ofiarą
            active.setMoveTask(new BukkitRunnable() {
                @Override public void run() {
                    if (!victim.isOnline() || !activeOlafs.containsKey(victim.getUniqueId())) {
                        cancel();
                        return;
                    }

                    Location loc = victim.getLocation().clone().add(0, 1.8, 0);

                    try {
                        PacketContainer teleport = pm.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
                        teleport.getIntegers().write(0, fakeEntityId);
                        teleport.getDoubles().write(0, loc.getX());
                        teleport.getDoubles().write(1, loc.getY());
                        teleport.getDoubles().write(2, loc.getZ());
                        pm.sendServerPacket(victim, teleport);
                    } catch (Exception ignored) {}
                }
            }.runTaskTimer(plugin, 0L, 1L));

        } catch (Exception e) {
            plugin.getLogger().warning("[Olaf] Błąd fake entity: " + e.getMessage());
        }
    }

    public void removeOlaf(Player victim) {
        ActiveOlaf active = activeOlafs.remove(victim.getUniqueId());
        if (active == null) return;

        // Anuluj taski
        if (active.getTask() != null) active.getTask().cancel();
        if (active.getMoveTask() != null) active.getMoveTask().cancel();

        // Usuń fake entity dla ofiary
        if (victim.isOnline()) {
            try {
                ProtocolManager pm = ProtocolLibrary.getProtocolManager();
                PacketContainer destroyPacket = pm.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntLists().write(0, List.of(active.getFakeEntityId()));
                pm.sendServerPacket(victim, destroyPacket);
            } catch (Exception e) {
                plugin.getLogger().warning("[Olaf] Błąd usuwania fake entity: " + e.getMessage());
            }
        }

        // Przywróć oryginalny hełm dla innych graczy
        sendSnowmanHead(victim, false);
    }

    public boolean hasActiveOlaf(Player victim) {
        return activeOlafs.containsKey(victim.getUniqueId());
    }

    public ActiveOlaf getActiveOlaf(Player victim) {
        return activeOlafs.get(victim.getUniqueId());
    }

    public void onVictimHit(Player victim) {
        ActiveOlaf active = activeOlafs.get(victim.getUniqueId());
        if (active == null) return;

        active.incrementHits();
        int hitsLeft = 3 - active.getHitCount();

        if (hitsLeft <= 0) {
            removeOlaf(victim);
        } else {
            showOlafSubtitle(victim, hitsLeft);
        }
    }

    private void showOlafSubtitle(Player victim, int left) {
        String subtitle = plugin.getItemsConfig().getOlafVictimSubtitle()
                .replace("{left}", String.valueOf(left));
        victim.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));
    }

    public void cleanup() {
        for (UUID victimId : new HashSet<>(activeOlafs.keySet())) {
            Player victim = Bukkit.getPlayer(victimId);
            if (victim != null) removeOlaf(victim);
        }
        activeOlafs.clear();
        shooterCooldowns.clear();
        victimCooldowns.clear();
    }

    // ==================== INNER CLASS ====================

    public static class ActiveOlaf {
        private final UUID shooterId;
        private final UUID victimId;
        private int hitCount = 0;
        private BukkitTask task;
        private BukkitTask moveTask;
        private int fakeEntityId;

        public ActiveOlaf(UUID shooterId, UUID victimId) {
            this.shooterId = shooterId;
            this.victimId = victimId;
        }

        public UUID getShooterId() { return shooterId; }
        public UUID getVictimId() { return victimId; }
        public int getHitCount() { return hitCount; }
        public void incrementHits() { hitCount++; }
        public BukkitTask getTask() { return task; }
        public void setTask(BukkitTask task) { this.task = task; }
        public BukkitTask getMoveTask() { return moveTask; }
        public void setMoveTask(BukkitTask moveTask) { this.moveTask = moveTask; }
        public int getFakeEntityId() { return fakeEntityId; }
        public void setFakeEntityId(int id) { this.fakeEntityId = id; }
    }
}
