package dev.nullcraft.prefixes;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends the rank-badge resource pack (bundled in the jar) to every player who joins, and
 * optionally serves it over HTTP itself, so no web host is needed. The pack is added next to
 * any other pack the server sends, because 1.20.3+ clients keep several packs at once.
 *
 * <p>A player who has not loaded the pack (declined it, turned server packs off, or is still
 * downloading it) would see an empty box where a badge is. Those players get the badge's text
 * form instead, in chat, join/quit messages and the TAB placeholder %rel_nullcraft_prefix%.
 */
public final class NullCraftPrefixes extends JavaPlugin implements Listener {

    private HttpServer http;
    private byte[] hash;
    private UUID packId;
    private String url;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        byte[] pack;
        try (InputStream in = getResource("prefix-pack.zip")) {
            if (in == null) {
                throw new IOException("prefix-pack.zip is missing from the jar");
            }
            pack = in.readAllBytes();
            hash = MessageDigest.getInstance("SHA-1").digest(pack);
        } catch (IOException | NoSuchAlgorithmException e) {
            getLogger().severe("Could not load the badge pack: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // The id follows the content, so a changed pack is downloaded again instead of reused.
        packId = UUID.nameUUIDFromBytes(hash);
        String file = "nullcraft-pack-" + HexFormat.of().formatHex(hash).substring(0, 12) + ".zip";

        if (getConfig().getBoolean("http.enabled", true)) {
            int port = getConfig().getInt("http.port", 8085);
            if (!serve(pack, file, port)) {
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        String configured = getConfig().getString("pack.url", "");
        url = configured == null || configured.isBlank()
                ? "http://" + getConfig().getString("http.public-host", "127.0.0.1") + ":"
                        + getConfig().getInt("http.port", 8085) + "/" + file
                : configured.replace("{file}", file);

        // A config written by an older version has no badges section; use the defaults then.
        // contains(..., true) ignores defaults: for a missing section, getConfigurationSection
        // would hand back a new empty one instead of the default.
        ConfigurationSection section = getConfig().contains("badges", true)
                ? getConfig().getConfigurationSection("badges")
                : getConfig().getDefaults().getConfigurationSection("badges");
        Badges badges = Badges.load(section);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new BadgeMessages(this, badges), this);
        ConfigurationSection models = getConfig().contains("legacy-item-models", true)
                ? getConfig().getConfigurationSection("legacy-item-models")
                : getConfig().getDefaults().getConfigurationSection("legacy-item-models");
        LegacyItemModels legacy = LegacyItemModels.load(this, models);
        if (legacy.size() > 0) {
            legacy.start(100L);
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                && getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            new BadgePlaceholders(this, badges).register();
            getLogger().info("Registered %rel_nullcraft_prefix% and %nullcraft_prefix%");
        }
        getLogger().info("Pack ready (" + pack.length / 1024 + " KB, " + badges.size() + " text badges, "
                + legacy.size() + " custom item models for 1.21-1.21.3): " + url);
    }

    private boolean serve(byte[] pack, String file, int port) {
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            getLogger().severe("Could not open port " + port + " for the badge pack: " + e.getMessage());
            return false;
        }
        http.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals("/" + file)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, pack.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(pack);
                }
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "NullCraftPrefixes-HTTP");
            thread.setDaemon(true);
            return thread;
        }));
        http.start();
        return true;
    }

    /** True once this player's client has reported the badge pack as loaded. */
    boolean hasPack(Player player) {
        return packStatus.get(player.getUniqueId()) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long delay = Math.max(0, getConfig().getLong("pack.delay-ticks", 20));
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                // Added next to any other pack (replace false), so a server switch with the same
                // pack id and hash does not make the client reload its resources.
                player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                        .packs(ResourcePackInfo.resourcePackInfo(packId, URI.create(url), HexFormat.of().formatHex(hash)))
                        .prompt(MiniMessage.miniMessage().deserialize(getConfig().getString("pack.prompt", "")))
                        .required(getConfig().getBoolean("pack.required", false))
                        .replace(false)
                        .build());
            }
        }, delay);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!packId.equals(event.getID())) {
            return;
        }
        packStatus.put(event.getPlayer().getUniqueId(), event.getStatus());
        if (getConfig().getBoolean("pack.log-status", true)) {
            getLogger().info(event.getPlayer().getName() + ": badge pack " + event.getStatus());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packStatus.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop(0);
        }
    }
}
