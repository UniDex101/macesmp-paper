package com.macesmp.listeners;

import com.macesmp.MaceSMP;
import com.macesmp.util.MaceUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/*
 * Listens for chat messages when a player has a pending mace reset confirmation.
 * If they type exactly "yes" (case-insensitive, trimmed), performs the reset.
 * Anything else cancels the pending request.
 * The "yes" message is hidden from chat.
 * They can still type in "no" or literally anything else.
 */
public class ResetConfirmListener implements Listener {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public ResetConfirmListener(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isPendingReset(player.getUniqueId())) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase();

        // Always remove pending state after first relevant chat
        plugin.removePendingReset(player.getUniqueId());

        if (message.equals("yes")) {
            // Hide the confirmation message from public chat
            event.setCancelled(true);

            // Perform reset on main thread (chat event is async)
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.performMaceReset(player);
            });

            utils.sendRaw(player, "<gray>Reset confirmed and applied.</gray>");
        } else {
            // Cancel showing their cancel message too (keeps chat clean)
            event.setCancelled(true);
            utils.sendRaw(player, "<yellow>Reset request cancelled.</yellow>");
        }
    }
}

