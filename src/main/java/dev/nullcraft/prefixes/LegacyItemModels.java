package dev.nullcraft.prefixes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.Plugin;

/**
 * Since 1.21.4 a custom item model is picked by a custom_model_data string ("god_blade").
 * 1.21-1.21.3 clients only know a custom_model_data number, which ViaBackwards takes from the
 * first float. This gives every item whose first string is listed in legacy-item-models that
 * number as well, so the same item shows its model on every 1.21 version. Items are fixed when
 * they come near a player: on join, on pickup, when a container opens, and every few seconds.
 */
final class LegacyItemModels implements Listener {

    private final Plugin plugin;
    private final Map<String, Float> ids;

    private LegacyItemModels(Plugin plugin, Map<String, Float> ids) {
        this.plugin = plugin;
        this.ids = Map.copyOf(ids);
    }

    static LegacyItemModels load(Plugin plugin, ConfigurationSection section) {
        Map<String, Float> ids = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ids.put(key, (float) section.getDouble(key));
            }
        }
        return new LegacyItemModels(plugin, ids);
    }

    int size() {
        return ids.size();
    }

    void start(long periodTicks) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                fixAll(player.getInventory());
            }
        }, periodTicks, periodTicks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> fixAll(player.getInventory()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        fixAll(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (fix(stack)) {
            entity.setItemStack(stack);
        }
    }

    private void fixAll(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (fix(stack)) {
                inventory.setItem(slot, stack);
            }
        }
    }

    /** Adds the legacy number when the item has a listed string and no floats yet. */
    private boolean fix(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasCustomModelDataComponent()) {
            return false;
        }
        CustomModelDataComponent data = meta.getCustomModelDataComponent();
        List<String> strings = data.getStrings();
        if (strings.isEmpty() || !data.getFloats().isEmpty()) {
            return false;
        }
        Float id = ids.get(strings.get(0));
        if (id == null) {
            return false;
        }
        data.setFloats(List.of(id));
        meta.setCustomModelDataComponent(data);
        stack.setItemMeta(meta);
        return true;
    }
}
