package pl.msurvival.auth;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;

public final class MSurvivalAuth extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private final Set<UUID> logged = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile=new File(getDataFolder(),"data.yml");
        if(!dataFile.exists()) try{getDataFolder().mkdirs();dataFile.createNewFile();}catch(Exception e){e.printStackTrace();}
        data=YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this,this);

        getCommand("register").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p))return true; if(bypass(p))return true; if(a.length<1){p.sendMessage(msg("register"));return true;} data.set("players."+p.getUniqueId()+".password",hash(a[0])); data.set("players."+p.getUniqueId()+".name",p.getName()); logged.add(p.getUniqueId()); save(); p.sendMessage(msg("registered")); return true;});
        getCommand("login").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p))return true; if(bypass(p))return true; if(a.length<1){p.sendMessage(msg("login"));return true;} if(hash(a[0]).equals(data.getString("players."+p.getUniqueId()+".password",""))){logged.add(p.getUniqueId());p.sendMessage(msg("logged"));}else p.sendMessage(msg("wrong")); return true;});
        getCommand("resetpassword").setExecutor((s,c,l,a)->{ if(!s.hasPermission("msurvival.auth.admin"))return true; if(a.length<1)return true; data.set("players."+Bukkit.getOfflinePlayer(a[0]).getUniqueId(),null); save(); return true;});
        getCommand("authbypass").setExecutor((s,c,l,a)->{ if(!s.hasPermission("msurvival.auth.admin"))return true; List<String> list=new ArrayList<>(getConfig().getStringList("settings.bypass")); if(a.length>=2&&a[0].equalsIgnoreCase("add"))list.add(a[1]); if(a.length>=2&&a[0].equalsIgnoreCase("remove"))list.removeIf(x->x.equalsIgnoreCase(a[1])); getConfig().set("settings.bypass",list); saveConfig(); s.sendMessage(list.toString()); return true;});
    }

    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();
        if(bypass(p)){logged.add(p.getUniqueId());return;}
        Bukkit.getScheduler().runTaskLater(this,()->{
            World w=Bukkit.getWorld(getConfig().getString("settings.lobby-world","Lobby"));
            if(w!=null)p.teleport(w.getSpawnLocation());
            if(!data.contains("players."+p.getUniqueId()+".password"))p.sendMessage(msg("register")); else p.sendMessage(msg("login"));
        },10L);
        Bukkit.getScheduler().runTaskLater(this,()->{ if(p.isOnline()&&locked(p))p.kickPlayer(color(getConfig().getString("messages.timeout")));},getConfig().getLong("settings.login-timeout-seconds",60)*20L);
    }
    @EventHandler public void quit(PlayerQuitEvent e){logged.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void move(PlayerMoveEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void chat(AsyncPlayerChatEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void cmd(PlayerCommandPreprocessEvent e){String m=e.getMessage().toLowerCase(Locale.ROOT); if(locked(e.getPlayer())&&!m.startsWith("/login")&&!m.startsWith("/register"))e.setCancelled(true);}
    @EventHandler public void interact(PlayerInteractEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void br(BlockBreakEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void bp(BlockPlaceEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}

    private boolean locked(Player p){return !bypass(p)&&!logged.contains(p.getUniqueId());}
    private boolean bypass(Player p){ for(String n:getConfig().getStringList("settings.bypass"))if(n.equalsIgnoreCase(p.getName()))return true; return false;}
    private String hash(String s){try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=md.digest(s.getBytes());StringBuilder sb=new StringBuilder();for(byte x:b)sb.append(String.format("%02x",x));return sb.toString();}catch(Exception e){return s;}}
    private void save(){try{data.save(dataFile);}catch(Exception e){e.printStackTrace();}}
    private String msg(String k){return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,""));}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
}
