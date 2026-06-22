package pl.anaheim.anaitemy;

import org.bukkit.plugin.java.JavaPlugin;
import pl.anaheim.anaitemy.commands.ItemyEventoweCommand;
import pl.anaheim.anaitemy.listeners.*;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;

public class AnaItemy extends JavaPlugin {

    private static AnaItemy instance;
    private HydroKlatkaManager hydroKlatkaManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // Inicjalizacja managerów
        hydroKlatkaManager = new HydroKlatkaManager(this);

        // Komendy
        ItemyEventoweCommand cmd = new ItemyEventoweCommand(this);
        getCommand("itemyeventowe").setExecutor(cmd);
        getCommand("itemyeventowe").setTabCompleter(cmd);

        // Listenery
        getServer().getPluginManager().registerEvents(new TotemListener(this), this);
        getServer().getPluginManager().registerEvents(new ExcaliburListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new HydroKlatkaListener(this), this);
        getServer().getPluginManager().registerEvents(new HydroKlatkaBlockListener(this), this);

        getLogger().info("AnaItemy zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        // Cleanup Hydro Klatki przy wyłączaniu
        if (hydroKlatkaManager != null) {
            hydroKlatkaManager.cleanup();
        }
        
        getLogger().info("AnaItemy zostal wylaczony!");
    }

    public static AnaItemy getInstance() {
        return instance;
    }

    public HydroKlatkaManager getHydroKlatkaManager() {
        return hydroKlatkaManager;
    }
}
