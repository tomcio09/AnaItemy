package pl.anaheim.anaitemy;

import org.bukkit.plugin.java.JavaPlugin;
import pl.anaheim.anaitemy.commands.ItemyEventoweCommand;
import pl.anaheim.anaitemy.listeners.ExcaliburListener;
import pl.anaheim.anaitemy.listeners.GUIListener;
import pl.anaheim.anaitemy.listeners.TotemListener;

public class AnaItemy extends JavaPlugin {

    private static AnaItemy instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        ItemyEventoweCommand cmd = new ItemyEventoweCommand(this);
        getCommand("itemyeventowe").setExecutor(cmd);
        getCommand("itemyeventowe").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new TotemListener(this), this);
        getServer().getPluginManager().registerEvents(new ExcaliburListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        getLogger().info("AnaItemy zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AnaItemy zostal wylaczony!");
    }

    public static AnaItemy getInstance() {
        return instance;
    }
}
