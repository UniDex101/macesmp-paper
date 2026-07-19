package com.macesmp.listeners;

import com.macesmp.MaceSMP;
import com.macesmp.util.MaceUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/*
 * Handles mace crafting:
 * - Normal mace craft (breeze rod + heavy core etc.): tags with owner + starter lore.
 * - NEW: Claim recipe (mace in center + 4 netherite ingots in cross): claims ownership of someone else's mace, preserves enchants/durability/custom name, updates "Bound to:" lore, costs netherite.
 * Uses PrepareItemCraftEvent for preview + CraftItemEvent for final item + messages.
 */
public class CraftListener implements Listener {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public CraftListener(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    /*
     * Check if the crafting matrix matches the claim mace recipe pattern:
     *   N
     * N M N
     *   N
     * (N = netherite ingot, M = any mace)
     */
    private boolean isClaimPattern(ItemStack[] matrix) {
        if (matrix == null || matrix.length < 9) return false;
        ItemStack pos1 = matrix[1];   // top center
        ItemStack pos3 = matrix[3];   // middle left
        ItemStack pos4 = matrix[4];   // center (mace)
        ItemStack pos5 = matrix[5];   // middle right
        ItemStack pos7 = matrix[7];   // bottom center
        return pos1 != null && pos1.getType() == Material.NETHERITE_INGOT &&
               pos3 != null && pos3.getType() == Material.NETHERITE_INGOT &&
               pos4 != null && pos4.getType() == Material.MACE &&
               pos5 != null && pos5.getType() == Material.NETHERITE_INGOT &&
               pos7 != null && pos7.getType() == Material.NETHERITE_INGOT;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;

        CraftingInventory craftingInv = event.getInventory();
        ItemStack[] matrix = craftingInv.getMatrix();

        // NEW: Claim mace recipe (priority over normal mace craft)
        if (isClaimPattern(matrix)) {
            ItemStack centerMace = matrix[4];
            if (centerMace != null && centerMace.getType() == Material.MACE) {
                // Clone the INPUT mace to preserve enchants, durability, custom displayName, etc.
                ItemStack claimed = centerMace.clone();
                utils.setOwner(claimed, player.getUniqueId());

                var meta = claimed.getItemMeta();
                if (meta != null) {
                    // Update lore: replace old "Bound to:" with new owner, keep other lore/enchants info
                    List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.removeIf(line -> {
                        String plain = PlainTextComponentSerializer.plainText().serialize(line);
                        return plain.contains("Bound to:");
                    });
                    // Add new bound line at top
                    lore.add(0, Component.text("Bound to: " + player.getName(), NamedTextColor.DARK_GRAY));
                    // Add claim note (only if not too many lines already)
                    if (lore.size() <= 3) {
                        lore.add(Component.text("Claimed with netherite — now yours to upgrade!", NamedTextColor.GRAY));
                    }
                    meta.lore(lore);
                    claimed.setItemMeta(meta);
                }

                craftingInv.setResult(claimed);
                return; // Claim handled, skip normal logic
            }
        }

        // ORIGINAL: Normal mace crafting (e.g. breeze rod + heavy core) = tag fresh mace
        ItemStack result = craftingInv.getResult();
        if (result == null || result.getType() != Material.MACE) return;

        ItemStack owned = result.clone();
        utils.setOwner(owned, player.getUniqueId());

        var meta = owned.getItemMeta();
        if (meta != null) {
            // Leave vanilla "Mace" so player can rename freely.
            // Only add ownership to lore.
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Bound to: " + player.getName(), NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Crafted mace — use /buy to enchant it!", NamedTextColor.GRAY));
            meta.lore(lore);
            owned.setItemMeta(meta);
        }

        craftingInv.setResult(owned);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        CraftingInventory craftingInv = event.getInventory();
        ItemStack[] matrix = craftingInv.getMatrix();
        ItemStack current = event.getCurrentItem();

        // NEW: Claim recipe handling + message
        if (isClaimPattern(matrix)) {
            if (current != null && current.getType() == Material.MACE) {
                // Double-check / ensure owner is set correctly (Prepare should have done it)
                if (utils.getOwner(current) == null || !utils.isOwnedBy(current, player)) {
                    utils.setOwner(current, player.getUniqueId());
                    var meta = current.getItemMeta();
                    if (meta != null) {
                        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                        lore.removeIf(line -> {
                            String plain = PlainTextComponentSerializer.plainText().serialize(line);
                            return plain.contains("Bound to:");
                        });
                        lore.add(0, Component.text("Bound to: " + player.getName(), NamedTextColor.DARK_GRAY));
                        lore.add(Component.text("Claimed with netherite — now yours to upgrade!", NamedTextColor.GRAY));
                        meta.lore(lore);
                        current.setItemMeta(meta);
                    }
                    event.setCurrentItem(current);
                }
                utils.sendRaw(player, "<green>You claimed this mace as your own by spending 4 netherite ingots!</green>");
                utils.sendRaw(player, "<yellow>Protect it well and upgrade it using /maceshop or /buy.</yellow>");
                plugin.getLogger().info(player.getName() + " claimed a mace via the netherite claim recipe.");
            }
            return; // Claim handled
        }

        // ORIGINAL: Normal mace craft double-check + message
        if (current != null && current.getType() == Material.MACE) {
            if (utils.getOwner(current) == null) {
                utils.setOwner(current, player.getUniqueId());
                var meta = current.getItemMeta();
                if (meta != null) {
                    // Ensure ownership lore, never touch display name
                    List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.removeIf(line -> {
                        String plain = PlainTextComponentSerializer.plainText().serialize(line);
                        return plain.contains("Bound to:");
                    });
                    lore.add(Component.text("Bound to: " + player.getName(), NamedTextColor.DARK_GRAY));
                    meta.lore(lore);
                    current.setItemMeta(meta);
                }
                event.setCurrentItem(current);
            }
            utils.sendRaw(player, "<green>Crafted mace has been bound to you as owner.</green>");
            plugin.getLogger().info(player.getName() + " crafted an owned mace.");

            // NEW: Unlock the claim recipe for this player now that they have crafted a mace
            try {
                player.discoverRecipe(new org.bukkit.NamespacedKey(plugin, "claim_mace"));
            } catch (Exception ignored) {}
        }
    }
}
