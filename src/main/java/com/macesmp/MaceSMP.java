package com.macesmp;

import com.macesmp.commands.BuyCommand;
import com.macesmp.commands.MaceShopCommand;
import com.macesmp.commands.MaceStatsCommand;
import com.macesmp.listeners.CraftListener;
import com.macesmp.listeners.DeathListener;
import com.macesmp.listeners.MaceProtectionListener;
import com.macesmp.listeners.PlayerJoinListener;
import com.macesmp.listeners.ResetConfirmListener;
import com.macesmp.listeners.ShopListener;
import com.macesmp.util.MaceUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/*
 * Main class for MaceSMP - a minimal Lifesteal-inspired mace plugin.
 */
public final class MaceSMP extends JavaPlugin {

    private static MaceSMP instance;

    // In-memory player data
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    // Namespaced keys helper 
    private MaceUtils maceUtils;

    // Data file for persistence
    private File dataFile;
    private FileConfiguration dataConfig;

    // Task that periodically clears lastKills queues
    private BukkitTask lastKillsResetTask;

    // Players pending chat confirmation for mace enchant reset (type "yes")
    private final Set<UUID> pendingResetConfirmations = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();
        reloadConfig();

        // Init utils (creates NamespacedKeys)
        maceUtils = new MaceUtils(this);

        // Load persistent player data
        loadData();

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new MaceProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new ResetConfirmListener(this), this);

        // Register commands
        getCommand("maceshop").setExecutor(new MaceShopCommand(this));
        BuyCommand buyCmd = new BuyCommand(this);
        getCommand("buy").setExecutor(buyCmd);
        getCommand("buy").setTabCompleter(buyCmd);
        getCommand("macestats").setExecutor(new MaceStatsCommand(this));

        // Schedule anti-farm lastKills reset (every N minutes)
        int resetMinutes = getConfig().getInt("last-kills-reset-minutes", 10);
        long ticks = resetMinutes * 60L * 20L;
        lastKillsResetTask = Bukkit.getScheduler().runTaskTimer(this, this::resetAllLastKills, ticks, ticks);

        // Register the claim mace recipe (mace surrounded by 4 netherite ingots in cross pattern)
        // Allows claiming ownership of another player's mace (preserves enchants etc.)
        NamespacedKey claimKey = new NamespacedKey(this, "claim_mace");
        ShapedRecipe claimRecipe = new ShapedRecipe(claimKey, new ItemStack(Material.MACE));
        claimRecipe.shape(" N ", "NMN", " N ");
        claimRecipe.setIngredient('N', Material.NETHERITE_INGOT);
        claimRecipe.setIngredient('M', Material.MACE);
        Bukkit.addRecipe(claimRecipe);
        getLogger().info("Registered claim mace recipe (netherite claim mechanic).");

        getLogger().info("MaceSMP enabled! (Paper 1.21.x, Java 21) - b2.2 with GUI refresh + reset confirmation + claim recipe");
    }

    @Override
    public void onDisable() {
        // Cancel scheduler
        if (lastKillsResetTask != null) {
            lastKillsResetTask.cancel();
        }

        // Clear any pending resets
        pendingResetConfirmations.clear();

        // Save all data
        saveData();

        playerDataMap.clear();
        getLogger().info("MaceSMP disabled. Data saved.");
    }

    public static MaceSMP getInstance() {
        return instance;
    }

    public MaceUtils getMaceUtils() {
        return maceUtils;
    }

    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }

    /*
     * Get or create PlayerData for a UUID.
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerData::new);
    }

    /*
     * Reset lastKills for every loaded player (anti-farm).
     */
    private void resetAllLastKills() {
        for (PlayerData data : playerDataMap.values()) {
            data.getLastKills().clear();
        }
        getLogger().info("Reset lastKills queues for all players (anti-farm timer).");
    }

    // Persistence (simple YAML)

    private void loadData() {
        dataFile = new File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create playerdata.yml", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    PlayerData data = PlayerData.fromConfig(uuid, dataConfig.getConfigurationSection("players." + key));
                    playerDataMap.put(uuid, data);
                } catch (IllegalArgumentException ex) {
                    getLogger().warning("Invalid UUID in playerdata.yml: " + key);
                }
            }
        }
        getLogger().info("Loaded data for " + playerDataMap.size() + " players.");
    }

    public void saveData() {
        if (dataConfig == null || dataFile == null) return;

        dataConfig.set("players", null); // clear old
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            String path = "players." + entry.getKey().toString();
            entry.getValue().saveToConfig(dataConfig, path);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save playerdata.yml", e);
        }
    }

    /*
     * Convenience: save a single player's data immediately (optional, we save on disable).
     */
    public void savePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;
        String path = "players." + uuid.toString();
        data.saveToConfig(dataConfig, path);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to save data for " + uuid, e);
        }
    }

    // Reset confirmation system (for /maceshop RESET ENCHANTS feature)

    public void addPendingReset(UUID uuid) {
        pendingResetConfirmations.add(uuid);
    }

    public boolean isPendingReset(UUID uuid) {
        return pendingResetConfirmations.contains(uuid);
    }

    public void removePendingReset(UUID uuid) {
        pendingResetConfirmations.remove(uuid);
    }

    /*
     * Perform the actual enchant reset on the player's currently held owned mace.
     * Called from chat confirmation listener (synced).
     */
    public void performMaceReset(Player player) {
        if (player == null || !player.isOnline()) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        MaceUtils utils = getMaceUtils();

        if (held.getType() != Material.MACE || !utils.isOwnedBy(held, player)) {
            utils.sendRaw(player, "<red>You must be holding your owned mace to reset enchants.</red>");
            return;
        }

        ItemMeta meta = held.getItemMeta();
        if (meta != null) {
            // Remove every enchant currently on the mace
            java.util.Set<org.bukkit.enchantments.Enchantment> currentEnchants =
                    new java.util.HashSet<>(meta.getEnchants().keySet());
            for (org.bukkit.enchantments.Enchantment e : currentEnchants) {
                meta.removeEnchant(e);
            }
            held.setItemMeta(meta);

            utils.sendRaw(player, "<green>All enchants have been successfully reset on your mace.</green>");
            utils.sendRaw(player, "<gray>Owner tag and lore preserved. Your mace is now vanilla base again.</gray>");
            getLogger().info(player.getName() + " confirmed mace enchant reset via GUI.");
        }
    }
}
