package pl.anaheim.anaitemy.managers;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class TurbotrapManager {

    private static final String META_TURBOTRAP = "anaitemy_turbotrap";

    private final AnaItemy plugin;
    private final boolean worldEditEnabled;
    private Clipboard trapSchematic;

    public TurbotrapManager(AnaItemy plugin) {
        this.plugin = plugin;

        boolean weFound = plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit");
        this.worldEditEnabled = weFound;

        if (worldEditEnabled) {
            loadSchematic();
            plugin.getLogger().info("[Turbotrap] WorldEdit wykryty - schematy WŁĄCZONE!");
        } else {
            plugin.getLogger().warning("[Turbotrap] WorldEdit nie znaleziony - Turbotrap nie będzie działał!");
        }
    }

    private void loadSchematic() {
        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        File schematicFile = new File(schematicsFolder, "turbotrap.schem");
        if (!schematicFile.exists()) {
            try {
                plugin.saveResource("schematics/turbotrap.schem", false);
            } catch (Exception e) {
                plugin.getLogger().warning("[Turbotrap] Nie znaleziono schematu w zasobach pluginu.");
            }
        }

        if (!schematicFile.exists()) {
            schematicFile = new File(schematicsFolder, "turbotrap.schematic");
        }

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("[Turbotrap] Nie znaleziono pliku schematu! Umieść go w: "
                    + schematicsFolder.getPath() + "/turbotrap.schem");
            return;
        }

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                plugin.getLogger().severe("[Turbotrap] Nierozpoznany format schematu: " + schematicFile.getName());
                return;
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                trapSchematic = reader.read();
                plugin.getLogger().info("[Turbotrap] Schemat załadowany pomyślnie: " + schematicFile.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[Turbotrap] Błąd ładowania schematu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isReady() {
        return worldEditEnabled && trapSchematic != null;
    }

    public boolean isInBlockedRegion(Location location) {
        return plugin.getWorldGuardManager().isInNamedRegion(location,
                plugin.getItemsConfig().getTurbotrapBlockedRegions());
    }

    public void markEgg(Egg egg, Player shooter) {
        egg.setMetadata(META_TURBOTRAP,
                new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
    }

    public boolean isTurbotrapEgg(Egg egg) {
        return egg.hasMetadata(META_TURBOTRAP);
    }

    /**
     * ✅ Wkleja schemat z animacją od góry do dołu.
     * Nie zamienia bedrocka.
     */
    public void pasteSchematic(Location location) {
        if (!isReady()) return;
        if (isInBlockedRegion(location)) return;

        // ✅ Zbierz wszystkie bloki schematu posortowane od góry do dołu
        BlockVector3 origin = trapSchematic.getOrigin();
        BlockVector3 min = trapSchematic.getMinimumPoint();
        BlockVector3 max = trapSchematic.getMaximumPoint();

        int offsetX = location.getBlockX() - origin.getBlockX();
        int offsetY = location.getBlockY() - origin.getBlockY();
        int offsetZ = location.getBlockZ() - origin.getBlockZ();

        // Zbierz bloki per warstwa Y (od góry do dołu)
        int maxY = max.getBlockY();
        int minY = min.getBlockY();
        int totalLayers = maxY - minY + 1;

        List<List<BlockPlacement>> layers = new ArrayList<>();

        for (int y = maxY; y >= minY; y--) {
            List<BlockPlacement> layer = new ArrayList<>();

            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    com.sk89q.worldedit.world.block.BlockState blockState =
                            trapSchematic.getBlock(BlockVector3.at(x, y, z));

                    if (blockState.getBlockType().getMaterial().isAir()) continue;

                    int worldX = x + offsetX;
                    int worldY = y + offsetY;
                    int worldZ = z + offsetZ;

                    Material bukkitMat = BukkitAdapter.adapt(blockState.getBlockType());
                    org.bukkit.block.data.BlockData bukkitData = BukkitAdapter.adapt(blockState);

                    layer.add(new BlockPlacement(worldX, worldY, worldZ, bukkitMat, bukkitData));
                }
            }

            if (!layer.isEmpty()) {
                layers.add(layer);
            }
        }

        // ✅ Animacja: 1 warstwa co 1 tick (szybka)
        new BukkitRunnable() {
            int layerIndex = 0;

            @Override
            public void run() {
                if (layerIndex >= layers.size()) {
                    cancel();
                    return;
                }

                List<BlockPlacement> layer = layers.get(layerIndex);

                for (BlockPlacement placement : layer) {
                    Block worldBlock = location.getWorld().getBlockAt(
                            placement.x, placement.y, placement.z);

                    // ✅ Nie zamieniaj bedrocka
                    if (worldBlock.getType() == Material.BEDROCK) continue;

                    worldBlock.setBlockData(placement.blockData, false);
                }

                layerIndex++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void cleanup() {
        // Nic do czyszczenia
    }

    // ==================== INNER CLASS ====================

    private static class BlockPlacement {
        final int x, y, z;
        final Material material;
        final org.bukkit.block.data.BlockData blockData;

        BlockPlacement(int x, int y, int z, Material material, org.bukkit.block.data.BlockData blockData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.blockData = blockData;
        }
    }
}
