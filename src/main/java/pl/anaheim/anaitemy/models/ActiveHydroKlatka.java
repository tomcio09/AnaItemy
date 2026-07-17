package pl.anaheim.anaitemy.models;

import org.bukkit.Location;
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
    private final Map<Location, BlockData> originalBlocks = new ConcurrentHashMap<>();
    private final Set<Location> destroyedBlocks = ConcurrentHashMap.newKeySet();
    
    // ✅ NOWE: Zaplanowane pozycje shella (dla niewidzialnej kolizji podczas animacji)
    private final Set<Location> plannedShellLocations = ConcurrentHashMap.newKeySet();

    private boolean animationComplete = false;

    public ActiveHydroKlatka(Location center, int radius, int duration, UUID creatorId) {
        this.id = UUID.randomUUID();
        this.center = center;
        this.radius = radius;
        this.originalDuration = duration;
        this.createdAt = System.currentTimeMillis();
        this.creatorId = creatorId;
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

    // ==================== ✅ PLANNED SHELL LOCATIONS ====================

    public void setPlannedShellLocations(Set<Location> locations) {
        this.plannedShellLocations.clear();
        this.plannedShellLocations.addAll(locations);
    }

    public boolean isPlannedShellLocation(Location location) {
        Location blockLoc = location.getBlock().getLocation();
        for (Location planned : plannedShellLocations) {
            if (planned.getBlockX() == blockLoc.getBlockX()
                    && planned.getBlockY() == blockLoc.getBlockY()
                    && planned.getBlockZ() == blockLoc.getBlockZ()
                    && planned.getWorld().equals(blockLoc.getWorld())) {
                return true;
            }
        }
        return false;
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
        originalBlocks.put(location, blockData);
    }

    public boolean hasOriginalBlock(Location location) {
        return originalBlocks.containsKey(location);
    }

    public Map<Location, BlockData> getOriginalBlocks() {
        return new HashMap<>(originalBlocks);
    }

    public void markBlockDestroyed(Location location) {
        destroyedBlocks.add(location);
    }

    public boolean wasBlockDestroyed(Location location) {
        return destroyedBlocks.contains(location);
    }

    // ==================== POMOCNICZE ====================

    public boolean isInsideCage(Location location) {
        return location.distance(center) <= radius;
    }
}
