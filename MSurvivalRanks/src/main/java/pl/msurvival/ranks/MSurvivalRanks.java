package pl.msurvival.ranks;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.util.*;

public final class MSurvivalRanks extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        try { getDataFolder().mkdirs(); if (!dataFile.exists()) dataFile.createNewFile(); } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::apply), 20L, Math.max(20L, getConfig().getLong("sidebar.update-ticks", 60L)));
    }
    @Override public void onDisable() { saveData(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("rank") || cmd.equals("ranga")) {
            Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
            if (target == null && sender instanceof Player p) target = p;
            if (target != null) sender.sendMessage(color("&6&lMSurvival &8» &fRanga gracza &e" + target.getName() + "&f: " + display(rank(target))));
            return true;
        }
        if (cmd.equals("ranksreload")) {
            if (!admin(sender)) return true;
            reloadConfig(); data = YamlConfiguration.loadConfiguration(dataFile);
            Bukkit.getOnlinePlayers().forEach(this::apply);
            sender.sendMessage(msg("reload"));
            return true;
        }
        if (cmd.equals("setrank")) {
            if (!admin(sender)) return true;
            if (args.length < 2) { sender.sendMessage(color("&c/setrank <gracz> <ranga>")); return true; }
            String r = normalRank(args[1]);
            if (r == null) { sender.sendMessage(msg("rank-not-found")); return true; }
            setRank(args[0], r, 0L);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) apply(target);
            sender.sendMessage(msg("rank-set").replace("%player%", args[0]).replace("%rank%", display(r)));
            return true;
        }
        if (cmd.equals("setranktemp")) {
            if (!admin(sender)) return true;
            if (args.length < 3) { sender.sendMessage(color("&c/setranktemp <gracz> <ranga> <7d/12h/30m>")); return true; }
            String r = normalRank(args[1]); long time = parseTime(args[2]);
            if (r == null || time <= 0) { sender.sendMessage(msg("rank-not-found")); return true; }
            setRank(args[0], r, System.currentTimeMillis() + time);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) apply(target);
            sender.sendMessage(msg("rank-temp-set").replace("%player%", args[0]).replace("%rank%", display(r)).replace("%time%", args[2]));
            return true;
        }
        if (cmd.equals("delrank")) {
            if (!admin(sender)) return true;
            if (args.length < 1) return true;
            setRank(args[0], "gracz", 0L);
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) apply(target);
            sender.sendMessage(msg("rank-reset").replace("%player%", args[0]));
            return true;
        }
        return true;
    }

    @EventHandler public void join(PlayerJoinEvent e) { Bukkit.getScheduler().runTaskLater(this, () -> apply(e.getPlayer()), 20L); }
    @EventHandler public void chat(AsyncPlayerChatEvent e) { e.setFormat(color(prefix(rank(e.getPlayer())) + "%1$s &8» &f%2$s").replace("%", "%%")); }

    private void apply(Player p) { p.setPlayerListName(color(prefix(rank(p)) + p.getName())); sidebar(p); }

    private void sidebar(Player p) {
        if (!getConfig().getBoolean("sidebar.enabled", true)) return;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("msr", "dummy", color(getConfig().getString("sidebar.title", "&6MSURVIVAL")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = getConfig().getStringList("sidebar.lines").size();
        Set<String> used = new HashSet<>();
        String expires = expires(p);
        for (String raw : getConfig().getStringList("sidebar.lines")) {
            if (raw.contains("%expires%") && expires.equals("nigdy")) { score--; continue; }
            String line = color(raw
                    .replace("%player%", p.getName())
                    .replace("%rank%", display(rank(p)))
                    .replace("%expires%", expires)
                    .replace("%money%", money(p.getName()))
                    .replace("%bank%", bank(p.getName()))
                    .replace("%clan%", clan(p.getName()))
                    .replace("%kills%", stat(p.getName(), "kills"))
                    .replace("%deaths%", stat(p.getName(), "deaths"))
                    .replace("%blocks%", stat(p.getName(), "blocks"))
                    .replace("%playtime%", playtime(p.getName()))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%ping%", String.valueOf(p.getPing())));
            while (used.contains(line)) line += ChatColor.RESET;
            used.add(line);
            if (line.length() > 40) line = line.substring(0, 40);
            obj.getScore(line).setScore(score--);
        }
        p.setScoreboard(board);
    }

    private void setRank(String player, String rank, long expiresAt) {
        String base = "players." + player.toLowerCase(Locale.ROOT);
        data.set(base + ".rank", rank); data.set(base + ".expires", expiresAt); saveData();
    }
    private String rank(Player p) {
        String base = "players." + p.getName().toLowerCase(Locale.ROOT);
        String r = data.getString(base + ".rank", "gracz");
        if (!getConfig().contains("ranks." + r)) r = "gracz";
        long expiresAt = data.getLong(base + ".expires", 0L);
        if (expiresAt > 0L && expiresAt <= System.currentTimeMillis()) { setRank(p.getName(), "gracz", 0L); p.sendMessage(msg("rank-expired")); return "gracz"; }
        return r;
    }
    private String normalRank(String input) {
        String r = input.toLowerCase(Locale.ROOT).replace("ą","a").replace("ć","c").replace("ę","e").replace("ł","l").replace("ń","n").replace("ó","o").replace("ś","s").replace("ż","z").replace("ź","z");
        r = getConfig().getString("aliases." + r, r);
        return getConfig().contains("ranks." + r) ? r : null;
    }
    private String expires(Player p) {
        long exp = data.getLong("players." + p.getName().toLowerCase(Locale.ROOT) + ".expires", 0L);
        if (exp <= 0L) return "nigdy";
        long minutes = Math.max(1L, (exp - System.currentTimeMillis()) / 60000L);
        long hours = minutes / 60L; long days = hours / 24L;
        if (days > 0) return days + "d " + (hours % 24L) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60L) + "m";
        return minutes + "m";
    }
    private long parseTime(String text) {
        try { long v = Long.parseLong(text.substring(0, text.length() - 1)); char u = Character.toLowerCase(text.charAt(text.length() - 1)); if (u == 'd') return v*86400000L; if (u == 'h') return v*3600000L; if (u == 'm') return v*60000L; } catch (Exception ignored) {}
        return 0L;
    }
    private String money(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalMarket/balances.yml");
        if (!f.exists()) return "0";
        return format(YamlConfiguration.loadConfiguration(f).getDouble("players." + player.toLowerCase(Locale.ROOT), 0D));
    }
    private String bank(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalEconomyPlus/data.yml");
        if (!f.exists()) return "0";
        return format(YamlConfiguration.loadConfiguration(f).getDouble("players." + player.toLowerCase(Locale.ROOT) + ".bank.balance", 0D));
    }
    private String stat(String player, String stat) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalEconomyPlus/data.yml");
        if (!f.exists()) return "0";
        return String.valueOf(YamlConfiguration.loadConfiguration(f).getLong("players." + player.toLowerCase(Locale.ROOT) + ".stats." + stat, 0L));
    }
    private String playtime(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalEconomyPlus/data.yml");
        if (!f.exists()) return "0h";
        long min = YamlConfiguration.loadConfiguration(f).getLong("players." + player.toLowerCase(Locale.ROOT) + ".stats.playtime_minutes", 0L);
        return (min / 60L) + "h " + (min % 60L) + "m";
    }
    private String clan(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalClans/clans.yml");
        if (!f.exists()) return "Brak";
        return YamlConfiguration.loadConfiguration(f).getString("players." + player.toLowerCase(Locale.ROOT) + ".clan", "Brak");
    }
    private String display(String rank) { return color(getConfig().getString("ranks." + rank + ".display", rank)); }
    private String prefix(String rank) { return getConfig().getString("ranks." + rank + ".prefix", "&7"); }
    private String format(double v) { if (v == (long)v) return String.valueOf((long)v); return String.format(Locale.US, "%.2f", v); }
    private boolean admin(CommandSender s) { if (!s.hasPermission("msurvival.ranks.admin")) { s.sendMessage(msg("no-permission")); return false; } return true; }
    private void saveData() { try { data.save(dataFile); } catch (Exception ignored) {} }
    private String msg(String key) { return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
