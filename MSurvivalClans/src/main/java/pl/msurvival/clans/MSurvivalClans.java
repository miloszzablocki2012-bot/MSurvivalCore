package pl.msurvival.clans;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalClans extends JavaPlugin {
    private File file;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "clans.yml");
        if (!file.exists()) {
            try { getDataFolder().mkdirs(); file.createNewFile(); } catch (Exception e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void onDisable() {
        save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Komenda tylko dla gracza.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(msg("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            if (args.length < 2) { player.sendMessage(msg("usage")); return true; }
            create(player, args[1]);
            return true;
        }

        if (sub.equals("invite")) {
            if (args.length < 2) { player.sendMessage(msg("usage")); return true; }
            invite(player, args[1]);
            return true;
        }

        if (sub.equals("join")) {
            if (args.length < 2) { player.sendMessage(msg("usage")); return true; }
            join(player, args[1]);
            return true;
        }

        if (sub.equals("leave")) {
            leave(player);
            return true;
        }

        if (sub.equals("disband")) {
            disband(player);
            return true;
        }

        if (sub.equals("info")) {
            info(player);
            return true;
        }

        player.sendMessage(msg("usage"));
        return true;
    }

    private void create(Player player, String rawTag) {
        String tag = normalize(rawTag);
        int min = getConfig().getInt("settings.min-tag-length", 2);
        int max = getConfig().getInt("settings.max-tag-length", 6);

        if (tag.length() < min || tag.length() > max) {
            player.sendMessage(color("&cTag musi mieć od " + min + " do " + max + " znaków."));
            return;
        }

        if (getClan(player.getName()) != null) {
            player.sendMessage(msg("already-in-clan"));
            return;
        }

        if (data.contains("clans." + tag)) {
            player.sendMessage(color("&cTaki klan już istnieje."));
            return;
        }

        data.set("clans." + tag + ".owner", player.getName().toLowerCase(Locale.ROOT));
        data.set("clans." + tag + ".members", new ArrayList<>(List.of(player.getName().toLowerCase(Locale.ROOT))));
        data.set("players." + player.getName().toLowerCase(Locale.ROOT) + ".clan", tag);
        save();
        player.sendMessage(msg("created").replace("%clan%", tag));
    }

    private void invite(Player player, String targetName) {
        String clan = getClan(player.getName());
        if (clan == null) { player.sendMessage(msg("not-in-clan")); return; }

        String owner = data.getString("clans." + clan + ".owner", "");
        if (!owner.equalsIgnoreCase(player.getName())) {
            player.sendMessage(msg("only-owner"));
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(color("&cGracz nie jest online."));
            return;
        }

        data.set("invites." + target.getName().toLowerCase(Locale.ROOT), clan);
        save();
        player.sendMessage(msg("invited").replace("%player%", target.getName()).replace("%clan%", clan));
        target.sendMessage(msg("invite-received").replace("%clan%", clan));
    }

    private void join(Player player, String rawClan) {
        String clan = normalize(rawClan);
        if (getClan(player.getName()) != null) {
            player.sendMessage(msg("already-in-clan"));
            return;
        }

        String invite = data.getString("invites." + player.getName().toLowerCase(Locale.ROOT), "");
        if (!invite.equalsIgnoreCase(clan)) {
            player.sendMessage(color("&cNie masz zaproszenia do tego klanu."));
            return;
        }

        List<String> members = data.getStringList("clans." + clan + ".members");
        members.add(player.getName().toLowerCase(Locale.ROOT));
        data.set("clans." + clan + ".members", members);
        data.set("players." + player.getName().toLowerCase(Locale.ROOT) + ".clan", clan);
        data.set("invites." + player.getName().toLowerCase(Locale.ROOT), null);
        save();
        player.sendMessage(msg("joined").replace("%clan%", clan));
    }

    private void leave(Player player) {
        String clan = getClan(player.getName());
        if (clan == null) { player.sendMessage(msg("not-in-clan")); return; }

        String name = player.getName().toLowerCase(Locale.ROOT);
        String owner = data.getString("clans." + clan + ".owner", "");
        if (owner.equalsIgnoreCase(name)) {
            player.sendMessage(color("&cJesteś właścicielem. Użyj &e/clan disband&c."));
            return;
        }

        List<String> members = data.getStringList("clans." + clan + ".members");
        members.removeIf(m -> m.equalsIgnoreCase(name));
        data.set("clans." + clan + ".members", members);
        data.set("players." + name, null);
        save();
        player.sendMessage(msg("left"));
    }

    private void disband(Player player) {
        String clan = getClan(player.getName());
        if (clan == null) { player.sendMessage(msg("not-in-clan")); return; }

        String owner = data.getString("clans." + clan + ".owner", "");
        if (!owner.equalsIgnoreCase(player.getName())) {
            player.sendMessage(msg("only-owner"));
            return;
        }

        for (String member : data.getStringList("clans." + clan + ".members")) {
            data.set("players." + member, null);
        }

        data.set("clans." + clan, null);
        save();
        player.sendMessage(msg("disbanded"));
    }

    private void info(Player player) {
        String clan = getClan(player.getName());
        if (clan == null) { player.sendMessage(msg("not-in-clan")); return; }
        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(color("&b&lKLAN &8» &f" + clan));
        player.sendMessage(color("&7Właściciel: &e" + data.getString("clans." + clan + ".owner")));
        player.sendMessage(color("&7Członkowie: &e" + String.join(", ", data.getStringList("clans." + clan + ".members"))));
        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━"));
    }

    private String getClan(String player) {
        return data.getString("players." + player.toLowerCase(Locale.ROOT) + ".clan");
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private void save() {
        try { data.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
