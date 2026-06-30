package pl.msurvival.admin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

@SuppressWarnings("deprecation")
public final class MSurvivalAdmin extends JavaPlugin implements Listener {
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> staffMode = new HashSet<>();
    private final Set<UUID> god = new HashSet<>();
    private final Set<UUID> socialSpy = new HashSet<>();

    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadPersistedVanish();

        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (vanished.contains(p.getUniqueId())) {
                    p.sendActionBar(color(getConfig().getString("vanish.actionbar", "&d👻 &fVANISH &aWŁĄCZONY")));
                }
            }
        }, 20L, 40L);
    }

    @Override
    public void onDisable() {
        savePersistedVanish();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("staffchat")) {
            if (!sender.hasPermission("msurvival.admin.staff")) return deny(sender);
            if (args.length < 1) return true;
            staffChat(sender.getName(), String.join(" ", args));
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        if (cmd.equals("vanish")) {
            if (!p.hasPermission("msurvival.admin.vanish")) return deny(p);
            toggleVanish(p);
            return true;
        }

        if (cmd.equals("staff")) {
            if (!p.hasPermission("msurvival.admin.staff")) return deny(p);
            toggleStaff(p);
            return true;
        }

        if (!p.hasPermission("msurvival.admin")) return deny(p);

        switch (cmd) {
            case "fly" -> {
                p.setAllowFlight(!p.getAllowFlight());
                p.sendMessage(prefix() + color(p.getAllowFlight() ? "&aFly włączony." : "&cFly wyłączony."));
            }
            case "god" -> {
                if (god.remove(p.getUniqueId())) p.sendMessage(prefix() + color("&cGod wyłączony."));
                else { god.add(p.getUniqueId()); p.sendMessage(prefix() + color("&aGod włączony.")); }
            }
            case "gm" -> {
                if (args.length < 1) return true;
                p.setGameMode(switch (args[0]) {
                    case "1", "creative" -> GameMode.CREATIVE;
                    case "2", "adventure" -> GameMode.ADVENTURE;
                    case "3", "spectator" -> GameMode.SPECTATOR;
                    default -> GameMode.SURVIVAL;
                });
            }
            case "heal" -> target(args, p).ifPresent(t -> t.setHealth(t.getMaxHealth()));
            case "feed" -> target(args, p).ifPresent(t -> { t.setFoodLevel(20); t.setSaturation(20); });
            case "invsee" -> {
                if (args.length > 0) {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t != null) p.openInventory(t.getInventory());
                }
            }
            case "ecsee" -> {
                if (args.length > 0) {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t != null) p.openInventory(t.getEnderChest());
                }
            }
            case "freeze" -> {
                if (args.length > 0) {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t != null) {
                        frozen.add(t.getUniqueId());
                        p.sendMessage(prefix() + color(getConfig().getString("messages.freeze-on").replace("%player%", t.getName())));
                        t.sendMessage(prefix() + msg("frozen"));
                    }
                }
            }
            case "unfreeze" -> {
                if (args.length > 0) {
                    Player t = Bukkit.getPlayerExact(args[0]);
                    if (t != null) {
                        frozen.remove(t.getUniqueId());
                        p.sendMessage(prefix() + color(getConfig().getString("messages.freeze-off").replace("%player%", t.getName())));
                    }
                }
            }
            case "socialspy" -> {
                if (socialSpy.remove(p.getUniqueId())) p.sendMessage(prefix() + msg("socialspy-off"));
                else { socialSpy.add(p.getUniqueId()); p.sendMessage(prefix() + msg("socialspy-on")); }
            }
            case "admin" -> openAdminGui(p);
        }

        return true;
    }

    private Optional<Player> target(String[] args, Player fallback) {
        if (args.length > 0) {
            Player t = Bukkit.getPlayerExact(args[0]);
            return Optional.ofNullable(t);
        }
        return Optional.of(fallback);
    }

    private void toggleVanish(Player p) {
        if (vanished.remove(p.getUniqueId())) {
            for (Player online : Bukkit.getOnlinePlayers()) online.showPlayer(this, p);
            p.sendMessage(prefix() + msg("vanish-off"));
        } else {
            vanished.add(p.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("msurvival.admin.vanish")) online.hidePlayer(this, p);
            }
            p.sendMessage(prefix() + msg("vanish-on"));
        }
        savePersistedVanish();
    }

    private void toggleStaff(Player p) {
        if (staffMode.remove(p.getUniqueId())) {
            p.sendMessage(prefix() + msg("staff-off"));
            return;
        }
        staffMode.add(p.getUniqueId());
        if (!vanished.contains(p.getUniqueId())) toggleVanish(p);
        p.setAllowFlight(true);
        god.add(p.getUniqueId());
        giveStaffItems(p);
        p.sendMessage(prefix() + msg("staff-on"));
    }

    private void giveStaffItems(Player p) {
        if (!getConfig().getBoolean("staff-items.enabled", true)) return;
        p.getInventory().setItem(0, named(Material.COMPASS, "&c&lTeleport"));
        p.getInventory().setItem(1, named(Material.ICE, "&b&lFreeze"));
        p.getInventory().setItem(4, named(Material.NETHER_STAR, "&6&lAdmin GUI"));
        p.getInventory().setItem(8, named(Material.BARRIER, "&c&lWyjdź ze StaffMode"));
    }

    private void openAdminGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&c&lADMIN PANEL"));
        ItemStack glass = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
        inv.setItem(10, named(Material.ENDER_EYE, "&d&lVanish"));
        inv.setItem(11, named(Material.FEATHER, "&f&lFly"));
        inv.setItem(12, named(Material.GOLDEN_APPLE, "&a&lHeal"));
        inv.setItem(13, named(Material.COOKED_BEEF, "&6&lFeed"));
        inv.setItem(14, named(Material.ICE, "&b&lFreeze"));
        inv.setItem(15, named(Material.CHEST, "&e&lInvsee"));
        inv.setItem(16, named(Material.ENDER_CHEST, "&5&lECSee"));
        p.openInventory(inv);
    }

    @EventHandler
    public void guiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(color("&c&lADMIN PANEL"))) return;
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
        p.closeInventory();
        if (name.contains("vanish")) p.performCommand("vanish");
        else if (name.contains("fly")) p.performCommand("fly");
        else if (name.contains("heal")) p.performCommand("heal");
        else if (name.contains("feed")) p.performCommand("feed");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();

        for (UUID uuid : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(uuid);
            if (vanishedPlayer != null && !joined.hasPermission("msurvival.admin.vanish")) {
                joined.hidePlayer(this, vanishedPlayer);
            }
        }

        if (vanished.contains(joined.getUniqueId())) {
            e.setJoinMessage(null);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.hasPermission("msurvival.admin.vanish")) online.hidePlayer(this, joined);
                }
            }, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) e.setQuitMessage(null);
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void cmd(PlayerCommandPreprocessEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(prefix() + msg("frozen"));
            return;
        }
        String lower = e.getMessage().toLowerCase(Locale.ROOT);
        if (lower.startsWith("/msg ") || lower.startsWith("/tell ") || lower.startsWith("/w ") || lower.startsWith("/r ")) {
            for (UUID id : socialSpy) {
                Player spy = Bukkit.getPlayer(id);
                if (spy != null && !spy.equals(e.getPlayer())) {
                    spy.sendMessage(color("&8[SPY] &f" + e.getPlayer().getName() + " &8» &7" + e.getMessage()));
                }
            }
        }
    }

    @EventHandler
    public void damage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (!staffMode.contains(e.getPlayer().getUniqueId())) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
        if (name.contains("admin gui")) {
            e.setCancelled(true);
            openAdminGui(e.getPlayer());
        }
        if (name.contains("wyjdź")) {
            e.setCancelled(true);
            toggleStaff(e.getPlayer());
        }
    }

    private void staffChat(String sender, String message) {
        String formatted = color(getConfig().getString("messages.staffchat-format")
                .replace("%player%", sender)
                .replace("%message%", message));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("msurvival.admin.staff")) p.sendMessage(formatted);
        }
        Bukkit.getConsoleSender().sendMessage(formatted);
    }

    private void loadPersistedVanish() {
        if (!getConfig().getBoolean("vanish.persist", true)) return;
        for (String raw : data.getStringList("vanished")) {
            try { vanished.add(UUID.fromString(raw)); } catch (Exception ignored) {}
        }
    }

    private void savePersistedVanish() {
        if (!getConfig().getBoolean("vanish.persist", true)) return;
        List<String> list = new ArrayList<>();
        for (UUID id : vanished) list.add(id.toString());
        data.set("vanished", list);
        try { data.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private ItemStack named(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(prefix() + msg("no-permission"));
        return true;
    }

    private String prefix() {
        return color(getConfig().getString("messages.prefix", "&c&lAdmin &8» &r"));
    }

    private String msg(String key) {
        return color(getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
