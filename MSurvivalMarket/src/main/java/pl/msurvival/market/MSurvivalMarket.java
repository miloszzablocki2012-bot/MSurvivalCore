package pl.msurvival.market;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalMarket extends JavaPlugin implements Listener {
    private File balancesFile;
    private File offersFile;
    private YamlConfiguration balances;
    private YamlConfiguration offers;
    private NamespacedKey actionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "market_action");

        balancesFile = new File(getDataFolder(), "balances.yml");
        offersFile = new File(getDataFolder(), "offers.yml");
        try {
            getDataFolder().mkdirs();
            if (!balancesFile.exists()) balancesFile.createNewFile();
            if (!offersFile.exists()) offersFile.createNewFile();
        } catch (Exception ignored) {}

        balances = YamlConfiguration.loadConfiguration(balancesFile);
        offers = YamlConfiguration.loadConfiguration(offersFile);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::expireOffers, 1200L, 1200L * 10L);
    }

    @Override
    public void onDisable() {
        saveBalances();
        saveOffers();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("rynek") || cmd.equals("sklep")) {
            if (sender instanceof Player p) openMain(p);
            return true;
        }

        if (cmd.equals("ah") || cmd.equals("aukcje") || cmd.equals("playershop") || cmd.equals("sklepgraczy")) {
            if (sender instanceof Player p) openPlayerMarket(p);
            return true;
        }

        if (cmd.equals("kasa") || cmd.equals("bal")) {
            if (sender instanceof Player p) p.sendMessage(msg("balance").replace("%money%", money(balance(p.getName()))));
            return true;
        }

        if (cmd.equals("baltop")) {
            sendBaltop(sender);
            return true;
        }

        if ((cmd.equals("sell") || cmd.equals("sellall")) && sender instanceof Player p) {
            double earned = sellAll(p);
            p.sendMessage(prefix() + color("&aSprzedano itemy za &e" + money(earned) + "$"));
            return true;
        }

        if (cmd.equals("kosz") && sender instanceof Player p) {
            p.openInventory(Bukkit.createInventory(null, 54, color("&c&lKOSZ &8» &7wrzuć itemy")));
            return true;
        }

        if (cmd.equals("pay") && sender instanceof Player p) {
            if (args.length < 2) return true;
            double amount = parse(args[1]);
            if (amount <= 0 || balance(p.getName()) < amount) { p.sendMessage(msg("no-money")); return true; }
            add(p.getName(), -amount);
            add(args[0], amount);
            p.sendMessage(msg("paid").replace("%amount%", money(amount)).replace("%player%", args[0]));
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) target.sendMessage(msg("received").replace("%amount%", money(amount)).replace("%player%", p.getName()));
            return true;
        }

        if (cmd.equals("sellhand") && sender instanceof Player p) {
            sellHand(p, args);
            return true;
        }

        if (cmd.equals("buyoffer") && sender instanceof Player p) {
            buyOffer(p, args);
            return true;
        }

        if (cmd.equals("canceloffer") && sender instanceof Player p) {
            cancelOffer(p, args);
            return true;
        }

        if (cmd.equals("mojeoferty") && sender instanceof Player p) {
            openMyOffers(p);
            return true;
        }

        if (cmd.equals("moneyadmin") || cmd.equals("kasaadmin") || cmd.equals("adminmoney") || cmd.equals("eco")) {
            if (!sender.hasPermission("msurvival.market.admin")) { sender.sendMessage(msg("no-permission")); return true; }
            if (args.length < 3) {
                sender.sendMessage(color("&c/moneyadmin <give|take|set> <gracz> <kwota>"));
                return true;
            }
            String mode = args[0].toLowerCase(Locale.ROOT);
            String player = args[1];
            double amount = parse(args[2]);
            if (mode.equals("give")) add(player, amount);
            else if (mode.equals("take")) add(player, -amount);
            else if (mode.equals("set")) set(player, amount);
            else return true;
            sender.sendMessage(msg("admin-money").replace("%player%", player).replace("%money%", money(balance(player))));
            return true;
        }

        if (cmd.equals("rynekadmin")) {
            if (!sender.hasPermission("msurvival.market.admin")) { sender.sendMessage(msg("no-permission")); return true; }
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                balances = YamlConfiguration.loadConfiguration(balancesFile);
                offers = YamlConfiguration.loadConfiguration(offersFile);
                sender.sendMessage(color("&aPrzeładowano rynek."));
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("clearoffers")) {
                offers.set("offers", null);
                saveOffers();
                sender.sendMessage(color("&cWyczyszczono oferty."));
                return true;
            }
        }

        return true;
    }

    private void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, color("&6&lRYNEK MSURVIVAL"));
        fill(inv);
        inv.setItem(10, button(Material.BAMBOO, "&a&lSkup serwera", "sell_all", List.of(
                "&7Sprzedaj farmy i surowce.",
                "&aBambus i trzcina są bardzo wartościowe.",
                "",
                "&eKliknij, aby sprzedać."
        )));
        inv.setItem(12, button(Material.TRIPWIRE_HOOK, "&6&lKlucze za dolary", "open_keys", List.of(
                "&7Kup wszystkie typy kluczy.",
                "&7Ceny są wysokie.",
                "",
                "&eKliknij, aby otworzyć."
        )));
        inv.setItem(14, button(Material.CHEST, "&e&lSklep graczy", "open_playershop", List.of(
                "&7Rzeczy wystawione przez graczy.",
                "&7Możesz kupować i sprzedawać itemy.",
                "",
                "&eKliknij, aby otworzyć."
        )));
        inv.setItem(16, button(Material.EMERALD, "&b&lSklep serwera", "open_server", List.of(
                "&7Stały sklep z podstawowymi itemami.",
                "",
                "&eKliknij, aby otworzyć."
        )));
        inv.setItem(31, button(Material.GOLD_INGOT, "&a&lKasa: &e" + money(balance(p.getName())) + "$", "none", List.of(
                "&7Twoje pieniądze na serwerze.",
                "&7Admin: &e/moneyadmin give nick kwota"
        )));
        p.openInventory(inv);
    }

    private void openKeys(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&6&lRYNEK &8» &eKLUCZE"));
        fill(inv);
        String[] keys = {"klasyczny","zelazny","diamentowy","epic","legendarny","mityczny","boski"};
        int slot = 10;
        for (String key : keys) {
            double price = getConfig().getDouble("keys." + key, 999999);
            inv.setItem(slot++, button(Material.TRIPWIRE_HOOK, "&e&l" + key.toUpperCase(Locale.ROOT), "buy_key:" + key, List.of(
                    "&7Cena: &a" + money(price) + "$",
                    "&7Stan konta: &e" + money(balance(p.getName())) + "$",
                    "",
                    "&aKliknij, aby kupić."
            )));
        }
        p.openInventory(inv);
    }

    private void openServerMarket(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lSKLEP SERWERA"));
        fill(inv);
        ConfigurationSection sec = getConfig().getConfigurationSection("server-shop");
        int slot = 0;
        if (sec != null) {
            for (String matName : sec.getKeys(false)) {
                if (slot >= 45) break;
                Material mat;
                try { mat = Material.valueOf(matName); } catch (Exception e) { continue; }
                double price = sec.getDouble(matName);
                inv.setItem(slot++, button(mat, "&e&l" + nice(matName), "buy_server:" + matName, List.of(
                        "&7Cena: &a" + money(price) + "$",
                        "&aKliknij, aby kupić 1 szt."
                )));
            }
        }
        p.openInventory(inv);
    }

    private void openPlayerMarket(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lSKLEP GRACZY"));
        fill(inv);

        ConfigurationSection sec = offers.getConfigurationSection("offers");
        int slot = 0;
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                if (slot >= 45) break;
                ItemStack item = offers.getItemStack("offers." + id + ".item");
                if (item == null || item.getType() == Material.AIR) continue;

                ItemStack display = item.clone();
                ItemMeta meta = display.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                                lore.add(color("&7ID: &e" + id));
                lore.add(color("&7Cena: &a" + money(offers.getDouble("offers." + id + ".price")) + "$"));
                lore.add(color("&7Sprzedawca: &e" + offers.getString("offers." + id + ".seller")));
                lore.add(color(""));
                lore.add(color("&aKliknij, aby kupić."));
                lore.add(color("&7Komenda: &e/buyoffer " + id));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy_offer:" + id);
                display.setItemMeta(meta);
                inv.setItem(slot++, display);
            }
        }

        inv.setItem(49, button(Material.GOLD_INGOT, "&e&lWystaw item", "none", List.of(
                "&7Trzymaj item w ręce i wpisz:",
                "&e/sellhand <cena>"
        )));
        inv.setItem(50, button(Material.BOOK, "&b&lMoje oferty", "my_offers", List.of(
                "&7Zobacz swoje aktywne oferty.",
                "&e/mojeoferty"
        )));
        p.openInventory(inv);
    }

    private void openMyOffers(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lMOJE OFERTY"));
        fill(inv);
        ConfigurationSection sec = offers.getConfigurationSection("offers");
        int slot = 0;
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                if (!p.getName().equalsIgnoreCase(offers.getString("offers." + id + ".seller", ""))) continue;
                if (slot >= 45) break;
                ItemStack item = offers.getItemStack("offers." + id + ".item");
                if (item == null) continue;
                ItemStack display = item.clone();
                ItemMeta meta = display.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                                lore.add(color("&7ID: &e" + id));
                lore.add(color("&7Cena: &a" + money(offers.getDouble("offers." + id + ".price")) + "$"));
                lore.add(color("&cKliknij, aby anulować."));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "cancel_offer:" + id);
                display.setItemMeta(meta);
                inv.setItem(slot++, display);
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();

        if (title.equals(color("&6&lRYNEK MSURVIVAL")) || title.equals(color("&6&lRYNEK &8» &eKLUCZE")) || title.equals(color("&6&lSKLEP GRACZY")) || title.equals(color("&6&lMOJE OFERTY")) || title.equals(color("&6&lSKLEP SERWERA"))) {
            e.setCancelled(true);
            String action = action(e.getCurrentItem());
            if (action == null) return;

            if (action.equals("sell_all")) {
                double earned = sellAll(p);
                p.closeInventory();
                p.sendMessage(prefix() + color("&aSprzedano itemy za &e" + money(earned) + "$"));
                return;
            }
            if (action.equals("open_keys")) { openKeys(p); return; }
            if (action.equals("open_playershop")) { openPlayerMarket(p); return; }
            if (action.equals("open_server")) { openServerMarket(p); return; }
            if (action.equals("my_offers")) { openMyOffers(p); return; }

            if (action.startsWith("buy_key:")) {
                String key = action.substring("buy_key:".length());
                double price = getConfig().getDouble("keys." + key, 999999);
                if (!takeMoney(p, price)) return;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "keyadmin give " + p.getName() + " " + key + " 1");
                p.sendMessage(color("&aKupiono klucz za &e" + money(price) + "$"));
                return;
            }

            if (action.startsWith("buy_server:")) {
                String matName = action.substring("buy_server:".length());
                double price = getConfig().getDouble("server-shop." + matName, 999999);
                Material mat;
                try { mat = Material.valueOf(matName); } catch (Exception ex) { return; }
                if (!takeMoney(p, price)) return;
                p.getInventory().addItem(new ItemStack(mat));
                p.sendMessage(color("&aKupiono za &e" + money(price) + "$"));
                return;
            }

            if (action.startsWith("buy_offer:")) {
                buyOffer(p, new String[]{action.substring("buy_offer:".length())});
                return;
            }

            if (action.startsWith("cancel_offer:")) {
                cancelOffer(p, new String[]{action.substring("cancel_offer:".length())});
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(color("&c&lKOSZ &8» &7wrzuć itemy"))) {
            e.getInventory().clear();
            if (e.getPlayer() instanceof Player p) p.sendMessage(prefix() + color("&aKosz został wyczyszczony."));
        }
    }

    private void sendBaltop(CommandSender sender) {
        ConfigurationSection section = balances.getConfigurationSection("players");
        if (section == null) { sender.sendMessage(prefix() + color("&cBrak danych ekonomii.")); return; }
        List<String> players = new ArrayList<>(section.getKeys(false));
        players.sort((a, b) -> Double.compare(balance(b), balance(a)));
        sender.sendMessage(color("&6&lTOP KASA"));
        for (int i = 0; i < Math.min(10, players.size()); i++) {
            String player = players.get(i);
            sender.sendMessage(color("&e" + (i + 1) + ". &f" + player + " &8» &a" + money(balance(player)) + "$"));
        }
    }

    private void sellHand(Player p, String[] args) {
        if (args.length < 1) { p.sendMessage(color("&c/sellhand <cena>")); return; }
        if (countOffers(p.getName()) >= getConfig().getInt("settings.max-offers-per-player", 20)) {
            p.sendMessage(msg("max-offers"));
            return;
        }
        double price = parse(args[0]);
        if (price <= 0) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        String id = String.valueOf(offers.getLong("next-id", 1));
        offers.set("next-id", Long.parseLong(id) + 1L);
        offers.set("offers." + id + ".seller", p.getName());
        offers.set("offers." + id + ".price", price);
        offers.set("offers." + id + ".created", System.currentTimeMillis());
        offers.set("offers." + id + ".item", item.clone());
        saveOffers();

        p.getInventory().setItemInMainHand(null);
        p.sendMessage(msg("offer-created").replace("%id%", id).replace("%price%", money(price)));
    }

    private void buyOffer(Player p, String[] args) {
        if (args.length < 1) return;
        String id = args[0];
        if (!offers.contains("offers." + id)) return;

        String seller = offers.getString("offers." + id + ".seller");
        double price = offers.getDouble("offers." + id + ".price");
        ItemStack item = offers.getItemStack("offers." + id + ".item");
        if (item == null || seller == null) return;
        if (seller.equalsIgnoreCase(p.getName())) { p.sendMessage(color("&cNie możesz kupić własnej oferty.")); return; }
        if (!takeMoney(p, price)) return;

        double tax = getConfig().getDouble("settings.player-market-tax-percent", 5.0) / 100.0;
        double sellerMoney = price * (1.0 - tax);
        add(seller, sellerMoney);
        giveOrDrop(p, item);
        offers.set("offers." + id, null);
        saveOffers();

        p.sendMessage(msg("offer-bought").replace("%id%", id));
        Player sellerPlayer = Bukkit.getPlayerExact(seller);
        if (sellerPlayer != null) sellerPlayer.sendMessage(msg("offer-sold").replace("%id%", id).replace("%money%", money(sellerMoney)));
    }

    private void cancelOffer(Player p, String[] args) {
        if (args.length < 1) return;
        String id = args[0];
        if (!offers.contains("offers." + id)) return;
        String seller = offers.getString("offers." + id + ".seller", "");
        if (!seller.equalsIgnoreCase(p.getName()) && !p.hasPermission("msurvival.market.admin")) return;

        ItemStack item = offers.getItemStack("offers." + id + ".item");
        if (item != null) giveOrDrop(p, item);
        offers.set("offers." + id, null);
        saveOffers();
        p.sendMessage(msg("offer-cancelled").replace("%id%", id));
    }

    private void expireOffers() {
        long expireMs = getConfig().getLong("settings.offer-expire-hours", 168L) * 3600_000L;
        ConfigurationSection sec = offers.getConfigurationSection("offers");
        if (sec == null) return;
        for (String id : new ArrayList<>(sec.getKeys(false))) {
            long created = offers.getLong("offers." + id + ".created", 0L);
            if (created > 0 && System.currentTimeMillis() - created > expireMs) {
                String seller = offers.getString("offers." + id + ".seller");
                ItemStack item = offers.getItemStack("offers." + id + ".item");
                Player p = seller == null ? null : Bukkit.getPlayerExact(seller);
                if (p != null && item != null) {
                    giveOrDrop(p, item);
                    p.sendMessage(color("&eTwoja oferta ID " + id + " wygasła i wróciła do ekwipunku."));
                    offers.set("offers." + id, null);
                } else {
                    offers.set("offers." + id + ".expired", true);
                }
            }
        }
        saveOffers();
    }

    private int countOffers(String player) {
        int count = 0;
        ConfigurationSection sec = offers.getConfigurationSection("offers");
        if (sec == null) return 0;
        for (String id : sec.getKeys(false)) {
            if (player.equalsIgnoreCase(offers.getString("offers." + id + ".seller", ""))) count++;
        }
        return count;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private boolean takeMoney(Player p, double amount) {
        if (balance(p.getName()) < amount) {
            p.sendMessage(msg("no-money"));
            return false;
        }
        add(p.getName(), -amount);
        return true;
    }

    private double sellAll(Player p) {
        double total = 0;
        ConfigurationSection section = getConfig().getConfigurationSection("sell");
        if (section == null) return 0;
        for (String matName : section.getKeys(false)) {
            Material mat;
            try { mat = Material.valueOf(matName); } catch (Exception e) { continue; }
            double price = section.getDouble(matName);
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() == mat) {
                    total += item.getAmount() * price;
                    item.setAmount(0);
                }
            }
        }
        add(p.getName(), total);
        return total;
    }

    private ItemStack button(Material mat, String name, String action, List<String> loreRaw) {
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
        ItemStack glass = button(Material.BLACK_STAINED_GLASS_PANE, " ", "none", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
    }

    private String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String nice(String s) { return s.toLowerCase(Locale.ROOT).replace("_", " "); }
    private double parse(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private String money(double v) { return v == (long)v ? String.valueOf((long)v) : String.format(Locale.US, "%.2f", v); }
    private double balance(String player) { return balances.getDouble("players." + player.toLowerCase(Locale.ROOT), 0); }
    private void set(String player, double amount) { balances.set("players." + player.toLowerCase(Locale.ROOT), Math.max(0, amount)); saveBalances(); }
    private void add(String player, double amount) { set(player, balance(player) + amount); }

    private void saveBalances() { try { balances.save(balancesFile); } catch (Exception ignored) {} }
    private void saveOffers() { try { offers.save(offersFile); } catch (Exception ignored) {} }
    private String prefix() { return color(getConfig().getString("messages.prefix", "&6&lRynek &8» &r")); }
    private String msg(String key) { return prefix() + color(getConfig().getString("messages." + key, "")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
