package pl.msurvival.inventorycore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;

public final class MSurvivalInventoryCore extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey compassKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        compassKey = new NamespacedKey(this, "lobby_compass");
        Bukkit.getPluginManager().registerEvents(this, this);
        commands();

        long ticks = Math.max(5, getConfig().getLong("settings.autosave-seconds", 20)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, ticks, ticks);
    }

    @Override
    public void onDisable() {
        saveAll();
        saveData();
    }

    private void commands() {
        getCommand("invcore").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvivalinventorycore.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(color("&e/invcore reload/save/load/status"));
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(msg("reload"));
                return true;
            }

            if (!(sender instanceof Player p)) return true;

            if (args[0].equalsIgnoreCase("save")) {
                saveCurrent(p);
                saveData();
                p.sendMessage(msg("saved"));
                return true;
            }

            if (args[0].equalsIgnoreCase("load")) {
                loadInventory(p, group(p.getWorld()));
                p.sendMessage(msg("loaded"));
                return true;
            }

            if (args[0].equalsIgnoreCase("status")) {
                p.sendMessage(msg("status").replace("%group%", group(p.getWorld())));
                return true;
            }

            return true;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        Player p = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            String g = group(p.getWorld());
            loadInventory(p, g);
            if (manageCompass() && isLobby(g)) giveCompass(p);
            if (manageCompass() && !isLobby(g)) removeCompass(p);
        }, getConfig().getLong("settings.join-load-delay-ticks", 40L));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled()) return;
        saveCurrent(event.getPlayer());
        saveData();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!enabled()) return;

        Player p = event.getPlayer();
        String oldGroup = group(event.getFrom());
        String newGroup = group(p.getWorld());

        if (oldGroup.equalsIgnoreCase(newGroup)) return;

        saveInventory(p, oldGroup);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            loadInventory(p, newGroup);
            if (manageCompass() && isLobby(newGroup)) giveCompass(p);
            if (manageCompass() && !isLobby(newGroup)) removeCompass(p);
        }, 2L);
    }

    private void saveAll() {
        if (!enabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) saveCurrent(p);
        saveData();
    }

    private void saveCurrent(Player p) {
        saveInventory(p, group(p.getWorld()));
    }

    private void saveInventory(Player p, String group) {
        try {
            String path = "players." + p.getUniqueId() + "." + group;
            PlayerInventory inv = p.getInventory();

            data.set(path + ".name", p.getName());
            data.set(path + ".contents", serialize(inv.getContents()));
            data.set(path + ".armor", serialize(inv.getArmorContents()));
            data.set(path + ".offhand", serialize(new ItemStack[]{inv.getItemInOffHand()}));
            data.set(path + ".level", p.getLevel());
            data.set(path + ".exp", p.getExp());
            data.set(path + ".totalExp", p.getTotalExperience());
            data.set(path + ".food", p.getFoodLevel());
            data.set(path + ".saturation", p.getSaturation());
            data.set(path + ".health", p.getHealth());
        } catch (Exception e) {
            getLogger().warning("Nie zapisano ekwipunku: " + p.getName() + " / " + group);
            e.printStackTrace();
        }
    }

    private void loadInventory(Player p, String group) {
        try {
            String path = "players." + p.getUniqueId() + "." + group;
            PlayerInventory inv = p.getInventory();

            inv.clear();
            inv.setArmorContents(null);
            inv.setItemInOffHand(null);

            if (!data.contains(path + ".contents")) {
                p.updateInventory();
                return;
            }

            inv.setContents(deserialize(data.getString(path + ".contents", "")));
            inv.setArmorContents(deserialize(data.getString(path + ".armor", "")));

            ItemStack[] offhand = deserialize(data.getString(path + ".offhand", ""));
            if (offhand.length > 0) inv.setItemInOffHand(offhand[0]);

            p.setLevel(data.getInt(path + ".level", 0));
            p.setExp((float) data.getDouble(path + ".exp", 0.0));
            p.setTotalExperience(data.getInt(path + ".totalExp", 0));
            p.setFoodLevel(data.getInt(path + ".food", 20));
            p.setSaturation((float) data.getDouble(path + ".saturation", 20.0));

            double health = data.getDouble(path + ".health", p.getMaxHealth());
            p.setHealth(Math.max(1.0, Math.min(p.getMaxHealth(), health)));

            p.updateInventory();
        } catch (Exception e) {
            getLogger().warning("Nie wczytano ekwipunku: " + p.getName() + " / " + group);
            e.printStackTrace();
        }
    }

    private String group(World world) {
        if (world == null) return getConfig().getString("settings.default-group", "survival").toLowerCase(Locale.ROOT);
        String mapped = getConfig().getString("settings.groups." + world.getName());
        if (mapped != null && !mapped.isBlank()) return mapped.toLowerCase(Locale.ROOT);
        return getConfig().getString("settings.default-group", "survival").toLowerCase(Locale.ROOT);
    }

    private boolean enabled() {
        return getConfig().getBoolean("settings.enabled", true);
    }

    private boolean manageCompass() {
        return getConfig().getBoolean("settings.manage-lobby-compass", false);
    }

    private boolean isLobby(String group) {
        return group.equalsIgnoreCase(getConfig().getString("settings.lobby-group", "lobby"));
    }

    private void giveCompass(Player p) {
        if (hasCompass(p)) return;
        int slot = getConfig().getInt("settings.compass-slot", 4);
        ItemStack item = compass();

        if (slot >= 0 && slot <= 35) {
            ItemStack old = p.getInventory().getItem(slot);
            if (old == null || old.getType() == Material.AIR || isCompass(old)) {
                p.getInventory().setItem(slot, item);
                return;
            }
        }

        p.getInventory().addItem(item);
    }

    private void removeCompass(Player p) {
        ItemStack[] items = p.getInventory().getContents();
        for (int i = 0; i < items.length; i++) {
            if (isCompass(items[i])) p.getInventory().setItem(i, null);
        }
    }

    private boolean hasCompass(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isCompass(item)) return true;
        }
        return false;
    }

    private ItemStack compass() {
        ItemStack item = new ItemStack(material(getConfig().getString("compass.material", "COMPASS")));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(getConfig().getString("compass.name", "&6&lMSurvival Menu")));
            ArrayList<String> lore = new ArrayList<>();
            for (String line : getConfig().getStringList("compass.lore")) lore.add(color(line));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, "true");
            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean isCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }

    private String serialize(ItemStack[] items) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(output);
        out.writeInt(items.length);
        for (ItemStack item : items) out.writeObject(item);
        out.close();
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private ItemStack[] deserialize(String raw) throws Exception {
        if (raw == null || raw.isBlank()) return new ItemStack[0];
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(raw)));
        int length = in.readInt();
        ItemStack[] items = new ItemStack[length];
        for (int i = 0; i < length; i++) items[i] = (ItemStack) in.readObject();
        in.close();
        return items;
    }

    private Material material(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Material.COMPASS;
        }
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "inventories.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }
}
