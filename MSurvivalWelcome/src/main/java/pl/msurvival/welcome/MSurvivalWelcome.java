package pl.msurvival.welcome;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class MSurvivalWelcome extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this,this);
        getCommand("pomoc").setExecutor((s,c,l,a)->{send(s,getConfig().getStringList("help-message"));return true;});
        getCommand("komendy").setExecutor((s,c,l,a)->{send(s,getConfig().getStringList("help-message"));return true;});
        getCommand("regulamin").setExecutor((s,c,l,a)->{send(s,getConfig().getStringList("welcome-message"));return true;});
        getCommand("donate").setExecutor((s,c,l,a)->{send(s,getConfig().getStringList("donate-message"));return true;});
        getCommand("social").setExecutor((s,c,l,a)->{send(s,getConfig().getStringList("welcome-message"));return true;});
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer();
        if(getConfig().getBoolean("title.enabled",true)) p.sendTitle(color(getConfig().getString("title.title")), color(getConfig().getString("title.subtitle")),10,60,10);
        if(getConfig().getBoolean("sound.enabled",true)) try{p.playSound(p.getLocation(), Sound.valueOf(getConfig().getString("sound.name").toUpperCase(Locale.ROOT)),1,1);}catch(Exception ignored){}
        send(p,getConfig().getStringList("welcome-message"));
    }

    private void send(org.bukkit.command.CommandSender s, List<String> lines) {
        long delay=0;
        for(String line:lines) {
            if(line.startsWith("DELAY:")) { try{delay += Long.parseLong(line.substring(6));}catch(Exception e){delay+=100;} continue; }
            long d=delay;
            Bukkit.getScheduler().runTaskLater(this,()->s.sendMessage(color(line.replace("%player%", s.getName()))),d);
        }
    }
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
}
