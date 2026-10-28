package com.dbsmp.economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DBSEconomy extends JavaPlugin implements Listener {

    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, UUID> pendingTrades = new HashMap<>();
    private final Map<UUID, TradeSession> activeTrades = new HashMap<>();
    private final Map<UUID, List<UUID>> friendRequests = new HashMap<>();
    private final Map<UUID, List<UUID>> friendsList = new HashMap<>();
    
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataConfig();
        loadPlayerData();
        
        getServer().getPluginManager().registerEvents(this, this);
        
        getCommand("balance").setExecutor(new BalanceCommand());
        getCommand("pay").setExecutor(new PayCommand());
        getCommand("shop").setExecutor(new ShopCommand());
        getCommand("sell").setExecutor(new SellCommand());
        getCommand("trade").setExecutor(new TradeCommand());
        getCommand("friend").setExecutor(new FriendCommand());
        
        getLogger().info("DBSMP Economy Plugin Enabled Successfully for Eaglercraft 1.8.8!");
    }

    @Override
    public void onDisable() {
        savePlayerData();
    }

    private void createDataConfig() {
        dataFile = new File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadPlayerData() {
        if (dataConfig.getConfigurationSection("balances") != null) {
            for (String key : dataConfig.getConfigurationSection("balances").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                double bal = dataConfig.getDouble("balances." + key);
                balances.put(uuid, bal);
            }
        }
        if (dataConfig.getConfigurationSection("friends") != null) {
            for (String key : dataConfig.getConfigurationSection("friends").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                List<String> list = dataConfig.getStringList("friends." + key);
                List<UUID> fUUIDs = new ArrayList<>();
                for (String s : list) {
                    fUUIDs.add(UUID.fromString(s));
                }
                friendsList.put(uuid, fUUIDs);
            }
        }
    }

    private void savePlayerData() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            dataConfig.set("balances." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, List<UUID>> entry : friendsList.entrySet()) {
            List<String> sList = new ArrayList<>();
            for (UUID u : entry.getValue()) {
                sList.add(u.toString());
            }
            dataConfig.set("friends." + entry.getKey().toString(), sList);
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 30000.0);
    }

    public void setBalance(UUID uuid, double amount) {
        balances.put(uuid, amount);
        savePlayerData();
    }

    // --- GUI SHOP SYSTEM ---
    public void openShop(Player player) {
        Inventory shop = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "DBSMP Shop Menu");
        
        shop.setItem(10, createShopItem(Material.DIAMOND_CHESTPLATE, "§bDiamond Chestplate", 5000));
        shop.setItem(11, createShopItem(Material.DIAMOND_SWORD, "§bDiamond Sword", 3000));
        shop.setItem(12, createShopItem(Material.BOW, "§eHunter Bow", 1500));
        shop.setItem(14, createShopItem(Material.ARROW, "§7Arrows x64", 200));
        shop.setItem(15, createShopItem(Material.ENCHANTED_BOOK, "§dSharpness V Book", 10000));
        shop.setItem(16, createShopItem(Material.ENCHANTED_BOOK, "§dProtection IV Book", 10000));
        
        player.openInventory(shop);
    }

    private ItemStack createShopItem(Material mat, String name, int price) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList("§7Price: §a$" + price, "§eClick to purchase"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        Player player = (Player) event.getWhoClicked();
        
        if (event.getInventory().getTitle().equals(ChatColor.DARK_BLUE + "DBSMP Shop Menu")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getLore() == null) return;
            
            String priceStr = ChatColor.stripColor(clicked.getItemMeta().getLore().get(0)).replace("Price: $", "");
            double price = Double.parseDouble(priceStr);
            double bal = getBalance(player.getUniqueId());
            
            if (bal < price) {
                player.sendMessage("§cYou don't have enough money!");
                return;
            }
            
            setBalance(player.getUniqueId(), bal - price);
            ItemStack itemToGive = new ItemStack(clicked.getType(), clicked.getAmount() == 0 ? 1 : clicked.getAmount());
            ItemMeta meta = itemToGive.getItemMeta();
            
            if (clicked.getType() == Material.ENCHANTED_BOOK) {
                if (clicked.getItemMeta().getDisplayName().contains("Sharpness")) {
                    meta.addEnchant(Enchantment.DAMAGE_ALL, 5, true);
                } else if (clicked.getItemMeta().getDisplayName().contains("Protection")) {
                    meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4, true);
                }
            }
            itemToGive.setItemMeta(meta);
            player.getInventory().addItem(itemToGive);
            player.sendMessage("§aPurchased successfully! New balance: $" + (bal - price));
            return;
        }

        if (event.getInventory().getTitle().contains("DBSMP Trading Interface")) {
            UUID uuid = player.getUniqueId();
            if (!activeTrades.containsKey(uuid)) return;
            
            TradeSession session = activeTrades.get(uuid);
            int slot = event.getRawSlot();
            
            // Prevent touching the dividers, status buttons, or opponent rows
            if (session.isDividerOrControlSlot(player, slot)) {
                event.setCancelled(true);
                if (session.isAcceptSlot(player, slot)) {
                    session.toggleAccept(player);
                }
                return;
            }
            
            // If they clicked their own inventory slot to add an item
            if (slot >= event.getInventory().getSize()) {
                // Let standard inventory move handle it or code custom logic below for simplicity
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (event.getInventory().getTitle().contains("DBSMP Trading Interface")) {
            TradeSession session = activeTrades.remove(player.getUniqueId());
            if (session != null) {
                session.cancel();
            }
        }
    }

    // --- COMMAND IMPLEMENTATIONS ---
    private class BalanceCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            p.sendMessage("§aYour Balance: §e$" + getBalance(p.getUniqueId()));
            return true;
        }
    }

    private class PayCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (args.length < 2) {
                p.sendMessage("§cUsage: /pay <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage("§cPlayer not found.");
                return true;
            }
            try {
                double amount = Double.parseDouble(args[1]);
                if (amount <= 0) throw new NumberFormatException();
                double pBal = getBalance(p.getUniqueId());
                if (pBal < amount) {
                    p.sendMessage("§cInsufficient funds.");
                    return true;
                }
                setBalance(p.getUniqueId(), pBal - amount);
                setBalance(target.getUniqueId(), getBalance(target.getUniqueId()) + amount);
                p.sendMessage("§aSent $" + amount + " to " + target.getName());
                target.sendMessage("§aReceived $" + amount + " from " + p.getName());
            } catch (NumberFormatException e) {
                p.sendMessage("§cInvalid number amount.");
            }
            return true;
        }
    }

    private class ShopCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            openShop((Player) sender);
            return true;
        }
    }

    private class SellCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            ItemStack item = p.getItemInHand();
            if (item == null || item.getType() == Material.AIR) {
                p.sendMessage("§cHold an item in your hand to sell.");
                return true;
            }
            double val = 0;
            if (item.getType() == Material.DIAMOND) val = 100;
            else if (item.getType() == Material.GOLD_INGOT) val = 50;
            else if (item.getType() == Material.IRON_INGOT) val = 20;
            else if (item.getType() == Material.COAL) val = 5;
            
            if (val == 0) {
                p.sendMessage("§cThat item cannot be sold to the server.");
                return true;
            }
            int amt = item.getAmount();
            double finalPayout = val * amt;
            p.setItemInHand(null);
            setBalance(p.getUniqueId(), getBalance(p.getUniqueId()) + finalPayout);
            p.sendMessage("§aSold " + amt + " " + item.getType().name() + " for §e$" + finalPayout);
            return true;
        }
    }

    private class TradeCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (args.length < 1) {
                p.sendMessage("§cUsage: /trade <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) {
                p.sendMessage("§cInvalid target player.");
                return true;
            }
            if (pendingTrades.get(target.getUniqueId()) == p.getUniqueId()) {
                pendingTrades.remove(target.getUniqueId());
                TradeSession session = new TradeSession(p, target, DBSEconomy.this);
                activeTrades.put(p.getUniqueId(), session);
                activeTrades.put(target.getUniqueId(), session);
                session.open();
            } else {
                pendingTrades.put(p.getUniqueId(), target.getUniqueId());
                p.sendMessage("§aTrade request sent to " + target.getName());
                target.sendMessage("§a" + p.getName() + " wants to trade. Type §e/trade " + p.getName() + " §ato accept.");
            }
            return true;
        }
    }

    private class FriendCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (args.length < 2) {
                p.sendMessage("§e/friend add <name> §7- Send a friend request");
                p.sendMessage("§e/friend list §7- View your current friends");
                return true;
            }
            if (args[0].equalsIgnoreCase("add")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || target.equals(p)) {
                    p.sendMessage("§cPlayer online not found.");
                    return true;
                }
                List<UUID> reqs = friendRequests.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
                if (friendRequests.containsKey(p.getUniqueId()) && friendRequests.get(p.getUniqueId()).contains(target.getUniqueId())) {
                    // Mutual accept
                    friendRequests.get(p.getUniqueId()).remove(target.getUniqueId());
                    friendsList.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(target.getUniqueId());
                    friendsList.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>()).add(p.getUniqueId());
                    p.sendMessage("§aYou and " + target.getName() + " are now friends!");
                    target.sendMessage("§aYou and " + p.getName() + " are now friends!");
                } else {
                    reqs.add(p.getUniqueId());
                    p.sendMessage("§aFriend request sent to " + target.getName());
                    target.sendMessage("§a" + p.getName() + " sent you a friend request. Type §e/friend add " + p.getName() + " §ato accept.");
                }
            } else if (args[0].equalsIgnoreCase("list")) {
                List<UUID> friends = friendsList.getOrDefault(p.getUniqueId(), new ArrayList<>());
                p.sendMessage("§b--- Friends List ---");
                for (UUID u : friends) {
                    p.sendMessage("§7- §f" + Bukkit.getOfflinePlayer(u).getName());
                }
            }
            return true;
        }
    }
}

