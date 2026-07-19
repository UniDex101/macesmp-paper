package com.macesmp.util;

import com.macesmp.MaceSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.format.NamedTextColor;

/*
 * Utility helpers for mace ownership (PDC), messages, and enchant checks.
 */
public class MaceUtils {

    private final MaceSMP plugin;
    private final NamespacedKey ownerKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MaceUtils(MaceSMP plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "owner");
    }

    public NamespacedKey getOwnerKey() {
        return ownerKey;
    }

    
    // Tag a mace (or any ItemStack) with the given owner's UUID.
     
    public void setOwner(ItemStack item, UUID owner) {
        if (item == null || item.getType() == Material.AIR) return;
        item.editPersistentDataContainer(pdc -> pdc.set(ownerKey, PersistentDataType.STRING, owner.toString()));
    }


    // Get the owner UUID from a mace, or null if none / not a valid mace.
    
    public UUID getOwner(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) return null;
        String raw = item.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Check whether the given ItemStack is a mace owned by the player.

    public boolean isOwnedBy(ItemStack item, Player player) {
        UUID owner = getOwner(item);
        return owner != null && owner.equals(player.getUniqueId());
    }

    // Create a brand-new starter mace owned by the player.
    public ItemStack createStarterMace(Player player) {
        ItemStack mace = new ItemStack(Material.MACE);
        setOwner(mace, player.getUniqueId());

        ItemMeta meta = mace.getItemMeta();
        if (meta != null) {
            // IMPORTANT: Never put owner name in displayName.
            // Only use lore so players can freely rename the mace via anvil.
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("\nBound to: " + player.getName(), NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Protect it well — this is your starter mace!", NamedTextColor.GRAY));
            meta.lore(lore);
            mace.setItemMeta(meta);
        }
        return mace;
    }

    // Does the player currently have at least one mace they own in their inventory?
  
    public boolean hasOwnedMace(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isOwnedBy(item, player)) {
                return true;
            }
        }
        // also check offhand / cursor? usually inventory is enough
        return isOwnedBy(player.getInventory().getItemInOffHand(), player);
    }

    // Parse a minimessage string (or legacy & codes as fallback) into a Component.

    public Component parse(String message) {
        if (message == null) return Component.empty();
        // Prefer MiniMessage; if it contains classic & codes we can still try
        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        }
    }

    // Send a prefixed message to the player.

    public void send(Player player, String messageKeyOrRaw) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + messageKeyOrRaw, messageKeyOrRaw);
        player.sendMessage(parse(prefix + msg));
    }

    public void sendRaw(Player player, String miniMessageText) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        player.sendMessage(parse(prefix + miniMessageText));
    }

    // Enchant helpers

    public Enchantment getEnchantByName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase().replace(" ", "_");
        return switch (lower) {
            case "density" -> Enchantment.DENSITY;
            case "breach" -> Enchantment.BREACH;
            case "wind_burst", "windburst", "wind" -> Enchantment.WIND_BURST;
            case "fire_aspect", "fireaspect", "fire" -> Enchantment.FIRE_ASPECT;
            case "unbreaking" -> Enchantment.UNBREAKING;
            case "mending" -> Enchantment.MENDING;
            default -> null;
        };
    }

    public String getEnchantDisplayName(Enchantment enchant) {
        if (enchant == null) return "Unknown";
        // Simple readable names
        String key = enchant.getKey().getKey();
        return switch (key) {
            case "density" -> "Density";
            case "breach" -> "Breach";
            case "wind_burst" -> "Wind Burst";
            case "fire_aspect" -> "Fire Aspect";
            case "unbreaking" -> "Unbreaking";
            case "mending" -> "Mending";
            default -> key;
        };
    }

    /*
     * Check if two enchants are mutually exclusive on a mace.
     * Vanilla: Density and Breach cannot coexist.
     */
    public boolean areIncompatible(Enchantment a, Enchantment b) {
        if (a == null || b == null) return false;
        // Density <-> Breach
        if ((a == Enchantment.DENSITY && b == Enchantment.BREACH) ||
            (a == Enchantment.BREACH && b == Enchantment.DENSITY)) {
            return true;
        }
        return false;
    }

    /*
     * Get the configured max level for an enchant.
     */
    public int getMaxLevel(Enchantment enchant) {
        if (enchant == null) return 0;
        String key = enchant.getKey().getKey();
        return plugin.getConfig().getInt("max-levels." + key, enchant.getMaxLevel());
    }
}
