package pl.msurvival.welcome;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class MSurvivalWelcome extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.setJoinMessage(color(getConfig().getString("join-message", "").replace("%player%", p.getName())));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (getConfig().getBoolean("title.enabled", true)) {
                p.sendTitle(color(getConfig().getString("title.title", "&6MSURVIVAL")), color(getConfig().getString("title.subtitle", "")), 10, 70, 20);
            }
            sendList(p, "welcome");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }, 20L);
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        e.setQuitMessage(color(getConfig().getString("quit-message", "").replace("%player%", e.getPlayer().getName())));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("welcome")) {
            if (!sender.hasPermission("msurvival.welcome.admin")) return true;
            reloadConfig();
            sender.sendMessage(color("&6&lMSurvival &8» &aPrzeładowano welcome."));
            return true;
        }
        if (sender instanceof Player p) {
            if (cmd.equals("pomoc")) sendList(p, "help");
            if (cmd.equals("regulamin")) sendList(p, "regulamin");
            if (cmd.equals("media")) sendList(p, "media");
            if (cmd.equals("donate")) sendList(p, "donate");
        }
        return true;
    }

    private void sendList(Player p, String path) {
        for (String line : getConfig().getStringList(path)) p.sendMessage(color(line.replace("%player%", p.getName())));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
