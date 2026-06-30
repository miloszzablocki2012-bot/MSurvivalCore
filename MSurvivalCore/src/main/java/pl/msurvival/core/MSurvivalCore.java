package pl.msurvival.core;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalCore extends JavaPlugin implements Listener {
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Location> back = new HashMap<>();
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Random random = new Random();

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
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld() != null && e.getTo() != null) {
            back.put(e.getPlayer().getUniqueId(), e.getFrom());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("mscore")) {
            if (!sender.hasPermission("msurvival.core.admin")) return deny(sender);
            reloadConfig();
            sender.sendMessage(msg("reload"));
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        switch (cmd) {
            case "sethome" -> {
                String name = args.length > 0 ? norm(args[0]) : "dom";
                if (!data.contains(path(p) + ".homes." + name) && homes(p).size() >= getConfig().getInt("settings.max-homes", 3)) {
                    p.sendMessage(msg("max-homes"));
                    return true;
                }
                saveLocation(path(p) + ".homes." + name, p.getLocation());
                p.sendMessage(msg("home-set").replace("%home%", name));
            }
            case "home" -> {
                String name = args.length > 0 ? norm(args[0]) : "dom";
                Location loc = getLocation(path(p) + ".homes." + name);
                if (loc == null) p.sendMessage(msg("home-not-found"));
                else {
                    p.teleport(loc);
                    p.sendMessage(msg("teleported"));
                }
            }
            case "delhome" -> {
                if (args.length < 1) return true;
                String name = norm(args[0]);
                data.set(path(p) + ".homes." + name, null);
                saveData();
                p.sendMessage(msg("home-deleted").replace("%home%", name));
            }
            case "homes" -> {
                Set<String> homes = homes(p);
                p.sendMessage(msg("homes").replace("%homes%", homes.isEmpty() ? "brak" : String.join(", ", homes)));
            }
            case "setspawn" -> {
                if (!p.hasPermission("msurvival.core.admin")) return deny(p);
                saveLocation("spawn", p.getLocation());
                p.sendMessage(msg("spawn-set"));
            }
            case "spawn" -> {
                Location loc = getLocation("spawn");
                if (loc == null && p.getWorld() != null) loc = p.getWorld().getSpawnLocation();
                if (loc != null) p.teleport(loc);
                p.sendMessage(msg("teleported"));
            }
            case "back" -> {
                Location loc = back.get(p.getUniqueId());
                if (loc == null) p.sendMessage(msg("back-none"));
                else {
                    p.teleport(loc);
                    p.sendMessage(msg("teleported"));
                }
            }
            case "tpa" -> {
                if (args.length < 1) return true;
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) return true;
                tpaRequests.put(target.getUniqueId(), p.getUniqueId());
                p.sendMessage(msg("tpa-sent").replace("%player%", target.getName()));
                target.sendMessage(msg("tpa-received").replace("%player%", p.getName()));
            }
            case "tpaccept" -> {
                UUID requesterId = tpaRequests.remove(p.getUniqueId());
                if (requesterId == null) {
                    p.sendMessage(msg("tpa-none"));
                    return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester != null) {
                    requester.teleport(p.getLocation());
                    requester.sendMessage(msg("teleported"));
                }
            }
            case "tpdeny" -> {
                UUID requesterId = tpaRequests.remove(p.getUniqueId());
                if (requesterId == null) p.sendMessage(msg("tpa-none"));
                else p.sendMessage(msg("tpa-denied"));
            }
            case "rtp" -> {
                rtp(p);
            }
            case "workbench" -> {
                if (!p.hasPermission("msurvival.core.workbench")) return deny(p);
                p.openWorkbench(null, true);
            }
            case "enderchest" -> {
                if (!p.hasPermission("msurvival.core.enderchest")) return deny(p);
                p.openInventory(p.getEnderChest());
            }
        }

        return true;
    }

    private void rtp(Player p) {
        World world = p.getWorld();
        int radius = getConfig().getInt("settings.rtp-radius", 2500);
        int x = random.nextInt(radius * 2 + 1) - radius;
        int z = random.nextInt(radius * 2 + 1) - radius;
        int y = world.getHighestBlockYAt(x, z) + 1;
        p.teleport(new Location(world, x + 0.5, y, z + 0.5));
        p.sendMessage(msg("teleported"));
    }

    private Set<String> homes(Player p) {
        if (!data.contains(path(p) + ".homes")) return new HashSet<>();
        return data.getConfigurationSection(path(p) + ".homes").getKeys(false);
    }

    private String path(Player p) {
        return "players." + p.getUniqueId();
    }

    private String norm(String s) {
        return s == null ? "dom" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private void saveLocation(String path, Location loc) {
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", loc.getYaw());
        data.set(path + ".pitch", loc.getPitch());
        saveData();
    }

    private Location getLocation(String path) {
        String worldName = data.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch"));
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(msg("no-permission"));
        return true;
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String msg(String key) {
        return color(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, ""));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
