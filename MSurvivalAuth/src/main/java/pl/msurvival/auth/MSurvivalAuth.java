package pl.msurvival.auth;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;

public final class MSurvivalAuth extends JavaPlugin implements Listener {
    private File file;
    private YamlConfiguration data;
    private final Set<UUID> logged = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "auth.yml");
        if (!file.exists()) {
            try {
                getDataFolder().mkdirs();
                file.createNewFile();
            } catch (Exception ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("authbypass")) {
            if (!sender.hasPermission("msurvival.auth.admin")) return true;
            List<String> list = new ArrayList<>(getConfig().getStringList("settings.bypass"));
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(color("&aBypass: &e" + String.join(", ", list)));
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("add")) list.add(args[1]);
            if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) list.removeIf(x -> x.equalsIgnoreCase(args[1]));
            getConfig().set("settings.bypass", list);
            saveConfig();
            sender.sendMessage(color("&aZmieniono bypass."));
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        if (premium(p) || bypass(p)) {
            logged.add(p.getUniqueId());
            p.sendMessage(premium(p) ? msg("premium") : msg("bypassed"));
            return true;
        }

        if (cmd.equals("register")) {
            if (registered(p)) {
                p.sendMessage(msg("already-registered"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(msg("register"));
                return true;
            }
            data.set("players." + p.getUniqueId() + ".name", p.getName());
            data.set("players." + p.getUniqueId() + ".password", hash(args[0]));
            logged.add(p.getUniqueId());
            save();
            p.sendMessage(msg("registered"));
            return true;
        }

        if (cmd.equals("login")) {
            if (!registered(p)) {
                p.sendMessage(msg("not-registered"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(msg("login"));
                return true;
            }
            if (hash(args[0]).equals(data.getString("players." + p.getUniqueId() + ".password", ""))) {
                logged.add(p.getUniqueId());
                p.sendMessage(msg("logged"));
            } else {
                p.sendMessage(msg("wrong"));
            }
            return true;
        }

        if (cmd.equals("changepassword")) {
            if (!registered(p) || args.length < 2) return true;
            if (!hash(args[0]).equals(data.getString("players." + p.getUniqueId() + ".password", ""))) {
                p.sendMessage(msg("wrong"));
                return true;
            }
            data.set("players." + p.getUniqueId() + ".password", hash(args[1]));
            save();
            p.sendMessage(msg("changed"));
            return true;
        }

        return true;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (premium(p)) {
            logged.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> p.sendMessage(msg("premium")), 10L);
            return;
        }

        if (bypass(p)) {
            logged.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> p.sendMessage(msg("bypassed")), 10L);
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (registered(p)) p.sendMessage(msg("login"));
            else p.sendMessage(msg("register"));
        }, 20L);
    }

    @EventHandler public void quit(PlayerQuitEvent e) { logged.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void move(PlayerMoveEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void chat(AsyncPlayerChatEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void interact(PlayerInteractEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void breakBlock(BlockBreakEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void placeBlock(BlockPlaceEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent e) {
        if (!locked(e.getPlayer())) return;
        String m = e.getMessage().toLowerCase(Locale.ROOT);
        if (!m.startsWith("/login") && !m.startsWith("/l ") && !m.startsWith("/register") && !m.startsWith("/reg ")) {
            e.setCancelled(true);
            if (registered(e.getPlayer())) e.getPlayer().sendMessage(msg("login"));
            else e.getPlayer().sendMessage(msg("register"));
        }
    }

    private boolean registered(Player p) {
        return data.contains("players." + p.getUniqueId() + ".password");
    }

    private boolean locked(Player p) {
        return !logged.contains(p.getUniqueId()) && !premium(p) && !bypass(p);
    }

    private boolean premium(Player p) {
        return getConfig().getBoolean("settings.premium-auto-login", true) && p.getUniqueId().version() == 4;
    }

    private boolean bypass(Player p) {
        for (String n : getConfig().getStringList("settings.bypass")) {
            if (n.equalsIgnoreCase(p.getName())) return true;
        }
        return false;
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }

    private void save() {
        try { data.save(file); } catch (Exception ignored) {}
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
