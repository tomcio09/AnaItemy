package pl.anaheim.anaitemy;

import org.bukkit.plugin.java.JavaPlugin;
import pl.anaheim.anaitemy.commands.ItemyEventoweCommand;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.listeners.*;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.managers.RozdzkailuzjonistyManager;
import pl.anaheim.anaitemy.managers.WorldGuardManager;

public class AnaItemy extends JavaPlugin {

    private static AnaItemy instance;
    private HydroKlatkaManager hydroKlatkaManager;
    private RozdzkailuzjonistyManager rozdzkailuzjonistyManager;
    private ItemsConfig itemsConfig;
    private WorldGuardManager worldGuardManager;

    @Override
    public void onEnable() {
        instance = this;

        // Konfiguracje
        saveDefaultConfig();
        itemsConfig = new ItemsConfig(this);

        // Inicjalizacja managerów
        worldGuardManager = new WorldGuardManager(this);
        hydroKlatkaManager = new HydroKlatkaManager(this);
        rozdzkailuzjonistyManager = new RozdzkailuzjonistyManager(this);

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
        getServer().getPluginManager().registerEvents(new HydroKlatkaMovementListener(this), this);
        getServer().getPluginManager().registerEvents(new HydroKlatkaJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new RozdzkailuzjonistyListener(this), this);

        getLogger().info("AnaItemy zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        if (hydroKlatkaManager != null) {
            hydroKlatkaManager.cleanup();
        }
        if (rozdzkailuzjonistyManager != null) {
            rozdzkailuzjonistyManager.cleanup();
        }
        getLogger().info("AnaItemy zostal wylaczony!");
    }

    public static AnaItemy getInstance() {
        return instance;
    }

    public HydroKlatkaManager getHydroKlatkaManager() {
        return hydroKlatkaManager;
    }

    public RozdzkailuzjonistyManager getRozdzkailuzjonistyManager() {
        return rozdzkailuzjonistyManager;
    }

    public ItemsConfig getItemsConfig() {
        return itemsConfig;
    }

    public WorldGuardManager getWorldGuardManager() {
        return worldGuardManager;
    }
}
