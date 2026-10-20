package dev.nullcraft.prefixes;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

/** The badge characters from the pack and the text that stands in for each one without it. */
final class Badges {

    private record Badge(String glyph, String text, TextReplacementConfig replacement) {
    }

    private final List<Badge> badges;

    private Badges(List<Badge> badges) {
        this.badges = List.copyOf(badges);
    }

    /** Reads "glyph: MiniMessage text" pairs; the keys are the private-use badge characters. */
    static Badges load(ConfigurationSection section) {
        List<Badge> list = new ArrayList<>();
        if (section != null) {
            for (String glyph : section.getKeys(false)) {
                String text = section.getString(glyph, "");
                Component component = MiniMessage.miniMessage().deserialize(text);
                list.add(new Badge(glyph, text,
                        TextReplacementConfig.builder().matchLiteral(glyph).replacement(component).build()));
            }
        }
        return new Badges(list);
    }

    int size() {
        return badges.size();
    }

    boolean contains(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return badges.stream().anyMatch(b -> plain.contains(b.glyph()));
    }

    /** The same component with every badge swapped for its text form. */
    Component asText(Component component) {
        Component result = component;
        for (Badge badge : badges) {
            result = result.replaceText(badge.replacement());
        }
        return result;
    }

    /** The same MiniMessage string with every badge swapped for its text form. */
    String asText(String miniMessage) {
        String result = miniMessage;
        for (Badge badge : badges) {
            result = result.replace(badge.glyph(), badge.text());
        }
        return result;
    }
}
