package com.vorlas.randomteleport.utils;

import com.hypixel.hytale.server.core.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing color codes in messages.
 * Supports Minecraft-style color codes: &0-9, &a-f
 * Example: "&aSuccess! &7Your location is ready."
 */
public final class MessageUtil {

    private MessageUtil() {}

    /**
     * Parse a string with &-prefixed color codes into a colored Message.
     * Codes: &0=black, &1=dark blue, &2=green, &3=cyan, &4=red, &5=purple,
     *        &6=gold, &7=gray, &8=dark gray, &9=blue, &a=green, &b=aqua,
     *        &c=red, &d=pink, &e=yellow, &f=white
     */
    public static Message parseColored(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        Pattern pattern = Pattern.compile("&([0-9a-fA-F])");
        Matcher matcher = pattern.matcher(text);
        List<Message> segments = new ArrayList<>();
        String currentColor = "#FFFFFF";
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String segment = text.substring(lastEnd, matcher.start());
                if (!segment.isEmpty()) {
                    segments.add(Message.raw(segment).color(currentColor));
                }
            }
            currentColor = resolveColor(matcher.group(1));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String segment = text.substring(lastEnd);
            if (!segment.isEmpty()) {
                segments.add(Message.raw(segment).color(currentColor));
            }
        }

        if (segments.isEmpty()) {
            return Message.raw(text).color("#FFFFFF");
        } else if (segments.size() == 1) {
            return segments.get(0);
        } else {
            return Message.join(segments.toArray(new Message[0]));
        }
    }

    private static String resolveColor(String code) {
        return switch (code.toLowerCase()) {
            case "0" -> "#000000"; // Black
            case "1" -> "#0000AA"; // Dark Blue
            case "2" -> "#00AA00"; // Dark Green
            case "3" -> "#00AAAA"; // Dark Aqua
            case "4" -> "#AA0000"; // Dark Red
            case "5" -> "#AA00AA"; // Purple
            case "6" -> "#FFAA00"; // Gold
            case "7" -> "#AAAAAA"; // Gray
            case "8" -> "#555555"; // Dark Gray
            case "9" -> "#5555FF"; // Blue
            case "a" -> "#55FF55"; // Green
            case "b" -> "#55FFFF"; // Aqua
            case "c" -> "#FF5555"; // Red
            case "d" -> "#FF55FF"; // Pink
            case "e" -> "#FFFF55"; // Yellow
            case "f" -> "#FFFFFF"; // White
            default -> "#FFFFFF";
        };
    }
}
