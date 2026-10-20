package de.neptun.worlds;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

import java.util.List;
import java.util.Locale;

public final class WorldsListener implements Listener {
    private final WorldsPlugin plugin;

    public WorldsListener(WorldsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Join / quit ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.initPoints(p);
        plugin.startTime(p);
        // remember where the player was, then send him to the lobby
        plugin.store().saveLast(p.getUniqueId(), p.getLocation());
        plugin.store().saveGamemode(p.getUniqueId(), p.getWorld().getName(), p.getGameMode());
        plugin.store().saveOp(p.getUniqueId(), p.getWorld().getName(), p.isOp());
        String joinWorld = plugin.getConfig().getString("join-world", "lobby");
        World target = Bukkit.getWorld(joinWorld);
        if (target != null && !p.getWorld().equals(target)) {
            p.teleport(plugin.spawnOf(target));
        }
        plugin.switchPoints(p);
        applyWorldState(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.store().saveLast(p.getUniqueId(), p.getLocation());
        plugin.store().saveGamemode(p.getUniqueId(), p.getWorld().getName(), p.getGameMode());
        plugin.store().saveOp(p.getUniqueId(), p.getWorld().getName(), p.isOp());
        plugin.stopTime(p, p.getWorld().getName());
        plugin.store().save();
    }

    // ---------- World changes ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null || from.getWorld().equals(to.getWorld())) {
            return;
        }
        Player p = e.getPlayer();
        plugin.flushTime(p, from.getWorld().getName());
        // remember position and gamemode of the world he is leaving
        plugin.store().saveLast(p.getUniqueId(), from);
        plugin.store().saveGamemode(p.getUniqueId(), from.getWorld().getName(), p.getGameMode());
        plugin.store().saveOp(p.getUniqueId(), from.getWorld().getName(), p.isOp());

        List<String> restore = plugin.getConfig().getStringList("restore-last-location");
        World tw = to.getWorld();
        Location custom = plugin.store().getSpawn(tw);
        // arriving at a spawn (also the default 0,0 of a world) = coming in through /mv tp, a menu or similar
        boolean spawnLike = to.distanceSquared(tw.getSpawnLocation()) < 16
                || (custom != null && to.distanceSquared(custom) < 16)
                || (Math.abs(to.getX()) < 2 && Math.abs(to.getZ()) < 2);
        if (spawnLike) {
            Location target = null;
            if (restore.contains(tw.getName())) {
                target = plugin.store().getLast(p.getUniqueId(), tw);
            }
            if (target == null) {
                target = custom;
            }
            if (target != null) {
                e.setTo(target);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) {
            return;
        }
        Location c = plugin.store().getSpawn(e.getRespawnLocation().getWorld());
        if (c != null) {
            e.setRespawnLocation(c);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.switchPoints(e.getPlayer());
        applyWorldState(e.getPlayer());
    }

    // ---------- Safe worlds (no damage, void rescue) ----------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)
                || !plugin.getConfig().getStringList("safe-worlds").contains(p.getWorld().getName())) {
            return;
        }
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            p.setFallDistance(0);
            p.teleportAsync(p.getWorld().getSpawnLocation().add(0.5, 0, 0.5));
        }
    }

    // ---------- Kills and deaths per world ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.store().addStat(victim.getUniqueId(), victim.getWorld().getName(), "deaths", 1);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            plugin.store().addStat(killer.getUniqueId(), killer.getWorld().getName(), "kills", 1);
        }
    }

    /** Sets gamemode and OP state for the world the player is in. */
    private void applyWorldState(Player p) {
        String world = p.getWorld().getName();

        GameMode gm = plugin.store().getGamemode(p.getUniqueId(), world);
        if (gm == null) {
            String def = plugin.getConfig().getString("gamemode." + world);
            if (def != null) {
                try {
                    gm = GameMode.valueOf(def.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (gm != null && p.getGameMode() != gm) {
            p.setGameMode(gm);
        }

        // OP is remembered per world like the gamemode: whoever is OP when leaving a world is OP there again later.
        Boolean stored = plugin.store().getOp(p.getUniqueId(), world);
        boolean shouldBeOp = stored != null ? stored
                : plugin.getConfig().getStringList("world-ops." + world).stream()
                        .anyMatch(n -> n.equalsIgnoreCase(p.getName()));
        if (p.isOp() != shouldBeOp) {
            p.setOp(shouldBeOp);
        }
    }

    // ---------- Commands per world ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("neptunworlds.bypass")) {
            return;
        }
        List<String> blocked = plugin.getConfig().getStringList("blocked-commands." + p.getWorld().getName());
        if (blocked.isEmpty()) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        for (String b : blocked) {
            if (b.equalsIgnoreCase(label)) {
                e.setCancelled(true);
                plugin.notify(p, "<#ff7b7b>You cannot use this command here.");
                return;
            }
        }
    }

    // ---------- Chat per world ----------

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("world-chat", true)) {
            return;
        }
        World w = e.getPlayer().getWorld();
        e.viewers().removeIf(a -> a instanceof Player v && !v.getWorld().equals(w));
    }
}
