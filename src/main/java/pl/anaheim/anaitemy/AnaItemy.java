package pl.anaheim.anaitemy;

import org.bukkit.plugin.java.JavaPlugin;
import pl.anaheim.anaitemy.commands.ItemyEventoweCommand;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.listeners.*;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.managers.RozdzkailuzjonistyManager;
import pl.anaheim.anaitemy.managers.WedkaNielotaManager;
import pl.anaheim.anaitemy.managers.WorldGuardManager;
import pl.anaheim.anaitemy.listeners.SakiewkaListener;
import pl.anaheim.anaitemy.listeners.SakiewkaGUIListener;
import pl.anaheim.anaitemy.listeners.SakiewkaPortalListener;

public class AnaItemy extends JavaPlugin {

    private static AnaItemy instance;
    private HydroKlatkaManager hydroKlatkaManager;
    private RozdzkailuzjonistyManager rozdzkailuzjonistyManager;
    private WedkaNielotaManager wedkaNielotaManager;
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
        wedkaNielotaManager = new WedkaNielotaManager(this);

        // Informacja o Citizens
        if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().warning("Citizens nie znaleziono - Różdżka Iluzjonisty działa bez NPC!");
        }

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
        getServer().getPluginManager().registerEvents(new WedkaNielotaListener(this), this);
        getServer().getPluginManager().registerEvents(new SakiewkaListener(this), this);
        getServer().getPluginManager().registerEvents(new SakiewkaGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new SakiewkaPortalListener(this), this);

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
        if (wedkaNielotaManager != null) {
            wedkaNielotaManager.cleanup();
        }
        getLogger().info("AnaItemy zostal wylaczony!");
    }

    public static AnaItemy getInstance() { return instance; }
    public HydroKlatkaManager getHydroKlatkaManager() { return hydroKlatkaManager; }
    public RozdzkailuzjonistyManager getRozdzkailuzjonistyManager() { return rozdzkailuzjonistyManager; }
    public WedkaNielotaManager getWedkaNielotaManager() { return wedkaNielotaManager; }
    public ItemsConfig getItemsConfig() { return itemsConfig; }
    public WorldGuardManager getWorldGuardManager() { return worldGuardManager; }
}
