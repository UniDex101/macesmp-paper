package com.macesmp.listeners;

import com.macesmp.MaceSMP;
import com.macesmp.PlayerData;
import com.macesmp.util.MaceUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/*
 * On join: ensure player data exists and give a starter mace if they don't own any.
 */
public class PlayerJoinListener implements Listener {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public PlayerJoinListener(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Ensure data is loaded / created
        PlayerData data = plugin.getPlayerData(uuid);

        // Give starter mace only if the player currently has no owned mace
        if (!utils.hasOwnedMace(player)) {
            ItemStack starter = utils.createStarterMace(player);
            // Try to add to inventory; drop if full
            var leftover = player.getInventory().addItem(starter);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            utils.sendRaw(player, "<green>You received your starter mace! Protect it well.</green>");
            plugin.getLogger().info("Gave starter mace to " + player.getName());
        }

        // Optional: welcome message with points
        utils.sendRaw(player, "<gray>Mace points: <yellow>" + data.getMacePoints() + "</yellow> | " +
                "Kills: <yellow>" + data.getKills() + "</yellow> | Deaths: <yellow>" + data.getDeaths() + "</yellow>");
    }
}

