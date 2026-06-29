package pl.msurvival.ranks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("deprecation")
public final class MSurvivalRanks extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();

        long refresh = Math.max(5L, getConfig().getLong("settings.refresh-seconds", 20L)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            checkExpiredRanks();
            refreshAll();
        }, 20L, refresh);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void registerCommands() {
        getCommand("setrank").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvivalranks.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(msg("usage-setrank"));
                return true;
            }

            setRankCommand(sender, args[0], args[1], 0L, null);
            return true;
        });

        getCommand("temprank").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvivalranks.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(msg("usage-temprank"));
                return true;
            }

            long duration = parseDuration(args[2]);
            if (duration <= 0) {
                sender.sendMessage(msg("bad-time"));
                return true;
            }

            setRankCommand(sender, args[0], args[1], System.currentTimeMillis() + duration, args[2]);
            return true;
        });

        getCommand("rankremove").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvivalranks.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(msg("usage-rankremove"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            data.set("players." + target.getUniqueId(), null);
            saveData();

            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) applyRank(online);

            sender.sendMessage(msg("removed-rank").replace("%player%", args[0]));
            return true;
        });

        getCommand("rank").setExecutor((sender, command, label, args) -> {
            if (args.length == 0) {
                if (sender instanceof Player p) sendRankInfo(sender, p);
                else sender.sendMessage(color("&c/rank <gracz> [ranga]"));
                return true;
            }

            if (args.length >= 2 && sender.hasPermission("msurvivalranks.admin")) {
                setRankCommand(sender, args[0], args[1], 0L, null);
                return true;
            }

            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) sendRankInfo(sender, online);
            else sender.sendMessage(color("&cGracz nie jest online."));
            return true;
        });

        getCommand("ranks").setExecutor((sender, command, label, args) -> {
            sendRankList(sender);
            return true;
        });

        getCommand("ranksreload").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("msurvivalranks.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }

            reloadConfig();
            loadData();
            refreshAll();
            sender.sendMessage(msg("reload"));
            return true;
        });
    }

    private void setRankCommand(org.bukkit.command.CommandSender sender, String playerName, String rankRaw, long expiresAt, String timeText) {
        String rank = normalize(rankRaw);

        if (!rankExists(rank)) {
            sender.sendMessage(msg("unknown-rank"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String path = "players." + target.getUniqueId();

        data.set(path + ".name", target.getName() == null ? playerName : target.getName());
        data.set(path + ".rank", rank);
        data.set(path + ".expiresAt", expiresAt);
        saveData();

        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) applyRank(online);

        if (expiresAt > 0) {
            sender.sendMessage(msg("temp-rank")
                    .replace("%player%", playerName)
                    .replace("%rank%", color(getRankDisplay(rank)))
                    .replace("%time%", timeText == null ? formatRemaining(expiresAt - System.currentTimeMillis()) : timeText));
        } else {
            sender.sendMessage(msg("set-rank")
                    .replace("%player%", playerName)
                    .replace("%rank%", color(getRankDisplay(rank))));
        }
    }

    private void sendRankInfo(org.bukkit.command.CommandSender sender, Player target) {
        String rank = getRank(target);
        long expires = getExpiresAt(target.getUniqueId());

        sender.sendMessage(msg("your-rank")
                .replace("%player%", target.getName())
                .replace("%rank%", color(getRankDisplay(rank)))
                .replace("%expires%", expires <= 0 ? "nigdy" : formatRemaining(expires - System.currentTimeMillis())));
    }

    private void sendRankList(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(msg("list-header"));

        ConfigurationSection section = getConfig().getConfigurationSection("ranks");
        if (section == null) return;

        List<String> ranks = new ArrayList<>(section.getKeys(false));
        ranks.sort((a, b) -> Integer.compare(getPriority(b), getPriority(a)));

        for (String rank : ranks) {
            sender.sendMessage(msg("list-line")
                    .replace("%rank%", rank)
                    .replace("%prefix%", color(getRankPrefix(rank))));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            applyRank(event.getPlayer());
            refreshAll();
        }, 10L);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!getConfig().getBoolean("settings.update-chat", true)) return;

        Player player = event.getPlayer();
        String rank = getRank(player);

        String prefix = color(getConfig().getString("ranks." + rank + ".chat-prefix", getRankPrefix(rank)));
        String nameColor = color(getConfig().getString("ranks." + rank + ".name-color", "&7"));

        event.setFormat(prefix + nameColor + "%1$s &8» &f%2$s");
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRank(player);
        }
    }

    private void checkExpiredRanks() {
        long now = System.currentTimeMillis();

        if (!data.contains("players")) return;

        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) return;

        boolean changed = false;

        for (String uuid : section.getKeys(false)) {
            long expiresAt = data.getLong("players." + uuid + ".expiresAt", 0L);

            if (expiresAt > 0 && expiresAt <= now) {
                data.set("players." + uuid, null);
                changed = true;
            }
        }

        if (changed) saveData();
    }

    private void applyRank(Player player) {
        String rank = getRank(player);

        if (getConfig().getBoolean("settings.update-tab", true)) {
            String tabPrefix = color(getConfig().getString("ranks." + rank + ".tab-prefix", getRankPrefix(rank)));
            String nameColor = color(getConfig().getString("ranks." + rank + ".name-color", "&7"));
            player.setPlayerListName(limit(tabPrefix + nameColor + player.getName(), 80));
        }

        updateScoreboard(player);

        if (getConfig().getBoolean("settings.update-nametag", true)) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                addNametagEntry(viewer, player, rank);
            }
        }
    }

    private void updateScoreboard(Player player) {
        Scoreboard board = player.getScoreboard();

        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        if (getConfig().getBoolean("settings.update-sidebar", true)) {
            Objective old = board.getObjective("msranks");
            if (old != null) old.unregister();

            Objective obj = board.registerNewObjective("msranks", "dummy", color(getConfig().getString("sidebar.title", "&6&lMSURVIVAL")));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            List<String> lines = getConfig().getStringList("sidebar.lines");
            int score = lines.size();

            Set<String> used = new HashSet<>();
            for (String raw : lines) {
                String line = applyPlaceholders(player, raw);

                while (used.contains(line)) {
                    line += ChatColor.RESET;
                }

                used.add(line);
                obj.getScore(limit(color(line), 40)).setScore(score--);
            }
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            addNametagEntry(player, target, getRank(target));
        }
    }

    private void addNametagEntry(Player viewer, Player target, String rank) {
        Scoreboard board = viewer.getScoreboard();

        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(board);
        }

        String teamName = teamName(rank);
        Team team = board.getTeam(teamName);

        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        String prefix = color(getRankPrefix(rank));
        String nameColor = color(getConfig().getString("ranks." + rank + ".name-color", "&7"));

        team.setPrefix(limit(prefix + nameColor, 64));
        team.setSuffix(ChatColor.RESET.toString());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        for (Team other : board.getTeams()) {
            if (!other.getName().equals(teamName) && other.hasEntry(target.getName())) {
                other.removeEntry(target.getName());
            }
        }

        if (!team.hasEntry(target.getName())) {
            team.addEntry(target.getName());
        }
    }

    private String applyPlaceholders(Player player, String raw) {
        String rank = getRank(player);
        long expiresAt = getExpiresAt(player.getUniqueId());

        return raw
                .replace("%player%", player.getName())
                .replace("%rank%", getRankDisplay(rank))
                .replace("%prefix%", getRankPrefix(rank))
                .replace("%expires%", expiresAt <= 0 ? "nigdy" : formatRemaining(expiresAt - System.currentTimeMillis()))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    private String getRank(Player player) {
        checkPlayerExpired(player.getUniqueId());

        String saved = data.getString("players." + player.getUniqueId() + ".rank");

        if (saved != null && rankExists(saved)) {
            return normalize(saved);
        }

        if (getConfig().getBoolean("settings.use-permissions-if-no-rank-set", true)) {
            String best = getBestPermissionRank(player);
            if (best != null) return best;
        }

        return normalize(getConfig().getString("settings.default-rank", "gracz"));
    }

    private void checkPlayerExpired(UUID uuid) {
        long expiresAt = getExpiresAt(uuid);

        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            data.set("players." + uuid, null);
            saveData();
        }
    }

    private long getExpiresAt(UUID uuid) {
        return data.getLong("players." + uuid + ".expiresAt", 0L);
    }

    private String getBestPermissionRank(Player player) {
        ConfigurationSection section = getConfig().getConfigurationSection("ranks");
        if (section == null) return null;

        String best = null;
        int bestPriority = Integer.MIN_VALUE;

        for (String rank : section.getKeys(false)) {
            String permission = getConfig().getString("ranks." + rank + ".permission", "");

            if (permission == null || permission.isBlank()) continue;

            if (player.hasPermission(permission)) {
                int priority = getPriority(rank);

                if (priority > bestPriority) {
                    bestPriority = priority;
                    best = normalize(rank);
                }
            }
        }

        return best;
    }

    private boolean rankExists(String rank) {
        return getConfig().contains("ranks." + normalize(rank));
    }

    private String getRankDisplay(String rank) {
        return getConfig().getString("ranks." + normalize(rank) + ".display", rank);
    }

    private String getRankPrefix(String rank) {
        return getConfig().getString("ranks." + normalize(rank) + ".prefix", "&7GRACZ &7");
    }

    private int getPriority(String rank) {
        return getConfig().getInt("ranks." + normalize(rank) + ".priority", 0);
    }

    private String teamName(String rank) {
        String n = "r" + String.format(Locale.ROOT, "%03d", Math.max(0, 999 - getPriority(rank))) + "_" + normalize(rank);
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    private long parseDuration(String raw) {
        if (raw == null || raw.length() < 2) return -1;

        raw = raw.toLowerCase(Locale.ROOT).trim();

        try {
            long number = Long.parseLong(raw.substring(0, raw.length() - 1));
            char unit = raw.charAt(raw.length() - 1);

            return switch (unit) {
                case 's' -> number * 1000L;
                case 'm' -> number * 60_000L;
                case 'h' -> number * 3_600_000L;
                case 'd' -> number * 86_400_000L;
                case 'w' -> number * 604_800_000L;
                default -> -1L;
            };
        } catch (Exception e) {
            return -1L;
        }
    }

    private String formatRemaining(long millis) {
        if (millis <= 0) return "wygasla";

        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return Math.max(1, seconds) + "s";
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }

    private String limit(String raw, int max) {
        if (raw == null) return "";
        return raw.length() <= max ? raw : raw.substring(0, max);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");

        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
