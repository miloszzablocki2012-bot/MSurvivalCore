package pl.msurvival.core;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class MSurvivalCore extends JavaPlugin {
    private static MSurvivalCore instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getCommand("mscore").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvival.core.admin")) {
                sender.sendMessage(color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.no-permission", "")));
                return true;
            }
            reloadConfig();
            sender.sendMessage(color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.reload", "")));
            return true;
        });
    }

    public static MSurvivalCore get() {
        return instance;
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
