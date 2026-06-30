package pl.msurvival.bans;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;

public final class MSurvivalBans extends JavaPlugin implements Listener {
    private File file; private YamlConfiguration data;
    @Override public void onEnable(){saveDefaultConfig(); file=new File(getDataFolder(),"bans.yml"); if(!file.exists())try{getDataFolder().mkdirs();file.createNewFile();}catch(Exception e){e.printStackTrace();} data=YamlConfiguration.loadConfiguration(file); Bukkit.getPluginManager().registerEvents(this,this); cmds();}
    private void cmds(){
        getCommand("msban").setExecutor((s,c,l,a)->{if(!s.hasPermission("msurvival.bans.admin")||a.length<2)return true; String r=join(a,1); data.set("bans."+a[0].toLowerCase()+".reason",r); save(); Player p=Bukkit.getPlayerExact(a[0]); if(p!=null)p.kickPlayer(color(getConfig().getString("messages.ban-screen").replace("%reason%",r))); return true;});
        getCommand("msunban").setExecutor((s,c,l,a)->{if(s.hasPermission("msurvival.bans.admin")&&a.length>0){data.set("bans."+a[0].toLowerCase(),null);save();}return true;});
        getCommand("mskick").setExecutor((s,c,l,a)->{if(s.hasPermission("msurvival.bans.admin")&&a.length>1){Player p=Bukkit.getPlayerExact(a[0]);if(p!=null)p.kickPlayer(color(join(a,1)));}return true;});
        getCommand("mswarn").setExecutor((s,c,l,a)->{if(s.hasPermission("msurvival.bans.admin")&&a.length>1){Player p=Bukkit.getPlayerExact(a[0]);if(p!=null)p.sendMessage(color("&cOstrzeżenie: &e"+join(a,1)));}return true;});
    }
    @EventHandler public void login(AsyncPlayerPreLoginEvent e){String r=data.getString("bans."+e.getName().toLowerCase()+".reason"); if(r!=null)e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,color(getConfig().getString("messages.ban-screen").replace("%reason%",r)));}
    private String join(String[] a,int start){return String.join(" ", Arrays.copyOfRange(a,start,a.length));}
    private void save(){try{data.save(file);}catch(Exception e){e.printStackTrace();}}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
}
