package pl.anaheim.anaitemy.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.List;

public class WorldGuardManager {

    private final AnaItemy plugin;
    private final boolean worldGuardEnabled;

    public WorldGuardManager(AnaItemy plugin) {
        this.plugin = plugin;

        Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        this.worldGuardEnabled = wg != null && wg.isEnabled();

        if (worldGuardEnabled) {
            plugin.getLogger().info("WorldGuard wykryty - integracja włączona!");
        } else {
            plugin.getLogger().warning("WorldGuard nie wykryty - blokowanie regionów wyłączone!");
        }
    }

    /**
     * ✅ Stare zachowanie:
     * blocked-regions + regiony z pvp=deny.
     */
    public boolean isInBlockedRegion(Location location, List<String> blockedRegions) {
        if (!worldGuardEnabled) return false;
        if (location == null || location.getWorld() == null) return false;
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

            for (ProtectedRegion region : regions) {
                if (blockedRegions.stream().anyMatch(name -> name.equalsIgnoreCase(region.getId()))) {
                    return true;
                }

                StateFlag.State pvpState = region.getFlag(Flags.PVP);
                if (pvpState == StateFlag.State.DENY) {
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
     * ✅ Tylko nazwy regionów z configu.
     */
    public boolean isInNamedRegion(Location location, List<String> regionNames) {
        if (!worldGuardEnabled) return false;
        if (location == null || location.getWorld() == null) return false;
        if (regionNames == null || regionNames.isEmpty()) return false;

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

            for (ProtectedRegion region : regions) {
                if (regionNames.stream().anyMatch(name -> name.equalsIgnoreCase(region.getId()))) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd podczas sprawdzania nazw regionów WorldGuard: " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ Sprawdza czy gracz może niszczyć bloki w danej lokalizacji.
     * Używa testBuild z BUILD + BLOCK_BREAK.
     */
    public boolean canBreakBlock(Player player, Location location) {
        if (!worldGuardEnabled) return true;
        if (location == null || location.getWorld() == null) return true;
        if (player == null) return true;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

            return query.testBuild(
                    BukkitAdapter.adapt(location),
                    localPlayer,
                    Flags.BLOCK_BREAK,
                    Flags.BUILD
            );

        } catch (Exception e) {
            plugin.getLogger().warning("Błąd podczas sprawdzania block-break/build: " + e.getMessage());
            return true;
        }
    }

    public boolean isEnabled() {
        return worldGuardEnabled;
    }
}
