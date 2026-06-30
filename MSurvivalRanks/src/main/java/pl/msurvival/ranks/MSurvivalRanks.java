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
    private File file;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "players.yml");
        if (!file.exists()) {
            try { getDataFolder().mkdirs(); file.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::apply), 20L, 100L);
    }

    @Override
    public void onDisable() {
        save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("setrank")) {
            if (!admin(sender)) return true;
            if (args.length < 2) { sender.sendMessage(color("&c/setrank <gracz> <ranga>")); return true; }
            setRank(args[0], args[1], 0L);
            sender.sendMessage(color(msg("rank-set").replace("%rank%", display(args[1])).replace("%player%", args[0])));
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) apply(target);
            return true;
        }

        if (cmd.equals("setranktemp")) {
            if (!admin(sender)) return true;
            if (args.length < 3) { sender.sendMessage(color("&c/setranktemp <gracz> <ranga> <czas np. 7d/12h/30m>")); return true; }
            long duration = parseTime(args[2]);
            if (duration <= 0) { sender.sendMessage(color("&cZły czas. Przykład: 7d, 12h, 30m")); return true; }
            setRank(args[0], args[1], System.currentTimeMillis() + duration);
            sender.sendMessage(color(msg("rank-temp-set").replace("%rank%", display(args[1])).replace("%player%", args[0]).replace("%time%", args[2])));
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) apply(target);
            return true;
        }

        if (cmd.equals("rank") && sender instanceof Player player) {
            player.sendMessage(color("&7Twoja ranga: " + display(rank(player))));
            String ex = expires(player);
            if (!ex.equals("nigdy")) player.sendMessage(color("&7Wygasa za: &e" + ex));
            return true;
        }

        if (cmd.equals("ranksreload")) {
            if (!admin(sender)) return true;
            reloadConfig();
            data = YamlConfiguration.loadConfiguration(file);
            Bukkit.getOnlinePlayers().forEach(this::apply);
            sender.sendMessage(msg("reload"));
            return true;
        }

        return true;
    }

    @EventHandler public void join(PlayerJoinEvent e) { Bukkit.getScheduler().runTaskLater(this, () -> apply(e.getPlayer()), 20L); }

    @EventHandler
    public void chat(AsyncPlayerChatEvent e) {
        String format = color(prefix(rank(e.getPlayer())) + "%1$s &8» &f%2$s");
        e.setFormat(format.replace("%", "%%"));
    }

    private void apply(Player player) {
        String r = rank(player);
        player.setPlayerListName(color(prefix(r) + player.getName()));
        sidebar(player);
    }

    private void sidebar(Player player) {
        if (!getConfig().getBoolean("sidebar.enabled", true)) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("msr", "dummy", color(getConfig().getString("sidebar.title", "&6MSURVIVAL")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = getConfig().getStringList("sidebar.lines");
        int score = lines.size();
        Set<String> used = new HashSet<>();

        for (String raw : lines) {
            String ex = expires(player);

            // Dla rang dożywotnich linia "Wygasa" znika całkowicie.
            if (raw.contains("%expires%") && ex.equals("nigdy")) {
                score--;
                continue;
            }

            String line = color(raw
                    .replace("%player%", player.getName())
                    .replace("%rank%", display(rank(player)))
                    .replace("%expires%", ex)
                    .replace("%money%", money(player.getName()))
                    .replace("%clan%", clan(player.getName()))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%ping%", String.valueOf(player.getPing())));

            while (used.contains(line)) line += ChatColor.RESET;
            used.add(line);
            obj.getScore(line.length() > 40 ? line.substring(0, 40) : line).setScore(score--);
        }

        player.setScoreboard(board);
    }

    private void setRank(String player, String rank, long expiresAt) {
        rank = rank.toLowerCase(Locale.ROOT);
        if (!getConfig().contains("ranks." + rank)) rank = "gracz";
        String base = "players." + player.toLowerCase(Locale.ROOT);
        data.set(base + ".rank", rank);
        data.set(base + ".expires", expiresAt);
        save();
    }

    private String rank(Player player) {
        String base = "players." + player.getName().toLowerCase(Locale.ROOT);
        String r = data.getString(base + ".rank", "gracz");
        long expiresAt = data.getLong(base + ".expires", 0L);

        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            data.set(base + ".rank", "gracz");
            data.set(base + ".expires", 0L);
            save();
            player.sendMessage(msg("rank-expired"));
            return "gracz";
        }

        if (!getConfig().contains("ranks." + r)) return "gracz";
        return r;
    }

    private String expires(Player player) {
        long expiresAt = data.getLong("players." + player.getName().toLowerCase(Locale.ROOT) + ".expires", 0L);
        if (expiresAt <= 0) return "nigdy";
        long left = expiresAt - System.currentTimeMillis();
        if (left <= 0) return "0m";
        return format(left);
    }

    private String money(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalMarket/balances.yml");
        if (!f.exists()) return "0";
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        double v = yml.getDouble("players." + player.toLowerCase(Locale.ROOT), 0D);
        if (v == (long) v) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private String clan(String player) {
        File f = new File(getDataFolder().getParentFile(), "MSurvivalClans/clans.yml");
        if (!f.exists()) return "Brak";
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        return yml.getString("players." + player.toLowerCase(Locale.ROOT) + ".clan", "Brak");
    }

    private String display(String rank) { return color(getConfig().getString("ranks." + rank.toLowerCase(Locale.ROOT) + ".display", rank)); }
    private String prefix(String rank) { return getConfig().getString("ranks." + rank.toLowerCase(Locale.ROOT) + ".prefix", "&7GRACZ &7"); }

    private long parseTime(String text) {
        try {
            long value = Long.parseLong(text.substring(0, text.length() - 1));
            char unit = Character.toLowerCase(text.charAt(text.length() - 1));
            return switch (unit) {
                case 'm' -> value * 60_000L;
                case 'h' -> value * 3_600_000L;
                case 'd' -> value * 86_400_000L;
                default -> 0L;
            };
        } catch (Exception e) {
            return 0L;
        }
    }

    private String format(long ms) {
        long minutes = Math.max(1, ms / 60_000L);
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }

    private boolean admin(CommandSender sender) {
        if (!sender.hasPermission("msurvival.ranks.admin")) {
            sender.sendMessage(msg("no-permission"));
            return false;
        }
        return true;
    }

    private void save() { try { data.save(file); } catch (Exception e) { e.printStackTrace(); } }
    private String msg(String key) { return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
