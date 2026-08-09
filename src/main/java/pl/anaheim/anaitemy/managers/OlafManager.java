package pl.anaheim.anaitemy.managers;

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

/**
 * ✅ PRZEPISANY OlafManager - bez ProtocolLib, bez ArmorStanda.
 *
 * Mechanika:
 * - Dla OFIARY: blindness (nie widzi nic) + particle śniegu wokół twarzy
 * - Dla INNYCH: widoczna zmiana głowy (SkullMeta na własnym ArmorStandzie który jest ukryty)
 * - Ofiara klika LPM 3x żeby usunąć Olafa
 * - Auto-usunięcie po 5 sekundach
 */
public class OlafManager {

    private final AnaItemy plugin;
    private final Map<UUID, Long> shooterCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> victimCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveOlaf> activeOlafs = new ConcurrentHashMap<>();

    public OlafManager(AnaItemy plugin) {
        this.plugin = plugin;

        // Cleanup cooldownów
        new BukkitRunnable() {
            @Override
            public void run() {
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

    // ==================== AKTYWACJA OLAFA ====================

    public void activateOlaf(Player shooter, Player victim) {
        // Ustaw cooldowny
        setShooterCooldown(shooter);
        setVictimCooldown(victim);

        // Aktywny Olaf
        ActiveOlaf active = new ActiveOlaf(shooter.getUniqueId(), victim.getUniqueId());
        activeOlafs.put(victim.getUniqueId(), active);

        // ✅ 1. Blindness dla ofiary - nie widzi nic przez 5s
        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS,
                100, // 5 sekund
                0, false, false, true
        ));

        // ✅ 2. Zamień hełm ofiary na głowę bałwana (widoczne dla INNYCH)
        swapHelmet(victim, true, active);

        // ✅ 3. Particle śniegu wokół twarzy ofiary (widoczne dla ofiary)
        startSnowParticles(victim, active);

        // ✅ 4. Dźwięk
        victim.playSound(victim.getLocation(), Sound.ENTITY_SNOW_GOLEM_AMBIENT,
                SoundCategory.PLAYERS, 2.0f, 1.0f);

        // ✅ 5. Subtitle dla ofiary
        showOlafSubtitle(victim, 3);

        // ✅ 6. Auto-usunięcie po 5s
        active.setTask(new BukkitRunnable() {
            @Override
            public void run() {
                if (activeOlafs.containsKey(victim.getUniqueId())) {
                    removeOlaf(victim);
                }
            }
        }.runTaskLater(plugin, 100L)); // 5 sekund = 100 ticków
    }

    // ==================== ZAMIANA HEŁMU ====================

    private void swapHelmet(Player victim, boolean snowman, ActiveOlaf active) {
        if (snowman) {
            // ✅ Zapisz oryginalny hełm
            active.setOriginalHelmet(victim.getInventory().getHelmet());

            // ✅ Stwórz głowę bałwana
            ItemStack snowmanHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) snowmanHead.getItemMeta();

            com.destroystokyo.paper.profile.PlayerProfile profile =
                    Bukkit.createProfile(OlafItem.getProfileUUID(), "Olaf");
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures", OlafItem.getSnowmanTexture()));
            meta.setPlayerProfile(profile);
            snowmanHead.setItemMeta(meta);

            // ✅ Załóż głowę bałwana - WIDOCZNA DLA WSZYSTKICH
            victim.getInventory().setHelmet(snowmanHead);

        } else {
            // ✅ Przywróć oryginalny hełm
            victim.getInventory().setHelmet(active.getOriginalHelmet());
        }
    }

    // ==================== PARTICLE ŚNIEGU ====================

    private void startSnowParticles(Player victim, ActiveOlaf active) {
        // ✅ Particle śniegu co 3 ticki (15 razy na sekundę)
        // Widoczne tylko dla samej ofiary (spawnowane w jej pozycji)
        BukkitTask particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline() || !activeOlafs.containsKey(victim.getUniqueId())) {
                    cancel();
                    return;
                }

                // ✅ Lokacja przed twarzą gracza (1.7 bloku w górę od stóp = okolice twarzy)
                Location faceLoc = victim.getLocation().clone().add(0, 1.7, 0);
                // ✅ Dodaj trochę w kierunku patrzenia
                faceLoc.add(victim.getLocation().getDirection().multiply(0.3));

                // ✅ Particle widoczne TYLKO dla ofiary (spawnPacket)
                victim.spawnParticle(
                        Particle.SNOWFLAKE,
                        faceLoc,
                        8,        // ilość
                        0.2,      // offsetX
                        0.15,     // offsetY
                        0.2,      // offsetZ
                        0.01      // speed
                );

                // ✅ Dodatkowe particle wokół głowy
                victim.spawnParticle(
                        Particle.SNOWFLAKE,
                        victim.getLocation().clone().add(0, 2.0, 0),
                        5,
                        0.3,
                        0.1,
                        0.3,
                        0.02
                );
            }
        }.runTaskTimer(plugin, 0L, 3L);

        active.setParticleTask(particleTask);
    }

    // ==================== USUWANIE OLAFA ====================

    public void removeOlaf(Player victim) {
        ActiveOlaf active = activeOlafs.remove(victim.getUniqueId());
        if (active == null) return;

        // ✅ Anuluj taski
        if (active.getTask() != null) active.getTask().cancel();
        if (active.getParticleTask() != null) active.getParticleTask().cancel();

        if (victim.isOnline()) {
            // ✅ Przywróć oryginalny hełm
            swapHelmet(victim, false, active);

            // ✅ Usuń blindness
            victim.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);

            // ✅ Dźwięk końca
            victim.playSound(victim.getLocation(), Sound.ENTITY_SNOW_GOLEM_DEATH,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
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
        victim.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(200)
                )
        ));
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (UUID victimId : new HashSet<>(activeOlafs.keySet())) {
            Player victim = Bukkit.getPlayer(victimId);
            if (victim != null) removeOlaf(victim);
            else {
                ActiveOlaf active = activeOlafs.remove(victimId);
                if (active != null) {
                    if (active.getTask() != null) active.getTask().cancel();
                    if (active.getParticleTask() != null) active.getParticleTask().cancel();
                }
            }
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
        private BukkitTask particleTask;
        private ItemStack originalHelmet;

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
        public BukkitTask getParticleTask() { return particleTask; }
        public void setParticleTask(BukkitTask particleTask) { this.particleTask = particleTask; }
        public ItemStack getOriginalHelmet() { return originalHelmet; }
        public void setOriginalHelmet(ItemStack helmet) { this.originalHelmet = helmet; }
    }
}
