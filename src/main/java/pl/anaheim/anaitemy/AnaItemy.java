package pl.anaheim.anaitemy;

import org.bukkit.plugin.java.JavaPlugin;
import pl.anaheim.anaitemy.commands.ItemyEventoweCommand;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.listeners.*;
import pl.anaheim.anaitemy.managers.*;

public class AnaItemy extends JavaPlugin {

    private static AnaItemy instance;
    private HydroKlatkaManager hydroKlatkaManager;
    private RozdzkailuzjonistyManager rozdzkailuzjonistyManager;
    private WedkaNielotaManager wedkaNielotaManager;
    private WzmocnianaElytraManager wzmocnianaElytraManager;
    private BlokWidmoManager blokWidmoManager;
    private SiekieraGrinchaManager siekieraGrinchaManager;
    private ItemsConfig itemsConfig;
    private WorldGuardManager worldGuardManager;
    private CombatIntegrationManager combatIntegrationManager;
    private ActionBarManager actionBarManager;
    private ItemProtectionManager itemProtectionManager;
    private TotemListener totemListener;

    @Override
    public void onEnable() {
        instance = this;

        // Konfiguracje
        saveDefaultConfig();
        itemsConfig = new ItemsConfig(this);

        // Inicjalizacja managerów
        worldGuardManager = new WorldGuardManager(this);
        combatIntegrationManager = new CombatIntegrationManager(this);
        actionBarManager = new ActionBarManager(this);
        itemProtectionManager = new ItemProtectionManager(this);
        hydroKlatkaManager = new HydroKlatkaManager(this);
        rozdzkailuzjonistyManager = new RozdzkailuzjonistyManager(this);
        wedkaNielotaManager = new WedkaNielotaManager(this);
        wzmocnianaElytraManager = new WzmocnianaElytraManager(this);
        blokWidmoManager = new BlokWidmoManager(this);
        siekieraGrinchaManager = new SiekieraGrinchaManager(this);

        // Informacja o Citizens
        if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().warning("Citizens nie znaleziono - Różdżka Iluzjonisty działa bez NPC!");
        }

        // Komendy
        ItemyEventoweCommand cmd = new ItemyEventoweCommand(this);
        getCommand("itemyeventowe").setExecutor(cmd);
        getCommand("itemyeventowe").setTabCompleter(cmd);

        totemListener = new TotemListener(this);

        // Listenery
        getServer().getPluginManager().registerEvents(totemListener, this);
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
        getServer().getPluginManager().registerEvents(new SakiewkaUUIDListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatActionBarListener(this), this);
        getServer().getPluginManager().registerEvents(new WzmocnianaElytraListener(this), this);
        getServer().getPluginManager().registerEvents(new BlokWidmoListener(this), this);
        getServer().getPluginManager().registerEvents(new SiekieraGrinchaListener(this), this);

        getLogger().info("AnaItemy zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        if (hydroKlatkaManager != null) hydroKlatkaManager.cleanup();
        if (rozdzkailuzjonistyManager != null) rozdzkailuzjonistyManager.cleanup();
        if (wedkaNielotaManager != null) wedkaNielotaManager.cleanup();
        if (wzmocnianaElytraManager != null) wzmocnianaElytraManager.cleanup();
        if (blokWidmoManager != null) blokWidmoManager.cleanup();
        if (siekieraGrinchaManager != null) siekieraGrinchaManager.cleanup();
        if (actionBarManager != null) actionBarManager.cleanup();
        if (itemProtectionManager != null) itemProtectionManager.cleanup();
        getLogger().info("AnaItemy zostal wylaczony!");
    }

    public static AnaItemy getInstance() { return instance; }
    public HydroKlatkaManager getHydroKlatkaManager() { return hydroKlatkaManager; }
    public RozdzkailuzjonistyManager getRozdzkailuzjonistyManager() { return rozdzkailuzjonistyManager; }
    public WedkaNielotaManager getWedkaNielotaManager() { return wedkaNielotaManager; }
    public WzmocnianaElytraManager getWzmocnianaElytraManager() { return wzmocnianaElytraManager; }
    public BlokWidmoManager getBlokWidmoManager() { return blokWidmoManager; }
    public SiekieraGrinchaManager getSiekieraGrinchaManager() { return siekieraGrinchaManager; }
    public ItemsConfig getItemsConfig() { return itemsConfig; }
    public WorldGuardManager getWorldGuardManager() { return worldGuardManager; }
    public TotemListener getTotemListener() { return totemListener; }
    public CombatIntegrationManager getCombatIntegrationManager() { return combatIntegrationManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }
    public ItemProtectionManager getItemProtectionManager() { return itemProtectionManager; }
}
