package pl.msurvival.keys;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalKeys extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey keyType;
    private NamespacedKey actionKey;
    private final Random random = new Random();

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        try { getDataFolder().mkdirs(); if (!dataFile.exists()) dataFile.createNewFile(); } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        keyType = new NamespacedKey(this, "key_type");
        actionKey = new NamespacedKey(this, "action");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() { saveData(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("keysmenu")) { if (sender instanceof Player p) openKeys(p); return true; }
        if (cmd.equals("kits")) { if (sender instanceof Player p) openKits(p); return true; }
        if (cmd.equals("weeklykey")) { if (sender instanceof Player p) weekly(p); return true; }

        if (cmd.equals("keyadmin")) {
            if (!sender.hasPermission("msurvival.keys.admin")) { sender.sendMessage(msg("no-permission")); return true; }
            if (args.length < 3) { sender.sendMessage(color("&c/keyadmin <give|item> <gracz> <klucz> [ilosc]")); return true; }
            String mode = args[0].toLowerCase(Locale.ROOT);
            String player = args[1];
            String key = norm(args[2]);
            int amount = args.length >= 4 ? parse(args[3]) : 1;
            if (!getConfig().contains("keys." + key)) { sender.sendMessage(color("&cNie ma klucza: &e" + key)); return true; }
            if (mode.equals("give")) {
                addVirtual(player, key, amount);
                sender.sendMessage(color(msg("admin-give").replace("%amount%", ""+amount).replace("%key%", display(key)).replace("%player%", player)));
            } else if (mode.equals("item")) {
                Player target = Bukkit.getPlayerExact(player);
                if (target != null) target.getInventory().addItem(keyItem(key, amount));
            }
            return true;
        }
        return true;
    }

    private void weekly(Player p) {
        long cd = getConfig().getLong("settings.weekly-cooldown-seconds", 604800L) * 1000L;
        long last = data.getLong(path(p.getName()) + ".weekly", 0L);
        long left = cd - (System.currentTimeMillis() - last);
        if (last > 0 && left > 0) {
            sendList(p, "messages.weekly-cooldown", Map.of("%time%", time(left)));
            return;
        }
        String key = rollWeekly();
        data.set(path(p.getName()) + ".weekly", System.currentTimeMillis());
        addVirtual(p.getName(), key, 1);
        saveData();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.sendTitle(color("&6&lWEEKLY KEY"), display(key), 5, 55, 10);
        sendList(p, "messages.weekly-claimed", Map.of("%key%", display(key)));
        if (key.equals("boski") || key.equals("mityczny") || key.equals("legendarny")) {
            Bukkit.broadcastMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            Bukkit.broadcastMessage(color("&6&lMSurvival &8» &e" + p.getName() + " &fwylosował " + display(key) + "&f z &6WeeklyKey&f!"));
            Bukkit.broadcastMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        }
    }

    private String rollWeekly() {
        int roll = random.nextInt(1_000_000) + 1;
        if (roll == 1) return "boski";
        if (roll <= 2) return "mityczny";       // 1:500 000
        if (roll <= 10) return "legendarny";    // 1:100 000
        if (roll <= 100) return "epic";         // 1:10 000
        if (roll <= 1000) return "diamentowy";  // 1:1 000
        if (roll <= 10000) return "zelazny";    // 1:100
        return "klasyczny";
    }

    private void openKeys(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lKLUCZE SERWERA"));
        fill(inv);
        inv.setItem(4, gui(Material.CHEST, "&6&lCotygodniowy Klucz", "weekly", List.of(
                "&8━━━━━━━━━━━━━━━━",
                "&7Szanse na top klucze:",
                "&4Boski &8- &c1:1 000 000",
                "&dMityczny &8- &d1:500 000",
                "&6Legendarny &8- &61:100 000",
                "",
                "&aKliknij, aby odebrać.",
                "&8━━━━━━━━━━━━━━━━"
        )));
        int slot = 10;
        for (String key : getConfig().getConfigurationSection("keys").getKeys(false)) {
            inv.setItem(slot, gui(Material.TRIPWIRE_HOOK, display(key), "withdraw:" + key, List.of(
                    "&8━━━━━━━━━━━━━━━━",
                    "&7Wirtualne: &e" + getVirtual(p.getName(), key),
                    "&7Fizyczne: &e" + countPhysical(p, key),
                    "&aKliknij, aby wyjąć 1 klucz",
                    "&8━━━━━━━━━━━━━━━━"
            )));
            slot++;
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
        }
        p.openInventory(inv);
    }

    private void openKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&b&lE-KITY ZA KLUCZE"));
        fill(inv);
        kitButton(inv, 10, Material.STONE_PICKAXE, "klasyczny", "&f&lE-Kit Klasyczny");
        kitButton(inv, 11, Material.IRON_CHESTPLATE, "zelazny", "&7&lE-Kit Żelazny");
        kitButton(inv, 12, Material.DIAMOND_CHESTPLATE, "diamentowy", "&b&lE-Kit Diamentowy");
        kitButton(inv, 13, Material.DIAMOND_SWORD, "epic", "&5&lE-Kit Epic");
        kitButton(inv, 14, Material.NETHERITE_CHESTPLATE, "legendarny", "&6&lE-Kit Legendarny");
        kitButton(inv, 15, Material.NETHER_STAR, "mityczny", "&d&lE-Kit Mityczny");
        kitButton(inv, 16, Material.DRAGON_EGG, "boski", "&4&lE-Kit Boski");
        p.openInventory(inv);
    }

    private void kitButton(Inventory inv, int slot, Material material, String key, String name) {
        inv.setItem(slot, gui(material, name, "kit:" + key, List.of("&7Wymagany: " + display(key), "&aKliknij, aby odebrać")));
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.equals(color("&6&lKLUCZE SERWERA")) && !title.equals(color("&b&lE-KITY ZA KLUCZE"))) return;
        e.setCancelled(true);
        String action = action(e.getCurrentItem());
        if (action == null) return;
        p.closeInventory();
        if (action.equals("weekly")) weekly(p);
        else if (action.startsWith("withdraw:")) withdraw(p, action.substring(9));
        else if (action.startsWith("kit:")) openKit(p, action.substring(4));
    }

    @EventHandler public void rightClick(PlayerInteractEvent e) {
        if (keyType(e.getItem()) != null) { e.setCancelled(true); e.getPlayer().sendMessage(msg("disabled-ppm")); }
    }

    private void withdraw(Player p, String key) {
        key = norm(key);
        if (!isSurvival(p)) { p.sendMessage(msg("only-survival")); return; }
        if (getVirtual(p.getName(), key) <= 0) { p.sendMessage(color(msg("no-key").replace("%key%", display(key)))); return; }
        addVirtual(p.getName(), key, -1);
        p.getInventory().addItem(keyItem(key, 1));
        p.sendMessage(color(msg("withdrawn").replace("%key%", display(key))));
    }

    private void openKit(Player p, String key) {
        key = norm(key);
        if (!isSurvival(p)) { p.sendMessage(msg("only-survival")); return; }
        if (!takeKey(p, key)) { p.sendMessage(color(msg("no-key").replace("%key%", display(key)))); return; }
        giveKit(p, key);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        p.sendTitle(color("&6&lE-KIT OTWARTY"), display(key), 5, 45, 10);
    }

    private boolean takeKey(Player p, String key) {
        for (int i=0;i<p.getInventory().getSize();i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (key.equals(keyType(item))) {
                if (item.getAmount() <= 1) p.getInventory().setItem(i, null); else item.setAmount(item.getAmount()-1);
                return true;
            }
        }
        if (getVirtual(p.getName(), key) > 0) { addVirtual(p.getName(), key, -1); return true; }
        return false;
    }

    private void giveKit(Player p, String key) {
        boolean god = key.equals("boski") || key.equals("mityczny") || key.equals("legendarny") || key.equals("epic");
        Material armor = switch (key) { case "zelazny" -> Material.IRON_CHESTPLATE; case "klasyczny" -> Material.LEATHER_CHESTPLATE; case "diamentowy", "epic" -> Material.DIAMOND_CHESTPLATE; default -> Material.NETHERITE_CHESTPLATE; };
        String name = switch (key) { case "boski" -> "&4&lBoski"; case "mityczny" -> "&d&lMityczny"; case "legendarny" -> "&6&lLegendarny"; case "epic" -> "&5&lEpic"; case "diamentowy" -> "&b&lDiamentowy"; case "zelazny" -> "&7&lŻelazny"; default -> "&f&lKlasyczny"; };
        Material sword = armor == Material.IRON_CHESTPLATE ? Material.IRON_SWORD : armor == Material.DIAMOND_CHESTPLATE ? Material.DIAMOND_SWORD : armor == Material.LEATHER_CHESTPLATE ? Material.STONE_SWORD : Material.NETHERITE_SWORD;
        p.getInventory().addItem(sword(sword, name + " Miecz", god), new ItemStack(Material.GOLDEN_APPLE, key.equals("boski") ? 64 : 16), new ItemStack(Material.TOTEM_OF_UNDYING, key.equals("boski") ? 6 : 2));
    }

    private ItemStack sword(Material mat, String name, boolean god) {
        ItemStack item = named(mat, name);
        add(item, "sharpness", god ? 7 : 5); add(item, "fire_aspect", 2); add(item, "looting", god ? 5 : 3); add(item, "unbreaking", 3); add(item, "mending", 1);
        return item;
    }

    private void add(ItemStack item, String ench, int lvl) {
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(ench));
        if (e != null) item.addUnsafeEnchantment(e, lvl);
    }

    private ItemStack keyItem(String key, int amount) {
        ItemStack item = new ItemStack(Material.valueOf(getConfig().getString("key-item.material", "TRIPWIRE_HOOK")), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&l✦ " + display(key) + " &6&l✦"));
        meta.setLore(List.of(color("&8━━━━━━━━━━━━━━━━"), color("&7Klucz do E-Kita."), color("&eUżyj &6/kits&e na Survivalu."), color("&8━━━━━━━━━━━━━━━━")));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, key);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack gui(Material mat, String name, String action, List<String> loreRaw) {
        ItemStack item = named(mat, name);
        ItemMeta meta = item.getItemMeta();
        ArrayList<String> lore = new ArrayList<>();
        for (String line : loreRaw) lore.add(color(line));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(List.of(color("&8━━━━━━━━━━━━━━━━"), color("&6&lMSURVIVAL"), color("&8━━━━━━━━━━━━━━━━")));
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta(); meta.setDisplayName(" "); glass.setItemMeta(meta);
        for (int i=0;i<inv.getSize();i++) inv.setItem(i, glass);
    }

    private void sendList(Player p, String path, Map<String,String> repl) {
        for (String line : getConfig().getStringList(path)) {
            for (Map.Entry<String,String> e : repl.entrySet()) line = line.replace(e.getKey(), e.getValue());
            p.sendMessage(color(line));
        }
    }

    private String action(ItemStack item) { return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING); }
    private String keyType(ItemStack item) { return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING); }
    private boolean isSurvival(Player p) { return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("settings.survival-world", "world")); }
    private int countPhysical(Player p, String key) { int n=0; for(ItemStack item:p.getInventory()) if(key.equals(keyType(item))) n += item.getAmount(); return n; }
    private int getVirtual(String player, String key) { return data.getInt(path(player)+".keys."+norm(key), 0); }
    private void addVirtual(String player, String key, int amount) { String p=path(player)+".keys."+norm(key); data.set(p, Math.max(0, data.getInt(p, 0)+amount)); saveData(); }
    private String display(String key) { return color(getConfig().getString("keys."+norm(key), key)); }
    private String path(String player) { return "players."+player.toLowerCase(Locale.ROOT); }
    private String norm(String s) { return s==null?"":s.toLowerCase(Locale.ROOT); }
    private int parse(String s) { try { return Math.max(1, Integer.parseInt(s)); } catch(Exception e) { return 1; } }
    private String time(long ms) { long sec=Math.max(1, ms/1000); long d=sec/86400; sec%=86400; long h=sec/3600; sec%=3600; long m=sec/60; long s=sec%60; return d+" dni, "+h+" godzin, "+m+" minut, "+s+" sekund"; }
    private void saveData() { try { data.save(dataFile); } catch(Exception ignored) {} }
    private String msg(String k) { return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,"")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
}