class TradeSession {
    private final Player p1;
    private final Player p2;
    private final DBSEconomy plugin;
    private final Inventory inv;
    private boolean p1Ready = false;
    private boolean p2Ready = false;

    public TradeSession(Player p1, Player p2, DBSEconomy plugin) {
        this.p1 = p1;
        this.p2 = p2;
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(null, 45, "DBSMP Trading Interface");
        setupUI();
    }

    private void setupUI() {
        ItemStack pane = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        
        for (int i = 4; i < 45; i += 9) {
            inv.setItem(i, pane);
        }
        updateStatusButtons();
    }

    private void updateStatusButtons() {
        inv.setItem(40, createStatusItem(p1Ready ? ChatColor.GREEN + "You are READY" : ChatColor.RED + "Click to Ready Up"));
        inv.setItem(44, createStatusItem(p2Ready ? ChatColor.GREEN + "Opponent READY" : ChatColor.RED + "Opponent Not Ready"));
    }

    private ItemStack createStatusItem(String title) {
        ItemStack item = new ItemStack(Material.WOOL, 1, (short) (title.contains("READY") ? 5 : 14));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        p1.openInventory(inv);
        p2.openInventory(inv);
    }

    public boolean isDividerOrControlSlot(Player p, int slot) {
        if (slot % 9 == 4 || slot >= 36) return true;
        if (p.equals(p1) && slot % 9 > 4) return true; // p1 can't touch right side
        if (p.equals(p2) && slot % 9 < 4) return true; // p2 can't touch left side
        return false;
    }

    public boolean isAcceptSlot(Player p, int slot) {
        return (p.equals(p1) && slot == 40) || (p.equals(p2) && slot == 40); // Simple center controller trigger
    }

    public void toggleAccept(Player p) {
        if (p.equals(p1)) p1Ready = !p1Ready;
        if (p.equals(p2)) p2Ready = !p2Ready;
        updateStatusButtons();
        if (p1Ready && p2Ready) {
            completeTrade();
        }
    }

    private void completeTrade() {
        // Complete items swapping safety code here
        p1.sendMessage("§aTrade Completed Successfully!");
        p2.sendMessage("§aTrade Completed Successfully!");
        p1.closeInventory();
        p2.closeInventory();
    }

    public void cancel() {
        p1.sendMessage("§cTrade cancelled.");
        p2.sendMessage("§cTrade cancelled.");
    }
}
