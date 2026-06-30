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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { getDataFolder().mkdirs(); dataFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        keyType = new NamespacedKey(this, "key_type");
        actionKey = new NamespacedKey(this, "action");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() { saveData(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("keysmenu")) {
            if (sender instanceof Player player) openKeys(player);
            return true;
        }

        if (cmd.equals("kits")) {
            if (sender instanceof Player player) openKits(player);
            return true;
        }

        if (cmd.equals("weeklykey")) {
            if (sender instanceof Player player) weekly(player);
            return true;
        }

        if (cmd.equals("keyadmin")) {
            if (!sender.hasPermission("msurvival.keys.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(color("&c/keyadmin <give|item> <gracz> <klucz> [ilosc]"));
                return true;
            }

            String mode = args[0].toLowerCase(Locale.ROOT);
            String playerName = args[1];
            String key = norm(args[2]);
            int amount = args.length >= 4 ? parse(args[3]) : 1;

            if (!getConfig().contains("keys." + key)) {
                sender.sendMessage(color("&cNie ma klucza: &e" + key));
                return true;
            }

            if (mode.equals("give")) {
                addVirtual(playerName, key, amount);
                sender.sendMessage(color(msg("admin-give")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%key%", display(key))
                        .replace("%player%", playerName)));
                return true;
            }

            if (mode.equals("item")) {
                Player target = Bukkit.getPlayerExact(playerName);
                if (target != null) target.getInventory().addItem(keyItem(key, amount));
                return true;
            }
        }

        return true;
    }

    private void openKeys(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lKLUCZE SERWERA"));
        fill(inv);
        inv.setItem(4, gui(Material.CHEST, "&a&lCotygodniowy klucz", "weekly", List.of(
                "&8━━━━━━━━━━━━━━━━",
                "&7Darmowy klucz co 7 dni.",
                "&aKliknij, aby odebrać.",
                "&8━━━━━━━━━━━━━━━━"
        )));

        int slot = 10;
        for (String key : getConfig().getConfigurationSection("keys").getKeys(false)) {
            inv.setItem(slot, gui(Material.TRIPWIRE_HOOK, display(key), "withdraw:" + key, List.of(
                    "&8━━━━━━━━━━━━━━━━",
                    "&7Wirtualne: &e" + getVirtual(player.getName(), key),
                    "&7Fizyczne: &e" + countPhysical(player, key),
                    "",
                    "&aKliknij, aby wyjąć 1 klucz",
                    "&cTylko na Survivalu",
                    "&8━━━━━━━━━━━━━━━━"
            )));
            slot++;
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
        }

        player.openInventory(inv);
    }

    private void openKits(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&b&lE-KITY ZA KLUCZE"));
        fill(inv);
        kitButton(inv, 10, Material.STONE_PICKAXE, "klasyczny", "&f&lE-Kit Klasyczny");
        kitButton(inv, 11, Material.IRON_CHESTPLATE, "zelazny", "&7&lE-Kit Żelazny");
        kitButton(inv, 12, Material.DIAMOND_CHESTPLATE, "diamentowy", "&b&lE-Kit Diamentowy");
        kitButton(inv, 13, Material.DIAMOND_SWORD, "epic", "&5&lE-Kit Epic");
        kitButton(inv, 14, Material.NETHERITE_CHESTPLATE, "legendarny", "&6&lE-Kit Legendarny");
        kitButton(inv, 15, Material.NETHER_STAR, "mityczny", "&d&lE-Kit Mityczny");
        kitButton(inv, 16, Material.DRAGON_EGG, "boski", "&4&lE-Kit Boski");
        player.openInventory(inv);
    }

    private void kitButton(Inventory inv, int slot, Material material, String key, String name) {
        inv.setItem(slot, gui(material, name, "kit:" + key, List.of(
                "&8━━━━━━━━━━━━━━━━",
                "&7Wymagany: " + display(key),
                "&7Zbroja trafia od razu na gracza.",
                "&7Reszta itemów trafia do ekwipunku.",
                "",
                "&aKliknij, aby odebrać",
                "&8━━━━━━━━━━━━━━━━"
        )));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(color("&6&lKLUCZE SERWERA")) && !title.equals(color("&b&lE-KITY ZA KLUCZE"))) return;
        event.setCancelled(true);

        String action = action(event.getCurrentItem());
        if (action == null) return;

        player.closeInventory();
        if (action.equals("weekly")) weekly(player);
        else if (action.startsWith("withdraw:")) withdraw(player, action.substring(9));
        else if (action.startsWith("kit:")) openKit(player, action.substring(4));
    }

    @EventHandler
    public void rightClick(PlayerInteractEvent event) {
        if (keyType(event.getItem()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(msg("disabled-ppm"));
        }
    }

    private void weekly(Player player) {
        long cd = getConfig().getLong("settings.weekly-cooldown-seconds", 604800) * 1000L;
        long last = data.getLong(path(player.getName()) + ".weekly", 0L);
        long left = cd - (System.currentTimeMillis() - last);

        if (last > 0 && left > 0) {
            player.sendMessage(msg("cooldown").replace("%time%", time(left)));
            return;
        }

        String key = rollWeekly();
        data.set(path(player.getName()) + ".weekly", System.currentTimeMillis());
        addVirtual(player.getName(), key, 1);
        player.sendMessage(color(msg("claimed").replace("%key%", display(key))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    private void withdraw(Player player, String key) {
        key = norm(key);

        if (!isSurvival(player)) {
            player.sendMessage(msg("only-survival"));
            return;
        }

        if (getVirtual(player.getName(), key) <= 0) {
            player.sendMessage(color(msg("no-key").replace("%key%", display(key))));
            return;
        }

        addVirtual(player.getName(), key, -1);
        player.getInventory().addItem(keyItem(key, 1));
        player.sendMessage(color(msg("withdrawn").replace("%key%", display(key))));
    }

    private void openKit(Player player, String key) {
        key = norm(key);

        if (!isSurvival(player)) {
            player.sendMessage(msg("only-survival"));
            return;
        }

        if (!takeKey(player, key)) {
            player.sendMessage(color(msg("no-key").replace("%key%", display(key))));
            return;
        }

        giveKit(player, key);
        player.sendMessage(color(msg("opened").replace("%kit%", key)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        player.sendTitle(color("&6&lE-KIT OTWARTY"), display(key), 5, 45, 10);
    }

    private boolean takeKey(Player player, String key) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (key.equals(keyType(item))) {
                if (item.getAmount() <= 1) player.getInventory().setItem(i, null);
                else item.setAmount(item.getAmount() - 1);
                return true;
            }
        }

        if (getVirtual(player.getName(), key) > 0) {
            addVirtual(player.getName(), key, -1);
            return true;
        }

        return false;
    }

    private void giveKit(Player player, String key) {
        switch (key) {
            case "klasyczny" -> {
                giveOrDrop(player, named(Material.LEATHER_HELMET, "&f&lKlasyczny Hełm"));
                giveOrDrop(player, named(Material.LEATHER_CHESTPLATE, "&f&lKlasyczna Klata"));
                giveOrDrop(player, named(Material.LEATHER_LEGGINGS, "&f&lKlasyczne Spodnie"));
                giveOrDrop(player, named(Material.LEATHER_BOOTS, "&f&lKlasyczne Buty"));
                giveTools(player, Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE, "&f&lKlasyczny", false);
                giveOrDrop(player, new ItemStack(Material.BREAD, 32));
            }
            case "zelazny" -> fullKit(player, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                    Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE,
                    "&7&lŻelazny", key, false);
            case "diamentowy" -> fullKit(player, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                    Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
                    "&b&lDiamentowy", key, false);
            case "epic" -> fullKit(player, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                    Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
                    "&5&lEpic", key, true);
            case "legendarny" -> fullKit(player, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                    Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
                    "&6&lLegendarny", key, true);
            case "mityczny" -> fullKit(player, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                    Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
                    "&d&lMityczny", key, true);
            case "boski" -> fullKit(player, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                    Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
                    "&4&lBoski", key, true);
        }
    }

    private void fullKit(Player player, Material helmet, Material chest, Material legs, Material boots,
                         Material sword, Material pick, Material axe, Material shovel, Material hoe,
                         String name, String key, boolean godTools) {

        // ZBROJA Z KITA IDZIE OD RAZU NA GRACZA, a stara zbroja wraca do inventory/drop.
        equipArmor(player, armor(helmet, name + " Hełm", false, godTools), EquipmentSlotType.HELMET);
        equipArmor(player, armor(chest, name + " Napierśnik", false, godTools), EquipmentSlotType.CHEST);
        equipArmor(player, armor(legs, name + " Spodnie", false, godTools), EquipmentSlotType.LEGS);
        equipArmor(player, armor(boots, name + " Buty", true, godTools), EquipmentSlotType.BOOTS);

        giveOrDrop(player, sword(sword, name + " Miecz", godTools));
        giveOrDrop(player, pickaxe(pick, name + " Kilof", godTools));
        giveOrDrop(player, axe(axe, name + " Siekiera", godTools));
        giveOrDrop(player, shovel(shovel, name + " Łopata", godTools));
        giveOrDrop(player, hoe(hoe, name + " Motyka", godTools));

        giveOrDrop(player, bow(name + " Łuk", godTools));
        giveOrDrop(player, new ItemStack(Material.ARROW, 64));
        giveOrDrop(player, new ItemStack(Material.GOLDEN_APPLE, key.equals("boski") ? 64 : key.equals("mityczny") ? 48 : key.equals("legendarny") ? 32 : 16));
        giveOrDrop(player, new ItemStack(Material.TOTEM_OF_UNDYING, key.equals("boski") ? 6 : key.equals("mityczny") ? 4 : 2));

        if (key.equals("legendarny") || key.equals("mityczny") || key.equals("boski")) {
            giveOrDrop(player, elytra(name + " Elytry"));
            giveOrDrop(player, new ItemStack(Material.FIREWORK_ROCKET, 64));
        }

        if (key.equals("mityczny") || key.equals("boski")) {
            giveOrDrop(player, crossbow(name + " Kusza"));
            giveOrDrop(player, trident(name + " Trójząb"));
            giveOrDrop(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, key.equals("boski") ? 16 : 6));
        }

        if (key.equals("boski")) {
            giveOrDrop(player, shield("&4&lBoska Tarcza"));
            giveOrDrop(player, new ItemStack(Material.NETHERITE_INGOT, 16));
            giveOrDrop(player, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
        }
    }

    private void giveTools(Player player, Material sword, Material pick, Material axe, Material shovel, Material hoe, String name, boolean god) {
        giveOrDrop(player, sword(sword, name + " Miecz", god));
        giveOrDrop(player, pickaxe(pick, name + " Kilof", god));
        giveOrDrop(player, axe(axe, name + " Siekiera", god));
        giveOrDrop(player, shovel(shovel, name + " Łopata", god));
        giveOrDrop(player, hoe(hoe, name + " Motyka", god));
    }

    private enum EquipmentSlotType { HELMET, CHEST, LEGS, BOOTS }

    private void equipArmor(Player player, ItemStack newItem, EquipmentSlotType slot) {
        ItemStack old = switch (slot) {
            case HELMET -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case BOOTS -> player.getInventory().getBoots();
        };

        if (old != null && old.getType() != Material.AIR) giveOrDrop(player, old);

        switch (slot) {
            case HELMET -> player.getInventory().setHelmet(newItem);
            case CHEST -> player.getInventory().setChestplate(newItem);
            case LEGS -> player.getInventory().setLeggings(newItem);
            case BOOTS -> player.getInventory().setBoots(newItem);
        }
    }

    private ItemStack armor(Material material, String name, boolean boots, boolean god) {
        ItemStack item = named(material, name);
        add(item, "protection", 4);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        if (god) {
            add(item, "fire_protection", 4);
            add(item, "blast_protection", 4);
            add(item, "projectile_protection", 4);
        }
        if (boots) {
            add(item, "feather_falling", 4);
            add(item, "depth_strider", 3);
            add(item, "soul_speed", 3);
        }
        return item;
    }

    private ItemStack sword(Material material, String name, boolean god) {
        ItemStack item = named(material, name);
        add(item, "sharpness", god ? 7 : 5);
        add(item, "fire_aspect", 2);
        add(item, "looting", god ? 5 : 3);
        add(item, "sweeping_edge", 3);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack pickaxe(Material material, String name, boolean god) {
        ItemStack item = named(material, name);
        add(item, "efficiency", god ? 7 : 5);
        add(item, "fortune", god ? 5 : 3);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack axe(Material material, String name, boolean god) {
        ItemStack item = named(material, name);
        add(item, "efficiency", god ? 7 : 5);
        add(item, "sharpness", god ? 7 : 5);
        add(item, "fortune", god ? 5 : 3);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack shovel(Material material, String name, boolean god) {
        ItemStack item = named(material, name);
        add(item, "efficiency", god ? 7 : 5);
        add(item, "fortune", god ? 5 : 3);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack hoe(Material material, String name, boolean god) {
        ItemStack item = named(material, name);
        add(item, "efficiency", god ? 7 : 5);
        add(item, "fortune", god ? 5 : 3);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack bow(String name, boolean god) {
        ItemStack item = named(Material.BOW, name);
        add(item, "power", god ? 7 : 5);
        add(item, "punch", 2);
        add(item, "flame", 1);
        add(item, "infinity", 1);
        add(item, "unbreaking", 3);
        return item;
    }

    private ItemStack crossbow(String name) {
        ItemStack item = named(Material.CROSSBOW, name);
        add(item, "quick_charge", 3);
        add(item, "multishot", 1);
        add(item, "piercing", 4);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack trident(String name) {
        ItemStack item = named(Material.TRIDENT, name);
        add(item, "loyalty", 3);
        add(item, "impaling", 5);
        add(item, "channeling", 1);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack elytra(String name) {
        ItemStack item = named(Material.ELYTRA, name);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private ItemStack shield(String name) {
        ItemStack item = named(Material.SHIELD, name);
        add(item, "unbreaking", 3);
        add(item, "mending", 1);
        return item;
    }

    private void add(ItemStack item, String enchant, int level) {
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(enchant));
        if (e != null) item.addUnsafeEnchantment(e, level);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        HashMap<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack drop : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);
    }

    private ItemStack keyItem(String key, int amount) {
        ItemStack item = new ItemStack(Material.valueOf(getConfig().getString("key-item.material", "TRIPWIRE_HOOK")), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&l✦ " + display(key) + " &6&l✦"));
        meta.setLore(List.of(
                color("&8━━━━━━━━━━━━━━━━"),
                color("&7Klucz do E-Kita."),
                color("&cNie klikaj PPM."),
                color("&7Użyj &e/kits&7 na Survivalu."),
                color("&6&lMSURVIVAL"),
                color("&8━━━━━━━━━━━━━━━━")
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, key);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack gui(Material material, String name, String action, List<String> lore) {
        ItemStack item = named(material, name);
        ItemMeta meta = item.getItemMeta();
        ArrayList<String> colored = new ArrayList<>();
        for (String line : lore) colored.add(color(line));
        meta.setLore(colored);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(List.of(
                color("&8━━━━━━━━━━━━━━━━"),
                color("&7Przedmiot z E-Kita"),
                color("&6&lMSURVIVAL"),
                color("&8━━━━━━━━━━━━━━━━")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String keyType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
    }

    private boolean isSurvival(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(getConfig().getString("settings.survival-world", "world"));
    }

    private int countPhysical(Player player, String key) {
        int amount = 0;
        for (ItemStack item : player.getInventory()) if (key.equals(keyType(item))) amount += item.getAmount();
        return amount;
    }

    private int getVirtual(String player, String key) {
        return data.getInt(path(player) + ".keys." + norm(key), 0);
    }

    private void addVirtual(String player, String key, int amount) {
        String path = path(player) + ".keys." + norm(key);
        data.set(path, Math.max(0, data.getInt(path, 0) + amount));
        saveData();
    }

    private String rollWeekly() {
        int total = 0;
        for (String k : getConfig().getConfigurationSection("weekly").getKeys(false)) total += getConfig().getInt("weekly." + k);
        int roll = random.nextInt(Math.max(1, total)) + 1;
        int current = 0;
        for (String k : getConfig().getConfigurationSection("weekly").getKeys(false)) {
            current += getConfig().getInt("weekly." + k);
            if (roll <= current) return norm(k);
        }
        return "klasyczny";
    }

    private String display(String key) { return color(getConfig().getString("keys." + norm(key), key)); }
    private String path(String player) { return "players." + player.toLowerCase(Locale.ROOT); }
    private String norm(String text) { return text == null ? "" : text.toLowerCase(Locale.ROOT); }
    private int parse(String text) { try { return Math.max(1, Integer.parseInt(text)); } catch (Exception e) { return 1; } }
    private String time(long ms) { long m = Math.max(1, ms / 60000L); long h = m / 60L; long d = h / 24L; if (d > 0) return d + "d " + (h % 24) + "h"; if (h > 0) return h + "h " + (m % 60) + "m"; return m + "m"; }
    private void saveData() { try { data.save(dataFile); } catch (Exception e) { e.printStackTrace(); } }
    private String msg(String key) { return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
