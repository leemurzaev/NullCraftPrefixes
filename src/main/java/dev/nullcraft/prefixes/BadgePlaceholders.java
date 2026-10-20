package dev.nullcraft.prefixes;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI placeholders for TAB, which shows one player's prefix to many viewers:
 * %rel_nullcraft_prefix% is the target's LuckPerms prefix as the viewer should see it (badge
 * or its text form), %nullcraft_prefix% is the prefix exactly as LuckPerms stores it.
 */
final class BadgePlaceholders extends PlaceholderExpansion implements Relational {

    private final NullCraftPrefixes plugin;
    private final Badges badges;

    BadgePlaceholders(NullCraftPrefixes plugin, Badges badges) {
        this.plugin = plugin;
        this.badges = badges;
    }

    @Override
    public String getIdentifier() {
        return "nullcraft";
    }

    @Override
    public String getAuthor() {
        return "NULLCRAFT";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Player online = player == null ? null : player.getPlayer();
        return "prefix".equals(params) && online != null ? prefix(online) : null;
    }

    /** PlaceholderAPI passes the viewer first and the player being shown second. */
    @Override
    public String onPlaceholderRequest(Player viewer, Player target, String params) {
        if (!"prefix".equals(params) || target == null) {
            return null;
        }
        String prefix = prefix(target);
        return viewer != null && plugin.hasPack(viewer) ? prefix : badges.asText(prefix);
    }

    private static String prefix(Player player) {
        String prefix = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
        return prefix == null ? "" : prefix;
    }
}
