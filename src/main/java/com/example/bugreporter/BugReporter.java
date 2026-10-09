package com.example.bugreporter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class BugReporter extends JavaPlugin implements CommandExecutor, Listener {
    private File reportsFile;
    private FileConfiguration reportsConfig;
    private NamespacedKey reportIdKey;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.reportIdKey = new NamespacedKey(this, "report_id");
        createReportsConfig();
         
        Objects.requireNonNull(getCommand("bug")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /bug [report|mail]", NamedTextColor.RED));
            return true;
        }
        if (args[0].equalsIgnoreCase("report")) {
            openReportBook(player);
            return true;
        } else if (args[0].equalsIgnoreCase("mail")) {
            if (!player.hasPermission("bugreporter.admin")) {
                player.sendMessage(Component.text("You do not have permission to view bug mail.", NamedTextColor.RED));
                return true;
            }
            openMailGUI(player);
            return true;
        }
        return false;
    }

    private void openReportBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Write your bug report!", NamedTextColor.GOLD));
            book.setItemMeta(meta);
        }
        player.openBook(book);
    }

    @EventHandler
    public void onBookSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) return;
         
        BookMeta meta = event.getNewBookMeta();
        String title = meta.getTitle();
        List<String> pages = meta.getPages();
        Player player = event.getPlayer();
        String id = UUID.randomUUID().toString();
        reportsConfig.set("reports." + id + ".title", title);
        reportsConfig.set("reports." + id + ".player", player.getUniqueId().toString());
        reportsConfig.set("reports." + id + ".playerName", player.getName());
        reportsConfig.set("reports." + id + ".pages", pages);
        reportsConfig.set("reports." + id + ".status", "PENDING");
        saveReportsConfig();
        player.sendMessage(Component.text("Your bug report has been submitted to the admins!", NamedTextColor.GREEN));
         
        // Notify online admins
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("bugreporter.admin")) {
                online.sendMessage(Component.text("[BugReport] New report submitted by " + player.getName() + ": " + title, NamedTextColor.YELLOW));
            }
        }
    }

    private void openMailGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Bug Mail - Reports"));
        if (reportsConfig.getConfigurationSection("reports") == null) {
            player.openInventory(inv);
            return;
        }
        int slot = 0;
        for (String id : reportsConfig.getConfigurationSection("reports").getKeys(false)) {
            if (slot >= 54) break;
            String status = reportsConfig.getString("reports." + id + ".status");
            if (!"PENDING".equals(status)) continue;
            String title = reportsConfig.getString("reports." + id + ".title");
            String senderName = reportsConfig.getString("reports." + id + ".playerName");
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(title != null ? title : "Untitled Report", NamedTextColor.YELLOW));
                meta.setLore(List.of("Submitted by: " + senderName));
                meta.getPersistentDataContainer().set(reportIdKey, PersistentDataType.STRING, id);
                paper.setItemMeta(meta);
            }
            inv.setItem(slot++, paper);
        }
        player.openInventory(inv);
    }

    private void openInspectGUI(Player player, String id) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Reviewing Report"));
         
        String title = reportsConfig.getString("reports." + id + ".title");
        List<String> pages = reportsConfig.getStringList("reports." + id + ".pages");
         
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text(title != null ? title : "Bug Content", NamedTextColor.GOLD));
            List<String> lore = new ArrayList<>(pages);
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);
        // Deny Button
        ItemStack deny = new ItemStack(Material.RED_WOOL);
        ItemMeta denyMeta = deny.getItemMeta();
        if (denyMeta != null) {
            denyMeta.displayName(Component.text("Deny", NamedTextColor.RED));
            denyMeta.getPersistentDataContainer().set(reportIdKey, PersistentDataType.STRING, id);
            deny.setItemMeta(denyMeta);
        }
        inv.setItem(11, deny);
        // Back Button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(Component.text("Back", NamedTextColor.GRAY));
            backMeta.getPersistentDataContainer().set(reportIdKey, PersistentDataType.STRING, id);
            back.setItemMeta(backMeta);
        }
        inv.setItem(13, back);
        // Approve Button
        ItemStack approve = new ItemStack(Material.GREEN_WOOL);
        ItemMeta approveMeta = approve.getItemMeta();
        if (approveMeta != null) {
            approveMeta.displayName(Component.text("Approve", NamedTextColor.GREEN));
            approveMeta.getPersistentDataContainer().set(reportIdKey, PersistentDataType.STRING, id);
            approve.setItemMeta(approveMeta);
        }
        inv.setItem(15, approve);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String viewTitle = event.getView().getTitle();
        if (!viewTitle.equals("Bug Mail - Reports") && !viewTitle.equals("Reviewing Report")) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String id = meta.getPersistentDataContainer().get(reportIdKey, PersistentDataType.STRING);
        if (id == null) return;
        if (viewTitle.equals("Bug Mail - Reports")) {
            openInspectGUI(player, id);
        } else if (viewTitle.equals("Reviewing Report")) {
            Material type = clicked.getType();
            if (type == Material.BARRIER) {
                openMailGUI(player);
            } else if (type == Material.GREEN_WOOL) {
                resolveReport(id, "APPROVED");
                player.sendMessage(Component.text("Report approved.", NamedTextColor.GREEN));
                player.closeInventory();
            } else if (type == Material.RED_WOOL) {
                resolveReport(id, "DENIED");
                player.sendMessage(Component.text("Report denied.", NamedTextColor.RED));
                player.closeInventory();
            }
        }
    }

    private void resolveReport(String id, String status) {
        reportsConfig.set("reports." + id + ".status", status);
        String uuidStr = reportsConfig.getString("reports." + id + ".player");
        String title = reportsConfig.getString("reports." + id + ".title");
        saveReportsConfig();
        if (uuidStr == null) return;
        UUID uuid = UUID.fromString(uuidStr);
        Player target = Bukkit.getPlayer(uuid);
        Component message = Component.text("[Bug Tracker] Your report '", NamedTextColor.BLUE)
                .append(Component.text(title != null ? title : "Untitled", NamedTextColor.YELLOW))
                .append(Component.text("' was ", NamedTextColor.BLUE))
                .append(Component.text(status, status.equals("APPROVED") ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (target != null && target.isOnline()) {
            target.sendMessage(message);
        } else {
            List<String> notifications = reportsConfig.getStringList("notifications." + uuidStr);
            if (notifications == null) {
                notifications = new ArrayList<>();
            }
            String dynamicAlert = (status.equals("APPROVED") ? " aAPPROVED" : " cDENIED");
            notifications.add(" 9[Bug Tracker] Your report ' e" + (title != null ? title : "Untitled") + " 9' was " + dynamicAlert);
            reportsConfig.set("notifications." + uuidStr, notifications);
            saveReportsConfig();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuidStr = player.getUniqueId().toString();
        if (reportsConfig.contains("notifications." + uuidStr)) {
            List<String> notifications = reportsConfig.getStringList("notifications." + uuidStr);
            for (String msg : notifications) {
                player.sendMessage(Component.text(msg));
            }
            reportsConfig.set("notifications." + uuidStr, null);
            saveReportsConfig();
        }
    }

    private void createReportsConfig() {
        reportsFile = new File(getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            reportsFile.getParentFile().mkdirs();
            try {
                reportsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
    }

    private void saveReportsConfig() {
        try {
            reportsConfig.save(reportsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}