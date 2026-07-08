package pl.anaheim.anaitemy.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;

import java.io.File;
import java.io.FileInputStream;
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
        // ✅ Stwórz folder schematics jeśli nie istnieje
        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        // ✅ Kopiuj schemat z zasobów pluginu jeśli nie istnieje
        File schematicFile = new File(schematicsFolder, "turbotrap.schem");
        if (!schematicFile.exists()) {
            plugin.saveResource("schematics/turbotrap.schem", false);
        }

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("[Turbotrap] Nie znaleziono pliku schematu! Umieść go w: "
                    + schematicFile.getPath());
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

    public void pasteSchematic(Location location) {
        if (!isReady()) return;
        if (isInBlockedRegion(location)) return;

        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                Operation operation = new ClipboardHolder(trapSchematic)
                        .createPaste(editSession)
                        .to(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                        .ignoreAirBlocks(true)
                        .build();

                Operations.complete(operation);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Turbotrap] Błąd wklejania schematu: " + e.getMessage());
        }
    }

    public void cleanup() {
        // Nic do czyszczenia
    }
}
