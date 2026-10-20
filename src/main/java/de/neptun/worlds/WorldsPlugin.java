package de.neptun.worlds;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorldsPlugin extends JavaPlugin implements CommandExecutor {
    private Store store;
    /** When the current time slice of a player started (for the per-world playtime). */
    private final Map<UUID, Long> since = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new Store(this);
        Bukkit.getPluginManager().registerEvents(new WorldsListener(this), this);
        for (String name : new String[] {"spawn", "lobby", "smp", "pvp", "setspawn"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(this);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new WorldsExpansion(this).register();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            startTime(p);
        }
        // once a minute: count playtime and write the data file
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                flushTime(p, p.getWorld().getName());
            }
            store.save();
        }, 20L * 60, 20L * 60);
    }

    @Override
    public void onDisable() {
        if (store != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                flushTime(p, p.getWorld().getName());
            }
            store.save();
        }
    }

    Store store() {
        return store;
    }

    void startTime(Player p) {
        since.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Adds the time since the last flush to the given world. */
    void flushTime(Player p, String world) {
        long now = System.currentTimeMillis();
        Long start = since.put(p.getUniqueId(), now);
        if (start != null && now > start) {
            store.addStat(p.getUniqueId(), world, "playtime", (now - start) / 1000);
        }
    }

    void stopTime(Player p, String world) {
        flushTime(p, world);
        since.remove(p.getUniqueId());
    }

    /** Seconds of the running time slice (not yet written to the store). */
    long pendingSeconds(UUID u) {
        Long s = since.get(u);
        return s == null ? 0 : Math.max(0, (System.currentTimeMillis() - s) / 1000);
    }

    /** Called before the join teleport: the balance in PlayerPoints belongs to the world the player was in. */
    void initPoints(Player p) {
        if (store.getLoaded(p.getUniqueId()) == null) {
            store.setLoaded(p.getUniqueId(), p.getWorld().getName());
        }
    }

    /** Stores the current Neptuns for the old world and loads the balance of the world the player is in now. */
    void switchPoints(Player p) {
        if (!getConfig().getBoolean("separate-neptuns", true)
                || !Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")
                || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        UUID u = p.getUniqueId();
        String now = p.getWorld().getName();
        String loaded = store.getLoaded(u);
        if (loaded == null) {
            store.setLoaded(u, now);
            return;
        }
        if (loaded.equals(now)) {
            return;
        }
        String raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%playerpoints_points%")
                .replace(",", "").replace(".", "").trim();
        long current;
        try {
            current = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            getLogger().warning("Could not read Neptuns of " + p.getName() + " (" + raw + "), not switching.");
            return;
        }
        store.setPoints(u, loaded, current);
        long target = store.getPoints(u, now);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points set " + p.getName() + " " + target);
        store.setLoaded(u, now);
    }

    void notify(Player p, String mm) {
        p.sendActionBar(MiniMessage.miniMessage().deserialize(mm));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "lobby", "smp", "pvp" -> travel(p, command.getName().toLowerCase());
            case "setspawn" -> {
                store.saveSpawn(p.getLocation());
                p.getWorld().setSpawnLocation(p.getLocation());
                store.save();
                notify(p, "<#bbbbbb>Spawn of this world set to your position.");
            }
            default -> {
                p.teleportAsync(spawnOf(p.getWorld()));
                notify(p, "<#bbbbbb>Teleported to spawn.");
            }
        }
        return true;
    }

    /** The spawn set with /setspawn, otherwise the normal world spawn. */
    org.bukkit.Location spawnOf(org.bukkit.World w) {
        org.bukkit.Location l = store.getSpawn(w);
        return l != null ? l : w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    /** Sends the player to the world configured under travel.<key>. Returning to the SMP restores his last position. */
    void travel(Player p, String key) {
        org.bukkit.World w = Bukkit.getWorld(getConfig().getString("travel." + key, key));
        if (w == null) {
            notify(p, "<#ff7b7b>This world is not available.");
            return;
        }
        p.teleportAsync(spawnOf(w));
    }
}
