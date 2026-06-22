package pl.anaheim.anaitemy.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.List;

public class WorldGuardManager {

    private final AnaItemy plugin;
    private final boolean worldGuardEnabled;

    public WorldGuardManager(AnaItemy plugin) {
        this.plugin = plugin;
        
        // Sprawdź czy WorldGuard jest załadowany
        Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        this.worldGuardEnabled = wg != null && wg.isEnabled();
        
        if (worldGuardEnabled) {
            plugin.getLogger().info("WorldGuard wykryty - integracja włączona!");
        } else {
            plugin.getLogger().warning("WorldGuard nie wykryty - blokowanie regionów wyłączone!");
        }
    }

    /**
     * Sprawdza czy lokalizacja jest w zablokowanym regionie (z listy blocked-regions w items.yml)
     */
    public boolean isInBlockedRegion(Location location, List<String> blockedRegions) {
        if (!worldGuardEnabled) return false;
        if (blockedRegions == null || blockedRegions.isEmpty()) return false;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));
            
            if (regionManager == null) return false;

            BlockVector3 position = BlockVector3.at(
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );

            ApplicableRegionSet regions = regionManager.getApplicableRegions(position);

            // Sprawdź czy któryś z regionów w tej lokalizacji jest na liście zablokowanych
            for (ProtectedRegion region : regions) {
                if (blockedRegions.contains(region.getId())) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd podczas sprawdzania regionu WorldGuard: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sprawdza czy gracz może niszczyć bloki w danej lokalizacji
     */
    public boolean canBreakBlock(Location location) {
        if (!worldGuardEnabled) return true;
        
        // Tutaj można dodać dodatkową logikę WorldGuard jeśli potrzebna
        // Na razie zwracamy true - gracz może niszczyć bloki w klatce
        return true;
    }

    public boolean isEnabled() {
        return worldGuardEnabled;
    }
}
