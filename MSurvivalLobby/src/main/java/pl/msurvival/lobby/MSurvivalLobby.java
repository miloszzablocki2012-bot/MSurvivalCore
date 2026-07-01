package pl.msurvival.lobby;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Lobby protection module. Compass/menu ownership belongs to MSurvivalCompass when that plugin is installed.
 * This keeps old standalone behavior only as fallback, so a second compass is never created.
 */
public final class MSurvivalLobby extends JavaPlugin implements Listener {
    private NamespacedKey key;

    @Override public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "lobby_item");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("setlobby")) {
            if (!player.hasPermission("msurvival.lobby.admin")) return deny(player);
            saveLoc("lobby", player.getLocation());
            player.sendMessage(msg("set-lobby"));
            return true;
        }
        if (name.equals("setsurvival")) {
            if (!player.hasPermission("msurvival.lobby.admin")) return deny(player);
            saveLoc("survival", player.getLocation());
            player.sendMessage(msg("set-survival"));
            return true;
        }
        return true;
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            if (compassPluginActive()) return;
            toLobbyFallback(player);
        }, 10L);
    }

    private void toLobbyFallback(Player player) {
        Location target = loc("lobby", getConfig().getString("worlds.lobby", "Lobby"));
        if (target != null) player.teleport(target);
        removeFallbackCompass(player);
        giveFallbackCompass(player);
        player.setFoodLevel(20);
        player.sendMessage(msg("lobby"));
    }

    private void giveFallbackCompass(Player player) {
        if (compassPluginActive()) return;
        removeFallbackCompass(player);
        int slot = Math.max(0, Math.min(35, getConfig().getInt("items.compass-slot", 4)));
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir()) player.getInventory().setItem(slot, compass());
        else {
            int empty = player.getInventory().firstEmpty();
            if (empty >= 0) player.getInventory().setItem(empty, compass());
        }
    }

    private ItemStack compass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("items.compass-name", "&6&lMenu")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "1");
        item.setItemMeta(meta);
        return item;
    }

    private boolean lobbyItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    private void removeFallbackCompass(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (lobbyItem(contents[i])) player.getInventory().setItem(i, null);
    }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (compassPluginActive()) return;
        if (lobbyItem(event.getItem())) {
            event.setCancelled(true);
            openFallbackMenu(event.getPlayer());
        }
    }

    private void openFallbackMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&6&lMSURVIVAL MENU"));
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);
        inv.setItem(10, item(Material.NETHER_STAR, "&e&lLobby"));
        inv.setItem(12, item(Material.GRASS_BLOCK, "&a&lSurvival"));
        inv.setItem(14, item(Material.TRIPWIRE_HOOK, "&6&lKlucze"));
        inv.setItem(16, item(Material.CHEST, "&b&lE-Kity"));
        player.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!compassPluginActive() && event.getView().getTitle().equals(color("&6&lMSURVIVAL MENU"))) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            player.closeInventory();
            if (name.contains("lobby")) toLobbyFallback(player);
            else if (name.contains("survival")) player.performCommand("survival");
            else if (name.contains("klucze")) player.performCommand("keysmenu");
            else if (name.contains("e-kity")) player.performCommand("kits");
            return;
        }
        if (inLobby(player)) event.setCancelled(true);
    }

    @EventHandler public void blockBreak(BlockBreakEvent event) { if (inLobby(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void blockPlace(BlockPlaceEvent event) { if (inLobby(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void drop(PlayerDropItemEvent event) { if (inLobby(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void damage(EntityDamageEvent event) { if (event.getEntity() instanceof Player player && inLobby(player)) event.setCancelled(true); }
    @EventHandler public void food(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player player && inLobby(player)) { event.setCancelled(true); player.setFoodLevel(20); } }

    private boolean inLobby(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(getConfig().getString("worlds.lobby", "Lobby"));
    }

    private boolean compassPluginActive() {
        return Bukkit.getPluginManager().isPluginEnabled("MSurvivalCompass");
    }

    private ItemStack item(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private void saveLoc(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());
        saveConfig();
    }

    private Location loc(String path, String worldName) {
        String savedWorld = getConfig().getString(path + ".world", worldName);
        World world = Bukkit.getWorld(savedWorld);
        return world == null ? null : new Location(world, getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"), getConfig().getDouble(path + ".z"), (float) getConfig().getDouble(path + ".yaw"), (float) getConfig().getDouble(path + ".pitch"));
    }

    private boolean deny(Player player) { player.sendMessage(msg("no-permission")); return true; }
    private String msg(String key) { return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
