package de.neptun.worlds;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Remembers the last position and the gamemode of every player per world (data.yml). */
final class Store {
    private final File file;
    private final YamlConfiguration y;
    private final WorldsPlugin plugin;
    private boolean dirty;

    Store(WorldsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.y = YamlConfiguration.loadConfiguration(file);
    }

    void saveLast(UUID u, Location l) {
        if (l == null || l.getWorld() == null) {
            return;
        }
        y.set("last." + u + "." + l.getWorld().getName(),
                l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch());
        dirty = true;
    }

    Location getLast(UUID u, World w) {
        String s = y.getString("last." + u + "." + w.getName());
        if (s == null) {
            return null;
        }
        try {
            String[] p = s.split(";");
            return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Float.parseFloat(p[3]), Float.parseFloat(p[4]));
        } catch (Exception e) {
            return null;
        }
    }

    void saveSpawn(Location l) {
        y.set("spawn." + l.getWorld().getName(),
                l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch());
        dirty = true;
    }

    /** The spawn set with /setspawn, or null. */
    Location getSpawn(World w) {
        String s = y.getString("spawn." + w.getName());
        if (s == null) {
            return null;
        }
        try {
            String[] p = s.split(";");
            return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Float.parseFloat(p[3]), Float.parseFloat(p[4]));
        } catch (Exception e) {
            return null;
        }
    }

    void saveGamemode(UUID u, String world, GameMode gm) {
        y.set("gm." + u + "." + world, gm.name());
        dirty = true;
    }

    GameMode getGamemode(UUID u, String world) {
        String s = y.getString("gm." + u + "." + world);
        if (s == null) {
            return null;
        }
        try {
            return GameMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    long getStat(UUID u, String world, String key) {
        return y.getLong("stats." + u + "." + world + "." + key);
    }

    void addStat(UUID u, String world, String key, long amount) {
        String path = "stats." + u + "." + world + "." + key;
        y.set(path, y.getLong(path) + amount);
        dirty = true;
    }

    long getPoints(UUID u, String world) {
        return y.getLong("points." + u + "." + world);
    }

    void setPoints(UUID u, String world, long v) {
        y.set("points." + u + "." + world, v);
        dirty = true;
    }

    /** The world whose Neptuns balance is currently loaded in PlayerPoints. */
    String getLoaded(UUID u) {
        return y.getString("loaded." + u);
    }

    void setLoaded(UUID u, String world) {
        y.set("loaded." + u, world);
        dirty = true;
    }

    void saveOp(UUID u, String world, boolean op) {
        y.set("op." + u + "." + world, op);
        dirty = true;
    }

    /** null = nothing remembered yet for this world. */
    Boolean getOp(UUID u, String world) {
        String path = "op." + u + "." + world;
        return y.contains(path) ? y.getBoolean(path) : null;
    }

    void save() {
        if (!dirty) {
            return;
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("data.yml could not be saved: " + e.getMessage());
        }
    }
}
