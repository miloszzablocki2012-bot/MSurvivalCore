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
    private boolean saveQueued;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        compassKey = new NamespacedKey(this, "msurvival_compass");
        dataFile = new File(getDataFolder(), "data.yml");
        try {
            getDataFolder().mkdirs();
            if (!dataFile.exists()) dataFile.createNewFile();
        } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("compass.enabled", true)) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isLobby(player)) giveCompassSafely(player);
                else removeAllCompasses(player);
            }
        }, 40L, 100L);
    }

    @Override
    public void onDisable() {
        try { data.save(dataFile); } catch (Exception ignored) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("compassreload")) {
            if (!sender.hasPermission("msurvival.compass.admin")) return true;
            reloadConfig();
            data = YamlConfiguration.loadConfiguration(dataFile);
            sender.sendMessage(msg("reload"));
            return true;
        }

        if (!(sender instanceof Player player)) return true;

        if (cmd.equals("setmsspawn")) {
            if (!player.hasPermission("msurvival.compass.admin")) return true;
            if (args.length < 1 || (!args[0].equalsIgnoreCase("lobby") && !args[0].equalsIgnoreCase("survival"))) {
                player.sendMessage(color("&c/setmsspawn <lobby|survival>"));
                return true;
            }
            saveSpawn(args[0].toLowerCase(Locale.ROOT), player.getLocation());
            player.sendMessage(msg("spawn-set")
                    .replace("%type%", args[0].toLowerCase(Locale.ROOT))
                    .replace("%world%", player.getWorld().getName()));
            return true;
        }

        if (cmd.equals("msspawns")) {
            if (!player.hasPermission("msurvival.compass.admin")) return true;
            sendSpawnInfo(player, "lobby");
            sendSpawnInfo(player, "survival");
            return true;
        }

        if (cmd.equals("compassbypass")) {
            if (!player.hasPermission("msurvival.compass.bypass")) return true;
            if (args.length < 1) {
                player.sendMessage(color("&c/compassbypass <on|off>"));
                return true;
            }
            boolean enabled = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
            data.set("bypass." + player.getUniqueId(), enabled);
            saveData();
            if (enabled) {
                removeAllCompasses(player);
                player.sendMessage(msg("bypass-on"));
            } else {
                player.sendMessage(msg("bypass-off"));
                if (isLobby(player)) giveCompassSafely(player);
            }
            return true;
        }

        if (cmd.equals("lobby")) {
            teleportNamed(player, "lobby", true);
            return true;
        }

        if (cmd.equals("survival")) {
            teleportNamed(player, "survival", true);
            return true;
        }

        if (cmd.equals("menu")) {
            openMenu(player);
            return true;
        }

        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            if (isLobby(player)) giveCompassSafely(player);
            else removeAllCompasses(player);
        }, 20L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (isLobby(player)) giveCompassSafely(player);
            else removeAllCompasses(player);
        }, 5L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isCompass(event.getItem())) {
            event.setCancelled(true);
            openMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isCompass(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTitle().equals(color(getConfig().getString("menu.title")))) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            player.closeInventory();

            if (name.contains("survival")) teleportNamed(player, "survival", true);
            else if (name.contains("klucze")) player.performCommand("keysmenu");
            else if (name.contains("kity")) player.performCommand("kits");
            else if (name.contains("rynek")) player.performCommand("rynek");
            return;
        }

        if (isCompass(event.getCurrentItem()) || isCompass(event.getCursor())) event.setCancelled(true);
    }

    private void teleportNamed(Player player, String type, boolean message) {
        if (type.equals("survival")) removeAllCompasses(player);

        Location target = getSpawn(type);
        if (target == null) {
            String worldName = getConfig().getString("worlds." + type, type.equals("lobby") ? "Lobby" : "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(msg("no-world").replace("%world%", worldName));
                return;
            }
            target = centered(world.getSpawnLocation());
        }

        final Location finalTarget = centered(target);
        player.teleport(finalTarget);

        if (getConfig().getBoolean("teleport.double-teleport-fix", true)) {
            long delay = getConfig().getLong("teleport.second-teleport-delay-ticks", 8L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) player.teleport(finalTarget);
            }, Math.max(1L, delay));
        }

        if (type.equals("lobby")) {
            Bukkit.getScheduler().runTaskLater(this, () -> giveCompassSafely(player), 10L);
            if (message) player.sendMessage(msg("lobby"));
        } else {
            Bukkit.getScheduler().runTaskLater(this, () -> removeAllCompasses(player), 10L);
            if (message) player.sendMessage(msg("survival"));
        }
    }

    private Location centered(Location location) {
        if (!getConfig().getBoolean("teleport.safe-center-block", true)) return location;
        Location clone = location.clone();
        clone.setX(clone.getBlockX() + 0.5);
        clone.setZ(clone.getBlockZ() + 0.5);
        return clone;
    }

    private void saveSpawn(String type, Location location) {
        String path = "spawns." + type;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
        saveData();
    }

    private Location getSpawn(String type) {
        String path = "spawns." + type;
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

    private void sendSpawnInfo(Player player, String type) {
        Location loc = getSpawn(type);
        if (loc == null) {
            player.sendMessage(msg("spawn-missing").replace("%type%", type));
            return;
        }
        player.sendMessage(color("&6" + type + " &8» &e" + loc.getWorld().getName() + " &7"
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("menu.title", "&6&lMSURVIVAL MENU")));
        ItemStack glass = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
        inv.setItem(10, named(Material.GRASS_BLOCK, getConfig().getString("menu.survival-name")));
        inv.setItem(12, named(Material.TRIPWIRE_HOOK, getConfig().getString("menu.keys-name")));
        inv.setItem(14, named(Material.CHEST, getConfig().getString("menu.kits-name")));
        inv.setItem(16, named(Material.GOLD_INGOT, getConfig().getString("menu.market-name")));
        player.openInventory(inv);
    }

    private void giveCompassSafely(Player player) {
        if (!getConfig().getBoolean("compass.enabled", true)) return;
        removeAllCompasses(player);
        if (data.getBoolean("bypass." + player.getUniqueId(), false)) return;

        ItemStack compass = compass();
        int slot = getConfig().getInt("compass.slot", 4);

        if (slot >= 0 && slot < 36) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                player.getInventory().setItem(slot, compass);
                return;
            }
        }

        int empty = player.getInventory().firstEmpty();
        if (empty != -1) {
            player.getInventory().setItem(empty, compass);
            return;
        }

        if (player.getInventory().getItemInOffHand().getType() == Material.AIR) {
            player.getInventory().setItemInOffHand(compass);
        }
    }

    private void removeAllCompasses(Player player) {
        boolean removeNormal = getConfig().getBoolean("compass.remove-normal-compasses-in-lobby", false);
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isCompass(item) || (removeNormal && isLobby(player) && item != null && item.getType() == Material.COMPASS)) {
                player.getInventory().setItem(i, null);
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
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }

    private boolean isLobby(Player player) {
        String lobbyWorld = getConfig().getString("worlds.lobby", "Lobby");
        return player.getWorld().getName().equalsIgnoreCase(lobbyWorld);
    }

    private ItemStack named(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private void saveData() {
        if (!isEnabled()) {
            try { data.save(dataFile); } catch (Exception ignored) {}
            return;
        }
        if (saveQueued) return;
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            saveQueued = false;
            String snapshot = data.saveToString();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try { java.nio.file.Files.writeString(dataFile.toPath(), snapshot, java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ignored) {}
            });
        }, 20L);
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
