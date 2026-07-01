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
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class MSurvivalAuth extends JavaPlugin implements Listener {
    private File file;
    private YamlConfiguration data;
    private boolean saveQueued;
    private final Set<UUID> logged = new HashSet<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "auth.yml");
        try { getDataFolder().mkdirs(); if (!file.exists()) file.createNewFile(); } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() { try { data.save(file); } catch (Exception ignored) {} }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("authpremium")) {
            if (!sender.hasPermission("msurvival.auth.admin")) return true;
            if (args.length < 1 || args[0].equalsIgnoreCase("status")) {
                sender.sendMessage(msg("premium-status").replace("%status%", getConfig().getBoolean("settings.premium-auto-login", true) ? "włączone" : "wyłączone"));
                return true;
            }
            if (args[0].equalsIgnoreCase("on")) {
                getConfig().set("settings.premium-auto-login", true);
                saveConfig();
                sender.sendMessage(msg("premium-on"));
                return true;
            }
            if (args[0].equalsIgnoreCase("off")) {
                getConfig().set("settings.premium-auto-login", false);
                saveConfig();
                sender.sendMessage(msg("premium-off"));
                return true;
            }
            return true;
        }

        if (cmd.equals("authbypass") || cmd.equals("authforce")) {
            if (!sender.hasPermission("msurvival.auth.admin")) return true;
            String listPath = cmd.equals("authbypass") ? "settings.bypass" : "settings.force-password";
            List<String> list = new ArrayList<>(getConfig().getStringList(listPath));
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(color("&6Auth &8» &e" + listPath + ": &f" + String.join(", ", list)));
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
                if (list.stream().noneMatch(x -> x.equalsIgnoreCase(args[1]))) list.add(args[1]);
                getConfig().set(listPath, list);
                saveConfig();
                sender.sendMessage(color("&6Auth &8» &aDodano &e" + args[1]));
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
                list.removeIf(x -> x.equalsIgnoreCase(args[1]));
                getConfig().set(listPath, list);
                saveConfig();
                sender.sendMessage(color("&6Auth &8» &cUsunięto &e" + args[1]));
            }
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        if (!forcePassword(p) && bypass(p)) {
            logged.add(p.getUniqueId());
            p.sendMessage(msg("bypassed"));
            title(p, "titles.logged-title", "titles.logged-subtitle");
            return true;
        }

        // Premium nie omija pierwszego logowania.
        // Auto-login działa dopiero po tym, gdy gracz raz poprawnie zaloguje się hasłem
        // i konto zostanie oznaczone jako premium-verified.

        if (cmd.equals("register")) {
            if (registered(p)) { p.sendMessage(msg("already-registered")); return true; }
            if (args.length < 1) { p.sendMessage(msg("register")); return true; }
            data.set(path(p) + ".uuid", p.getUniqueId().toString());
            data.set(path(p) + ".password", hash(args[0]));
            if (premiumDetected(p)) {
                data.set(path(p) + ".premium-verified", true);
                data.set(path(p) + ".premium-uuid", p.getUniqueId().toString());
            }
            logged.add(p.getUniqueId());
            save();
            p.sendMessage(msg("registered"));
            title(p, "titles.registered-title", "titles.registered-subtitle");
            return true;
        }

        if (cmd.equals("login")) {
            if (!registered(p)) { p.sendMessage(msg("not-registered")); return true; }
            if (args.length < 1) { p.sendMessage(msg("login")); return true; }
            String storedPassword = data.getString(path(p) + ".password", "");
            if (verifyPassword(args[0], storedPassword)) {
                if (!storedPassword.startsWith("pbkdf2$")) {
                    data.set(path(p) + ".password", hash(args[0]));
                    save();
                }
                if (premiumDetected(p) && getConfig().getBoolean("settings.premium-auto-login", true)) {
                    data.set(path(p) + ".premium-verified", true);
                    data.set(path(p) + ".premium-uuid", p.getUniqueId().toString());
                    save();
                }
                logged.add(p.getUniqueId());
                p.sendMessage(msg("logged"));
                title(p, "titles.logged-title", "titles.logged-subtitle");
            } else p.sendMessage(msg("wrong"));
            return true;
        }

        if (cmd.equals("changepassword")) {
            if (!registered(p) || args.length < 2) return true;
            if (!verifyPassword(args[0], data.getString(path(p) + ".password", ""))) { p.sendMessage(msg("wrong")); return true; }
            data.set(path(p) + ".password", hash(args[1]));
            save();
            p.sendMessage(msg("changed"));
            return true;
        }

        return true;
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!forcePassword(p) && bypass(p)) {
            logged.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> { p.sendMessage(msg("bypassed")); title(p, "titles.logged-title", "titles.logged-subtitle"); }, 10L);
            return;
        }
        if (!forcePassword(p) && premiumAutoAllowed(p)) {
            logged.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(msg("premium"));
                title(p, "titles.premium-title", "titles.premium-subtitle");
            }, 10L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (premiumDetected(p) && !registered(p) && getConfig().getBoolean("settings.premium-first-join-register", true)) {
                p.sendMessage(msg("premium-first-register"));
                title(p, "titles.premium-first-title", "titles.premium-first-subtitle");
                return;
            }
            if (premiumDetected(p) && registered(p) && !premiumAutoAllowed(p)) {
                p.sendMessage(msg("premium-first-login"));
                title(p, "titles.premium-login-title", "titles.premium-login-subtitle");
                return;
            }
            p.sendMessage(registered(p) ? msg("login") : msg("register"));
        }, 20L);
    }

    @EventHandler public void quit(PlayerQuitEvent e) { logged.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void move(PlayerMoveEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void chat(AsyncPlayerChatEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void interact(PlayerInteractEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void br(BlockBreakEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void bp(BlockPlaceEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }

    @EventHandler public void command(PlayerCommandPreprocessEvent e) {
        if (!locked(e.getPlayer())) return;
        String m = e.getMessage().toLowerCase(Locale.ROOT);
        if (!m.startsWith("/login") && !m.startsWith("/l ") && !m.startsWith("/register") && !m.startsWith("/reg ")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(registered(e.getPlayer()) ? msg("login") : msg("register"));
        }
    }

    private String path(Player p) { return "players." + p.getName().toLowerCase(Locale.ROOT); }
    private boolean registered(Player p) { return data.contains(path(p) + ".password"); }
    private boolean locked(Player p) { return !logged.contains(p.getUniqueId()) && !bypass(p); }
    private boolean premiumDetected(Player p) { return p.getUniqueId().version() == 4; }
    private boolean premiumAutoAllowed(Player p) {
        if (!getConfig().getBoolean("settings.premium-auto-login", true)) return false;
        if (!premiumDetected(p)) return false;
        if (!registered(p)) return false;
        if (!data.getBoolean(path(p) + ".premium-verified", false)) return false;
        String verifiedUuid = data.getString(path(p) + ".premium-uuid", "");
        return verifiedUuid.isBlank() || verifiedUuid.equals(p.getUniqueId().toString());
    }
    private boolean bypass(Player p) { return listContains("settings.bypass", p.getName()); }
    private boolean forcePassword(Player p) { return listContains("settings.force-password", p.getName()); }
    private boolean listContains(String path, String name) { for (String n : getConfig().getStringList(path)) if (n.equalsIgnoreCase(name)) return true; return false; }

    private void title(Player p, String titlePath, String subtitlePath) {
        if (!getConfig().getBoolean("settings.center-title", true)) return;
        p.sendTitle(color(getConfig().getString(titlePath, "")), color(getConfig().getString(subtitlePath, "")), 10, 50, 15);
    }

    private boolean verifyPassword(String password, String stored) {
        if (stored == null || stored.isBlank()) return false;
        if (!stored.startsWith("pbkdf2$")) {
            // Kompatybilność ze starymi kontami SHA-256. Po poprawnym logowaniu hasło zostanie podniesione przy zmianie hasła.
            return legacySha256(password).equals(stored);
        }
        try {
            String[] parts = stored.split("\\$", 3);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password.toCharArray(), salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String hash(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(pbkdf2(password.toCharArray(), salt));
        } catch (Exception ignored) {
            return legacySha256(password);
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 120_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private String legacySha256(String s) {
        try { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] b = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); for(byte x:b) sb.append(String.format("%02x",x)); return sb.toString(); } catch(Exception e) { return s; }
    }
    private void save() {
        if (!isEnabled()) {
            try { data.save(file); } catch (Exception ignored) {}
            return;
        }
        if (saveQueued) return;
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            saveQueued = false;
            String snapshot = data.saveToString();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try { java.nio.file.Files.writeString(file.toPath(), snapshot, java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ignored) {}
            });
        }, 20L);
    }
    private String msg(String k) { return color(getConfig().getString("messages.prefix","")+getConfig().getString("messages."+k,"")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
}
