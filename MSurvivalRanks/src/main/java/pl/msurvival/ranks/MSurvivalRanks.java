package pl.msurvival.ranks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MSurvivalRanks extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        try {
            getDataFolder().mkdirs();
            if (!dataFile.exists()) dataFile.createNewFile();
        } catch (Exception ignored) {}
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);

        long ticks = Math.max(20L, getConfig().getLong("sidebar.update-ticks", 80L));
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::applyAll), 20L, ticks);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("rank") || cmd.equals("ranga")) {
            Player target = null;
            if (args.length >= 1) target = Bukkit.getPlayerExact(args[0]);
            if (target == null && sender instanceof Player p) target = p;
            if (target == null) return true;
            sendRankInfo(sender, target);
            return true;
        }

        if (cmd.equals("ranks")) {
            for (String line : getConfig().getStringList("messages.ranks-list")) sender.sendMessage(color(line));
            return true;
        }

        if (cmd.equals("ranksreload")) {
            if (!admin(sender)) return true;
            reloadConfig();
            data = YamlConfiguration.loadConfiguration(dataFile);
            Bukkit.getOnlinePlayers().forEach(this::applyAll);
            sender.sendMessage(msg("reload"));
            return true;
        }

        if (cmd.equals("setrank")) {
            if (!admin(sender)) return true;
            if (args.length < 2) {
                sender.sendMessage(msg("usage-setrank"));
                return true;
            }
            setRank(args[0], args[1], 0L);
            Player p = Bukkit.getPlayerExact(args[0]);
            if (p != null) applyAll(p);
            sender.sendMessage(color(msg("rank-set").replace("%player%", args[0]).replace("%rank%", display(normalRank(args[1])))));
            return true;
        }

        if (cmd.equals("setranktemp")) {
            if (!admin(sender)) return true;
            if (args.length < 3) {
                sender.sendMessage(msg("usage-setranktemp"));
                return true;
            }
            long duration = parseTime(args[2]);
            if (duration <= 0L) {
                sender.sendMessage(msg("usage-setranktemp"));
                return true;
            }
            setRank(args[0], args[1], System.currentTimeMillis() + duration);
            Player p = Bukkit.getPlayerExact(args[0]);
            if (p != null) applyAll(p);
            sender.sendMessage(color(msg("rank-temp-set").replace("%player%", args[0]).replace("%rank%", display(normalRank(args[1]))).replace("%time%", args[2])));
            return true;
        }

        if (cmd.equals("delrank")) {
            if (!admin(sender)) return true;
            if (args.length < 1) return true;
            String base = playerPath(args[0]);
            data.set(base + ".rank", "gracz");
            data.set(base + ".expires", 0L);
            saveData();
            Player p = Bukkit.getPlayerExact(args[0]);
            if (p != null) applyAll(p);
            sender.sendMessage(msg("rank-reset").replace("%player%", args[0]));
            return true;
        }

        return true;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> applyAll(event.getPlayer()), 20L);
    }

    @EventHandler
    public void chat(AsyncPlayerChatEvent event) {
        if (!getConfig().getBoolean("chat.enabled", true)) return;
        String rank = rank(event.getPlayer());
        String format = getConfig().getString("chat.format", "%prefix%%player% &8» &f%message%");
        format = format
                .replace("%prefix%", prefix(rank))
                .replace("%player%", event.getPlayer().getName())
                .replace("%message%", event.getMessage());
        event.setFormat("%2$s");
        event.setMessage(color(format));
    }

    private void applyAll(Player player) {
        applyTab(player);
        applySidebar(player);
    }

    private void applyTab(Player player) {
        if (!getConfig().getBoolean("tab.enabled", true)) return;
        String rank = rank(player);
        String format = getConfig().getString("tab.format", "%prefix%%player%");
        player.setPlayerListName(color(format.replace("%prefix%", prefix(rank)).replace("%player%", player.getName())));
    }

    private void applySidebar(Player player) {
        if (!getConfig().getBoolean("sidebar.enabled", true)) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("msr", "dummy", color(getConfig().getString("sidebar.title", "&6MSURVIVAL")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = getConfig().getStringList("sidebar.lines").size();
        Set<String> used = new HashSet<>();
        String expires = expires(player);

        for (String raw : getConfig().getStringList("sidebar.lines")) {
            if (raw.contains("%expires%") && expires.equals("nigdy")) {
                score--;
                continue;
            }

            String line = raw
                    .replace("%player%", player.getName())
                    .replace("%rank%", display(rank(player)))
                    .replace("%expires%", expires)
                    .replace("%money%", money(player.getName()))
                    .replace("%clan%", clan(player.getName()))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%ping%", String.valueOf(player.getPing()));

            line = color(line);
            while (used.contains(line)) line += ChatColor.RESET;
            used.add(line);

            if (line.length() > 40) line = line.substring(0, 40);
            obj.getScore(line).setScore(score--);
        }

        player.setScoreboard(board);
    }

    private void sendRankInfo(CommandSender sender, Player target) {
        String expires = expires(target);
        for (String line : getConfig().getStringList("messages.rank-info")) {
            sender.sendMessage(color(line
                    .replace("%player%", target.getName())
                    .replace("%rank%", display(rank(target)))
                    .replace("%expires%", expires)));
        }
    }

    private void setRank(String player, String rank, long expiresAt) {
        rank = normalRank(rank);
        String base = playerPath(player);
        data.set(base + ".rank", rank);
        data.set(base + ".expires", expiresAt);
        saveData();
    }

    private String rank(Player player) {
        String base = playerPath(player.getName());
        String rank = data.getString(base + ".rank", "gracz");
        if (!getConfig().contains("ranks." + rank)) rank = "gracz";

        long expiresAt = data.getLong(base + ".expires", 0L);
        if (expiresAt > 0L && expiresAt <= System.currentTimeMillis()) {
            data.set(base + ".rank", "gracz");
            data.set(base + ".expires", 0L);
            saveData();
            player.sendMessage(msg("rank-expired"));
            return "gracz";
        }

        return rank;
    }

    private String expires(Player player) {
        long expiresAt = data.getLong(playerPath(player.getName()) + ".expires", 0L);
        if (expiresAt <= 0L) return "nigdy";
        long left = expiresAt - System.currentTimeMillis();
        if (left <= 0L) return "0m";
        return formatTime(left);
    }

    private String playerPath(String player) {
        return "players." + player.toLowerCase(Locale.ROOT);
    }

    private String normalRank(String rank) {
        rank = rank.toLowerCase(Locale.ROOT).replace("ą","a").replace("ę","e").replace("ł","l").replace("ó","o").replace("ś","s").replace("ż","z").replace("ź","z").replace("ć","c").replace("ń","n");
        if (!getConfig().contains("ranks." + rank)) return "gracz";
        return rank;
    }

    private String display(String rank) {
        return color(getConfig().getString("ranks." + rank + ".display", rank));
    }

    private String prefix(String rank) {
        return getConfig().getString("ranks." + rank + ".prefix", "&7");
    }

    private String money(String player) {
        File file = new File(getDataFolder().getParentFile(), "MSurvivalMarket/balances.yml");
        if (!file.exists()) return "0";
        double value = YamlConfiguration.loadConfiguration(file).getDouble("players." + player.toLowerCase(Locale.ROOT), 0D);
        if (value == (long) value) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    private String clan(String player) {
        File file = new File(getDataFolder().getParentFile(), "MSurvivalClans/clans.yml");
        if (!file.exists()) return "Brak";
        return YamlConfiguration.loadConfiguration(file).getString("players." + player.toLowerCase(Locale.ROOT) + ".clan", "Brak");
    }

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

    private String formatTime(long ms) {
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

    private void saveData() {
        try { data.save(dataFile); } catch (Exception ignored) {}
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
