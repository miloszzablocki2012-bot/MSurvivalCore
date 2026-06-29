package pl.msurvival.bans;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MSurvivalBans extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("msban").setExecutor((s, c, l, a) -> {
            if (!s.hasPermission("msurvivalbans.admin")) { s.sendMessage(msg("no-permission")); return true; }
            if (a.length < 2) return true;
            String player = a[0];
            String reason = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
            data.set("bans." + player.toLowerCase() + ".reason", reason);
            data.set("bans." + player.toLowerCase() + ".by", s.getName());
            saveData();
            Player online = Bukkit.getPlayerExact(player);
            if (online != null) online.kickPlayer(color(getConfig().getString("messages.ban-screen").replace("%reason%", reason)));
            s.sendMessage(msg("banned").replace("%player%", player).replace("%reason%", reason));
            return true;
        });

        getCommand("msunban").setExecutor((s, c, l, a) -> {
            if (!s.hasPermission("msurvivalbans.admin")) { s.sendMessage(msg("no-permission")); return true; }
            if (a.length < 1) return true;
            data.set("bans." + a[0].toLowerCase(), null);
            saveData();
            s.sendMessage(msg("unbanned").replace("%player%", a[0]));
            return true;
        });

        getCommand("mskick").setExecutor((s, c, l, a) -> {
            if (!s.hasPermission("msurvivalbans.admin")) { s.sendMessage(msg("no-permission")); return true; }
            if (a.length < 2) return true;
            Player p = Bukkit.getPlayerExact(a[0]);
            String reason = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
            if (p != null) p.kickPlayer(color(reason));
            s.sendMessage(msg("kicked").replace("%player%", a[0]));
            return true;
        });

        getCommand("mswarn").setExecutor((s, c, l, a) -> {
            if (!s.hasPermission("msurvivalbans.admin")) { s.sendMessage(msg("no-permission")); return true; }
            if (a.length < 2) return true;
            String reason = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
            data.set("warns." + a[0].toLowerCase() + "." + System.currentTimeMillis(), reason);
            saveData();
            Player p = Bukkit.getPlayerExact(a[0]);
            if (p != null) p.sendMessage(color("&cOstrzeżenie: &e" + reason));
            s.sendMessage(msg("warned").replace("%player%", a[0]));
            return true;
        });
    }

    @EventHandler
    public void login(PlayerLoginEvent e) {
        String path = "bans." + e.getPlayer().getName().toLowerCase();
        if (data.contains(path)) {
            String reason = data.getString(path + ".reason", "Brak powodu");
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, color(getConfig().getString("messages.ban-screen").replace("%reason%", reason)));
        }
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { getDataFolder().mkdirs(); dataFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try { data.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
