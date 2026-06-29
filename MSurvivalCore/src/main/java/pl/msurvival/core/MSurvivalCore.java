package pl.msurvival.core;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.*;
import java.util.*;

@SuppressWarnings("deprecation")
public final class MSurvivalCore extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey menuKey, actionKey, keyKey;
    private final Random random = new Random();
    private final Set<UUID> vanished = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        loadData();
        menuKey = new NamespacedKey(this, "menu");
        actionKey = new NamespacedKey(this, "action");
        keyKey = new NamespacedKey(this, "key_type");
        Bukkit.getPluginManager().registerEvents(this, this);
        commands();
        long saveTicks = Math.max(5, getConfig().getLong("settings.autosave-seconds",20))*20L;
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, saveTicks, saveTicks);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshAllRanks, 40L, 100L);
    }

    @Override public void onDisable() { saveAll(); saveData(); }

    private void commands() {
        getCommand("menu").setExecutor((s,c,l,a)->{ if(s instanceof Player p) openMainMenu(p); return true; });
        getCommand("lobby").setExecutor((s,c,l,a)->{ if(s instanceof Player p) toLobby(p,true); return true; });
        getCommand("survival").setExecutor((s,c,l,a)->{ if(s instanceof Player p) toSurvival(p,true); return true; });
        getCommand("setlobby").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)) return true; saveLoc("lobby",p.getLocation()); p.sendMessage(msg("lobby-set")); return true; });
        getCommand("setsurvival").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)) return true; saveLoc("survival",p.getLocation()); p.sendMessage(msg("survival-set")); return true; });
        getCommand("keysmenu").setExecutor((s,c,l,a)->{ if(s instanceof Player p) openKeysMenu(p); return true; });
        getCommand("kits").setExecutor((s,c,l,a)->{ if(s instanceof Player p) openKitsMenu(p); return true; });
        getCommand("core").setExecutor((s,c,l,a)->{ if(!s.hasPermission("msurvivalcore.admin")){s.sendMessage(msg("no-permission")); return true;} reloadConfig(); s.sendMessage(msg("reload")); return true; });

        getCommand("keyadmin").setExecutor((s,c,l,a)->{
            if(!s.hasPermission("msurvivalcore.keys")){ s.sendMessage(msg("no-permission")); return true; }
            if(a.length>=1 && a[0].equalsIgnoreCase("reload")){ reloadConfig(); s.sendMessage(msg("reload")); return true; }
            if(a.length<3){ s.sendMessage(color("&c/keyadmin <give|item|reset> <gracz> <klucz> [ilosc]")); return true; }
            String mode=a[0].toLowerCase(Locale.ROOT), player=a[1], key=norm(a[2]);
            int amount=a.length>=4?parseInt(a[3]):1;
            if(mode.equals("reset")){ data.set(path(player)+".lastWeekly",0L); saveData(); return true; }
            if(!getConfig().contains("keys."+key)){ s.sendMessage(color("&cNie ma klucza.")); return true; }
            if(mode.equals("give")){ setKeys(player,key,getKeys(player,key)+amount); s.sendMessage(color("&aDodano klucz.")); return true; }
            if(mode.equals("item")){ Player t=Bukkit.getPlayerExact(player); if(t!=null) t.getInventory().addItem(keyItem(key,amount)); return true; }
            return true;
        });

        getCommand("setrank").setExecutor((s,c,l,a)->{ if(!rankAdmin(s)) return true; if(a.length<2)return true; setRank(s,a[0],a[1],0L,null); return true; });
        getCommand("temprank").setExecutor((s,c,l,a)->{ if(!rankAdmin(s)) return true; if(a.length<3)return true; long d=parseDuration(a[2]); if(d<=0){s.sendMessage(color("&cZły czas.")); return true;} setRank(s,a[0],a[1],System.currentTimeMillis()+d,a[2]); return true; });
        getCommand("rankremove").setExecutor((s,c,l,a)->{ if(!rankAdmin(s)) return true; if(a.length<1)return true; OfflinePlayer op=Bukkit.getOfflinePlayer(a[0]); data.set("ranks."+op.getUniqueId(),null); saveData(); Player p=Bukkit.getPlayerExact(a[0]); if(p!=null) applyRank(p); s.sendMessage(msg("rank-remove").replace("%player%",a[0])); return true; });
        getCommand("ranks").setExecutor((s,c,l,a)->{ sendRanks(s); return true; });

        getCommand("vanish").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p))return true; if(vanished.remove(p.getUniqueId())){ for(Player o:Bukkit.getOnlinePlayers()) o.showPlayer(this,p); p.sendMessage(msg("vanish-off")); } else { vanished.add(p.getUniqueId()); for(Player o:Bukkit.getOnlinePlayers()) if(!o.hasPermission("msurvivalcore.admin")) o.hidePlayer(this,p); p.sendMessage(msg("vanish-on")); } return true; });
        getCommand("fly").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p))return true; p.setAllowFlight(!p.getAllowFlight()); return true; });
        getCommand("gm").setExecutor((s,c,l,a)->{ if(!(s instanceof Player p)||!admin(p)||a.length<1)return true; p.setGameMode(switch(a[0]){case"0"->GameMode.SURVIVAL;case"1"->GameMode.CREATIVE;case"2"->GameMode.ADVENTURE;case"3"->GameMode.SPECTATOR;default->p.getGameMode();}); return true; });
        getCommand("heal").setExecutor((s,c,l,a)->{ if(!adminSender(s))return true; Player t=target(s,a); if(t!=null){t.setHealth(t.getMaxHealth()); t.setFoodLevel(20);} return true; });
        getCommand("feed").setExecutor((s,c,l,a)->{ if(!adminSender(s))return true; Player t=target(s,a); if(t!=null){t.setFoodLevel(20); t.setSaturation(20);} return true; });
    }

    @EventHandler public void join(PlayerJoinEvent e){ Player p=e.getPlayer(); Bukkit.getScheduler().runTaskLater(this,()->{ if(!p.isOnline())return; loadInv(p, group(p.getWorld())); if(inLobby(p)) giveMenu(p); else removeMenu(p); applyRank(p); }, getConfig().getLong("settings.join-delay-ticks",20L)); }
    @EventHandler public void quit(PlayerQuitEvent e){ saveCurrent(e.getPlayer()); saveData(); }
    @EventHandler public void world(PlayerChangedWorldEvent e){ Player p=e.getPlayer(); String old=group(e.getFrom()), now=group(p.getWorld()); if(old.equals(now)) return; saveInv(p,old); Bukkit.getScheduler().runTaskLater(this,()->{ if(!p.isOnline())return; loadInv(p,now); if(inLobby(p))giveMenu(p); else removeMenu(p); },2L); }
    @EventHandler public void interact(PlayerInteractEvent e){ Action a=e.getAction(); if(a!=Action.RIGHT_CLICK_AIR&&a!=Action.RIGHT_CLICK_BLOCK)return; if(isMenu(e.getItem())){e.setCancelled(true);openMainMenu(e.getPlayer());return;} if(keyFromItem(e.getItem())!=null){e.setCancelled(true);e.getPlayer().sendMessage(msg("key-right-click-disabled"));} }

    @EventHandler public void invClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        if(title.equals(color(getConfig().getString("gui.menu-title")))){ e.setCancelled(true); action(p,e.getCurrentItem()); return; }
        if(title.equals(color(getConfig().getString("gui.keys-title")))){ e.setCancelled(true); keyAction(p,e.getCurrentItem()); return; }
        if(title.equals(color(getConfig().getString("gui.kits-title")))){ e.setCancelled(true); kitAction(p,e.getCurrentItem()); return; }
        if(inLobby(p)&&!getConfig().getBoolean("protection.lobby-inventory-click",false)&&!bypass(p)) e.setCancelled(true);
    }

    @EventHandler public void chat(AsyncPlayerChatEvent e){ String r=getRank(e.getPlayer()); e.setFormat(color(getConfig().getString("ranks."+r+".prefix","&7")) + "%1$s &8» &f%2$s"); }
    @EventHandler public void blockBreak(BlockBreakEvent e){ if(blocked(e.getPlayer(),"block-break")) e.setCancelled(true); }
    @EventHandler public void blockPlace(BlockPlaceEvent e){ if(blocked(e.getPlayer(),"block-place")) e.setCancelled(true); }
    @EventHandler public void drop(PlayerDropItemEvent e){ if(blocked(e.getPlayer(),"drop")) e.setCancelled(true); }
    @EventHandler public void pickup(PlayerPickupItemEvent e){ if(blocked(e.getPlayer(),"pickup")) e.setCancelled(true); }
    @EventHandler public void damage(EntityDamageEvent e){ if(e.getEntity() instanceof Player p && inLobby(p) && !getConfig().getBoolean("protection.lobby-damage",false)) e.setCancelled(true); }
    @EventHandler public void pvp(EntityDamageByEntityEvent e){ if(!getConfig().getBoolean("protection.lobby-pvp",false)){ if(e.getDamager() instanceof Player p && inLobby(p))e.setCancelled(true); if(e.getEntity() instanceof Player p && inLobby(p))e.setCancelled(true); } }
    @EventHandler public void food(FoodLevelChangeEvent e){ if(e.getEntity() instanceof Player p && inLobby(p)&&!getConfig().getBoolean("protection.lobby-hunger",false)){ e.setCancelled(true); p.setFoodLevel(20);} }

    private void openMainMenu(Player p){ Inventory inv=Bukkit.createInventory(null,27,color(getConfig().getString("gui.menu-title"))); fill(inv); inv.setItem(10,gui(Material.NETHER_STAR,"&e&lLobby","lobby")); inv.setItem(12,gui(Material.GRASS_BLOCK,"&a&lSurvival","survival")); inv.setItem(14,gui(Material.TRIPWIRE_HOOK,"&6&lKlucze","keys")); inv.setItem(16,gui(Material.CHEST,"&b&lKity","kits")); p.openInventory(inv); }
    private void action(Player p,ItemStack it){ String a=getAction(it); if(a==null)return; p.closeInventory(); if(a.equals("lobby"))toLobby(p,true); if(a.equals("survival"))toSurvival(p,true); if(a.equals("keys"))openKeysMenu(p); if(a.equals("kits"))openKitsMenu(p); }
    private void openKeysMenu(Player p){ Inventory inv=Bukkit.createInventory(null,54,color(getConfig().getString("gui.keys-title"))); fill(inv); inv.setItem(getConfig().getInt("weekly.slot",4),gui(parseMat(getConfig().getString("weekly.material")),"&a&lCotygodniowy klucz","weekly")); int slot=10; ConfigurationSection s=getConfig().getConfigurationSection("keys"); if(s!=null)for(String k:s.getKeys(false)){ inv.setItem(slot,gui(Material.TRIPWIRE_HOOK,display(k)+" &7("+getKeys(p.getName(),k)+")","withdraw:"+k)); slot++; if(slot==17||slot==26||slot==35)slot+=2; } p.openInventory(inv); }
    private void openKitsMenu(Player p){ Inventory inv=Bukkit.createInventory(null,27,color(getConfig().getString("gui.kits-title"))); fill(inv); ConfigurationSection s=getConfig().getConfigurationSection("kits"); if(s!=null)for(String k:s.getKeys(false)) inv.setItem(getConfig().getInt("kits."+k+".slot",13),gui(parseMat(getConfig().getString("kits."+k+".material","CHEST")),getConfig().getString("kits."+k+".name",k),"kit:"+k)); p.openInventory(inv); }
    private void keyAction(Player p,ItemStack it){ String a=getAction(it); if(a==null)return; p.closeInventory(); if(a.equals("weekly"))weekly(p); else if(a.startsWith("withdraw:")) withdraw(p,a.substring(9)); }
    private void kitAction(Player p,ItemStack it){ String a=getAction(it); if(a==null)return; p.closeInventory(); if(a.startsWith("kit:"))openKit(p,a.substring(4)); }

    private void weekly(Player p){ long cd=getConfig().getLong("weekly.cooldown-seconds")*1000L,last=data.getLong(path(p.getName())+".lastWeekly",0),left=cd-(System.currentTimeMillis()-last); if(last>0&&left>0){p.sendMessage(msg("cooldown").replace("%time%",time(left)));return;} String k=roll("weekly.random"); data.set(path(p.getName())+".lastWeekly",System.currentTimeMillis()); setKeys(p.getName(),k,getKeys(p.getName(),k)+1); p.sendMessage(msg("claimed").replace("%key%",display(k))); }
    private void withdraw(Player p,String k){ if(getKeys(p.getName(),k)<=0){p.sendMessage(msg("no-key").replace("%key%",display(k)));return;} setKeys(p.getName(),k,getKeys(p.getName(),k)-1); p.getInventory().addItem(keyItem(k,1)); p.sendMessage(msg("withdrawn").replace("%key%",display(k))); }
    private void openKit(Player p,String kit){ kit=norm(kit); if(inLobby(p)){p.sendMessage(color("&cKity otwieraj na survivalu."));return;} String req=getConfig().getString("kits."+kit+".required-key",kit); if(!takeKey(p,req)){p.sendMessage(msg("no-key").replace("%key%",display(req)));return;} String reward=kit; if(getConfig().contains("kits."+kit+".random-rewards"))reward=roll("kits."+kit+".random-rewards"); givePresetKit(p,reward); p.sendMessage(msg("opened").replace("%kit%",reward)); p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1,1); }
    private boolean takeKey(Player p,String k){ if(getKeys(p.getName(),k)>0){setKeys(p.getName(),k,getKeys(p.getName(),k)-1);return true;} ItemStack[] c=p.getInventory().getContents(); for(int i=0;i<c.length;i++) if(k.equals(keyFromItem(c[i]))){ if(c[i].getAmount()<=1)p.getInventory().setItem(i,null); else c[i].setAmount(c[i].getAmount()-1); return true;} return false; }

    private void givePresetKit(Player p,String kit){
        if(kit.equals("klasyczny")){ p.getInventory().addItem(new ItemStack(Material.BREAD,32),named(Material.STONE_SWORD,"&fMiecz Klasyczny"),named(Material.STONE_PICKAXE,"&fKilof Klasyczny"),named(Material.STONE_AXE,"&fSiekiera Klasyczna"),named(Material.STONE_SHOVEL,"&fŁopata Klasyczna"),named(Material.STONE_HOE,"&fMotyka Klasyczna")); return; }
        if(kit.equals("zelazny")){ armorTools(p,Material.IRON_HELMET,Material.IRON_CHESTPLATE,Material.IRON_LEGGINGS,Material.IRON_BOOTS,Material.IRON_SWORD,Material.IRON_PICKAXE,Material.IRON_AXE,Material.IRON_SHOVEL,Material.IRON_HOE,"&7Żelazny",1); p.getInventory().addItem(new ItemStack(Material.BOW),new ItemStack(Material.ARROW,32),new ItemStack(Material.GOLDEN_APPLE,2),new ItemStack(Material.SHIELD)); return; }
        if(kit.equals("diamentowy")){ armorTools(p,Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS,Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.DIAMOND_HOE,"&bDiamentowy",2); p.getInventory().addItem(enchant(Material.BOW,"&bŁuk Diamentowy",new String[][]{{"power","3"},{"unbreaking","2"}}),new ItemStack(Material.ARROW,64),new ItemStack(Material.GOLDEN_APPLE,6),new ItemStack(Material.SHIELD)); return; }
        if(kit.equals("epic")){ armorTools(p,Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS,Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.DIAMOND_HOE,"&5Epic",3); p.getInventory().addItem(enchant(Material.BOW,"&5Łuk Epic",new String[][]{{"power","4"},{"flame","1"},{"unbreaking","3"}}),new ItemStack(Material.GOLDEN_APPLE,12),new ItemStack(Material.TOTEM_OF_UNDYING)); return; }
        if(kit.equals("legendarny")){ armorTools(p,Material.NETHERITE_HELMET,Material.NETHERITE_CHESTPLATE,Material.NETHERITE_LEGGINGS,Material.NETHERITE_BOOTS,Material.NETHERITE_SWORD,Material.NETHERITE_PICKAXE,Material.NETHERITE_AXE,Material.NETHERITE_SHOVEL,Material.NETHERITE_HOE,"&6Legendarny",4); p.getInventory().addItem(enchant(Material.BOW,"&6Łuk Legendarny",new String[][]{{"power","5"},{"flame","1"},{"infinity","1"},{"unbreaking","3"}}),new ItemStack(Material.GOLDEN_APPLE,16),new ItemStack(Material.TOTEM_OF_UNDYING,2),enchant(Material.ELYTRA,"&6Elytry",new String[][]{{"unbreaking","3"},{"mending","1"}})); return; }
        if(kit.equals("mityczny")){ armorTools(p,Material.NETHERITE_HELMET,Material.NETHERITE_CHESTPLATE,Material.NETHERITE_LEGGINGS,Material.NETHERITE_BOOTS,Material.NETHERITE_SWORD,Material.NETHERITE_PICKAXE,Material.NETHERITE_AXE,Material.NETHERITE_SHOVEL,Material.NETHERITE_HOE,"&dMityczny",5); p.getInventory().addItem(enchant(Material.BOW,"&dŁuk Mityczny",new String[][]{{"power","5"},{"punch","2"},{"flame","1"},{"infinity","1"},{"unbreaking","3"},{"mending","1"}}),new ItemStack(Material.GOLDEN_APPLE,24),new ItemStack(Material.ENCHANTED_GOLDEN_APPLE,2),new ItemStack(Material.TOTEM_OF_UNDYING,4),enchant(Material.ELYTRA,"&dElytry",new String[][]{{"unbreaking","3"},{"mending","1"}})); return; }
        if(kit.equals("boski")) ownerSet(p);
    }
    private void armorTools(Player p,Material h,Material c,Material l,Material b,Material sw,Material pick,Material ax,Material sh,Material hoe,String n,int lvl){ p.getInventory().addItem(enchant(h,n+" Hełm",new String[][]{{"protection",String.valueOf(Math.min(4,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(c,n+" Napierśnik",new String[][]{{"protection",String.valueOf(Math.min(4,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(l,n+" Spodnie",new String[][]{{"protection",String.valueOf(Math.min(4,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(b,n+" Buty",new String[][]{{"protection",String.valueOf(Math.min(4,lvl))},{"feather_falling",String.valueOf(Math.min(4,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(sw,n+" Miecz",new String[][]{{"sharpness",String.valueOf(Math.min(5,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(pick,n+" Kilof",new String[][]{{"efficiency",String.valueOf(Math.min(5,lvl))},{"fortune","3"},{"unbreaking","3"},{"mending","1"}}),enchant(ax,n+" Siekiera",new String[][]{{"efficiency",String.valueOf(Math.min(5,lvl))},{"sharpness",String.valueOf(Math.min(5,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(sh,n+" Łopata",new String[][]{{"efficiency",String.valueOf(Math.min(5,lvl))},{"unbreaking","3"},{"mending","1"}}),enchant(hoe,n+" Motyka",new String[][]{{"efficiency",String.valueOf(Math.min(5,lvl))},{"unbreaking","3"},{"mending","1"}})); }
    private void ownerSet(Player p){ armorTools(p,Material.NETHERITE_HELMET,Material.NETHERITE_CHESTPLATE,Material.NETHERITE_LEGGINGS,Material.NETHERITE_BOOTS,Material.NETHERITE_SWORD,Material.NETHERITE_PICKAXE,Material.NETHERITE_AXE,Material.NETHERITE_SHOVEL,Material.NETHERITE_HOE,"&4&lBOSKI",5); p.getInventory().addItem(enchant(Material.BOW,"&e&lŁuk Boga",new String[][]{{"power","5"},{"punch","2"},{"flame","1"},{"infinity","1"},{"unbreaking","3"},{"mending","1"}}),enchant(Material.CROSSBOW,"&6&lKusza Boga",new String[][]{{"quick_charge","3"},{"multishot","1"},{"piercing","4"},{"unbreaking","3"},{"mending","1"}}),enchant(Material.TRIDENT,"&3&lTrójząb Boga",new String[][]{{"impaling","5"},{"loyalty","3"},{"channeling","1"},{"unbreaking","3"},{"mending","1"}}),new ItemStack(Material.GOLDEN_APPLE,64),new ItemStack(Material.ENCHANTED_GOLDEN_APPLE,16),new ItemStack(Material.TOTEM_OF_UNDYING,5),new ItemStack(Material.FIREWORK_ROCKET,64),enchant(Material.ELYTRA,"&f&lElytry Boga",new String[][]{{"unbreaking","3"},{"mending","1"}}),enchant(Material.SHIELD,"&6&lTarcza Boga",new String[][]{{"unbreaking","3"},{"mending","1"}})); Material mace=parseMat("MACE"); if(mace!=Material.STONE)p.getInventory().addItem(enchant(mace,"&4&lBuzdygan Boga",new String[][]{{"density","5"},{"breach","3"},{"unbreaking","3"},{"mending","1"}})); }

    private void toLobby(Player p,boolean m){ saveInv(p,group(p.getWorld())); loadInv(p,"lobby"); Location loc=loc("lobby"); if(loc!=null)p.teleport(loc); giveMenu(p); if(m)p.sendMessage(msg("lobby")); }
    private void toSurvival(Player p,boolean m){ saveInv(p,group(p.getWorld())); loadInv(p,"survival"); removeMenu(p); Location loc=getConfig().getBoolean("survival.use-bed-spawn")?p.getBedSpawnLocation():null; if(loc==null)loc=loc("survival"); if(loc!=null)p.teleport(loc); if(m)p.sendMessage(msg("survival")); }
    private String group(World w){ if(w!=null && w.getName().equalsIgnoreCase(getConfig().getString("worlds.lobby-world")))return"lobby"; return"survival"; }
    private void saveAll(){ for(Player p:Bukkit.getOnlinePlayers())saveInv(p,group(p.getWorld())); saveData(); }
    private void saveCurrent(Player p){ saveInv(p,group(p.getWorld())); }
    private void saveInv(Player p,String g){ try{ String pa="inventories."+p.getUniqueId()+"."+g; PlayerInventory inv=p.getInventory(); data.set(pa+".contents",ser(inv.getContents())); data.set(pa+".armor",ser(inv.getArmorContents())); data.set(pa+".offhand",ser(new ItemStack[]{inv.getItemInOffHand()})); data.set(pa+".level",p.getLevel()); data.set(pa+".exp",p.getExp()); }catch(Exception e){e.printStackTrace();} }
    private void loadInv(Player p,String g){ try{ String pa="inventories."+p.getUniqueId()+"."+g; p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getInventory().setItemInOffHand(null); if(data.contains(pa+".contents")){ p.getInventory().setContents(deser(data.getString(pa+".contents",""))); p.getInventory().setArmorContents(deser(data.getString(pa+".armor",""))); ItemStack[] off=deser(data.getString(pa+".offhand","")); if(off.length>0)p.getInventory().setItemInOffHand(off[0]); p.setLevel(data.getInt(pa+".level",0)); p.setExp((float)data.getDouble(pa+".exp",0)); } p.updateInventory(); }catch(Exception e){e.printStackTrace();} }

    private void setRank(org.bukkit.command.CommandSender s,String player,String rank,long exp,String time){ rank=norm(rank); if(!getConfig().contains("ranks."+rank)){s.sendMessage(color("&cNie ma rangi."));return;} OfflinePlayer op=Bukkit.getOfflinePlayer(player); data.set("ranks."+op.getUniqueId()+".name",player); data.set("ranks."+op.getUniqueId()+".rank",rank); data.set("ranks."+op.getUniqueId()+".expires",exp); saveData(); Player online=Bukkit.getPlayerExact(player); if(online!=null)applyRank(online); s.sendMessage((exp>0?msg("rank-temp").replace("%time%",time):msg("rank-set")).replace("%player%",player).replace("%rank%",rankDisplay(rank))); }
    private String getRank(Player p){ long exp=data.getLong("ranks."+p.getUniqueId()+".expires",0); if(exp>0&&exp<System.currentTimeMillis()){data.set("ranks."+p.getUniqueId(),null);saveData();} String r=data.getString("ranks."+p.getUniqueId()+".rank","gracz"); return getConfig().contains("ranks."+r)?r:"gracz"; }
    private void applyRank(Player p){ String r=getRank(p); String tab=color(getConfig().getString("ranks."+r+".tab-prefix","")+getConfig().getString("ranks."+r+".name-color","&7")+p.getName()); p.setPlayerListName(tab.length()>80?tab.substring(0,80):tab); updateBoard(p); for(Player v:Bukkit.getOnlinePlayers())addTag(v,p,r); }
    private void refreshAllRanks(){ for(Player p:Bukkit.getOnlinePlayers())applyRank(p); }
    private void updateBoard(Player p){ if(!getConfig().getBoolean("sidebar.enabled",true))return; Scoreboard b=p.getScoreboard(); if(b==null||b==Bukkit.getScoreboardManager().getMainScoreboard()){b=Bukkit.getScoreboardManager().getNewScoreboard();p.setScoreboard(b);} Objective old=b.getObjective("mscore"); if(old!=null)old.unregister(); Objective o=b.registerNewObjective("mscore","dummy",color(getConfig().getString("sidebar.title"))); o.setDisplaySlot(DisplaySlot.SIDEBAR); int sc=getConfig().getStringList("sidebar.lines").size(); Set<String> used=new HashSet<>(); for(String line:getConfig().getStringList("sidebar.lines")){ String l=color(line.replace("%player%",p.getName()).replace("%rank%",rankDisplay(getRank(p))).replace("%expires%",expires(p)).replace("%online%",String.valueOf(Bukkit.getOnlinePlayers().size())).replace("%world%",p.getWorld().getName())); while(used.contains(l))l+=ChatColor.RESET; used.add(l); o.getScore(l.length()>40?l.substring(0,40):l).setScore(sc--);} }
    private void addTag(Player viewer,Player target,String r){ Scoreboard b=viewer.getScoreboard(); if(b==null||b==Bukkit.getScoreboardManager().getMainScoreboard()){b=Bukkit.getScoreboardManager().getNewScoreboard();viewer.setScoreboard(b);} String tn=("r"+(999-getConfig().getInt("ranks."+r+".priority",0))+"_"+r); if(tn.length()>16)tn=tn.substring(0,16); Team t=b.getTeam(tn); if(t==null)t=b.registerNewTeam(tn); t.setPrefix(color(getConfig().getString("ranks."+r+".prefix"))); for(Team ot:b.getTeams())if(!ot.getName().equals(tn)&&ot.hasEntry(target.getName()))ot.removeEntry(target.getName()); if(!t.hasEntry(target.getName()))t.addEntry(target.getName()); }
    private String rankDisplay(String r){return color(getConfig().getString("ranks."+r+".display",r));}
    private String expires(Player p){long e=data.getLong("ranks."+p.getUniqueId()+".expires",0); return e<=0?"nigdy":time(e-System.currentTimeMillis());}
    private void sendRanks(org.bukkit.command.CommandSender s){ ConfigurationSection cs=getConfig().getConfigurationSection("ranks"); if(cs!=null) for(String r:cs.getKeys(false))s.sendMessage(color("&8- &e"+r+" &7=> "+getConfig().getString("ranks."+r+".prefix")));}

    private ItemStack keyItem(String k,int a){ ItemStack it=new ItemStack(parseMat(getConfig().getString("key-item.material")),a); ItemMeta m=it.getItemMeta(); m.setDisplayName(color(getConfig().getString("key-item.name").replace("%key_name%",display(k)))); ArrayList<String> lore=new ArrayList<>(); for(String line:getConfig().getStringList("key-item.lore"))lore.add(color(line.replace("%key_name%",display(k)).replace("%key%",k))); m.setLore(lore); m.addEnchant(Enchantment.UNBREAKING,1,true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS,ItemFlag.HIDE_ATTRIBUTES); m.getPersistentDataContainer().set(keyKey,PersistentDataType.STRING,k); it.setItemMeta(m); return it; }
    private String keyFromItem(ItemStack it){ if(it==null||!it.hasItemMeta())return null; return it.getItemMeta().getPersistentDataContainer().get(keyKey,PersistentDataType.STRING); }
    private int getKeys(String pl,String k){return data.getInt(path(pl)+".keys."+norm(k),0);}
    private void setKeys(String pl,String k,int a){data.set(path(pl)+".keys."+norm(k),Math.max(0,a));saveData();}
    private String path(String pl){return"players."+pl.toLowerCase(Locale.ROOT);}
    private String display(String k){return getConfig().getString("keys."+norm(k)+".display",k);}
    private String roll(String p){ ConfigurationSection s=getConfig().getConfigurationSection(p); if(s==null)return"klasyczny"; int total=0; for(String k:s.getKeys(false))total+=s.getInt(k); int r=random.nextInt(Math.max(1,total))+1,c=0; for(String k:s.getKeys(false)){c+=s.getInt(k); if(r<=c)return norm(k);} return"klasyczny"; }

    private ItemStack enchant(Material m,String name,String[][] es){ ItemStack it=named(m,name); for(String[] e:es){ Enchantment en=Enchantment.getByKey(NamespacedKey.minecraft(e[0])); if(en!=null)it.addUnsafeEnchantment(en,Integer.parseInt(e[1])); } return it; }
    private ItemStack gui(Material m,String name,String action){ ItemStack it=named(m,name); ItemMeta im=it.getItemMeta(); im.getPersistentDataContainer().set(actionKey,PersistentDataType.STRING,action); it.setItemMeta(im); return it; }
    private String getAction(ItemStack it){ if(it==null||!it.hasItemMeta())return null; return it.getItemMeta().getPersistentDataContainer().get(actionKey,PersistentDataType.STRING); }
    private void fill(Inventory inv){ ItemStack f=named(parseMat(getConfig().getString("gui.filler"))," "); for(int i=0;i<inv.getSize();i++)inv.setItem(i,f); }
    private ItemStack named(Material m,String n){ ItemStack it=new ItemStack(m); ItemMeta im=it.getItemMeta(); im.setDisplayName(color(n)); it.setItemMeta(im); return it; }
    private void giveMenu(Player p){ if(hasMenu(p))return; int slot=getConfig().getInt("menu-item.slot",4); p.getInventory().setItem(slot,menuItem());}
    private ItemStack menuItem(){ ItemStack it=named(parseMat(getConfig().getString("menu-item.material")),getConfig().getString("menu-item.name")); ItemMeta im=it.getItemMeta(); im.getPersistentDataContainer().set(menuKey,PersistentDataType.STRING,"1"); ArrayList<String> lore=new ArrayList<>(); for(String l:getConfig().getStringList("menu-item.lore"))lore.add(color(l)); im.setLore(lore); it.setItemMeta(im); return it;}
    private boolean hasMenu(Player p){ for(ItemStack i:p.getInventory().getContents())if(isMenu(i))return true; return false;}
    private boolean isMenu(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(menuKey,PersistentDataType.STRING);}
    private void removeMenu(Player p){ ItemStack[] c=p.getInventory().getContents(); for(int i=0;i<c.length;i++)if(isMenu(c[i]))p.getInventory().setItem(i,null); }
    private boolean inLobby(Player p){ return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("worlds.lobby-world")); }
    private boolean blocked(Player p,String k){ return inLobby(p)&&getConfig().getBoolean("protection.lobby-"+k,true)&&!bypass(p);}
    private boolean bypass(Player p){return p.hasPermission("msurvivalcore.admin")&&p.getGameMode()==GameMode.CREATIVE;}
    private boolean admin(Player p){ if(!p.hasPermission("msurvivalcore.admin")){p.sendMessage(msg("no-permission")); return false;} return true;}
    private boolean adminSender(org.bukkit.command.CommandSender s){ if(!s.hasPermission("msurvivalcore.admin")){s.sendMessage(msg("no-permission")); return false;} return true;}
    private boolean rankAdmin(org.bukkit.command.CommandSender s){ if(!s.hasPermission("msurvivalcore.ranks")){s.sendMessage(msg("no-permission")); return false;} return true;}
    private Player target(org.bukkit.command.CommandSender s,String[] a){ if(a.length>0)return Bukkit.getPlayerExact(a[0]); return s instanceof Player p?p:null; }
    private void saveLoc(String k,Location l){ getConfig().set(k+".x",l.getX());getConfig().set(k+".y",l.getY());getConfig().set(k+".z",l.getZ());getConfig().set(k+".yaw",l.getYaw());getConfig().set(k+".pitch",l.getPitch());saveConfig();}
    private Location loc(String k){ World w=Bukkit.getWorld(k.equals("lobby")?getConfig().getString("worlds.lobby-world"):getConfig().getString("worlds.survival-world")); if(w==null)return null; return new Location(w,getConfig().getDouble(k+".x"),getConfig().getDouble(k+".y"),getConfig().getDouble(k+".z"),(float)getConfig().getDouble(k+".yaw"),(float)getConfig().getDouble(k+".pitch")); }
    private String ser(ItemStack[] its)throws Exception{ ByteArrayOutputStream bo=new ByteArrayOutputStream(); ObjectOutputStream oo=new ObjectOutputStream(bo); oo.writeInt(its.length); for(ItemStack it:its)oo.writeObject(it); oo.close(); return Base64.getEncoder().encodeToString(bo.toByteArray());}
    private ItemStack[] deser(String s)throws Exception{ if(s==null||s.isBlank())return new ItemStack[0]; ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(s))); int len=in.readInt(); ItemStack[] out=new ItemStack[len]; for(int i=0;i<len;i++)out[i]=(ItemStack)in.readObject(); in.close(); return out;}
    private long parseDuration(String raw){ try{ long n=Long.parseLong(raw.substring(0,raw.length()-1)); char u=raw.charAt(raw.length()-1); return switch(u){case'm'->n*60000L;case'h'->n*3600000L;case'd'->n*86400000L;case'w'->n*604800000L;default->-1L;}; }catch(Exception e){return -1;}}
    private String time(long ms){ if(ms<=0)return"wygasła"; long s=ms/1000,d=s/86400; s%=86400; long h=s/3600; s%=3600; long m=s/60; if(d>0)return d+"d "+h+"h"; if(h>0)return h+"h "+m+"m"; return Math.max(1,m)+"m";}
    private int parseInt(String s){try{return Math.max(1,Integer.parseInt(s));}catch(Exception e){return 1;}}
    private Material parseMat(String s){try{return Material.valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){return Material.STONE;}}
    private String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}
    private String color(String s){return s==null?"":ChatColor.translateAlternateColorCodes('&',s);}
    private String msg(String k){return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,""));}
    private void loadData(){ dataFile=new File(getDataFolder(),"data.yml"); if(!dataFile.exists())try{getDataFolder().mkdirs();dataFile.createNewFile();}catch(Exception e){e.printStackTrace();} data=YamlConfiguration.loadConfiguration(dataFile);}
    private void saveData(){ try{data.save(dataFile);}catch(Exception e){e.printStackTrace();}}
}
