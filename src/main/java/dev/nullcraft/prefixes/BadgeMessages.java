package dev.nullcraft.prefixes;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gives each viewer their own copy of chat and join/quit messages: with the badge for players
 * whose client loaded the pack, with the text form for everyone else and for the console.
 * It runs last (MONITOR) so it wraps whatever the chat plugin, such as LPC, has already built.
 */
final class BadgeMessages implements Listener {

    private final NullCraftPrefixes plugin;
    private final Badges badges;

    BadgeMessages(NullCraftPrefixes plugin, Badges badges) {
        this.plugin = plugin;
        this.badges = badges;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatRenderer inner = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = inner.render(source, displayName, message, viewer);
            return viewer instanceof Player player && plugin.hasPack(player) ? rendered : badges.asText(rendered);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Component message = event.joinMessage();
        if (message != null && badges.contains(message)) {
            event.joinMessage(null);
            sendToEach(message, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Component message = event.quitMessage();
        if (message != null && badges.contains(message)) {
            event.quitMessage(null);
            sendToEach(message, event.getPlayer());
        }
    }

    private void sendToEach(Component message, Player leaving) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer != leaving) {
                viewer.sendMessage(plugin.hasPack(viewer) ? message : badges.asText(message));
            }
        }
        Bukkit.getConsoleSender().sendMessage(badges.asText(message));
    }
}
