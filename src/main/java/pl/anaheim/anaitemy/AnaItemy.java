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
    private HydroTrojzabManager hydroTrojzabManager;
    private CudownaLatarniaManager cudownaLatarniaManager;
    private RogJednorozcaManager rogJednorozcaManager;
    private BoskiToporManager boskiToporManager;
    private SuperMarchewkaManager superMarchewkaManager;
    private LopataGrinchaManager lopataGrinchaManager;
    private ArcusMagnusManager arcusMagnusManager;
    private KroliczyMieczManager kroliczyMieczManager;
    private SmoczyMieczManager smoczyMieczManager;
    private ItemsConfig itemsConfig;
    private WorldGuardManager worldGuardManager;
    private CombatIntegrationManager combatIntegrationManager;
    private ActionBarManager actionBarManager;
    private ItemProtectionManager itemProtectionManager;
    private TotemListener totemListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        itemsConfig = new ItemsConfig(this);

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
        hydroTrojzabManager = new HydroTrojzabManager(this);
        cudownaLatarniaManager = new CudownaLatarniaManager(this);
        rogJednorozcaManager = new RogJednorozcaManager(this);
        boskiToporManager = new BoskiToporManager(this);
        superMarchewkaManager = new SuperMarchewkaManager(this);
        lopataGrinchaManager = new LopataGrinchaManager(this);
        arcusMagnusManager = new ArcusMagnusManager(this);
        kroliczyMieczManager = new KroliczyMieczManager(this);
        smoczyMieczManager = new SmoczyMieczManager(this);

        if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().warning("Citizens nie znaleziono - Różdżka Iluzjonisty działa bez NPC!");
        }

        ItemyEventoweCommand cmd = new ItemyEventoweCommand(this);
        getCommand("itemyeventowe").setExecutor(cmd);
        getCommand("itemyeventowe").setTabCompleter(cmd);

        totemListener = new TotemListener(this);

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
        getServer().getPluginManager().registerEvents(new HydroTrojzabListener(this), this);
        getServer().getPluginManager().registerEvents(new CudownaLatarniaListener(this), this);
        getServer().getPluginManager().registerEvents(new RogJednorozcaListener(this), this);
        getServer().getPluginManager().registerEvents(new BoskiToporListener(this), this);
        getServer().getPluginManager().registerEvents(new SuperMarchewkaListener(this), this);
        getServer().getPluginManager().registerEvents(new LopataGrinchaListener(this), this);
        getServer().getPluginManager().registerEvents(new ArcusMagnusListener(this), this);
        getServer().getPluginManager().registerEvents(new KroliczyMieczListener(this), this);
        getServer().getPluginManager().registerEvents(new PiekielnyMieczListener(this), this);
        getServer().getPluginManager().registerEvents(new SmoczyMieczListener(this), this);

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
        if (hydroTrojzabManager != null) hydroTrojzabManager.cleanup();
        if (cudownaLatarniaManager != null) cudownaLatarniaManager.cleanup();
        if (rogJednorozcaManager != null) rogJednorozcaManager.cleanup();
        if (boskiToporManager != null) boskiToporManager.cleanup();
        if (superMarchewkaManager != null) superMarchewkaManager.cleanup();
        if (lopataGrinchaManager != null) lopataGrinchaManager.cleanup();
        if (arcusMagnusManager != null) arcusMagnusManager.cleanup();
        if (kroliczyMieczManager != null) kroliczyMieczManager.cleanup();
        if (smoczyMieczManager != null) smoczyMieczManager.cleanup();
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
    public HydroTrojzabManager getHydroTrojzabManager() { return hydroTrojzabManager; }
    public CudownaLatarniaManager getCudownaLatarniaManager() { return cudownaLatarniaManager; }
    public RogJednorozcaManager getRogJednorozcaManager() { return rogJednorozcaManager; }
    public BoskiToporManager getBoskiToporManager() { return boskiToporManager; }
    public SuperMarchewkaManager getSuperMarchewkaManager() { return superMarchewkaManager; }
    public LopataGrinchaManager getLopataGrinchaManager() { return lopataGrinchaManager; }
    public ArcusMagnusManager getArcusMagnusManager() { return arcusMagnusManager; }
    public KroliczyMieczManager getKroliczyMieczManager() { return kroliczyMieczManager; }
    public SmoczyMieczManager getSmoczyMieczManager() { return smoczyMieczManager; }
    public ItemsConfig getItemsConfig() { return itemsConfig; }
    public WorldGuardManager getWorldGuardManager() { return worldGuardManager; }
    public TotemListener getTotemListener() { return totemListener; }
    public CombatIntegrationManager getCombatIntegrationManager() { return combatIntegrationManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }
    public ItemProtectionManager getItemProtectionManager() { return itemProtectionManager; }
}
