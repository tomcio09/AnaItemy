// src/main/java/pl/anaheim/anaitemy/models/ActiveHydroKlatka.java
package pl.anaheim.anaitemy.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHydroKlatka {

    private final UUID id;
    private final Location center;
    private final int radius;
    private final int originalDuration;
    private final long createdAt;
    private final UUID creatorId;

    private final Set<UUID> trappedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> offlinePlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, BlockData> originalBlocks = new ConcurrentHashMap<>();
    private final Set<String> destroyedBlocks = ConcurrentHashMap.newKeySet();
    private final Set<String> plannedShellLocations = ConcurrentHashMap.newKeySet();

    private boolean animationComplete = false;

    public ActiveHydroKlatka(Location center, int radius, int duration, UUID creatorId) {
        this.id = UUID.randomUUID();
        this.center = center.clone();
        this.radius = radius;
        this.originalDuration = duration;
        this.createdAt = System.currentTimeMillis();
        this.creatorId = creatorId;
    }

    // ==================== BLOCK KEY ====================

    public static String blockKey(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            throw new IllegalArgumentException("Location or world cannot be null");
        }
        return loc.getWorld().getName() + ":"
                + loc.getBlockX() + ":"
                + loc.getBlockY() + ":"
                + loc.getBlockZ();
    }

    public static Location keyToLocation(String key) {
        if (key == null) return null;
        try {
            String[] parts = key.split(":");
            if (parts.length != 4) return null;
            
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== GETTERY ====================

    public UUID getId() {
        return id;
    }

    public Location getCenter() {
        return center.clone();
    }

    public int getRadius() {
        return radius;
    }

    public int getOriginalDuration() {
        return originalDuration;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public int getRemainingSeconds() {
        long elapsed = (System.currentTimeMillis() - createdAt) / 1000;
        return Math.max(0, originalDuration - (int) elapsed);
    }

    public boolean isExpired() {
        return getRemainingSeconds() <= 0;
    }

    // ==================== ANIMACJA ====================

    public boolean isAnimationComplete() {
        return animationComplete;
    }

    public void setAnimationComplete(boolean complete) {
        this.animationComplete = complete;
    }

    // ==================== PLANNED SHELL LOCATIONS ====================

    public void setPlannedShellLocations(Set<Location> locations) {
        this.plannedShellLocations.clear();
        for (Location loc : locations) {
            if (loc != null && loc.getWorld() != null) {
                this.plannedShellLocations.add(blockKey(loc));
            }
        }
    }

    public boolean isPlannedShellLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return plannedShellLocations.contains(blockKey(location));
    }

    // ==================== TRAPPED PLAYERS ====================

    public void addTrappedPlayer(UUID playerId) {
        trappedPlayers.add(playerId);
        offlinePlayers.remove(playerId);
    }

    public void removeTrappedPlayer(UUID playerId) {
        trappedPlayers.remove(playerId);
    }

    public boolean isPlayerTrapped(UUID playerId) {
        return trappedPlayers.contains(playerId);
    }

    public Set<UUID> getTrappedPlayers() {
        return new HashSet<>(trappedPlayers);
    }

    public void addOfflinePlayer(UUID playerId) {
        offlinePlayers.add(playerId);
    }

    public Set<UUID> getOfflinePlayers() {
        return new HashSet<>(offlinePlayers);
    }

    // ==================== BLOKI ====================

    public void addOriginalBlock(Location location, BlockData blockData) {
        if (location == null || location.getWorld() == null || blockData == null) return;
        originalBlocks.put(blockKey(location), blockData);
    }

    public boolean hasOriginalBlock(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return originalBlocks.containsKey(blockKey(location));
    }

    public Map<String, BlockData> getOriginalBlocks() {
        return new HashMap<>(originalBlocks);
    }

    public void markBlockDestroyed(Location location) {
        if (location == null || location.getWorld() == null) return;
        destroyedBlocks.add(blockKey(location));
    }

    public boolean wasBlockDestroyed(String key) {
        return destroyedBlocks.contains(key);
    }

    public boolean wasBlockDestroyedAt(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return destroyedBlocks.contains(blockKey(location));
    }

    // ==================== GEOMETRIA ====================

    /**
     * Bariera niewidzialna - w połowie bloku shell (wewnętrzna krawędź).
     * Shell jest na dystansie radius-1.0 do radius od centrum.
     * Bariera = radius - 0.5 (środek shella).
     */
    public double getBarrierRadius() {
        return radius - 0.5;
    }

    /**
     * Dystans bezpieczeństwa - jeśli gracz jest dalej niż to, teleport na środek.
     */
    public double getSafetyRadius() {
        return radius + 0.8;
    }

    /**
     * Sprawdza czy lokacja jest wewnątrz klatki (całej sfery).
     */
    public boolean isInsideCage(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distance(center) <= radius;
    }

    /**
     * Sprawdza czy lokacja jest wewnątrz bariery.
     */
    public boolean isInsideBarrier(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distance(center) < getBarrierRadius();
    }

    /**
     * Sprawdza czy gracz dotyka lub przekracza barierę (od wewnątrz).
     */
    public boolean isTouchingBarrier(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distance(center) >= getBarrierRadius();
    }

    /**
     * Sprawdza czy gracz jest poza strefą bezpieczeństwa.
     */
    public boolean isBeyondSafety(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distance(center) >= getSafetyRadius();
    }
}
