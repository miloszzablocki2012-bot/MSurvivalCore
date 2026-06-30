package pl.msurvival.transfer;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class MSurvivalTransfer extends JavaPlugin implements Listener {
    private File file;
    private YamlConfiguration data;
    private final Set<UUID> depositing = new HashSet<>();
    private final Set<UUID> withdrawing = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "items.yml");
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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (command.getName().equalsIgnoreCase("lobby")) {
            openDeposit(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("survival")) {
            openWithdraw(p);
            return true;
        }

        return true;
    }

    private void openDeposit(Player p) {
        depositing.add(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, color(getConfig().getString("messages.deposit-title", "&6&lODDAJ ITEMY")));

        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) inv.addItem(item);
        }
        addIf(inv, p.getInventory().getHelmet());
        addIf(inv, p.getInventory().getChestplate());
        addIf(inv, p.getInventory().getLeggings());
        addIf(inv, p.getInventory().getBoots());
        addIf(inv, p.getInventory().getItemInOffHand());

        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);

        p.openInventory(inv);
        p.sendMessage(color("&ePrzenieś wszystkie itemy do skrzynki. Jak GUI będzie pełne itemów i je zamkniesz, trafisz do lobby."));
    }

    private void openWithdraw(Player p) {
        World w = Bukkit.getWorld(getConfig().getString("worlds.survival", "world"));
        if (w != null) p.teleport(w.getSpawnLocation());

        List<ItemStack> items = load(p.getUniqueId());
        if (items.isEmpty()) return;

        withdrawing.add(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, color(getConfig().getString("messages.withdraw-title", "&a&lWCZYTAJ ITEMY")));
        for (ItemStack item : items) inv.addItem(item);

        data.set("players." + p.getUniqueId(), null);
        save();

        p.openInventory(inv);
        p.sendMessage(color("&ePrzenieś itemy z GUI do ekwipunku. Dopiero jak GUI będzie puste, możesz grać."));
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        String title = e.getView().getTitle();

        if (depositing.contains(p.getUniqueId()) && title.equals(color(getConfig().getString("messages.deposit-title", "&6&lODDAJ ITEMY")))) {
            if (emptyInv(e.getInventory())) {
                Bukkit.getScheduler().runTaskLater(this, () -> p.openInventory(e.getInventory()), 2L);
                return;
            }

            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : e.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) items.add(item);
            }

            saveItems(p.getUniqueId(), items);
            depositing.remove(p.getUniqueId());

            Bukkit.getScheduler().runTaskLater(this, () -> {
                World w = Bukkit.getWorld(getConfig().getString("worlds.lobby", "Lobby"));
                if (w != null) p.teleport(w.getSpawnLocation());
                p.performCommand("menu");
            }, 2L);
        }

        if (withdrawing.contains(p.getUniqueId()) && title.equals(color(getConfig().getString("messages.withdraw-title", "&a&lWCZYTAJ ITEMY")))) {
            if (!emptyInv(e.getInventory())) {
                Bukkit.getScheduler().runTaskLater(this, () -> p.openInventory(e.getInventory()), 2L);
                return;
            }
            withdrawing.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        if (depositing.contains(e.getPlayer().getUniqueId()) || withdrawing.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    private void addIf(Inventory inv, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) inv.addItem(item);
    }

    private boolean emptyInv(Inventory inv) {
        for (ItemStack item : inv.getContents()) if (item != null && item.getType() != Material.AIR) return false;
        return true;
    }

    private void saveItems(UUID uuid, List<ItemStack> items) {
        data.set("players." + uuid, items);
        save();
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> load(UUID uuid) {
        List<?> raw = data.getList("players." + uuid, new ArrayList<>());
        List<ItemStack> out = new ArrayList<>();
        for (Object o : raw) if (o instanceof ItemStack item) out.add(item);
        return out;
    }

    private void save() {
        try { data.save(file); } catch (Exception ignored) {}
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
