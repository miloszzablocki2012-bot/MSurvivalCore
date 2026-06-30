package pl.msurvival.admin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class MSurvivalAdmin extends JavaPlugin implements Listener {
    private final Set<UUID> vanished=new HashSet<>(), frozen=new HashSet<>();
    @Override public void onEnable(){saveDefaultConfig(); Bukkit.getPluginManager().registerEvents(this,this); cmds();}
    private void cmds(){
        getCommand("vanish").setExecutor((s,c,l,a)->{if(!(s instanceof Player p)||!admin(p))return true; if(vanished.remove(p.getUniqueId())){for(Player o:Bukkit.getOnlinePlayers())o.showPlayer(this,p);p.sendMessage(color("&cVanish off"));}else{vanished.add(p.getUniqueId());for(Player o:Bukkit.getOnlinePlayers())if(!o.hasPermission("msurvival.admin"))o.hidePlayer(this,p);p.sendMessage(color("&aVanish on"));} return true;});
        getCommand("fly").setExecutor((s,c,l,a)->{if(s instanceof Player p&&admin(p)){p.setAllowFlight(!p.getAllowFlight());}return true;});
        getCommand("gm").setExecutor((s,c,l,a)->{if(!(s instanceof Player p)||!admin(p)||a.length<1)return true; p.setGameMode(switch(a[0]){case"0"->GameMode.SURVIVAL;case"1"->GameMode.CREATIVE;case"2"->GameMode.ADVENTURE;case"3"->GameMode.SPECTATOR;default->p.getGameMode();});return true;});
        getCommand("heal").setExecutor((s,c,l,a)->{if(!s.hasPermission("msurvival.admin"))return true; Player p=a.length>0?Bukkit.getPlayerExact(a[0]):(s instanceof Player pl?pl:null); if(p!=null)p.setHealth(p.getMaxHealth());return true;});
        getCommand("feed").setExecutor((s,c,l,a)->{if(!s.hasPermission("msurvival.admin"))return true; Player p=a.length>0?Bukkit.getPlayerExact(a[0]):(s instanceof Player pl?pl:null); if(p!=null){p.setFoodLevel(20);p.setSaturation(20);}return true;});
        getCommand("invsee").setExecutor((s,c,l,a)->{if(s instanceof Player p&&admin(p)&&a.length>0){Player t=Bukkit.getPlayerExact(a[0]);if(t!=null)p.openInventory(t.getInventory());}return true;});
        getCommand("ecsee").setExecutor((s,c,l,a)->{if(s instanceof Player p&&admin(p)&&a.length>0){Player t=Bukkit.getPlayerExact(a[0]);if(t!=null)p.openInventory(t.getEnderChest());}return true;});
        getCommand("staffchat").setExecutor((s,c,l,a)->{if(!s.hasPermission("msurvival.admin")||a.length<1)return true; String msg=String.join(" ",a); for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("msurvival.admin"))p.sendMessage(color("&c[SC] &f"+s.getName()+": &7"+msg));return true;});
        getCommand("freeze").setExecutor((s,c,l,a)->{if(s.hasPermission("msurvival.admin")&&a.length>0){Player p=Bukkit.getPlayerExact(a[0]);if(p!=null)frozen.add(p.getUniqueId());}return true;});
        getCommand("unfreeze").setExecutor((s,c,l,a)->{if(s.hasPermission("msurvival.admin")&&a.length>0){Player p=Bukkit.getPlayerExact(a[0]);if(p!=null)frozen.remove(p.getUniqueId());}return true;});
    }
    @EventHandler public void move(PlayerMoveEvent e){if(frozen.contains(e.getPlayer().getUniqueId()))e.setCancelled(true);}
    private boolean admin(Player p){if(!p.hasPermission("msurvival.admin")){p.sendMessage(color("&cBrak uprawnień."));return false;}return true;}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
}
