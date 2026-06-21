package pl.anaheim.anaitemy;

import org.bukkit.plugin.java.JavaPlugin;
import pl.anaheim.anaitemy.commands.ItemyEventoweCommand;
import pl.anaheim.anaitemy.listeners.ExcaliburListener;
import pl.anaheim.anaitemy.listeners.TotemListener;

public class AnaItemy extends JavaPlugin {

    private static AnaItemy instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getCommand("itemyeventowe").setExecutor(new ItemyEventoweCommand(this));

        getServer().getPluginManager().registerEvents(new TotemListener(this), this);
        getServer().getPluginManager().registerEvents(new ExcaliburListener(this), this);

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
