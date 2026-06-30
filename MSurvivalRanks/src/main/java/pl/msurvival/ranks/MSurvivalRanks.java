package pl.msurvival.ranks;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import java.io.File;
import java.util.*;

public final class MSurvivalRanks extends JavaPlugin implements Listener {
    private File file; private YamlConfiguration data;
    public void onEnable(){saveDefaultConfig(); file=new File(getDataFolder(),"players.yml"); try{getDataFolder().mkdirs(); file.createNewFile();}catch(Exception ignored){} data=YamlConfiguration.loadConfiguration(file); Bukkit.getPluginManager().registerEvents(this,this); Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().forEach(this::sidebar),20,80);}
    public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if(c.getName().equalsIgnoreCase("rank") && s instanceof Player p){p.sendMessage(color("&7Ranga: "+display(rank(p)))); return true;}
        if(c.getName().equalsIgnoreCase("setrank") || c.getName().equalsIgnoreCase("setranktemp")){
            if(!s.hasPermission("msurvival.ranks.admin")) return true; if(a.length<2) return true;
            long exp=0; if(c.getName().equalsIgnoreCase("setranktemp") && a.length>=3) exp=System.currentTimeMillis()+parse(a[2]);
            data.set("players."+a[0].toLowerCase()+".rank", a[1].toLowerCase()); data.set("players."+a[0].toLowerCase()+".expires", exp); save();
            Player p=Bukkit.getPlayerExact(a[0]); if(p!=null) sidebar(p); s.sendMessage(color("&aUstawiono rangę.")); return true;
        } return true;
    }
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->sidebar(e.getPlayer()),20);}
    @EventHandler public void chat(AsyncPlayerChatEvent e){e.setFormat(color(prefix(rank(e.getPlayer()))+"%1$s &8» &f%2$s").replace("%","%%"));}
    private void sidebar(Player p){
        String r=rank(p); p.setPlayerListName(color(prefix(r)+p.getName()));
        Scoreboard b=Bukkit.getScoreboardManager().getNewScoreboard(); Objective o=b.registerNewObjective("ms","dummy",color(getConfig().getString("sidebar.title"))); o.setDisplaySlot(DisplaySlot.SIDEBAR);
        int sc=getConfig().getStringList("sidebar.lines").size(); Set<String> used=new HashSet<>(); String ex=expires(p);
        for(String raw:getConfig().getStringList("sidebar.lines")){
            if(raw.contains("%expires%") && ex.equals("nigdy")){sc--; continue;}
            String line=color(raw.replace("%player%",p.getName()).replace("%rank%",display(r)).replace("%expires%",ex).replace("%money%",money(p.getName())).replace("%clan%",clan(p.getName())).replace("%online%",""+Bukkit.getOnlinePlayers().size()).replace("%ping%",""+p.getPing()));
            while(used.contains(line)) line += ChatColor.RESET; used.add(line); o.getScore(line.length()>40?line.substring(0,40):line).setScore(sc--);
        } p.setScoreboard(b);
    }
    private String rank(Player p){String base="players."+p.getName().toLowerCase(); long exp=data.getLong(base+".expires",0); if(exp>0&&exp<System.currentTimeMillis()){data.set(base+".rank","gracz");data.set(base+".expires",0);save();return"gracz";} return data.getString(base+".rank","gracz");}
    private String expires(Player p){long exp=data.getLong("players."+p.getName().toLowerCase()+".expires",0); if(exp<=0)return"nigdy"; long m=Math.max(1,(exp-System.currentTimeMillis())/60000); long h=m/60,d=h/24; if(d>0)return d+"d "+(h%24)+"h"; if(h>0)return h+"h "+(m%60)+"m"; return m+"m";}
    private String money(String name){File f=new File(getDataFolder().getParentFile(),"MSurvivalMarket/balances.yml"); if(!f.exists())return"0"; return ""+(long)YamlConfiguration.loadConfiguration(f).getDouble("players."+name.toLowerCase(),0);}
    private String clan(String name){File f=new File(getDataFolder().getParentFile(),"MSurvivalClans/clans.yml"); if(!f.exists())return"Brak"; return YamlConfiguration.loadConfiguration(f).getString("players."+name.toLowerCase()+".clan","Brak");}
    private long parse(String t){try{long v=Long.parseLong(t.substring(0,t.length()-1)); char u=t.charAt(t.length()-1); return u=='d'?v*86400000:u=='h'?v*3600000:v*60000;}catch(Exception e){return 0;}}
    private String display(String r){return color(getConfig().getString("ranks."+r+".display",r));} private String prefix(String r){return getConfig().getString("ranks."+r+".prefix","&7GRACZ &7");}
    private void save(){try{data.save(file);}catch(Exception e){}} private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
}
