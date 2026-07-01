package pl.msurvival.compass;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public final class MSurvivalCompass extends JavaPlugin implements Listener {
    private NamespacedKey compassKey;
    private File dataFile;
    private YamlConfiguration data;

    public void onEnable() {
        saveDefaultConfig();
        compassKey = new NamespacedKey(this, "msurvival_compass");
        dataFile = new File(getDataFolder(), "data.yml");
        try { getDataFolder().mkdirs(); if (!dataFile.exists()) dataFile.createNewFile(); } catch(Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isLobby(p)) giveCompassSafely(p);
                else removeAllCompasses(p);
            }
        }, 40L, 100L);
    }

    public void onDisable() { saveData(); }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("compassreload")) {
            if (!sender.hasPermission("msurvival.compass.admin")) return true;
            reloadConfig(); sender.sendMessage(msg("reload")); return true;
        }
        if (!(sender instanceof Player p)) return true;

        if (cmd.equals("compassbypass")) {
            if (!p.hasPermission("msurvival.compass.bypass")) return true;
            if (args.length < 1) { p.sendMessage(color("&c/compassbypass <on|off>")); return true; }
            boolean enabled = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
            data.set("bypass." + p.getUniqueId(), enabled);
            saveData();
            if (enabled) { removeAllCompasses(p); p.sendMessage(msg("bypass-on")); }
            else { p.sendMessage(msg("bypass-off")); if (isLobby(p)) giveCompassSafely(p); }
            return true;
        }
        if (cmd.equals("lobby")) { toLobby(p); return true; }
        if (cmd.equals("survival")) { toSurvival(p); return true; }
        if (cmd.equals("menu")) { openMenu(p); return true; }
        return true;
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> { if (e.getPlayer().isOnline()) { if (isLobby(e.getPlayer())) giveCompassSafely(e.getPlayer()); else removeAllCompasses(e.getPlayer()); } }, 10L);
    }
    @EventHandler public void world(PlayerChangedWorldEvent e) { if (isLobby(e.getPlayer())) giveCompassSafely(e.getPlayer()); else removeAllCompasses(e.getPlayer()); }
    @EventHandler public void interact(PlayerInteractEvent e) { if (isCompass(e.getItem())) { e.setCancelled(true); openMenu(e.getPlayer()); } }
    @EventHandler public void drop(PlayerDropItemEvent e) { if (isCompass(e.getItemDrop().getItemStack())) e.setCancelled(true); }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTitle().equals(color(getConfig().getString("menu.title")))) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            p.closeInventory();
            if (name.contains("survival")) toSurvival(p);
            else if (name.contains("klucze")) p.performCommand("keysmenu");
            else if (name.contains("kity")) p.performCommand("kits");
            else if (name.contains("rynek")) p.performCommand("rynek");
            return;
        }
        if (isCompass(e.getCurrentItem()) || isCompass(e.getCursor())) e.setCancelled(true);
    }

    private void toLobby(Player p) {
        World w = Bukkit.getWorld(getConfig().getString("worlds.lobby", "Lobby"));
        if (w == null) { p.sendMessage(msg("no-world")); return; }
        p.teleport(w.getSpawnLocation());
        Bukkit.getScheduler().runTaskLater(this, () -> giveCompassSafely(p), 5L);
        p.sendMessage(msg("lobby"));
    }

    private void toSurvival(Player p) {
        removeAllCompasses(p);
        World w = Bukkit.getWorld(getConfig().getString("worlds.survival", "world"));
        if (w == null) { p.sendMessage(msg("no-world")); return; }
        p.teleport(w.getSpawnLocation());
        p.sendMessage(msg("survival"));
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("menu.title", "&6&lMSURVIVAL MENU")));
        ItemStack glass = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i=0;i<inv.getSize();i++) inv.setItem(i, glass);
        inv.setItem(10, named(Material.GRASS_BLOCK, getConfig().getString("menu.survival-name")));
        inv.setItem(12, named(Material.TRIPWIRE_HOOK, getConfig().getString("menu.keys-name")));
        inv.setItem(14, named(Material.CHEST, getConfig().getString("menu.kits-name")));
        inv.setItem(16, named(Material.GOLD_INGOT, getConfig().getString("menu.market-name")));
        p.openInventory(inv);
    }

    private void giveCompassSafely(Player p) {
        removeAllCompasses(p);
        if (data.getBoolean("bypass." + p.getUniqueId(), false)) return;
        ItemStack compass = compass();
        int slot = getConfig().getInt("compass.slot", 4);
        if (slot >= 0 && slot < p.getInventory().getSize()) {
            ItemStack current = p.getInventory().getItem(slot);
            if (current == null || current.getType() == Material.AIR) { p.getInventory().setItem(slot, compass); return; }
        }
        int empty = p.getInventory().firstEmpty();
        if (empty != -1) p.getInventory().setItem(empty, compass);
    }

    private void removeAllCompasses(Player p) {
        boolean removeNormal = getConfig().getBoolean("compass.remove-normal-compasses-in-lobby", true);
        ItemStack[] contents = p.getInventory().getContents();
        for (int i=0;i<contents.length;i++) {
            ItemStack item = contents[i];
            if (isCompass(item) || (removeNormal && isLobby(p) && item != null && item.getType() == Material.COMPASS)) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    private ItemStack compass() {
        Material material = Material.valueOf(getConfig().getString("compass.material", "COMPASS"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("compass.name", "&6&lMSurvival Menu")));
        ArrayList<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList("compass.lore")) lore.add(color(line));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCompass(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }
    private boolean isLobby(Player p) { return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("worlds.lobby", "Lobby")); }
    private ItemStack named(Material mat, String name) { ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(color(name)); item.setItemMeta(meta); return item; }
    private void saveData() { try { data.save(dataFile); } catch(Exception ignored) {} }
    private String msg(String key) { return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
