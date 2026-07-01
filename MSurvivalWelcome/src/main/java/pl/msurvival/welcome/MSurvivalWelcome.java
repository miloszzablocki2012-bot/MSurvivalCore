package pl.msurvival.welcome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Professional delayed welcome flow for MSurvival.
 *
 * <p>The plugin intentionally schedules every section with a player-specific session token.
 * Old sessions are ignored after relog, quit or /skipwelcome, so delayed tasks do not spam
 * the player and do not keep long-lived Player references alive after disconnect.</p>
 */
public final class MSurvivalWelcome extends JavaPlugin implements Listener {
    private final Map<UUID, Integer> sessions = new HashMap<>();
    private int nextSessionId = 1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        sessions.clear();
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage(color(getConfig().getString("settings.join-message", "")
                .replace("%player%", player.getName())));

        if (!player.hasPlayedBefore()) {
            Bukkit.broadcastMessage(color(getConfig().getString("settings.first-join-broadcast", "")
                    .replace("%player%", player.getName())));
        }

        startWelcome(player);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessions.remove(player.getUniqueId());
        event.setQuitMessage(color(getConfig().getString("settings.quit-message", "")
                .replace("%player%", player.getName())));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("welcome")) {
            if (!sender.hasPermission("msurvival.welcome.admin")) {
                sender.sendMessage(color("&cNie masz uprawnień."));
                return true;
            }
            reloadConfig();
            sender.sendMessage(color("&6MSurvival &8» &aPrzeładowano moduł Welcome."));
            return true;
        }

        if (cmd.equals("skipwelcome")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Ta komenda jest tylko dla gracza.");
                return true;
            }
            sessions.remove(player.getUniqueId());
            sendActionBar(player, getConfig().getString("skip.actionbar", "&aPominięto wiadomości powitalne."));
            player.sendMessage(color(getConfig().getString("skip.chat", "&6MSurvival &8» &aPominięto intro powitalne.")));
            return true;
        }

        if (sender instanceof Player player) {
            if (cmd.equals("pomoc")) sendNow(player, "help");
            if (cmd.equals("regulamin")) sendNow(player, "regulamin");
            if (cmd.equals("media")) sendNow(player, "media");
            if (cmd.equals("donate")) sendNow(player, "donate");
        }
        return true;
    }

    private void startWelcome(Player player) {
        int sessionId = nextSessionId++;
        if (nextSessionId == Integer.MAX_VALUE) nextSessionId = 1;
        sessions.put(player.getUniqueId(), sessionId);

        schedule(player, sessionId, getConfig().getLong("sequence.title.delay-ticks", 30L), () -> {
            if (!getConfig().getBoolean("title.enabled", true)) return;
            player.sendTitle(
                    color(replace(player, getConfig().getString("title.title", "&6&lMSURVIVAL"))),
                    color(replace(player, getConfig().getString("title.subtitle", "&fNajlepszy Survival 1.21.8"))),
                    getConfig().getInt("title.fade-in", 15),
                    getConfig().getInt("title.stay", 60),
                    getConfig().getInt("title.fade-out", 20)
            );
            playConfiguredSound(player, "sounds.title");
        });

        schedule(player, sessionId, getConfig().getLong("sequence.actionbar.delay-ticks", 80L), () ->
                sendActionBar(player, getConfig().getString("actionbar.login", "&a✔ Konto zostało pomyślnie zalogowane.")));

        schedule(player, sessionId, getConfig().getLong("sequence.bossbar.delay-ticks", 140L), () -> showBossBar(player));

        scheduleSections(player, sessionId);
    }

    private void scheduleSections(Player player, int sessionId) {
        long defaultStart = getConfig().getLong("sequence.chat.start-delay-ticks", 200L);
        long defaultGap = getConfig().getLong("sequence.chat.section-gap-ticks", 120L);
        List<String> sections = getConfig().getStringList("sequence.chat.sections");
        if (sections.isEmpty()) sections = List.of("welcome-main", "account-info", "commands", "economy", "keys", "community", "finish");

        for (int index = 0; index < sections.size(); index++) {
            String section = sections.get(index);
            long delay = defaultStart + (index * defaultGap);
            schedule(player, sessionId, delay, () -> sendNow(player, "messages." + section));
        }
    }

    private void schedule(Player player, int sessionId, long delayTicks, Runnable action) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player current = Bukkit.getPlayer(uuid);
            Integer active = sessions.get(uuid);
            if (current == null || !current.isOnline() || active == null || active != sessionId) return;
            action.run();
        }, Math.max(1L, delayTicks));
    }

    private void showBossBar(Player player) {
        if (!getConfig().getBoolean("bossbar.enabled", true)) return;

        String title = color(replace(player, getConfig().getString("bossbar.title", "&6Miłej gry na MSurvival!")));
        long removeAfter = Math.max(20L, getConfig().getLong("bossbar.remove-after-ticks", 160L));

        try {
            Class<?> barColor = Class.forName("org.bukkit.boss.BarColor");
            Class<?> barStyle = Class.forName("org.bukkit.boss.BarStyle");
            Object color = Enum.valueOf((Class<Enum>) barColor.asSubclass(Enum.class), getConfig().getString("bossbar.color", "YELLOW"));
            Object style = Enum.valueOf((Class<Enum>) barStyle.asSubclass(Enum.class), getConfig().getString("bossbar.style", "SOLID"));
            Method createBossBar = Bukkit.class.getMethod("createBossBar", String.class, barColor, barStyle);
            Object bossBar = createBossBar.invoke(null, title, color, style);
            bossBar.getClass().getMethod("addPlayer", Player.class).invoke(bossBar, player);
            bossBar.getClass().getMethod("setProgress", double.class).invoke(bossBar, 1.0D);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    bossBar.getClass().getMethod("removeAll").invoke(bossBar);
                } catch (Exception ignored) {
                    // Fallback-safe: bossbar removal failure should never break login flow.
                }
            }, removeAfter);
        } catch (Exception ignored) {
            // Older API/stub fallback. Real Paper 1.21.8 supports BossBar.
            player.sendMessage(title);
        }
    }

    private void sendNow(Player player, String path) {
        for (String line : getConfig().getStringList(path)) {
            player.sendMessage(color(replace(player, line)));
        }
    }

    private void sendActionBar(Player player, String text) {
        player.sendActionBar(color(replace(player, text)));
    }

    private void playConfiguredSound(Player player, String path) {
        String soundName = getConfig().getString(path + ".sound", "ENTITY_PLAYER_LEVELUP");
        float volume = (float) getConfig().getDouble(path + ".volume", 0.6D);
        float pitch = (float) getConfig().getDouble(path + ".pitch", 1.2D);
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Invalid sound in config: silently skip instead of crashing the join event.
        }
    }

    private String replace(Player player, String text) {
        if (text == null) return "";
        return text
                .replace("%player%", player.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%server%", getConfig().getString("settings.server-name", "MSurvival"))
                .replace("%ip%", getConfig().getString("settings.server-ip", "msurvival.6mc.pl"));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
