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
    private final long creationTime;
    private final long expirationTime;
    private final UUID creatorId;
    private final Map<Location, BlockData> originalBlocks;
    private final Set<UUID> trappedPlayers;
    private final Set<Location> destroyedBlocks;
    private boolean animationComplete;
    private int currentAnimationY;

    public ActiveHydroKlatka(Location center, int radius, int durationSeconds, UUID creatorId) {
        this.id = UUID.randomUUID();
        this.center = center.clone();
        this.radius = radius;
        this.originalDuration = durationSeconds;
        this.creationTime = System.currentTimeMillis();
        this.expirationTime = creationTime + (durationSeconds * 1000L);
        this.creatorId = creatorId;
        this.originalBlocks = new ConcurrentHashMap<>();
        this.trappedPlayers = ConcurrentHashMap.newKeySet();
        this.destroyedBlocks = ConcurrentHashMap.newKeySet();
        this.animationComplete = false;
        this.currentAnimationY = center.getBlockY() + radius;
    }

    // Getters
    public UUID getId() { return id; }
    public Location getCenter() { return center.clone(); }
    public int getRadius() { return radius; }
    public int getOriginalDuration() { return originalDuration; }
    public long getCreationTime() { return creationTime; }
    public long getExpirationTime() { return expirationTime; }
    public UUID getCreatorId() { return creatorId; }

    public int getRemainingSeconds() {
        long remaining = expirationTime - System.currentTimeMillis();
        return (int) Math.max(0, remaining / 1000);
    }

    public boolean isExpired() { 
        return System.currentTimeMillis() >= expirationTime; 
    }

    // Original blocks management
    public void addOriginalBlock(Location location, BlockData blockData) {
        originalBlocks.put(location.clone(), blockData.clone());
    }

    public Map<Location, BlockData> getOriginalBlocks() { 
        return new HashMap<>(originalBlocks); 
    }

    public boolean hasOriginalBlock(Location location) { 
        return originalBlocks.containsKey(location); 
    }

    public void removeOriginalBlock(Location location) {
        originalBlocks.remove(location);
        destroyedBlocks.add(location.clone());
    }

    public boolean wasBlockDestroyed(Location location) { 
        return destroyedBlocks.contains(location); 
    }

    public void markBlockDestroyed(Location location) {
        if (hasOriginalBlock(location)) {
            removeOriginalBlock(location);
        }
    }

    // Trapped players management
    public void addTrappedPlayer(UUID playerId) { 
        trappedPlayers.add(playerId); 
    }

    public boolean isPlayerTrapped(UUID playerId) { 
        return trappedPlayers.contains(playerId); 
    }

    public Set<UUID> getTrappedPlayers() { 
        return new HashSet<>(trappedPlayers); 
    }

    public void removeTrappedPlayer(UUID playerId) {
        trappedPlayers.remove(playerId);
    }

    // Animation
    public boolean isAnimationComplete() { 
        return animationComplete; 
    }

    public void setAnimationComplete(boolean complete) { 
        this.animationComplete = complete; 
    }

    public int getCurrentAnimationY() { 
        return currentAnimationY; 
    }

    public void setCurrentAnimationY(int y) { 
        this.currentAnimationY = y; 
    }

    // Location checks
    public boolean isInsideCage(Location location) {
        if (location.getWorld() == null || center.getWorld() == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;
        return location.distance(center) <= radius;
    }

    public boolean isOnShell(Location location) {
        if (location.getWorld() == null || center.getWorld() == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;
        double distance = location.distance(center);
        return distance > radius - 1.0 && distance <= radius;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ActiveHydroKlatka that = (ActiveHydroKlatka) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ActiveHydroKlatka{" +
                "id=" + id +
                ", center=" + center +
                ", radius=" + radius +
                ", remaining=" + getRemainingSeconds() + "s" +
                ", trapped=" + trappedPlayers.size() +
                '}';
    }
}
