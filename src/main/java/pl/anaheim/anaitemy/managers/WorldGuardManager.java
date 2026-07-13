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

        boolean wgFound = false;
        try {
            Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            wgFound = wg != null && wg.isEnabled();
        } catch (Throwable t) {
            wgFound = false;
        }

        this.worldGuardEnabled = wgFound;

        if (worldGuardEnabled) {
            plugin.getLogger().info("WorldGuard wykryty - integracja wlaczona!");
        } else {
            plugin.getLogger().warning("WorldGuard nie wykryty - blokowanie regionow wylaczone!");
        }
    }

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
                for (String blockedName : blockedRegions) {
                    if (blockedName.equalsIgnoreCase(region.getId())) {
                        return true;
                    }
                }

                StateFlag.State pvpState = region.getFlag(Flags.PVP);
                if (pvpState == StateFlag.State.DENY) {
                    return true;
                }
            }

            return false;
        } catch (Throwable t) {
            return false;
        }
    }

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
                for (String name : regionNames) {
                    if (name.equalsIgnoreCase(region.getId())) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Throwable t) {
            return false;
        }
    }

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
        } catch (Throwable t) {
            return true;
        }
    }

    public boolean isEnabled() {
        return worldGuardEnabled;
    }
}
