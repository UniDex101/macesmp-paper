package com.macesmp.commands;

import com.macesmp.MaceSMP;
import com.macesmp.PlayerData;
import com.macesmp.util.MaceUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * `/maceshop` – shows current points and a clickable list of buyable enchants.
 * Updated in b2.2: Player head in info slot, RESET ENCHANTS barrier when holding mace,
 * GUI can be refreshed after purchase instead of closing.
 */

public class MaceShopCommand implements CommandExecutor {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public MaceShopCommand(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    private static final NamespacedKey SHOP_ENCHANT_KEY = new NamespacedKey("macesmp", "shop_enchant");
    private static final NamespacedKey RESET_KEY = new NamespacedKey("macesmp", "reset_action");

    // Nice representative icons for each enchant in the shop
    private static final Map<String, Material> ENCHANT_ICONS = Map.of(
            "density", Material.ANVIL,
            "breach", Material.NETHERITE_AXE,
            "wind_burst", Material.FEATHER,
            "fire_aspect", Material.BLAZE_POWDER,
            "unbreaking", Material.NETHERITE_PICKAXE,
            "mending", Material.EXPERIENCE_BOTTLE
    );

    private static final List<String> ENCHANT_ORDER = List.of(
            "density", "breach", "wind_burst", "fire_aspect", "unbreaking", "mending"
    );

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("macesmp.shop")) {
            utils.send(player, "no-permission");
            return true;
        }

