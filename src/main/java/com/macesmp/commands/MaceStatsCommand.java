package com.macesmp.commands;

import com.macesmp.MaceSMP;
import com.macesmp.PlayerData;
import com.macesmp.util.MaceUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/*
 * /macestats – private stats view (points, kills, deaths, list of owned maces by name).
 */
public class MaceStatsCommand implements CommandExecutor {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public MaceStatsCommand(MaceSMP plugin) {
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

        if (!player.hasPermission("macesmp.stats")) {
            utils.send(player, "no-permission");
            return true;
        }

        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        player.sendMessage(utils.parse(plugin.getConfig().getString("messages.stats-header",
                "<gold>===== Your Mace Stats =====</gold>")));
        player.sendMessage(Component.text("Points: ", NamedTextColor.GRAY)
                .append(Component.text(data.getMacePoints(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Kills: ", NamedTextColor.GRAY)
                .append(Component.text(data.getKills(), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Deaths: ", NamedTextColor.GRAY)
                .append(Component.text(data.getDeaths(), NamedTextColor.RED)));

        // List owned maces currently in inventory (name or default)
        List<String> ownedNames = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.MACE && utils.isOwnedBy(item, player)) {
                String name = extractName(item);
                ownedNames.add(name);
            }
        }
        // offhand
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getType() == Material.MACE && utils.isOwnedBy(off, player)) {
            ownedNames.add(extractName(off));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Owned maces in inventory (" + ownedNames.size() + "):",
                NamedTextColor.GRAY));

        if (ownedNames.isEmpty()) {
            player.sendMessage(Component.text("  (none found – craft or claim a starter)", NamedTextColor.DARK_GRAY));
        } else {
            for (String n : ownedNames) {
                player.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(n, NamedTextColor.AQUA)));
            }
        }

        // Also show recent lastKills count for transparency
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Recent unique kills (anti-farm queue): ", NamedTextColor.DARK_GRAY)
                .append(Component.text(data.getLastKills().size() + "/" +
                        plugin.getConfig().getInt("last-kills-max-size", 3), NamedTextColor.GRAY)));

        return true;
    }

    private String extractName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            // Convert Component display name to plain string for chat
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
        }
        return "Unnamed Mace";
    }
}

