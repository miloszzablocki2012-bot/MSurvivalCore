package pl.msurvival.admin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class MSurvivalAdmin extends JavaPlugin implements Listener {
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();

    @Override public void onEnable(){ saveDefaultConfig(); Bukkit.getPluginManager().registerEvents(this,this); commands(); }

    private void commands(){
        getCommand("vanish").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)) return true; if(vanished.remove(p.getUniqueId())){ show(p); p.sendMessage(msg("vanish-off")); } else { vanished.add(p.getUniqueId()); hide(p); p.sendMessage(msg("vanish-on")); } return true; });
        getCommand("fly").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)) return true; p.setAllowFlight(!p.getAllowFlight()); p.sendMessage(msg(p.getAllowFlight()?"fly-on":"fly-off")); return true; });
        getCommand("gm").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)||a.length<1) return true; p.setGameMode(switch(a[0]){case "0"->GameMode.SURVIVAL; case "1"->GameMode.CREATIVE; case "2"->GameMode.ADVENTURE; case "3"->GameMode.SPECTATOR; default->p.getGameMode();}); return true; });
        getCommand("heal").setExecutor((s,c,l,a)->{ Player t=target(s,a); if(t!=null&&adminPerm(s)){ t.setHealth(20); t.setFoodLevel(20); t.sendMessage(color("&aUleczono.")); } return true; });
        getCommand("feed").setExecutor((s,c,l,a)->{ Player t=target(s,a); if(t!=null&&adminPerm(s)){ t.setFoodLevel(20); t.setSaturation(20); t.sendMessage(color("&aNajedzono.")); } return true; });
        getCommand("invsee").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)||a.length<1) return true; Player t=Bukkit.getPlayerExact(a[0]); if(t!=null) p.openInventory(t.getInventory()); return true; });
        getCommand("endersee").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)||a.length<1) return true; Player t=Bukkit.getPlayerExact(a[0]); if(t!=null) p.openInventory(t.getEnderChest()); return true; });
        getCommand("freeze").setExecutor((s,c,l,a)->{ if(!adminPerm(s)||a.length<1) return true; Player t=Bukkit.getPlayerExact(a[0]); if(t!=null){ frozen.add(t.getUniqueId()); t.sendMessage(msg("frozen")); } return true; });
        getCommand("unfreeze").setExecutor((s,c,l,a)->{ if(!adminPerm(s)||a.length<1) return true; Player t=Bukkit.getPlayerExact(a[0]); if(t!=null){ frozen.remove(t.getUniqueId()); t.sendMessage(msg("unfrozen")); } return true; });
        getCommand("clearchat").setExecutor((s,c,l,a)->{ if(!adminPerm(s)) return true; for(Player p:Bukkit.getOnlinePlayers()){ for(int i=0;i<120;i++) p.sendMessage(""); p.sendMessage(color("&cChat został wyczyszczony.")); } return true; });
        getCommand("staffchat").setExecutor((s,c,l,a)->{ if(!adminPerm(s)||a.length<1) return true; String m=String.join(" ",a); for(Player p:Bukkit.getOnlinePlayers()) if(p.hasPermission("msurvivaladmin.admin")) p.sendMessage(color("&c[SC] &f"+s.getName()+": &7"+m)); return true; });
        getCommand("admin").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)) return true; Inventory inv=Bukkit.createInventory(null,27,color("&c&lADMIN PANEL")); inv.setItem(11,item(Material.ENDER_EYE,"&cVanish")); inv.setItem(13,item(Material.FEATHER,"&aFly")); inv.setItem(15,item(Material.GOLDEN_APPLE,"&eHeal")); p.openInventory(inv); return true; });
    }

    @EventHandler public void move(PlayerMoveEvent e){ if(frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    private void hide(Player p){ for(Player other:Bukkit.getOnlinePlayers()) if(!other.hasPermission("msurvivaladmin.admin")) other.hidePlayer(this,p); }
    private void show(Player p){ for(Player other:Bukkit.getOnlinePlayers()) other.showPlayer(this,p); }
    private Player target(org.bukkit.command.CommandSender s,String[] a){ if(a.length>=1) return Bukkit.getPlayerExact(a[0]); return s instanceof Player p ? p : null; }
    private boolean admin(Player p){ if(!p.hasPermission("msurvivaladmin.admin")){ p.sendMessage(msg("no-permission")); return false; } return true; }
    private boolean adminPerm(org.bukkit.command.CommandSender s){ if(!s.hasPermission("msurvivaladmin.admin")){ s.sendMessage(msg("no-permission")); return false; } return true; }
    private org.bukkit.inventory.ItemStack item(Material m,String n){ var it=new org.bukkit.inventory.ItemStack(m); var meta=it.getItemMeta(); meta.setDisplayName(color(n)); it.setItemMeta(meta); return it; }
    private String msg(String k){ return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,"")); }
    private String color(String s){ return ChatColor.translateAlternateColorCodes('&',s); }
}
