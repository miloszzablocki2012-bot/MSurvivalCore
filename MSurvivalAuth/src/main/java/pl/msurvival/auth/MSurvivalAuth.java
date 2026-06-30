package pl.msurvival.auth;

import org.bukkit.*;import org.bukkit.command.*;import org.bukkit.configuration.file.YamlConfiguration;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.block.*;import org.bukkit.event.player.*;import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;import java.security.MessageDigest;import java.util.*;

public final class MSurvivalAuth extends JavaPlugin implements Listener{
 File file; YamlConfiguration data; Set<UUID> logged=new HashSet<>();
 public void onEnable(){saveDefaultConfig();file=new File(getDataFolder(),"auth.yml");try{getDataFolder().mkdirs();file.createNewFile();}catch(Exception ignored){}data=YamlConfiguration.loadConfiguration(file);Bukkit.getPluginManager().registerEvents(this,this);}
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(c.getName().equalsIgnoreCase("authbypass")){if(!s.hasPermission("msurvival.auth.admin"))return true; if(a.length>=2){List<String>b=new ArrayList<>(getConfig().getStringList("settings.bypass")); if(a[0].equalsIgnoreCase("add"))b.add(a[1]); if(a[0].equalsIgnoreCase("remove"))b.removeIf(x->x.equalsIgnoreCase(a[1])); getConfig().set("settings.bypass",b);saveConfig();}return true;}
  if(!(s instanceof Player p))return true;
  if(c.getName().equalsIgnoreCase("register")){if(a.length<1){p.sendMessage(msg("register"));return true;}data.set("players."+p.getUniqueId()+".password",hash(a[0]));logged.add(p.getUniqueId());save();p.sendMessage(msg("ok"));return true;}
  if(c.getName().equalsIgnoreCase("login")){if(a.length<1){p.sendMessage(msg("login"));return true;}if(hash(a[0]).equals(data.getString("players."+p.getUniqueId()+".password",""))){logged.add(p.getUniqueId());p.sendMessage(msg("ok"));}else p.sendMessage(msg("wrong"));return true;}
  return true;
 }
 @EventHandler public void join(PlayerJoinEvent e){Player p=e.getPlayer(); if(bypass(p)||premium(p)){logged.add(p.getUniqueId()); if(premium(p))p.sendMessage(msg("premium"));return;} Bukkit.getScheduler().runTaskLater(this,()->p.sendMessage(data.contains("players."+p.getUniqueId()+".password")?msg("login"):msg("register")),20);}
 @EventHandler public void quit(PlayerQuitEvent e){logged.remove(e.getPlayer().getUniqueId());}
 @EventHandler public void move(PlayerMoveEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void chat(AsyncPlayerChatEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void cmd(PlayerCommandPreprocessEvent e){String m=e.getMessage().toLowerCase(); if(locked(e.getPlayer())&&!m.startsWith("/login")&&!m.startsWith("/register"))e.setCancelled(true);}
 @EventHandler public void br(BlockBreakEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}@EventHandler public void bp(BlockPlaceEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
 private boolean premium(Player p){return getConfig().getBoolean("settings.premium-auto-login",true)&&p.getUniqueId().version()==4;}
 private boolean bypass(Player p){for(String n:getConfig().getStringList("settings.bypass"))if(n.equalsIgnoreCase(p.getName()))return true;return false;}
 private boolean locked(Player p){return !logged.contains(p.getUniqueId())&&!bypass(p)&&!premium(p);}
 private String hash(String s){try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[]b=md.digest(s.getBytes());StringBuilder sb=new StringBuilder();for(byte x:b)sb.append(String.format("%02x",x));return sb.toString();}catch(Exception e){return s;}}
 private void save(){try{data.save(file);}catch(Exception e){}} private String msg(String k){return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,""));} private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
}
