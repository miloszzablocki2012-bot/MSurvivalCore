package pl.msurvival.auth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MSurvivalAuth extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private final Set<UUID> logged = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("register").setExecutor((s, c, l, a) -> {
            if (!(s instanceof Player p)) return true;
            if (a.length < 1) return true;
            String path = "players." + p.getUniqueId() + ".password";
            if (data.contains(path)) {
                p.sendMessage(msg("login"));
                return true;
            }
            data.set(path, hash(a[0]));
            saveData();
            logged.add(p.getUniqueId());
            p.sendMessage(msg("registered"));
            return true;
        });

        getCommand("login").setExecutor((s, c, l, a) -> {
            if (!(s instanceof Player p)) return true;
            if (a.length < 1) return true;
            if (logged.contains(p.getUniqueId())) {
                p.sendMessage(msg("already"));
                return true;
            }
            String saved = data.getString("players." + p.getUniqueId() + ".password", "");
            if (saved.equals(hash(a[0]))) {
                logged.add(p.getUniqueId());
                p.sendMessage(msg("logged"));
            } else {
                p.sendMessage(msg("wrong"));
            }
            return true;
        });
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!getConfig().getBoolean("settings.require-login", true)) return;
        if (data.contains("players." + p.getUniqueId() + ".password")) p.sendMessage(msg("login"));
        else p.sendMessage(msg("register"));
    }

    @EventHandler public void quit(PlayerQuitEvent e) { logged.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void move(PlayerMoveEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void chat(AsyncPlayerChatEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void breakBlock(BlockBreakEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void placeBlock(BlockPlaceEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    private boolean locked(Player p) {
        if (!getConfig().getBoolean("settings.require-login", true)) return false;
        if (!data.contains("players." + p.getUniqueId() + ".password")) return true;
        return !logged.contains(p.getUniqueId());
    }

    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { getDataFolder().mkdirs(); dataFile.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try { data.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
