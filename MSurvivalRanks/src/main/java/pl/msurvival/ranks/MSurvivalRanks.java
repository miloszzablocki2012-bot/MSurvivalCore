package pl.msurvival.ranks;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.util.*;

@SuppressWarnings("deprecation")
public final class MSurvivalRanks extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if(!dataFile.exists()) try{getDataFolder().mkdirs(); dataFile.createNewFile();}catch(Exception e){e.printStackTrace();}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        commands();
        Bukkit.getScheduler().runTaskTimer(this, this::refresh, 20L, 100L);
    }

    @Override public void onDisable(){ saveData(); }

    private void commands() {
        getCommand("setrank").setExecutor((s,c,l,a)->{ if(!admin(s))return true; if(a.length<2)return true; setRank(s,a[0],a[1],0); return true; });
        getCommand("setranktemp").setExecutor((s,c,l,a)->{ if(!admin(s))return true; if(a.length<3)return true; setRank(s,a[0],a[1],System.currentTimeMillis()+parseTime(a[2])); return true; });
        getCommand("temprank").setExecutor((s,c,l,a)->{ if(!admin(s))return true; if(a.length<3)return true; setRank(s,a[0],a[1],System.currentTimeMillis()+parseTime(a[2])); return true; });
        getCommand("rankremove").setExecutor((s,c,l,a)->{ if(!admin(s))return true; if(a.length<1)return true; data.set("players."+Bukkit.getOfflinePlayer(a[0]).getUniqueId(),null); saveData(); return true; });
        getCommand("rank").setExecutor((s,c,l,a)->{ if(s instanceof Player p) s.sendMessage(color("&aTwoja ranga: " + display(rank(p)))); return true; });
        getCommand("ranks").setExecutor((s,c,l,a)->{ for(String r:getConfig().getConfigurationSection("ranks").getKeys(false)) s.sendMessage(color("&8- &e"+r+" &7=> "+display(r))); return true; });
    }

    private void setRank(org.bukkit.command.CommandSender s,String name,String rank,long expires) {
        rank=rank.toLowerCase(Locale.ROOT);
        if(!getConfig().contains("ranks."+rank)) { s.sendMessage(color("&cNie ma rangi.")); return; }
        UUID uuid=Bukkit.getOfflinePlayer(name).getUniqueId();
        data.set("players."+uuid+".name", name);
        data.set("players."+uuid+".rank", rank);
        data.set("players."+uuid+".expires", expires);
        saveData();
        Player p=Bukkit.getPlayerExact(name);
        if(p!=null) apply(p);
        s.sendMessage(color("&aUstawiono rangę "+display(rank)+" &adla &e"+name));
    }

    @EventHandler public void join(PlayerJoinEvent e){ Bukkit.getScheduler().runTaskLater(this,()->apply(e.getPlayer()),20L); }
    @EventHandler public void chat(AsyncPlayerChatEvent e){ String r=rank(e.getPlayer()); e.setFormat(color(getConfig().getString("ranks."+r+".prefix"))+"%1$s &8» &f%2$s"); }

    private void refresh(){ for(Player p:Bukkit.getOnlinePlayers()) apply(p); }

    private void apply(Player p) {
        String r=rank(p);
        p.setPlayerListName(color(getConfig().getString("ranks."+r+".tab")+getConfig().getString("ranks."+r+".color")+p.getName()));
        sidebar(p);
        for(Player viewer:Bukkit.getOnlinePlayers()) tag(viewer,p,r);
    }

    private String rank(Player p) {
        long exp=data.getLong("players."+p.getUniqueId()+".expires",0);
        if(exp>0 && exp<System.currentTimeMillis()) { data.set("players."+p.getUniqueId(),null); saveData(); }
        String r=data.getString("players."+p.getUniqueId()+".rank","gracz");
        return getConfig().contains("ranks."+r)?r:"gracz";
    }

    private void sidebar(Player p) {
        if(!getConfig().getBoolean("sidebar.enabled",true))return;
        Scoreboard b=Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o=b.registerNewObjective("ms","dummy",color(getConfig().getString("sidebar.title")));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score=getConfig().getStringList("sidebar.lines").size();
        Set<String> used=new HashSet<>();
        for(String raw:getConfig().getStringList("sidebar.lines")) {
            String ex=expires(p);
            if(raw.contains("%expires%") && ex.equals("nigdy")) { score--; continue; }
            String line=color(raw.replace("%player%",p.getName()).replace("%rank%",display(rank(p))).replace("%expires%",ex).replace("%online%",String.valueOf(Bukkit.getOnlinePlayers().size())).replace("%ping%",String.valueOf(p.getPing())));
            while(used.contains(line)) line += ChatColor.RESET;
            used.add(line);
            o.getScore(line.length()>40?line.substring(0,40):line).setScore(score--);
        }
        p.setScoreboard(b);
    }

    private void tag(Player viewer, Player target, String r) {
        Scoreboard b=viewer.getScoreboard();
        Team t=b.getTeam("r"+getConfig().getInt("ranks."+r+".priority")+r.substring(0,Math.min(3,r.length())));
        if(t==null)t=b.registerNewTeam("r"+getConfig().getInt("ranks."+r+".priority")+r.substring(0,Math.min(3,r.length())));
        t.setPrefix(color(getConfig().getString("ranks."+r+".prefix")));
        if(!t.hasEntry(target.getName())) t.addEntry(target.getName());
    }

    private String expires(Player p){ long e=data.getLong("players."+p.getUniqueId()+".expires",0); return e<=0?"nigdy":time(e-System.currentTimeMillis()); }
    private String display(String r){ return color(getConfig().getString("ranks."+r+".display",r)); }
    private boolean admin(org.bukkit.command.CommandSender s){ if(!s.hasPermission("msurvival.ranks.admin")){s.sendMessage(color("&cNie masz uprawnień.")); return false;} return true; }
    private long parseTime(String s){ try{long n=Long.parseLong(s.substring(0,s.length()-1)); return switch(s.charAt(s.length()-1)){case 'm'->n*60000; case 'h'->n*3600000; case 'd'->n*86400000; default->n*1000;};}catch(Exception e){return 0;} }
    private String time(long ms){ long m=ms/60000,h=m/60,d=h/24; if(d>0)return d+"d "+(h%24)+"h"; if(h>0)return h+"h "+(m%60)+"m"; return Math.max(1,m)+"m"; }
    private void saveData(){ try{data.save(dataFile);}catch(Exception e){e.printStackTrace();} }
    private String color(String s){ return ChatColor.translateAlternateColorCodes('&',s==null?"":s); }
}
