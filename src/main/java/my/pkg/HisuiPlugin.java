package my.pkg;

import my.pkg.HisuiManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HisuiPlugin extends JavaPlugin {

    private HisuiManager hisuiManager;

    @Override
    public void onEnable() {
        hisuiManager = new HisuiManager(this);
        hisuiManager.register();
    }

    @Override
    public void onDisable() {
    }
}