        openShopGUI(player);
        return true;
    }

    /*
     * Opens a premium double-chest GUI shop for the player.
     * Public so ShopListener can re-open it after successful purchase (if points remain).
     */
    public void openShopGUI(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int cost = plugin.getConfig().getInt("enchant-cost", 1);

        // Create double chest inventory with Adventure Component title
        Component title = Component.text("Mace Enchant Shop", NamedTextColor.GOLD)
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Points: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(data.getMacePoints()), NamedTextColor.YELLOW)));

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill with nice black glass border + accents
        ItemStack filler = createFillerPane();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Top info bar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }

        // Info item in center top : NOW PLAYER HEAD with skin!
        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta infoMeta = (SkullMeta) info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setOwningPlayer(player);
            infoMeta.displayName(Component.text("Mace Shop", NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Your Points: ", NamedTextColor.GRAY)
                    .append(Component.text(data.getMacePoints(), NamedTextColor.YELLOW)
                            .decorate(TextDecoration.BOLD)));
            lore.add(Component.text("Cost per upgrade: ", NamedTextColor.GRAY)
                    .append(Component.text(cost + " point", NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            lore.add(Component.text("Hold your owned mace to view", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("current levels & purchase!", NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Click an enchant to buy/upgrade", NamedTextColor.GREEN));
            infoMeta.lore(lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);

        // Enchant items - nice centered layout (two rows of 3 with spacing)
        int[] slots = {11, 13, 15, 29, 31, 33};

        ItemStack held = player.getInventory().getItemInMainHand();
        boolean hasOwnedMace = held.getType() == Material.MACE && utils.isOwnedBy(held, player);

        for (int i = 0; i < ENCHANT_ORDER.size(); i++) {
            String enchantName = ENCHANT_ORDER.get(i);
            int slot = slots[i];
            Material icon = ENCHANT_ICONS.getOrDefault(enchantName, Material.ENCHANTED_BOOK);
            inv.setItem(slot, createEnchantItem(enchantName, icon, held, hasOwnedMace, cost));
        }

        // Bottom tip row
        ItemStack tip = new ItemStack(Material.PAPER);
        ItemMeta tipMeta = tip.getItemMeta();
        if (tipMeta != null) {
            tipMeta.displayName(Component.text("Tip", NamedTextColor.YELLOW));
            tipMeta.lore(List.of(
                    Component.text("Hold owned mace → Click enchant to upgrade", NamedTextColor.GRAY),
                    Component.text("Enchants bought with points are permanent!", NamedTextColor.DARK_GRAY),
                    Component.text("Use /macestats to see your stats", NamedTextColor.DARK_GRAY)
            ));
            tip.setItemMeta(tipMeta);
        }
        inv.setItem(49, tip);

        // RESET ENCHANTS barrier button (bottom left) - only visible when holding owned mace
        if (hasOwnedMace) {
            inv.setItem(45, createResetItem());
        }

        // Open for player
        player.openInventory(inv);
        utils.sendRaw(player, "<gray>Opened Mace Enchant Shop. Hold your mace to see current levels.</gray>");
    }

    private ItemStack createFillerPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createEnchantItem(String enchantName, Material icon, ItemStack heldMace,
                                        boolean hasOwnedMace, int cost) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Enchantment enchant = utils.getEnchantByName(enchantName);
        String displayName = utils.getEnchantDisplayName(enchant);

        // Determine current level
        int current = 0;
        int max = (enchant != null) ? utils.getMaxLevel(enchant) : 5;

        if (hasOwnedMace && enchant != null) {
            current = heldMace.getEnchantmentLevel(enchant);
        }

        // Display name with level info (proper Components, no mini-message tags)
        NamedTextColor levelColor = (current >= max) ? NamedTextColor.RED : NamedTextColor.YELLOW;
        meta.displayName(Component.text(displayName, NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("  [" + (current >= max ? "MAX" : current + "/" + max) + "]", levelColor)));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(getEnchantDescription(enchantName), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Level: ", NamedTextColor.GRAY)
                .append(Component.text(current + " / " + max, (current >= max ? NamedTextColor.RED : NamedTextColor.YELLOW))));
        lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                .append(Component.text(cost + " point per level", NamedTextColor.YELLOW)));
        lore.add(Component.empty());

        if (!hasOwnedMace) {
            lore.add(Component.text("⚠ Hold your owned mace in main hand", NamedTextColor.RED));
            lore.add(Component.text("to purchase this upgrade!", NamedTextColor.RED));
        } else if (current >= max) {
            lore.add(Component.text("✓ Already at maximum level", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("➤ Click to upgrade to level " + (current + 1), NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD));
        }

        meta.lore(lore);

        // Tag the item so ShopListener knows which enchant it is
        meta.getPersistentDataContainer().set(SHOP_ENCHANT_KEY, PersistentDataType.STRING, enchantName);

        item.setItemMeta(meta);
        return item;
    }

    /*
     * Creates the red barrier item for resetting enchants (with confirmation flow).
     */
    private ItemStack createResetItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("RESET ENCHANTS", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("⚠ WARNING: PERMANENT ACTION", NamedTextColor.RED));
        lore.add(Component.text("This will remove ALL enchants from", NamedTextColor.GRAY));
        lore.add(Component.text("your currently held mace!", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Clicking will close the shop and ask", NamedTextColor.YELLOW));
        lore.add(Component.text("you to type 'yes' in chat to confirm.", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("No points are refunded.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        // Tag so ShopListener can detect the reset action
        meta.getPersistentDataContainer().set(RESET_KEY, PersistentDataType.STRING, "confirm");

        item.setItemMeta(meta);
        return item;
    }

    private String getEnchantDescription(String name) {
        return switch (name.toLowerCase()) {
            case "density" -> "Increases smash attack damage";
            case "breach" -> "Reduces effectiveness of armor on smash";
            case "wind_burst" -> "Launches you upward on smash hit";
            case "fire_aspect" -> "Sets targets on fire";
            case "unbreaking" -> "Increases durability";
            case "mending" -> "Repairs with XP orbs";
            default -> "Mace enchantment";
        };
    }

    // Old text method kept for reference / future use if needed
    @SuppressWarnings("unused")
    private void sendEnchantLine(Player player, String cmdName, String display, String desc) {
        // No longer used - GUI replaced the text shop
    }
}

