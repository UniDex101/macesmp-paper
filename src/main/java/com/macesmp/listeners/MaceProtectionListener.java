package com.macesmp.listeners;

import com.macesmp.MaceSMP;
import com.macesmp.util.MaceUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

import java.util.Arrays;

/*
 * Clean protection for maces:
 * - Players can fully use Enchantment Tables and Anvils for everything else.
 * - When a mace is placed in the enchantment table → no enchant offers appear.
 * - When trying to enchant a mace via anvil (book or combine) → result is blocked.
 * - Renaming a mace via anvil still works perfectly.
 * - Grindstone is blocked for maces (keeps bought enchants permanent).
 */
public class MaceProtectionListener implements Listener {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public MaceProtectionListener(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    // ENCHANTMENT TABLE

    /*
     * When a mace is placed in the enchantment table, clear all offered enchants.
     * The table still opens normally, lapis can be inserted, but there are no enchant buttons to click.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.MACE) {
            // Clear all three offer slots so nothing can be selected
            Arrays.fill(event.getOffers(), null);
            // Optional: tell the player why (subtle)
            if (event.getEnchanter() instanceof Player player) {
                // We don't spam chat; the empty offers are self-explanatory
                // utils.sendRaw(player, "<gray>This mace cannot be enchanted at the table. Use /buy instead.</gray>");
            }
        }
    }

    /*
     * Safety net: If somehow an enchant still tries to apply to a mace, cancel it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.MACE) {
            event.setCancelled(true);
            Player player = event.getEnchanter();
            utils.sendRaw(player, "<red>Maces cannot be enchanted at the enchantment table.</red>");
            utils.sendRaw(player, "<gray>Use <yellow>/buy <enchant></yellow> while holding your owned mace (costs points).</gray>");
            plugin.getLogger().info(player.getName() + " tried to enchant a mace at the etable (blocked).");
        }
    }

    // ANVIL

    /*
     * Allow renaming maces freely.
     * Block only when someone tries to add enchants to a mace via book or combining maces.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        if (left == null || left.getType() != Material.MACE) {
            return; // Not a mace → do nothing, let vanilla handle everything
        }

        ItemStack right = event.getInventory().getItem(1);
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;

        // Case 1: Pure renaming (right slot empty + player typed a new name)
        String renameText = null;
        if (event.getView() instanceof AnvilView anvilView) {
            renameText = anvilView.getRenameText();
        }
        boolean isRenaming = (right == null || right.getType() == Material.AIR)
                && renameText != null
                && !renameText.trim().isEmpty();

        if (isRenaming) {
            // Completely allow it. Do not touch result. Vanilla will apply the new name.
            return;
        }

        // Case 2: Trying to enchant or combine → block the result
        if (right != null && right.getType() != Material.AIR) {
            boolean rightHasEnchants = !right.getEnchantments().isEmpty()
                    || right.getType() == Material.ENCHANTED_BOOK;
            boolean isAnotherMace = right.getType() == Material.MACE;

            if (rightHasEnchants || isAnotherMace) {
                event.setResult(null); // Prevents taking the item
                if (player != null) {
                    utils.sendRaw(player, "<red>You cannot enchant maces using the anvil.</red>");
                    utils.sendRaw(player, "<gray>Renaming is allowed.</gray>");
                    utils.sendRaw(player, "<gray>For enchants, hold the mace and use <yellow>/buy</yellow>.</gray>");
                }
                plugin.getLogger().info("Blocked anvil enchant attempt on mace by " + (player != null ? player.getName() : "unknown"));
            }
            // Other cases (e.g. repairing with plain items) are left to vanilla
        }
    }

    // GRINDSTONE

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack upper = event.getInventory().getUpperItem();
        ItemStack lower = event.getInventory().getLowerItem();

        if ((upper != null && upper.getType() == Material.MACE) ||
            (lower != null && lower.getType() == Material.MACE)) {
            event.setResult(null);
            Player player = event.getView().getPlayer() instanceof Player p ? p : null;
            if (player != null) {
                utils.sendRaw(player, "<red>You cannot disenchant maces at a grindstone.</red>");
                utils.sendRaw(player, "<gray>Enchants bought with points stay on the mace. <red>PERMANENTLY</red>.</gray>");
            }
        }
    }
}

