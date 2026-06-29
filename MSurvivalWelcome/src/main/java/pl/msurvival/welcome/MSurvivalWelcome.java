package pl.msurvival.welcome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class MSurvivalWelcome extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("welcome").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvivalwelcome.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            reloadConfig();
            sender.sendMessage(msg("reload"));
            return true;
        });
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendTitle(
                color(getConfig().getString("messages.join-title", "&6&lMSURVIVAL")),
                color(getConfig().getString("messages.join-subtitle", "&7Witaj, &e%player%&7!").replace("%player%", player.getName())),
                10, 60, 10
        );

        String path = "players." + player.getUniqueId();
        if (!data.getBoolean(path + ".joinedBefore", false)) {
            data.set(path + ".joinedBefore", true);
            saveData();

            Bukkit.broadcastMessage(color(getConfig().getString("messages.first-join-broadcast", "").replace("%player%", player.getName())));

            if (getConfig().getBoolean("settings.first-join-kit", true)) {
                for (String cmd : getConfig().getStringList("first-join-commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }
            }
        }
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
