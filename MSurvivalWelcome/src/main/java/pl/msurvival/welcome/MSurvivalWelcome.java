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
        e.setJoinMessage(color(getConfig().getString("settings.join-message", "").replace("%player%", p.getName())));

        if (!p.hasPlayedBefore()) {
            Bukkit.broadcastMessage(color(getConfig().getString("settings.first-join-broadcast", "").replace("%player%", p.getName())));
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (getConfig().getBoolean("title.enabled", true)) {
                p.sendTitle(color(getConfig().getString("title.title", "&6MSURVIVAL")), color(getConfig().getString("title.subtitle", "")), 10, 70, 20);
            }
            sendDelayed(p, "welcome");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }, 20L);
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        e.setQuitMessage(color(getConfig().getString("settings.quit-message", "").replace("%player%", e.getPlayer().getName())));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("welcome")) {
            if (!sender.hasPermission("msurvival.welcome.admin")) return true;
            reloadConfig();
            sender.sendMessage(color("&6MSurvival &8» &aPrzeładowano welcome."));
            return true;
        }
        if (sender instanceof Player p) {
            if (cmd.equals("pomoc")) sendNow(p, "help");
            if (cmd.equals("regulamin")) sendNow(p, "regulamin");
            if (cmd.equals("media")) sendNow(p, "media");
            if (cmd.equals("donate")) sendNow(p, "donate");
        }
        return true;
    }

    private void sendDelayed(Player p, String path) {
        long delay = Math.max(1L, getConfig().getLong("settings.message-delay-ticks", 10L));
        int i = 0;
        for (String line : getConfig().getStringList(path)) {
            long runAt = i++ * delay;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) p.sendMessage(color(line.replace("%player%", p.getName())));
            }, runAt);
        }
    }

    private void sendNow(Player p, String path) {
        for (String line : getConfig().getStringList(path)) p.sendMessage(color(line.replace("%player%", p.getName())));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
