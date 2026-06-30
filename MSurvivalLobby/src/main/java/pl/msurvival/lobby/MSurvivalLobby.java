package pl.msurvival.lobby;

import org.bukkit.*;
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

import java.util.ArrayList;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class MSurvivalLobby extends JavaPlugin implements Listener {
    private NamespacedKey menuKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        menuKey = new NamespacedKey(this, "menu_item");
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("lobby").setExecutor((s,c,l,a)->{ if(s instanceof Player p) toLobby(p); return true; });
        getCommand("survival").setExecutor((s,c,l,a)->{ if(s instanceof Player p) toSurvival(p); return true; });
        getCommand("menu").setExecutor((s,c,l,a)->{ if(s instanceof Player p) openMenu(p); return true; });

        getCommand("setlobby").setExecutor((s,c,l,a)->{
            if (!(s instanceof Player p)) return true;
            if (!p.hasPermission("msurvival.lobby.admin")) { p.sendMessage(msg("no-permission")); return true; }
            saveLocation("lobby-spawn", p.getLocation());
            p.sendMessage(msg("lobby-set"));
            return true;
        });

        getCommand("setsurvival").setExecutor((s,c,l,a)->{
            if (!(s instanceof Player p)) return true;
            if (!p.hasPermission("msurvival.lobby.admin")) { p.sendMessage(msg("no-permission")); return true; }
            saveLocation("survival-spawn", p.getLocation());
            p.sendMessage(msg("survival-set"));
            return true;
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!e.getPlayer().isOnline()) return;
            toLobby(e.getPlayer());
        }, 10L);
    }

    private void toLobby(Player p) {
        Location loc = loc("lobby-spawn", getConfig().getString("worlds.lobby", "Lobby"));
        if (loc != null) p.teleport(loc);

        if (getConfig().getBoolean("lobby-items.clear-on-lobby-join", true)) p.getInventory().clear();
        giveLobbyItems(p);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.sendMessage(msg("lobby"));
    }

    private void toSurvival(Player p) {
        removeLobbyItems(p);
        Location loc = p.getBedSpawnLocation();
        if (loc == null) loc = loc("survival-spawn", getConfig().getString("worlds.survival", "world"));
        if (loc != null) p.teleport(loc);
        p.sendMessage(msg("survival"));
    }

    private boolean inLobby(Player p) {
        return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("worlds.lobby", "Lobby"));
    }

    private void giveLobbyItems(Player p) {
        if (!getConfig().getBoolean("lobby-items.enabled", true)) return;
        p.getInventory().setItem(getConfig().getInt("lobby-items.compass-slot", 4), menuItem());
    }

    private ItemStack menuItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("lobby-items.compass-name", "&6Menu")));
        ArrayList<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList("lobby-items.compass-lore")) lore.add(color(line));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    private boolean isLobbyItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.STRING);
    }

    private void removeLobbyItems(Player p) {
        ItemStack[] items = p.getInventory().getContents();
        for (int i = 0; i < items.length; i++) if (isLobbyItem(items[i])) p.getInventory().setItem(i, null);
    }

    @EventHandler public void interact(PlayerInteractEvent e) {
        if (isLobbyItem(e.getItem())) {
            e.setCancelled(true);
            openMenu(e.getPlayer());
        }
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&6&lMSURVIVAL MENU"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i=0;i<27;i++) inv.setItem(i, filler);
        inv.setItem(11, item(Material.NETHER_STAR, "&e&lLobby"));
        inv.setItem(13, item(Material.GRASS_BLOCK, "&a&lSurvival"));
        inv.setItem(15, item(Material.TRIPWIRE_HOOK, "&6&lKlucze i E-Kity"));
        p.openInventory(inv);
    }

    @EventHandler public void menuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(color("&6&lMSURVIVAL MENU"))) {
            if (inLobby(p) && getConfig().getBoolean("protection.inventory-move", true)) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
        p.closeInventory();
        if (name.contains("lobby")) toLobby(p);
        else if (name.contains("survival")) toSurvival(p);
        else p.performCommand("kits");
    }

    @EventHandler public void breakBlock(BlockBreakEvent e){ if(inLobby(e.getPlayer()) && getConfig().getBoolean("protection.block-break", true)) e.setCancelled(true); }
    @EventHandler public void placeBlock(BlockPlaceEvent e){ if(inLobby(e.getPlayer()) && getConfig().getBoolean("protection.block-place", true)) e.setCancelled(true); }
    @EventHandler public void drop(PlayerDropItemEvent e){ if(inLobby(e.getPlayer()) && getConfig().getBoolean("protection.drop", true)) e.setCancelled(true); }
    @EventHandler public void pickup(PlayerPickupItemEvent e){ if(inLobby(e.getPlayer()) && getConfig().getBoolean("protection.pickup", true)) e.setCancelled(true); }
    @EventHandler public void hunger(FoodLevelChangeEvent e){ if(e.getEntity() instanceof Player p && inLobby(p) && !getConfig().getBoolean("protection.hunger", false)){ e.setCancelled(true); p.setFoodLevel(20); } }
    @EventHandler public void damage(EntityDamageEvent e){ if(e.getEntity() instanceof Player p && inLobby(p) && !getConfig().getBoolean("protection.damage", false)) e.setCancelled(true); }
    @EventHandler public void pvp(EntityDamageByEntityEvent e){ if(!getConfig().getBoolean("protection.pvp", false)){ if(e.getDamager() instanceof Player p && inLobby(p)) e.setCancelled(true); if(e.getEntity() instanceof Player p && inLobby(p)) e.setCancelled(true); } }

    private ItemStack item(Material m, String name) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private void saveLocation(String path, Location loc) {
        getConfig().set(path + ".x", loc.getX());
        getConfig().set(path + ".y", loc.getY());
        getConfig().set(path + ".z", loc.getZ());
        getConfig().set(path + ".yaw", loc.getYaw());
        getConfig().set(path + ".pitch", loc.getPitch());
        saveConfig();
    }

    private Location loc(String path, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, getConfig().getDouble(path+".x"), getConfig().getDouble(path+".y"), getConfig().getDouble(path+".z"), (float)getConfig().getDouble(path+".yaw"), (float)getConfig().getDouble(path+".pitch"));
    }

    private String msg(String key){ return color(getConfig().getString("messages.prefix","") + getConfig().getString("messages."+key,"")); }
    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
