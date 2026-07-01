package pl.msurvival.economyplus;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalEconomyPlus extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey actionKey;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        actionKey = new NamespacedKey(this, "action");
        dataFile = new File(getDataFolder(), "data.yml");
        try { getDataFolder().mkdirs(); if (!dataFile.exists()) dataFile.createNewFile(); } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickPlaytime, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::expireOffers, 20L * 60L, 20L * 60L * 10L);
    }

    @Override public void onDisable() { save(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("econplus")) {
            if (!sender.hasPermission("msurvival.economyplus.admin")) return true;
            reloadConfig();
            sender.sendMessage(msg("reload"));
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        switch (cmd) {
            case "bank" -> bank(p, args);
            case "daily" -> daily(p);
            case "stats" -> stats(p, args);
            case "osiagniecia", "achievements", "ach" -> achievements(p);
            case "friend", "friends", "znajomy", "znajomi" -> friend(p, args);
            case "team", "druzyna", "party" -> team(p, args);
            case "betterrtp", "brtp", "losuj" -> betterRtp(p);
            case "playershop", "ah", "aukcje", "oferty" -> openPlayerShop(p, 0);
            case "sellhand" -> sellHand(p, args);
            case "buyoffer" -> buyOffer(p, args);
            case "canceloffer" -> cancelOffer(p, args);
            case "bid" -> bid(p, args);
            case "servermarket", "sklepserwera", "sklep" -> openServerMarket(p);
        }
        return true;
    }

    // MONEY shared with MSurvivalMarket file
    private double wallet(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalMarket/balances.yml");
        if (!f.exists()) return data.getDouble("wallet." + player.toLowerCase(Locale.ROOT), 0D);
        return YamlConfiguration.loadConfiguration(f).getDouble("players." + player.toLowerCase(Locale.ROOT), 0D);
    }

    private void addWallet(String player, double amount) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalMarket/balances.yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String path = "players." + player.toLowerCase(Locale.ROOT);
            y.set(path, Math.max(0, y.getDouble(path, 0D) + amount));
            try { y.save(f); } catch (Exception ignored) {}
            return;
        }
        String path = "wallet." + player.toLowerCase(Locale.ROOT);
        data.set(path, Math.max(0, data.getDouble(path, 0D) + amount));
        save();
    }

    // BANK
    private void bank(Player p, String[] args) {
        if (args.length == 0) {
            p.sendMessage(msg("bank-balance").replace("%wallet%", money(wallet(p.getName()))).replace("%bank%", money(bankBalance(p))));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("deposit") && args.length >= 2) {
            double amount = parse(args[1]);
            if (amount <= 0 || wallet(p.getName()) < amount) { p.sendMessage(msg("no-money")); return; }
            addWallet(p.getName(), -amount);
            setBank(p, bankBalance(p) + amount);
            p.sendMessage(msg("bank-deposit").replace("%amount%", money(amount)));
            return;
        }
        if (sub.equals("withdraw") && args.length >= 2) {
            double amount = parse(args[1]);
            if (amount <= 0 || bankBalance(p) < amount) { p.sendMessage(msg("no-money")); return; }
            setBank(p, bankBalance(p) - amount);
            addWallet(p.getName(), amount);
            p.sendMessage(msg("bank-withdraw").replace("%amount%", money(amount)));
            return;
        }
        if (sub.equals("interest")) {
            long cd = getConfig().getLong("settings.bank-interest-cooldown-seconds", 86400L) * 1000L;
            long last = data.getLong(playerPath(p) + ".bank.last_interest", 0L);
            long left = cd - (System.currentTimeMillis() - last);
            if (last > 0 && left > 0) { p.sendMessage(msg("bank-interest-cooldown").replace("%time%", time(left))); return; }
            double interest = bankBalance(p) * (getConfig().getDouble("settings.bank-interest-percent", 2D) / 100D);
            addWallet(p.getName(), interest);
            data.set(playerPath(p) + ".bank.last_interest", System.currentTimeMillis());
            save();
            p.sendMessage(msg("bank-interest").replace("%amount%", money(interest)));
            return;
        }
        p.sendMessage(color("&e/bank deposit <kwota> &8| &e/bank withdraw <kwota> &8| &e/bank interest"));
    }

    private double bankBalance(Player p) { return data.getDouble(playerPath(p) + ".bank.balance", 0D); }
    private void setBank(Player p, double value) { data.set(playerPath(p) + ".bank.balance", Math.max(0, value)); save(); }

    // DAILY
    private void daily(Player p) {
        long cd = getConfig().getLong("settings.daily-cooldown-seconds", 86400L) * 1000L;
        long last = data.getLong(playerPath(p) + ".daily", 0L);
        long left = cd - (System.currentTimeMillis() - last);
        if (last > 0 && left > 0) { p.sendMessage(msg("daily-cooldown").replace("%time%", time(left))); return; }

        double money = getConfig().getDouble("settings.daily-money", 5000D);
        addWallet(p.getName(), money);
        data.set(playerPath(p) + ".daily", System.currentTimeMillis());
        save();

        String keyCommand = getConfig().getString("settings.daily-key-command", "");
        if (keyCommand != null && !keyCommand.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), keyCommand.replace("%player%", p.getName()));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
        p.sendMessage(msg("daily-claimed").replace("%money%", money(money)));
    }

    // STATS
    @EventHandler public void blockBreak(BlockBreakEvent e) {
        addStat(e.getPlayer(), "blocks", 1);
        checkAchievements(e.getPlayer());
    }

    @EventHandler public void death(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        addStat(victim, "deaths", 1);
        if (victim.getKiller() != null) {
            addStat(victim.getKiller(), "kills", 1);
            checkAchievements(victim.getKiller());
        }
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        data.set(playerPath(e.getPlayer()) + ".name", e.getPlayer().getName());
        save();
    }

    private void tickPlaytime() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            addStat(p, "playtime_minutes", 1);
            checkAchievements(p);
        }
    }

    private void addStat(Player p, String stat, long amount) {
        String path = playerPath(p) + ".stats." + stat;
        data.set(path, data.getLong(path, 0L) + amount);
        save();
    }

    private long stat(Player p, String stat) { return data.getLong(playerPath(p) + ".stats." + stat, 0L); }

    private void stats(Player p, String[] args) {
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
        if (target == null) target = p;
        p.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
        p.sendMessage(color("&6&lSTATYSTYKI &8» &e" + target.getName()));
        p.sendMessage(color("&7Kille: &c" + stat(target, "kills")));
        p.sendMessage(color("&7Śmierci: &c" + stat(target, "deaths")));
        p.sendMessage(color("&7Bloki: &a" + stat(target, "blocks")));
        p.sendMessage(color("&7Czas gry: &b" + stat(target, "playtime_minutes") + " min"));
        p.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
    }

    // ACHIEVEMENTS
    private void achievements(Player p) {
        p.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
        p.sendMessage(color("&6&lOSIĄGNIĘCIA"));
        ConfigurationSection sec = getConfig().getConfigurationSection("achievements");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                boolean done = data.getBoolean(playerPath(p) + ".achievements." + id, false);
                p.sendMessage(color((done ? "&a✔ " : "&c✘ ") + getConfig().getString("achievements." + id + ".name", id)));
            }
        }
        p.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
    }

    private void checkAchievements(Player p) {
        ConfigurationSection sec = getConfig().getConfigurationSection("achievements");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            if (data.getBoolean(playerPath(p) + ".achievements." + id, false)) continue;
            String type = getConfig().getString("achievements." + id + ".type", "blocks");
            long amount = getConfig().getLong("achievements." + id + ".amount", 1L);
            if (stat(p, type) >= amount) {
                data.set(playerPath(p) + ".achievements." + id, true);
                double reward = getConfig().getDouble("achievements." + id + ".reward", 0D);
                addWallet(p.getName(), reward);
                p.sendMessage(color("&6&lOsiągnięcie! &f" + getConfig().getString("achievements." + id + ".name", id) + " &8+ &e" + money(reward) + "$"));
                save();
            }
        }
    }

    // FRIENDS
    private void friend(Player p, String[] args) {
        if (args.length < 1) {
            p.sendMessage(color("&e/friend add <gracz>, /friend accept <gracz>, /friend remove <gracz>, /friend list"));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            p.sendMessage(color("&aZnajomi: &e" + String.join(", ", data.getStringList(playerPath(p) + ".friends"))));
            return;
        }
        if (args.length < 2) return;
        String target = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            data.set("friend_requests." + target + "." + p.getName().toLowerCase(Locale.ROOT), true);
            save();
            p.sendMessage(msg("friend-request"));
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t != null) t.sendMessage(msg("friend-received").replace("%player%", p.getName()));
            return;
        }
        if (sub.equals("accept")) {
            String req = "friend_requests." + p.getName().toLowerCase(Locale.ROOT) + "." + target;
            if (!data.getBoolean(req, false)) return;
            addToStringList(playerPath(p) + ".friends", target);
            addToStringList("players." + target + ".friends", p.getName().toLowerCase(Locale.ROOT));
            data.set(req, null);
            save();
            p.sendMessage(msg("friend-added").replace("%player%", args[1]));
            return;
        }
        if (sub.equals("remove")) {
            removeFromStringList(playerPath(p) + ".friends", target);
            removeFromStringList("players." + target + ".friends", p.getName().toLowerCase(Locale.ROOT));
            save();
            p.sendMessage(color("&aUsunięto znajomego."));
        }
    }

    // TEAM
    private void team(Player p, String[] args) {
        if (args.length < 1) {
            p.sendMessage(color("&e/team create <nazwa>, /team invite <gracz>, /team join <nazwa>, /team leave, /team info"));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String current = data.getString(playerPath(p) + ".team");

        if (sub.equals("create") && args.length >= 2) {
            String team = clean(args[1]);
            if (team.isBlank() || current != null) return;
            data.set("teams." + team + ".owner", p.getName().toLowerCase(Locale.ROOT));
            data.set("teams." + team + ".members", new ArrayList<>(List.of(p.getName().toLowerCase(Locale.ROOT))));
            data.set(playerPath(p) + ".team", team);
            save();
            p.sendMessage(msg("team-created").replace("%team%", team));
            return;
        }
        if (sub.equals("invite") && args.length >= 2 && current != null) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return;
            data.set("team_invites." + target.getName().toLowerCase(Locale.ROOT), current);
            save();
            p.sendMessage(msg("team-invite").replace("%player%", target.getName()));
            target.sendMessage(msg("team-received").replace("%player%", p.getName()).replace("%team%", current));
            return;
        }
        if (sub.equals("join") && args.length >= 2) {
            String team = clean(args[1]);
            String invite = data.getString("team_invites." + p.getName().toLowerCase(Locale.ROOT));
            if (!team.equals(invite)) return;
            addToStringList("teams." + team + ".members", p.getName().toLowerCase(Locale.ROOT));
            data.set(playerPath(p) + ".team", team);
            data.set("team_invites." + p.getName().toLowerCase(Locale.ROOT), null);
            save();
            p.sendMessage(color("&aDołączono do drużyny."));
            return;
        }
        if (sub.equals("leave") && current != null) {
            removeFromStringList("teams." + current + ".members", p.getName().toLowerCase(Locale.ROOT));
            data.set(playerPath(p) + ".team", null);
            save();
            p.sendMessage(color("&aOpuszczono drużynę."));
            return;
        }
        if (sub.equals("info") && current != null) {
            p.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage(color("&6Drużyna: &e" + current));
            p.sendMessage(color("&7Członkowie: &e" + String.join(", ", data.getStringList("teams." + current + ".members"))));
            p.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
        }
    }

    // RTP
    private void betterRtp(Player p) {
        int seconds = getConfig().getInt("settings.rtp-countdown-seconds", 5);
        p.sendMessage(msg("rtp-start").replace("%seconds%", String.valueOf(seconds)));
        for (int i = seconds; i > 0; i--) {
            final int left = i;
            Bukkit.getScheduler().runTaskLater(this, () -> p.sendTitle(color("&6&lRTP"), color("&eTeleport za " + left + "s"), 0, 25, 0), 20L * (seconds - i));
        }
        Bukkit.getScheduler().runTaskLater(this, () -> safeTeleport(p), 20L * seconds);
    }

    private void safeTeleport(Player p) {
        World world = Bukkit.getWorld(getConfig().getString("settings.rtp-world", "world"));
        if (world == null) world = p.getWorld();
        int radius = getConfig().getInt("settings.rtp-radius", 2500);
        int attempts = getConfig().getInt("settings.rtp-max-attempts", 24);
        for (int i = 0; i < attempts; i++) {
            int x = random.nextInt(radius * 2 + 1) - radius;
            int z = random.nextInt(radius * 2 + 1) - radius;
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (safe(loc)) {
                p.teleport(loc);
                p.sendMessage(msg("rtp-success"));
                return;
            }
        }
        p.sendMessage(color("&cNie znaleziono bezpiecznego miejsca. Spróbuj ponownie."));
    }

    private boolean safe(Location loc) {
        Block feet = loc.getBlock();
        Block ground = loc.clone().subtract(0,1,0).getBlock();
        Material g = ground.getType();
        return feet.getType().isAir() && g.isSolid() && g != Material.LAVA && g != Material.WATER && !g.name().contains("LEAVES");
    }

    // PLAYER MARKET / AUCTIONS
    private void openPlayerShop(Player p, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&6&lRYNEK GRACZY"));
        fill(inv);
        ConfigurationSection sec = data.getConfigurationSection("offers");
        int slot = 0;
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                if (slot >= 45) break;
                ItemStack item = data.getItemStack("offers." + id + ".item");
                if (item == null) continue;
                ItemStack display = item.clone();
                ItemMeta meta = display.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(color("&8━━━━━━━━━━━━━━━━"));
                lore.add(color("&7ID: &e" + id));
                lore.add(color("&7Cena: &a" + money(data.getDouble("offers." + id + ".price")) + "$"));
                lore.add(color("&7Sprzedawca: &e" + data.getString("offers." + id + ".seller")));
                lore.add(color("&a/buyoffer " + id));
                meta.setLore(lore);
                display.setItemMeta(meta);
                inv.setItem(slot++, display);
            }
        }
        inv.setItem(49, button(Material.GOLD_INGOT, "&e&lWystaw item", "none", List.of("&7Trzymaj item w ręce:", "&e/sellhand <cena>")));
        p.openInventory(inv);
    }

    private void sellHand(Player p, String[] args) {
        if (args.length < 1) { p.sendMessage(color("&c/sellhand <cena>")); return; }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        double price = parse(args[0]);
        if (price <= 0) return;
        String id = String.valueOf(data.getLong("next_offer_id", 1L));
        data.set("next_offer_id", Long.parseLong(id) + 1L);
        data.set("offers." + id + ".seller", p.getName());
        data.set("offers." + id + ".price", price);
        data.set("offers." + id + ".created", System.currentTimeMillis());
        data.set("offers." + id + ".item", item.clone());
        p.getInventory().setItemInMainHand(null);
        save();
        p.sendMessage(msg("offer-created").replace("%id%", id).replace("%price%", money(price)));
    }

    private void buyOffer(Player p, String[] args) {
        if (args.length < 1) return;
        String id = args[0];
        if (!data.contains("offers." + id)) return;
        String seller = data.getString("offers." + id + ".seller");
        double price = data.getDouble("offers." + id + ".price");
        ItemStack item = data.getItemStack("offers." + id + ".item");
        if (item == null) return;
        if (wallet(p.getName()) < price) { p.sendMessage(msg("no-money")); return; }
        addWallet(p.getName(), -price);
        double tax = getConfig().getDouble("settings.tax-percent", 5D) / 100D;
        addWallet(seller, price * (1D - tax));
        p.getInventory().addItem(item);
        data.set("offers." + id, null);
        save();
        p.sendMessage(msg("offer-bought").replace("%id%", id));
        Player s = Bukkit.getPlayerExact(seller);
        if (s != null) s.sendMessage(msg("offer-sold").replace("%id%", id).replace("%money%", money(price * (1D - tax))));
    }

    private void cancelOffer(Player p, String[] args) {
        if (args.length < 1) return;
        String id = args[0];
        String seller = data.getString("offers." + id + ".seller", "");
        if (!seller.equalsIgnoreCase(p.getName()) && !p.hasPermission("msurvival.economyplus.admin")) return;
        ItemStack item = data.getItemStack("offers." + id + ".item");
        if (item != null) p.getInventory().addItem(item);
        data.set("offers." + id, null);
        save();
        p.sendMessage(msg("offer-cancelled").replace("%id%", id));
    }

    private void bid(Player p, String[] args) {
        if (args.length < 2) return;
        String id = args[0];
        double amount = parse(args[1]);
        if (!data.contains("auctions." + id)) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR || amount <= 0) return;
            data.set("auctions." + id + ".seller", p.getName());
            data.set("auctions." + id + ".item", item.clone());
            data.set("auctions." + id + ".highest", amount);
            data.set("auctions." + id + ".bidder", p.getName());
            data.set("auctions." + id + ".created", System.currentTimeMillis());
            p.getInventory().setItemInMainHand(null);
            save();
            p.sendMessage(msg("auction-created").replace("%id%", id));
            return;
        }
        double highest = data.getDouble("auctions." + id + ".highest", 0D);
        if (amount <= highest || wallet(p.getName()) < amount) return;
        data.set("auctions." + id + ".highest", amount);
        data.set("auctions." + id + ".bidder", p.getName());
        save();
        p.sendMessage(msg("bid-placed").replace("%id%", id).replace("%amount%", money(amount)));
    }

    private void expireOffers() {
        long offerMs = getConfig().getLong("settings.offer-expire-hours", 168L) * 3600_000L;
        ConfigurationSection sec = data.getConfigurationSection("offers");
        if (sec != null) {
            for (String id : new ArrayList<>(sec.getKeys(false))) {
                long created = data.getLong("offers." + id + ".created", 0L);
                if (created > 0 && System.currentTimeMillis() - created > offerMs) {
                    String seller = data.getString("offers." + id + ".seller");
                    ItemStack item = data.getItemStack("offers." + id + ".item");
                    if (seller != null && item != null) {
                        Player p = Bukkit.getPlayerExact(seller);
                        if (p != null) p.getInventory().addItem(item);
                    }
                    data.set("offers." + id, null);
                }
            }
            save();
        }
    }

    // SERVER SHOP
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
                inv.setItem(slot++, button(mat, "&e&l" + matName.toLowerCase(Locale.ROOT).replace("_"," "), "buy_server:" + matName, List.of("&7Cena: &a" + money(price) + "$", "&aKliknij, aby kupić.")));
            }
        }
        p.openInventory(inv);
    }

    @EventHandler public void invClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.equals(color("&6&lSKLEP SERWERA"))) return;
        e.setCancelled(true);
        String action = action(e.getCurrentItem());
        if (action == null || !action.startsWith("buy_server:")) return;
        String matName = action.substring("buy_server:".length());
        double price = getConfig().getDouble("server-shop." + matName, 999999D);
        if (wallet(p.getName()) < price) { p.sendMessage(msg("no-money")); return; }
        Material mat;
        try { mat = Material.valueOf(matName); } catch (Exception ex) { return; }
        addWallet(p.getName(), -price);
        p.getInventory().addItem(new ItemStack(mat, 1));
        p.sendMessage(color("&aKupiono za &e" + money(price) + "$"));
    }

    // UTILS
    private String playerPath(Player p) { return "players." + p.getName().toLowerCase(Locale.ROOT); }

    private void addToStringList(String path, String value) {
        List<String> list = new ArrayList<>(data.getStringList(path));
        if (list.stream().noneMatch(x -> x.equalsIgnoreCase(value))) list.add(value);
        data.set(path, list);
    }

    private void removeFromStringList(String path, String value) {
        List<String> list = new ArrayList<>(data.getStringList(path));
        list.removeIf(x -> x.equalsIgnoreCase(value));
        data.set(path, list);
    }

    private String clean(String s) { return s == null ? "" : s.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(Locale.ROOT); }
    private double parse(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0D; } }
    private String money(double v) { return v == (long)v ? String.valueOf((long)v) : String.format(Locale.US, "%.2f", v); }

    private String time(long ms) {
        long sec = Math.max(1, ms / 1000L);
        long d = sec / 86400L; sec %= 86400L;
        long h = sec / 3600L; sec %= 3600L;
        long m = sec / 60L; long s = sec % 60L;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + s + "s";
    }

    private void fill(Inventory inv) {
        ItemStack glass = button(Material.BLACK_STAINED_GLASS_PANE, " ", "none", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
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

    private String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void save() { try { data.save(dataFile); } catch (Exception ignored) {} }
    private String msg(String key) { return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
