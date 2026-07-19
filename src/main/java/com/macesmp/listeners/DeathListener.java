package com.macesmp.listeners;

import com.macesmp.MaceSMP;
import com.macesmp.PlayerData;
import com.macesmp.util.MaceUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/*
 * Handles kills for the point system with simple anti-farm (last 3 unique victims).
 */
public class DeathListener implements Listener {

    private final MaceSMP plugin;
    private final MaceUtils utils;

    public DeathListener(MaceSMP plugin) {
        this.plugin = plugin;
        this.utils = plugin.getMaceUtils();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Always increment deaths
        PlayerData victimData = plugin.getPlayerData(victim.getUniqueId());
        victimData.incrementDeaths();

        if (killer == null || killer.equals(victim)) {
            // Natural death or suicide – just save
            plugin.savePlayerData(victim.getUniqueId());
            return;
        }

        // Valid player kill
        PlayerData killerData = plugin.getPlayerData(killer.getUniqueId());
        killerData.incrementKills();

        int maxSize = plugin.getConfig().getInt("last-kills-max-size", 3);
        int points = plugin.getConfig().getInt("points-per-kill", 1);

        boolean isNewKill = killerData.tryAddLastKill(victim.getUniqueId(), maxSize);

        if (isNewKill) {
            killerData.addMacePoints(points);
            utils.sendRaw(killer, "<green>+" + points + " mace point(s)!</green> <gray>(Total: " +
                    killerData.getMacePoints() + ")</gray>");
            utils.sendRaw(victim, "<red>You were slain by " + killer.getName() + ".</red>");
            plugin.getLogger().info(killer.getName() + " killed " + victim.getName() +
                    " → +" + points + " point(s). Total: " + killerData.getMacePoints());
        } else {
            utils.sendRaw(killer, "<yellow>No points – you already killed " + victim.getName() +
                    " recently.</yellow> <red>(anti-farm)</red>");
            plugin.getLogger().info(killer.getName() + " killed " + victim.getName() +
                    " but received no points. <red>(anti-farm)</red>");
        }

        // Persist both
        plugin.savePlayerData(killer.getUniqueId());
        plugin.savePlayerData(victim.getUniqueId());
    }
}

