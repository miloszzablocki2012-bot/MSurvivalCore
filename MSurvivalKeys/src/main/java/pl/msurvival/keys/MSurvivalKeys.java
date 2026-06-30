package pl.msurvival.keys;

import org.bukkit.*;
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

@SuppressWarnings("deprecation")
public final class MSurvivalKeys extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey keyType;
    private NamespacedKey action;
    private final Random random = new Random();

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) try { getDataFolder().mkdirs(); dataFile.createNewFile(); } catch(Exception e){ e.printStackTrace(); }
        data = YamlConfiguration.loadConfiguration(dataFile);
        keyType = new NamespacedKey(this, "key_type");
        action = new NamespacedKey(this, "action");
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("keysmenu").setExecutor((s,c,l,a)->{ if(s instanceof Player p) openKeys(p); return true; });
        getCommand("kits").setExecutor((s,c,l,a)->{ if(s instanceof Player p) openKits(p); return true; });
        getCommand("weeklykey").setExecutor((s,c,l,a)->{ if(s instanceof Player p) weekly(p); return true; });
        getCommand("keyadmin").setExecutor((s,c,l,a)->{
            if(!s.hasPermission("msurvival.keys.admin")) { s.sendMessage(msg("no-permission")); return true; }
            if(a.length < 3) { s.sendMessage(color("&c/keyadmin <give|item|reset> <gracz> <klucz> [ilosc]")); return true; }
            Player p = Bukkit.getPlayerExact(a[1]);
            String k = a[2].toLowerCase(Locale.ROOT);
            int amount = a.length >= 4 ? parse(a[3]) : 1;
            if(a[0].equalsIgnoreCase("give")) {
                addVirtual(a[1], k, amount);
                s.sendMessage(color(msg("admin-give").replace("%amount%", String.valueOf(amount)).replace("%key%", display(k)).replace("%player%", a[1])));
            } else if(a[0].equalsIgnoreCase("item") && p != null) {
                p.getInventory().addItem(keyItem(k, amount));
            } else if(a[0].equalsIgnoreCase("reset")) {
                data.set(path(a[1]) + ".weekly", 0L);
                saveData();
            }
            return true;
        });
    }

    @Override public void onDisable(){ saveData(); }

    private void openKeys(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lKLUCZE SERWERA"));
        fill(inv);
        inv.setItem(4, gui(Material.CHEST, "&a&lCotygodniowy klucz", "weekly", List.of("&7Odbierz darmowy klucz.", "&7Działa co 7 dni.")));

        int slot = 10;
        for(String k : getConfig().getConfigurationSection("keys").getKeys(false)) {
            inv.setItem(slot, gui(Material.TRIPWIRE_HOOK, display(k), "withdraw:" + k, List.of("&8━━━━━━━━━━━━━━━━", "&7Wirtualne: &e" + getVirtual(p.getName(), k), "&7Fizyczne w eq: &e" + physical(p,k), "", "&aKliknij, aby wyjąć 1 klucz", "&cTylko na Survivalu!", "&8━━━━━━━━━━━━━━━━")));
            slot++;
            if(slot == 17 || slot == 26 || slot == 35) slot += 2;
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

    private void kitButton(Inventory inv, int slot, Material mat, String key, String name) {
        inv.setItem(slot, gui(mat, name, "kit:" + key, List.of("&8━━━━━━━━━━━━━━━━", "&7Wymagany klucz: " + display(key), "", "&aKliknij, aby odebrać E-Kit", "&cTylko na Survivalu!", "&8━━━━━━━━━━━━━━━━")));
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if(!title.equals(color("&6&lKLUCZE SERWERA")) && !title.equals(color("&b&lE-KITY ZA KLUCZE"))) return;
        e.setCancelled(true);
        String a = action(e.getCurrentItem());
        if(a == null) return;
        p.closeInventory();
        if(a.equals("weekly")) weekly(p);
        else if(a.startsWith("withdraw:")) withdraw(p, a.substring(9));
        else if(a.startsWith("kit:")) kit(p, a.substring(4));
    }

    @EventHandler public void interact(PlayerInteractEvent e) {
        if(key(e.getItem()) != null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(msg("disabled-ppm"));
        }
    }

    private void weekly(Player p) {
        long cd = getConfig().getLong("settings.weekly-cooldown-seconds") * 1000L;
        long last = data.getLong(path(p.getName()) + ".weekly", 0L);
        long left = cd - (System.currentTimeMillis() - last);
        if(last > 0 && left > 0) { p.sendMessage(msg("cooldown").replace("%time%", time(left))); return; }
        String key = rollWeekly();
        data.set(path(p.getName()) + ".weekly", System.currentTimeMillis());
        addVirtual(p.getName(), key, 1);
        p.sendMessage(color(msg("claimed").replace("%key%", display(key))));
    }

    private void withdraw(Player p, String key) {
        if(!isSurvival(p)) { p.sendMessage(msg("only-survival")); return; }
        if(getVirtual(p.getName(), key) <= 0) { p.sendMessage(color(msg("no-key").replace("%key%", display(key)))); return; }
        addVirtual(p.getName(), key, -1);
        p.getInventory().addItem(keyItem(key, 1));
        p.sendMessage(color(msg("withdrawn").replace("%key%", display(key))));
    }

    private void kit(Player p, String key) {
        if(!isSurvival(p)) { p.sendMessage(msg("only-survival")); return; }
        if(!takeKey(p, key)) { p.sendMessage(color(msg("no-key").replace("%key%", display(key)))); return; }
        giveKit(p, key);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        p.sendTitle(color("&6&lE-KIT OTWARTY"), display(key), 5, 40, 10);
        p.sendMessage(msg("opened").replace("%kit%", key));
    }

    private boolean takeKey(Player p, String key) {
        ItemStack[] inv = p.getInventory().getContents();
        for(int i=0;i<inv.length;i++) {
            if(key.equals(key(inv[i]))) {
                if(inv[i].getAmount() <= 1) p.getInventory().setItem(i, null);
                else inv[i].setAmount(inv[i].getAmount()-1);
                return true;
            }
        }
        if(getVirtual(p.getName(), key) > 0) {
            addVirtual(p.getName(), key, -1);
            return true;
        }
        return false;
    }

    private void giveKit(Player p, String key) {
        switch(key) {
            case "klasyczny" -> p.getInventory().addItem(new ItemStack(Material.BREAD,32), item(Material.STONE_SWORD, "&fMiecz Klasyczny"), item(Material.STONE_PICKAXE, "&fKilof Klasyczny"), item(Material.STONE_AXE, "&fSiekiera Klasyczna"), item(Material.STONE_SHOVEL, "&fŁopata Klasyczna"), item(Material.STONE_HOE, "&fMotyka Klasyczna"));
            case "zelazny" -> kitTools(p, Material.IRON_HELMET,Material.IRON_CHESTPLATE,Material.IRON_LEGGINGS,Material.IRON_BOOTS,Material.IRON_SWORD,Material.IRON_PICKAXE,Material.IRON_AXE,Material.IRON_SHOVEL,Material.IRON_HOE,"&7Żelazny",1);
            case "diamentowy" -> kitTools(p, Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS,Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.DIAMOND_HOE,"&bDiamentowy",3);
            case "epic" -> kitTools(p, Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS,Material.DIAMOND_SWORD,Material.DIAMOND_PICKAXE,Material.DIAMOND_AXE,Material.DIAMOND_SHOVEL,Material.DIAMOND_HOE,"&5Epic",4);
            case "legendarny", "mityczny", "boski" -> {
                kitTools(p, Material.NETHERITE_HELMET,Material.NETHERITE_CHESTPLATE,Material.NETHERITE_LEGGINGS,Material.NETHERITE_BOOTS,Material.NETHERITE_SWORD,Material.NETHERITE_PICKAXE,Material.NETHERITE_AXE,Material.NETHERITE_SHOVEL,Material.NETHERITE_HOE,"&6"+key,5);
                p.getInventory().addItem(enchant(Material.BOW, "&6Łuk", "power", 5), new ItemStack(Material.GOLDEN_APPLE, key.equals("boski") ? 64 : 16), new ItemStack(Material.TOTEM_OF_UNDYING, key.equals("boski") ? 5 : 2), enchant(Material.ELYTRA, "&6Elytry", "unbreaking", 3));
                if(key.equals("boski")) p.getInventory().addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16), enchant(Material.TRIDENT, "&3Trójząb Boga", "loyalty", 3), enchant(Material.CROSSBOW, "&6Kusza Boga", "quick_charge", 3));
            }
        }
    }

    private void kitTools(Player p, Material h, Material c, Material l, Material b, Material sw, Material pick, Material ax, Material sh, Material hoe, String name, int lvl) {
        p.getInventory().addItem(enchant(h, name+" Hełm","protection",Math.min(4,lvl)), enchant(c,name+" Napierśnik","protection",Math.min(4,lvl)), enchant(l,name+" Spodnie","protection",Math.min(4,lvl)), enchant(b,name+" Buty","feather_falling",Math.min(4,lvl)), enchant(sw,name+" Miecz","sharpness",Math.min(5,lvl)), enchant(pick,name+" Kilof","efficiency",Math.min(5,lvl)), enchant(ax,name+" Siekiera","efficiency",Math.min(5,lvl)), enchant(sh,name+" Łopata","efficiency",Math.min(5,lvl)), enchant(hoe,name+" Motyka","efficiency",Math.min(5,lvl)));
    }

    private ItemStack keyItem(String k, int amount) {
        ItemStack item = new ItemStack(Material.valueOf(getConfig().getString("key-item.material", "TRIPWIRE_HOOK")), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("key-item.name").replace("%key_name%", display(k))));
        ArrayList<String> lore = new ArrayList<>();
        for(String line : getConfig().getStringList("key-item.lore")) lore.add(color(line.replace("%key_name%", display(k))));
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, k);
        item.setItemMeta(meta);
        return item;
    }

    private String key(ItemStack it){ if(it==null||!it.hasItemMeta()) return null; return it.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING); }
    private String action(ItemStack it){ if(it==null||!it.hasItemMeta()) return null; return it.getItemMeta().getPersistentDataContainer().get(action, PersistentDataType.STRING); }

    private ItemStack gui(Material mat, String name, String act, List<String> loreRaw) {
        ItemStack item = item(mat, name);
        ItemMeta meta = item.getItemMeta();
        ArrayList<String> lore = new ArrayList<>();
        for(String l : loreRaw) lore.add(color(l));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(action, PersistentDataType.STRING, act);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack enchant(Material mat, String name, String ench, int lvl) {
        ItemStack item = item(mat, name);
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(ench));
        if(e != null) item.addUnsafeEnchantment(e, lvl);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    private void fill(Inventory inv){ ItemStack f=item(Material.BLACK_STAINED_GLASS_PANE," "); for(int i=0;i<inv.getSize();i++) inv.setItem(i,f); }
    private boolean isSurvival(Player p){ return p.getWorld().getName().equalsIgnoreCase(getConfig().getString("settings.survival-world", "world")); }
    private int physical(Player p, String key){ int x=0; for(ItemStack i:p.getInventory()) if(key.equals(key(i))) x+=i.getAmount(); return x; }
    private int getVirtual(String player,String key){ return data.getInt(path(player)+".keys."+key,0); }
    private void addVirtual(String player,String key,int amount){ data.set(path(player)+".keys."+key, Math.max(0, getVirtual(player,key)+amount)); saveData(); }
    private String path(String p){ return "players."+p.toLowerCase(Locale.ROOT); }
    private String display(String k){ return color(getConfig().getString("keys."+k,k)); }
    private String msg(String k){ return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,"")); }
    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
    private int parse(String s){ try{return Integer.parseInt(s);}catch(Exception e){return 1;} }
    private void saveData(){ try{ data.save(dataFile); } catch(Exception e){ e.printStackTrace(); } }
    private String time(long ms){ long m=ms/60000; long h=m/60; long d=h/24; if(d>0)return d+"d "+(h%24)+"h"; if(h>0)return h+"h "+(m%60)+"m"; return Math.max(1,m)+"m"; }
    private String rollWeekly(){ int total=0; for(String k:getConfig().getConfigurationSection("weekly").getKeys(false)) total+=getConfig().getInt("weekly."+k); int r=random.nextInt(Math.max(1,total))+1,c=0; for(String k:getConfig().getConfigurationSection("weekly").getKeys(false)){c+=getConfig().getInt("weekly."+k); if(r<=c)return k;} return "klasyczny"; }
}
