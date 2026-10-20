package de.neptun.worlds;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * %nworlds_online_<world>% (players in a world),
 * %nworlds_kills%, %nworlds_deaths%, %nworlds_kd%, %nworlds_playtime% (world the player is in)
 * and the same with a world name: %nworlds_kills_pvp%, %nworlds_playtime_world% ...
 */
public final class WorldsExpansion extends PlaceholderExpansion {
    private final WorldsPlugin plugin;

    WorldsExpansion(WorldsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nworlds";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Neptun";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        String[] a = params.toLowerCase(Locale.ROOT).split("_", 2);
        String key = a[0];
        String world;
        if (a.length == 2) {
            world = params.substring(params.indexOf('_') + 1);
        } else if (player.getPlayer() != null) {
            world = player.getPlayer().getWorld().getName();
        } else {
            return "0";
        }
        if (key.equals("online")) {
            World w = Bukkit.getWorld(world);
            return String.valueOf(w == null ? 0 : w.getPlayers().size());
        }
        long kills = plugin.store().getStat(player.getUniqueId(), world, "kills");
        long deaths = plugin.store().getStat(player.getUniqueId(), world, "deaths");
        switch (key) {
            case "kills":
                return String.valueOf(kills);
            case "deaths":
                return String.valueOf(deaths);
            case "kd":
                return String.format(Locale.ROOT, "%.2f", deaths == 0 ? (double) kills : (double) kills / deaths);
            case "playtime": {
                long s = plugin.store().getStat(player.getUniqueId(), world, "playtime");
                Player online = player.getPlayer();
                if (online != null && online.getWorld().getName().equals(world)) {
                    s += plugin.pendingSeconds(player.getUniqueId());
                }
                return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
            }
            default:
                return null;
        }
    }
}
