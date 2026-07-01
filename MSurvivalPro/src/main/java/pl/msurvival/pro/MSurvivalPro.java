package pl.msurvival.pro;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalPro extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey actionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "pro_action");
        dataFile = new File(getDataFolder(), "data.yml");
        try { getDataFolder().mkdirs(); if (!dataFile.exists()) dataFile.createNewFile(); } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("broadcastpro") || cmd.equals("alert")) {
            if (!sender.hasPermission("msurvival.pro.admin")) { sender.sendMessage(msg("no-permission")); return true; }
            if (args.length < 1) return true;
            String message = String.join(" ", args);
            for (String line : getConfig().getStringList("messages.alert-format")) Bukkit.broadcastMessage(color(line.replace("%message%", message)));
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        switch (cmd) {
            case "mspro" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    if (!p.hasPermission("msurvival.pro.admin")) { p.sendMessage(msg("no-permission")); return true; }
                    reloadConfig();
                    data = YamlConfiguration.loadConfiguration(dataFile);
                    p.sendMessage(msg("reload"));
                    return true;
                }
                openMain(p);
            }
            case "profil" -> openProfile(p);
            case "ustawienia" -> openSettings(p);
            case "regulamin" -> sendList(p, "regulamin");
            case "media" -> sendList(p, "media");
            case "donate" -> sendList(p, "donate");
            case "event" -> p.performCommand("mspro");
            case "setwarp" -> {
                if (!p.hasPermission("msurvival.pro.admin")) { p.sendMessage(msg("no-permission")); return true; }
                if (args.length < 1) return true;
                saveLocation("warps." + clean(args[0]), p.getLocation());
                p.sendMessage(msg("warp-set").replace("%warp%", clean(args[0])));
            }
            case "delwarp" -> {
                if (!p.hasPermission("msurvival.pro.admin")) { p.sendMessage(msg("no-permission")); return true; }
                if (args.length < 1) return true;
                data.set("warps." + clean(args[0]), null);
                saveData();
                p.sendMessage(msg("warp-deleted").replace("%warp%", clean(args[0])));
            }
            case "warp" -> {
                if (args.length < 1) { openWarps(p); return true; }
                Location loc = loadLocation("warps." + clean(args[0]));
                if (loc == null) p.sendMessage(msg("warp-not-found"));
                else { p.teleport(loc); p.sendMessage(msg("teleported")); }
            }
            case "warpy" -> openWarps(p);
        }
        return true;
    }

    private void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, color(getConfig().getString("menus.main-title")));
        fill(inv);
        inv.setItem(10, item(Material.PLAYER_HEAD, "&e&lProfil", "profile", List.of("&7Twoje informacje i skróty.")));
        inv.setItem(11, item(Material.COMPASS, "&a&lWarpy", "warps", List.of("&7Szybka teleportacja.")));
        inv.setItem(12, item(Material.GOLD_INGOT, "&6&lRynek", "market", List.of("&7Sklep serwera i graczy.")));
        inv.setItem(13, item(Material.TRIPWIRE_HOOK, "&b&lKlucze", "keys", List.of("&7Klucze i E-Kity.")));
        inv.setItem(14, item(Material.BOOK, "&c&lRegulamin", "rules", List.of("&7Zasady serwera.")));
        inv.setItem(15, item(Material.NETHER_STAR, "&d&lMedia", "media", List.of("&7YouTube, TikTok, Discord.")));
        inv.setItem(16, item(Material.HEART_OF_THE_SEA, "&d&lDonate", "donate", List.of("&7Wsparcie serwera.")));
        inv.setItem(31, item(Material.REDSTONE, "&b&lUstawienia", "settings", List.of("&7Opcje gracza.")));
        p.openInventory(inv);
    }

    private void openProfile(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("menus.profile-title")));
        fill(inv);
        inv.setItem(11, item(Material.NAME_TAG, "&eNick: &f" + p.getName(), "none", List.of("&7Ping: &a" + p.getPing() + "ms")));
        inv.setItem(13, item(Material.GOLD_INGOT, "&aKasa", "money", List.of("&7Sprawdź: &e/kasa")));
        inv.setItem(15, item(Material.CHEST, "&6Rynek", "market", List.of("&7Otwórz rynek.")));
        p.openInventory(inv);
    }

    private void openSettings(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("menus.settings-title")));
        fill(inv);
        inv.setItem(11, item(Material.COMPASS, "&6Kompas bypass", "compass_bypass", List.of("&7Włącz/wyłącz kompas:", "&e/compassbypass on/off")));
        inv.setItem(13, item(Material.BELL, "&ePowiadomienia", "none", List.of("&7Wkrótce.")));
        inv.setItem(15, item(Material.MAP, "&aWarpy", "warps", List.of("&7Otwórz warpy.")));
        p.openInventory(inv);
    }

    private void openWarps(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lWARPY"));
        fill(inv);
        if (data.getConfigurationSection("warps") != null) {
            int slot = 0;
            for (String warp : data.getConfigurationSection("warps").getKeys(false)) {
                if (slot >= 45) break;
                inv.setItem(slot++, item(Material.ENDER_PEARL, "&e&l" + warp, "warp:" + warp, List.of("&7Kliknij, aby się teleportować.")));
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.equals(color(getConfig().getString("menus.main-title"))) &&
            !title.equals(color(getConfig().getString("menus.profile-title"))) &&
            !title.equals(color(getConfig().getString("menus.settings-title"))) &&
            !title.equals(color("&6&lWARPY"))) return;

        e.setCancelled(true);
        String action = action(e.getCurrentItem());
        if (action == null || action.equals("none")) return;

        p.closeInventory();
        if (action.equals("profile")) openProfile(p);
        else if (action.equals("settings")) openSettings(p);
        else if (action.equals("warps")) openWarps(p);
        else if (action.equals("market")) p.performCommand("rynek");
        else if (action.equals("keys")) p.performCommand("keysmenu");
        else if (action.equals("rules")) p.performCommand("regulamin");
        else if (action.equals("media")) p.performCommand("media");
        else if (action.equals("donate")) p.performCommand("donate");
        else if (action.equals("compass_bypass")) p.performCommand("compassbypass on");
        else if (action.startsWith("warp:")) p.performCommand("warp " + action.substring(5));
    }

    private ItemStack item(Material mat, String name, String action, List<String> loreRaw) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> lore = new ArrayList<>();
        for (String line : loreRaw) lore.add(color(line));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ", "none", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
    }

    private String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void sendList(Player p, String path) {
        for (String line : getConfig().getStringList(path)) p.sendMessage(color(line));
    }

    private void saveLocation(String path, Location loc) {
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", loc.getYaw());
        data.set(path + ".pitch", loc.getPitch());
        saveData();
    }

    private Location loadLocation(String path) {
        String worldName = data.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch"));
    }

    private String clean(String s) {
        return s == null ? "" : s.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private void saveData() {
        try { data.save(dataFile); } catch (Exception ignored) {}
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
