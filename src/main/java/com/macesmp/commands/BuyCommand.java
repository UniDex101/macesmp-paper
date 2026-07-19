package com.macesmp.commands;

import com.macesmp.MaceSMP;
import com.macesmp.PlayerData;
import com.macesmp.util.MaceUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// `/buy <enchant>` – spend 1 point to add/upgrade an enchant on the held owned mace.

public class BuyCommand implements CommandExecutor, TabCompleter {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    private static final List<String> ENCHANT_NAMES = Arrays.asList(
            "density", "breach", "wind_burst", "fire_aspect", "unbreaking", "mending"
    );

    public BuyCommand(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("macesmp.buy")) {
            utils.send(player, "no-permission");
            return true;
        }

        if (args.length < 1) {
            utils.sendRaw(player, "<red>Usage: /buy <enchant></red>");
            utils.sendRaw(player, "<gray>Available: density, breach, wind_burst, fire_aspect, unbreaking, mending</gray>");
            return true;
        }

        // Must hold owned mace in main hand
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.MACE || !utils.isOwnedBy(held, player)) {
            utils.send(player, "must-hold-owned-mace");
            return true;
        }

        Enchantment enchant = utils.getEnchantByName(args[0]);
        if (enchant == null) {
            utils.sendRaw(player, "<red>Unknown enchant: " + args[0] + "</red>");
            return true;
        }

        int cost = plugin.getConfig().getInt("enchant-cost", 1);
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data.getMacePoints() < cost) {
            utils.send(player, "not-enough-points");
            return true;
        }

        int maxLevel = utils.getMaxLevel(enchant);
        int currentLevel = held.getEnchantmentLevel(enchant);

        if (currentLevel >= maxLevel) {
            utils.send(player, "already-max");
            return true;
        }

        // Check incompatibilities with existing enchants
        for (Map.Entry<Enchantment, Integer> entry : held.getEnchantments().entrySet()) {
            if (utils.areIncompatible(enchant, entry.getKey())) {
                utils.send(player, "incompatible");
                utils.sendRaw(player, "<gray>Remove " + utils.getEnchantDisplayName(entry.getKey()) +
                        " first (or it will be replaced if you force, but currently blocked).</gray>");
                return true;
            }
        }

        // All good = apply
        int newLevel  = currentLevel + 1;
        ItemMeta meta = held.getItemMeta();
        if (meta == null) {
            utils.sendRaw(player, "<red>Could not modify item meta.</red>");
            return true;
        }

        // Use addEnchant with force=true so we can go beyond vanilla if config allows, or just set
        meta.addEnchant(enchant, newLevel, true);
        held.setItemMeta(meta);

        // Deduct points
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
                " (cost " + cost + "). Points left: " + data.getMacePoints());

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return ENCHANT_NAMES.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
