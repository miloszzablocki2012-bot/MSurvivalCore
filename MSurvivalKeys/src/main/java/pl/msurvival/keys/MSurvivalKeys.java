package pl.msurvival.keys;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
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
    private boolean saveQueued;
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

    @Override public void onDisable() { try { data.save(dataFile); } catch (Exception ignored) {} }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("keysmenu")) { if (sender instanceof Player p) openKeys(p); return true; }
        if (cmd.equals("kits")) { if (sender instanceof Player p) openKits(p); return true; }
        if (cmd.equals("dailykey")) { if (sender instanceof Player p) claimTimedKey(p, "daily"); return true; }
        if (cmd.equals("weeklykey")) { if (sender instanceof Player p) claimTimedKey(p, "weekly"); return true; }

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

    private void claimTimedKey(Player p, String type) {
        long cd = getConfig().getLong("settings." + type + "-cooldown-seconds", type.equals("daily") ? 86400L : 604800L) * 1000L;
        long last = data.getLong(path(p.getName()) + "." + type, 0L);
        long left = cd - (System.currentTimeMillis() - last);
        if (last > 0 && left > 0) {
            sendList(p, "messages." + type + "-cooldown", Map.of("%time%", time(left)));
            return;
        }
        String key = roll(type);
        data.set(path(p.getName()) + "." + type, System.currentTimeMillis());
        addVirtual(p.getName(), key, 1);
        saveData();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, type.equals("daily") ? 1.25f : 1f);
        p.sendTitle(color(type.equals("daily") ? "&a&lKLUCZ CODZIENNY" : "&e&lKLUCZ COTYGODNIOWY"), display(key), 5, 55, 10);
        sendList(p, "messages." + type + "-claimed", Map.of("%key%", display(key)));
        if (List.of("administracyjny", "boski", "mityczny", "legendarny").contains(key)) {
            Bukkit.broadcastMessage(color("&x&F&F&D&7&0&0" + p.getName() + " &fwylosował " + display(key) + "&f!"));
        }
    }

    private String roll(String type) {
        ConfigurationSection rewards = getConfig().getConfigurationSection("timed-keys." + type + ".rewards");
        if (rewards == null) return type.equals("daily") ? "klasyczny" : "zelazny";
        double total = 0D;
        for (String key : rewards.getKeys(false)) total += Math.max(0D, rewards.getDouble(key + ".chance", 0D));
        if (total <= 0D) return "klasyczny";
        double rolled = random.nextDouble() * total;
        double current = 0D;
        for (String key : rewards.getKeys(false)) {
            current += Math.max(0D, rewards.getDouble(key + ".chance", 0D));
            if (rolled <= current) return norm(key);
        }
        return rewards.getKeys(false).iterator().next();
    }

    private void openKeys(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&x&5&C&F&F&8&D&lKLUCZE MSURVIVAL"));
        fill(inv);
        inv.setItem(3, gui(Material.LIME_DYE, "&a&lKlucz Codzienny", "daily", List.of(
                "&7Odbiór co 24 godziny",
                "&fKlasyczny &a70%",
                "&fŻelazny &a25%",
                "&fZłoty &a5%",
                "&aKliknij, aby odebrać"
        )));
        inv.setItem(5, gui(Material.GOLD_INGOT, "&e&lKlucz Cotygodniowy", "weekly", List.of(
                "&7Odbiór co 7 dni",
                "&fNagrody z całej puli kluczy",
                "&fBoski &e1.8%",
                "&fAdministracyjny &e0.2%",
                "&aKliknij, aby odebrać"
        )));
        int slot = 10;
        for (String key : getConfig().getConfigurationSection("keys").getKeys(false)) {
            if (key.equals("codzienny") || key.equals("cotygodniowy")) continue;
            inv.setItem(slot, gui(Material.TRIPWIRE_HOOK, display(key), "withdraw:" + key, List.of(
                    "&7Wirtualne &f" + getVirtual(p.getName(), key),
                    "&7Fizyczne &f" + countPhysical(p, key),
                    "&aKliknij, aby wyjąć 1 klucz"
            )));
            slot++;
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
        }
        p.openInventory(inv);
    }

    private void openKits(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&x&B&B&8&C&F&F&lE-KITY ZA KLUCZE"));
        fill(inv);
        kitButton(inv, 10, Material.STONE_SWORD, "klasyczny", "&f&lE-Kit Klasyczny");
        kitButton(inv, 11, Material.IRON_CHESTPLATE, "zelazny", "&7&lE-Kit Żelazny");
        kitButton(inv, 12, Material.GOLDEN_APPLE, "zloty", "&6&lE-Kit Złoty");
        kitButton(inv, 13, Material.DIAMOND_CHESTPLATE, "diamentowy", "&b&lE-Kit Diamentowy");
        kitButton(inv, 14, Material.EMERALD, "szmaragdowy", "&a&lE-Kit Szmaragdowy");
        kitButton(inv, 15, Material.DIAMOND_SWORD, "epic", "&5&lE-Kit Epic");
        kitButton(inv, 16, Material.NETHERITE_CHESTPLATE, "legendarny", "&6&lE-Kit Legendarny");
        kitButton(inv, 20, Material.NETHER_STAR, "mityczny", "&d&lE-Kit Mityczny");
        kitButton(inv, 22, Material.DRAGON_EGG, "boski", "&4&lE-Kit Boski");
        kitButton(inv, 24, Material.COMMAND_BLOCK, "administracyjny", "&c&lE-Kit Administracyjny");
        p.openInventory(inv);
    }

    private void kitButton(Inventory inv, int slot, Material material, String key, String name) {
        inv.setItem(slot, gui(material, name, "kit:" + key, List.of("&7Wymagany klucz", display(key), "&aKliknij, aby odebrać")));
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.equals(color("&x&5&C&F&F&8&D&lKLUCZE MSURVIVAL")) && !title.equals(color("&x&B&B&8&C&F&F&lE-KITY ZA KLUCZE"))) return;
        e.setCancelled(true);
        String action = action(e.getCurrentItem());
        if (action == null) return;
        p.closeInventory();
        if (action.equals("daily")) claimTimedKey(p, "daily");
        else if (action.equals("weekly")) claimTimedKey(p, "weekly");
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
        p.sendTitle(color("&x&F&F&D&7&0&0&lE-KIT OTWARTY"), display(key), 5, 45, 10);
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
        KitPower power = kitPower(key);
        Material armorMaterial = power.armor;
        p.getInventory().addItem(
                armor(helmet(armorMaterial), power.name + " Hełm", power.protection, power.unbreaking),
                armor(chestplate(armorMaterial), power.name + " Klata", power.protection, power.unbreaking),
                armor(leggings(armorMaterial), power.name + " Spodnie", power.protection, power.unbreaking),
                armor(boots(armorMaterial), power.name + " Buty", power.protection, power.unbreaking),
                sword(power.sword, power.name + " Miecz", power.sharpness, power.looting, power.unbreaking),
                tool(power.pickaxe, power.name + " Kilof", power.efficiency, power.fortune, power.unbreaking),
                tool(power.axe, power.name + " Siekiera", power.efficiency, power.fortune, power.unbreaking),
                bow(power.name + " Łuk", power.sharpness, power.unbreaking),
                new ItemStack(Material.GOLDEN_APPLE, power.gapples),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, power.egapples),
                new ItemStack(Material.TOTEM_OF_UNDYING, power.totems),
                new ItemStack(Material.ENDER_PEARL, power.pearls)
        );
    }

    private KitPower kitPower(String key) {
        return switch (key) {
            case "administracyjny" -> new KitPower("&c&lAdministracyjny", Material.NETHERITE_CHESTPLATE, Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, 10, 10, 10, 8, 10, 8, 64, 32, 16, 32);
            case "boski" -> new KitPower("&4&lBoski", Material.NETHERITE_CHESTPLATE, Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, 8, 7, 7, 6, 7, 6, 64, 12, 8, 24);
            case "mityczny" -> new KitPower("&d&lMityczny", Material.NETHERITE_CHESTPLATE, Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, 7, 6, 6, 5, 6, 5, 48, 8, 6, 16);
            case "legendarny" -> new KitPower("&6&lLegendarny", Material.NETHERITE_CHESTPLATE, Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, 6, 5, 5, 4, 5, 4, 32, 5, 4, 12);
            case "epic" -> new KitPower("&5&lEpic", Material.DIAMOND_CHESTPLATE, Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, 5, 5, 4, 3, 5, 3, 24, 3, 3, 8);
            case "szmaragdowy" -> new KitPower("&a&lSzmaragdowy", Material.DIAMOND_CHESTPLATE, Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, 4, 4, 3, 3, 4, 2, 20, 2, 2, 6);
            case "diamentowy" -> new KitPower("&b&lDiamentowy", Material.DIAMOND_CHESTPLATE, Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, 4, 3, 3, 2, 4, 2, 16, 1, 2, 4);
            case "zloty" -> new KitPower("&6&lZłoty", Material.IRON_CHESTPLATE, Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, 3, 3, 2, 1, 3, 1, 12, 0, 1, 2);
            case "zelazny" -> new KitPower("&7&lŻelazny", Material.IRON_CHESTPLATE, Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, 2, 2, 1, 1, 2, 1, 8, 0, 1, 0);
            default -> new KitPower("&f&lKlasyczny", Material.LEATHER_CHESTPLATE, Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONE_AXE, 1, 1, 1, 0, 1, 1, 6, 0, 0, 0);
        };
    }

    private record KitPower(String name, Material armor, Material sword, Material pickaxe, Material axe, int protection, int sharpness, int efficiency, int fortune, int unbreaking, int looting, int gapples, int egapples, int totems, int pearls) {
        int power() { return sharpness; }
    }

    private Material helmet(Material chest) { return switch (chest) { case NETHERITE_CHESTPLATE -> Material.NETHERITE_HELMET; case DIAMOND_CHESTPLATE -> Material.DIAMOND_HELMET; case IRON_CHESTPLATE -> Material.IRON_HELMET; default -> Material.LEATHER_HELMET; }; }
    private Material chestplate(Material chest) { return chest; }
    private Material leggings(Material chest) { return switch (chest) { case NETHERITE_CHESTPLATE -> Material.NETHERITE_LEGGINGS; case DIAMOND_CHESTPLATE -> Material.DIAMOND_LEGGINGS; case IRON_CHESTPLATE -> Material.IRON_LEGGINGS; default -> Material.LEATHER_LEGGINGS; }; }
    private Material boots(Material chest) { return switch (chest) { case NETHERITE_CHESTPLATE -> Material.NETHERITE_BOOTS; case DIAMOND_CHESTPLATE -> Material.DIAMOND_BOOTS; case IRON_CHESTPLATE -> Material.IRON_BOOTS; default -> Material.LEATHER_BOOTS; }; }

    private ItemStack armor(Material mat, String name, int protection, int unbreaking) {
        ItemStack item = named(mat, name);
        add(item, "protection", protection);
        add(item, "unbreaking", unbreaking);
        add(item, "mending", 1);
        if (mat.name().endsWith("BOOTS")) add(item, "feather_falling", Math.max(4, protection));
        return item;
    }

    private ItemStack sword(Material mat, String name, int sharpness, int looting, int unbreaking) {
        ItemStack item = named(mat, name);
        add(item, "sharpness", sharpness); add(item, "fire_aspect", 2); add(item, "looting", looting); add(item, "unbreaking", unbreaking); add(item, "mending", 1);
        return item;
    }

    private ItemStack tool(Material mat, String name, int efficiency, int fortune, int unbreaking) {
        ItemStack item = named(mat, name);
        add(item, "efficiency", efficiency); add(item, "fortune", fortune); add(item, "unbreaking", unbreaking); add(item, "mending", 1);
        return item;
    }

    private ItemStack bow(String name, int power, int unbreaking) {
        ItemStack item = named(Material.BOW, name);
        add(item, "power", power); add(item, "flame", 1); add(item, "punch", Math.min(3, Math.max(1, power / 3))); add(item, "unbreaking", unbreaking); add(item, "mending", 1);
        return item;
    }

    private void add(ItemStack item, String ench, int lvl) {
        if (lvl <= 0) return;
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(ench));
        if (e != null) item.addUnsafeEnchantment(e, lvl);
    }

    private ItemStack keyItem(String key, int amount) {
        ItemStack item = new ItemStack(Material.valueOf(getConfig().getString("key-item.material", "TRIPWIRE_HOOK")), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(display(key)));
        meta.setLore(List.of(color("&7Klucz do E-Kita"), color("&eUżyj /kits na Survivalu")));
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
        meta.setLore(List.of(color("&x&5&C&F&F&8&D&lMSURVIVAL")));
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
    private String msg(String k) { return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,"")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
}
