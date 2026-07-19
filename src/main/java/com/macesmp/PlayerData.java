package com.macesmp;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/*
 * Simple in-memory + YAML serializable player data for MaceSMP.
 */
public class PlayerData {

    private final UUID uuid;
    private int macePoints;
    private int kills;
    private int deaths;
    // FIFO queue of recent victim UUIDs (anti-farm). Max size enforced by caller / config.
    private final Deque<UUID> lastKills = new ArrayDeque<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.macePoints = 0;
        this.kills = 0;
        this.deaths = 0;
    }

    // Getters / Setters

    public UUID getUuid() {
        return uuid;
    }

    public int getMacePoints() {
        return macePoints;
    }

    public void setMacePoints(int macePoints) {
        this.macePoints = Math.max(0, macePoints);
    }

    public void addMacePoints(int amount) {
        this.macePoints = Math.max(0, this.macePoints + amount);
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public Deque<UUID> getLastKills() {
        return lastKills;
    }

    /*
     * Add a victim to the anti-farm queue. Evicts oldest if over maxSize.
     * @return true if the kill was NEW (not already in queue), false if already present (farm attempt).
     */
    public boolean tryAddLastKill(UUID victim, int maxSize) {
        if (lastKills.contains(victim)) {
            return false; // already killed recently → no points
        }
        lastKills.addLast(victim);
        while (lastKills.size() > maxSize) {
            lastKills.removeFirst();
        }
        return true;
    }

    // YAML serialization

    public void saveToConfig(org.bukkit.configuration.file.FileConfiguration config, String path) {
        config.set(path + ".macePoints", macePoints);
        config.set(path + ".kills", kills);
        config.set(path + ".deaths", deaths);

        // Store lastKills as list of strings
        java.util.List<String> list = new java.util.ArrayList<>();
        for (UUID id : lastKills) {
            list.add(id.toString());
        }
        config.set(path + ".lastKills", list);
    }

    public static PlayerData fromConfig(UUID uuid, ConfigurationSection section) {
        PlayerData data = new PlayerData(uuid);
        if (section == null) return data;

        data.macePoints = section.getInt("macePoints", 0);
        data.kills = section.getInt("kills", 0);
        data.deaths = section.getInt("deaths", 0);

        data.lastKills.clear();
        for (String s : section.getStringList("lastKills")) {
            try {
                data.lastKills.addLast(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip bad entries
            }
        }
        return data;
    }
}
