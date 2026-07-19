package com.macesmp.listeners;

import com.macesmp.MaceSMP;
import com.macesmp.PlayerData;
import com.macesmp.commands.MaceShopCommand;
import com.macesmp.util.MaceUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/*
 * Handles clicks in the Mace Shop GUI.
 * Prevents taking shop items and processes enchant purchases.
 * Updated b2.2: After purchase, if player still has >0 points, auto-reopen (refresh) the GUI
 * with updated points and current levels. Also handles RESET ENCHANTS barrier clicks.
 */
public class ShopListener implements Listener {

    private final MaceSMP plugin;
    private final MaceUtils utils;
    private final NamespacedKey shopEnchantKey;
    private final NamespacedKey resetKey;

    // Map enchant name -> slot in the 54-slot GUI (for reference, not strictly needed)
    private static final Map<String, Integer> ENCHANT_SLOTS = Map.of(
            "density", 11,
            "breach", 13,
            "wind_burst", 15,
            "fire_aspect", 29,
            "unbreaking", 31,
            "mending", 33
    );

    public ShopListener(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
        this.shopEnchantKey = new NamespacedKey(plugin, "shop_enchant");
        this.resetKey = new NamespacedKey(plugin, "reset_action");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        Component title = event.getView().title();
        if (title == null) return;

        // Check if this is our shop GUI (by title)
        String plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(title);
        if (!plainTitle.contains("Mace Enchant Shop")) {
            return;
        }

        // Always cancel clicks in our shop to prevent taking items (except player inventory)
        if (event.getClickedInventory() == top) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            // Check if this item has our shop enchant tag
            ItemMeta clickedMeta = clicked.getItemMeta();
            String enchantName = (clickedMeta != null)
                    ? clickedMeta.getPersistentDataContainer().get(shopEnchantKey, PersistentDataType.STRING)
                    : null;

            if (enchantName != null) {
                handleEnchantPurchase(player, enchantName, top);
                return;
            }

            // Check for RESET ENCHANTS barrier
            String resetAction = (clickedMeta != null)
                    ? clickedMeta.getPersistentDataContainer().get(resetKey, PersistentDataType.STRING)
                    : null;

            if ("confirm".equals(resetAction)) {
                handleResetRequest(player, top);
            }
        }
        // Player inventory clicks are allowed (shift etc), but we don't process them
    }

    private void handleEnchantPurchase(Player player, String enchantName, Inventory shopInv) {
        // Re-validate: must be holding owned mace RIGHT NOW
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.MACE || !utils.isOwnedBy(held, player)) {
            utils.send(player, "must-hold-owned-mace");
            player.closeInventory();
            return;
        }

        Enchantment enchant = utils.getEnchantByName(enchantName);
        if (enchant == null) {
            utils.sendRaw(player, "<red>Unknown enchant in shop.</red>");
            return;
        }

        int cost = plugin.getConfig().getInt("enchant-cost", 1);
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data.getMacePoints() < cost) {
            utils.send(player, "not-enough-points");
            return;
        }

        int maxLevel = utils.getMaxLevel(enchant);
        int currentLevel = held.getEnchantmentLevel(enchant);

        if (currentLevel >= maxLevel) {
            utils.send(player, "already-max");
            return;
        }

        // Check incompatibilities
        for (Map.Entry<Enchantment, Integer> entry : held.getEnchantments().entrySet()) {
            if (utils.areIncompatible(enchant, entry.getKey())) {
                utils.send(player, "incompatible");
                utils.sendRaw(player, "<gray>Remove " + utils.getEnchantDisplayName(entry.getKey()) +
                        " first.</gray>");
                return;
            }
        }

        // All good - apply upgrade
        int newLevel = currentLevel + 1;

        ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            utils.sendRaw(player, "<red>Could not modify item meta.</red>");
            return;
        }

        meta.addEnchant(enchant, newLevel, true);
        held.setItemMeta(meta);

        // Deduct
        data.addMacePoints(-cost);
        plugin.savePlayerData(player.getUniqueId());

        String display = utils.getEnchantDisplayName(enchant);
        String msg = plugin.getConfig().getString("messages.bought",
                        "<green>Successfully purchased / upgraded <yellow>%enchant%</yellow> to level <yellow>%level%</yellow>!</green>")
                .replace("%enchant%", display)
                .replace("%level%", String.valueOf(newLevel));
        utils.sendRaw(player, msg);
        utils.sendRaw(player, "<gray>Remaining points: <yellow>" + data.getMacePoints() + "</yellow></gray>");

        plugin.getLogger().info(player.getName() + " bought " + display + " " + newLevel +
                " via GUI (cost " + cost + "). Points left: " + data.getMacePoints());

        // NEW b2.2+ behavior: if still has points, refresh/reopen the GUI instead of just closing
        player.closeInventory();
        if (data.getMacePoints() > 0) {
            // Re-open shop so player sees updated points in title + updated current levels on items
            // without needing to re-type /maceshop
            new MaceShopCommand(plugin).openShopGUI(player);
        }
        // If 0 points left, just leave it closed (they can still use /buy if they want)
    }

    private void handleResetRequest(Player player, Inventory shopInv) {
        // Re-validate: must be holding owned mace RIGHT NOW
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.MACE || !utils.isOwnedBy(held, player)) {
            utils.send(player, "must-hold-owned-mace");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        // Mark as pending confirmation
        plugin.addPendingReset(player.getUniqueId());

        // Send warning + instructions
        utils.sendRaw(player, "<red>══════════════════════════════════════</red>");
        utils.sendRaw(player, "<red>⚠ WARNING: RESET ENCHANTS</red>");
        utils.sendRaw(player, "<red>══════════════════════════════════════</red>");
        utils.sendRaw(player, "<gray>This will <red>PERMANENTLY</red> remove <yellow>ALL</yellow> enchantments");
        utils.sendRaw(player, "<gray>from your held mace (owner tag stays).</gray>");
        utils.sendRaw(player, "<gray>No points will be refunded.</gray>");
        utils.sendRaw(player, "");
        utils.sendRaw(player, "<yellow>Type <green>YES</green> in chat to confirm.</yellow>");
        utils.sendRaw(player, "<gray>Type anything else (or wait) to cancel.</gray>");
        utils.sendRaw(player, "<dark_gray>(Auto-cancels after 30 seconds)</dark_gray>");

        // Auto-timeout after 30 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isPendingReset(player.getUniqueId())) {
                plugin.removePendingReset(player.getUniqueId());
                if (player.isOnline()) {
                    utils.sendRaw(player, "<gray>Reset confirmation timed out — cancelled.</gray>");
                }
            }
        }, 30 * 20L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Optional: could clean up if needed
    }
}

