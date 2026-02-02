package sierra.thing.playernamestyler.util;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class ColorUtil {
    private static final int MAX_EFFECT_VISIBLE_CHARS = 128;
    private static final Pattern HEX_TAG = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern COLOR_TAG = Pattern.compile("<([a-zA-Z_]+)>");
    private static final Pattern AMP_CODE = Pattern.compile("(?i)&([0-9A-FK-OR])");
    private static final Pattern AMP_HEX = Pattern.compile("(?i)&\\#([A-F0-9]{6})");
    private static final Pattern GRADIENT_OPEN = Pattern.compile("(?i)<gradient:#([A-F0-9]{6}):#([A-F0-9]{6})>");
    private static final Pattern RAINBOW_OPEN = Pattern.compile("(?i)<rainbow>");
    private static final Pattern FIGURA_TAG = Pattern.compile(":(?!#)[^:]+:");
    private static final Map<String, ChatFormatting> COLOR_NAMES = new HashMap<String, ChatFormatting>();
    private static final Map<String, ChatFormatting> FORMAT_NAMES = new HashMap<String, ChatFormatting>();
    private static final Map<Character, ChatFormatting> AMP_FORMATS = new HashMap<Character, ChatFormatting>();

    public static Component parseColoredText(String text) {
        MutableComponent output = Component.empty();
        if (text == null) {
            return output;
        }
        text = AMP_HEX.matcher(text).replaceAll("<#$1>");
        text = ColorUtil.expandEffects(text);
        if (!text.contains("<") && !text.contains("&")) {
            return Component.literal((String)text);
        }
        StringBuilder currChunk = new StringBuilder();
        Integer currColor = null;
        EnumSet<ChatFormatting> currFormats = EnumSet.noneOf(ChatFormatting.class);
        int pos = 0;
        while (pos < text.length()) {
            char code;
            ChatFormatting fmt;
            char c = text.charAt(pos);
            if (c == '<') {
                int endTag = text.indexOf(62, pos);
                if (endTag > pos) {
                    String potentialTag = text.substring(pos + 1, endTag).toLowerCase(Locale.ROOT);
                    boolean isColorTag = potentialTag.startsWith("#") && potentialTag.length() == 7;
                    isColorTag = isColorTag || COLOR_NAMES.containsKey(potentialTag);
                    isColorTag = isColorTag || FORMAT_NAMES.containsKey(potentialTag);
                    isColorTag = isColorTag || potentialTag.startsWith("/") && FORMAT_NAMES.containsKey(potentialTag.substring(1));
                    boolean bl = isColorTag = isColorTag || potentialTag.equals("reset");
                    if (isColorTag) {
                        if (currChunk.length() > 0) {
                            ColorUtil.appendStyled(output, currChunk.toString(), currColor, currFormats);
                            currChunk.setLength(0);
                        }
                        if (potentialTag.startsWith("#") && potentialTag.length() == 7) {
                            try {
                                currColor = Integer.parseInt(potentialTag.substring(1), 16);
                            }
                            catch (NumberFormatException numberFormatException) {}
                        } else if (COLOR_NAMES.containsKey(potentialTag)) {
                            ChatFormatting fmt2 = COLOR_NAMES.get(potentialTag);
                            currColor = fmt2.getColor();
                        } else if (FORMAT_NAMES.containsKey(potentialTag)) {
                            currFormats.add(FORMAT_NAMES.get(potentialTag));
                        } else if (potentialTag.startsWith("/") && FORMAT_NAMES.containsKey(potentialTag.substring(1))) {
                            currFormats.remove(FORMAT_NAMES.get(potentialTag.substring(1)));
                        } else if (potentialTag.equals("reset")) {
                            currColor = null;
                            currFormats.clear();
                        }
                        pos = endTag + 1;
                        continue;
                    }
                    currChunk.append('<');
                    ++pos;
                    continue;
                }
            } else if (c == '&' && pos + 1 < text.length() && (fmt = AMP_FORMATS.get(Character.valueOf(code = Character.toLowerCase(text.charAt(pos + 1))))) != null) {
                if (currChunk.length() > 0) {
                    ColorUtil.appendStyled(output, currChunk.toString(), currColor, currFormats);
                    currChunk.setLength(0);
                }
                if (fmt.isColor()) {
                    currColor = fmt.getColor();
                    currFormats.clear();
                } else if (fmt == ChatFormatting.RESET) {
                    currColor = null;
                    currFormats.clear();
                } else {
                    currFormats.add(fmt);
                }
                pos += 2;
                continue;
            }
            currChunk.append(c);
            ++pos;
        }
        if (currChunk.length() > 0) {
            ColorUtil.appendStyled(output, currChunk.toString(), currColor, currFormats);
        }
        return output;
    }

    private static String expandEffects(String text) {
        if (text == null || text.isEmpty() || !text.contains("<")) {
            return text;
        }
        text = ColorUtil.expandGradient(text);
        text = ColorUtil.expandRainbow(text);
        return text;
    }

    private static String expandGradient(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        Matcher matcher = GRADIENT_OPEN.matcher(input);
        StringBuilder output = new StringBuilder(input.length());
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            int openStart = matcher.start();
            int openEnd = matcher.end();
            int closeStart = lower.indexOf("</gradient>", openEnd);
            if (closeStart == -1) {
                break;
            }
            output.append(input, searchFrom, openStart);
            int startRgb = ColorUtil.parseHexRgb(matcher.group(1));
            int endRgb = ColorUtil.parseHexRgb(matcher.group(2));
            String inner = input.substring(openEnd, closeStart);
            output.append(ColorUtil.applyGradientToText(inner, startRgb, endRgb));
            searchFrom = closeStart + "</gradient>".length();
        }
        output.append(input, searchFrom, input.length());
        return output.toString();
    }

    private static String expandRainbow(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        Matcher matcher = RAINBOW_OPEN.matcher(input);
        StringBuilder output = new StringBuilder(input.length());
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            int openStart = matcher.start();
            int openEnd = matcher.end();
            int closeStart = lower.indexOf("</rainbow>", openEnd);
            if (closeStart == -1) {
                break;
            }
            output.append(input, searchFrom, openStart);
            String inner = input.substring(openEnd, closeStart);
            output.append(ColorUtil.applyRainbowToText(inner));
            searchFrom = closeStart + "</rainbow>".length();
        }
        output.append(input, searchFrom, input.length());
        return output.toString();
    }

    private static String applyGradientToText(String text, int startRgb, int endRgb) {
        int visibleChars = ColorUtil.countEffectVisibleChars(text);
        if (visibleChars == 0 || visibleChars > MAX_EFFECT_VISIBLE_CHARS) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + visibleChars * 10);
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            if (c == '&') {
                int consumed = ColorUtil.appendAmpSequence(text, i, out);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            } else if (c == '<') {
                int endTag = text.indexOf('>', i);
                if (endTag > i) {
                    out.append(text, i, endTag + 1);
                    i = endTag + 1;
                    continue;
                }
            }
            float t = visibleChars == 1 ? 0.0f : (float)visibleIndex / (float)(visibleChars - 1);
            int rgb = ColorUtil.lerpRgb(startRgb, endRgb, t);
            out.append("<#").append(ColorUtil.toHexRgb(rgb)).append(">");
            out.append(c);
            ++i;
            ++visibleIndex;
        }
        return out.toString();
    }

    private static String applyRainbowToText(String text) {
        int visibleChars = ColorUtil.countEffectVisibleChars(text);
        if (visibleChars == 0 || visibleChars > MAX_EFFECT_VISIBLE_CHARS) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + visibleChars * 10);
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            if (c == '&') {
                int consumed = ColorUtil.appendAmpSequence(text, i, out);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            } else if (c == '<') {
                int endTag = text.indexOf('>', i);
                if (endTag > i) {
                    out.append(text, i, endTag + 1);
                    i = endTag + 1;
                    continue;
                }
            }
            float hue = (float)visibleIndex / (float)visibleChars;
            int rgb = ColorUtil.hsvToRgb(hue, 1.0f, 1.0f);
            out.append("<#").append(ColorUtil.toHexRgb(rgb)).append(">");
            out.append(c);
            ++i;
            ++visibleIndex;
        }
        return out.toString();
    }

    private static int countEffectVisibleChars(String text) {
        int visible = 0;
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            if (c == '&') {
                int consumed = ColorUtil.consumeAmpSequence(text, i);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            } else if (c == '<') {
                int endTag = text.indexOf('>', i);
                if (endTag > i) {
                    i = endTag + 1;
                    continue;
                }
            }
            ++visible;
            ++i;
        }
        return visible;
    }

    private static int appendAmpSequence(String text, int start, StringBuilder out) {
        int consumed = ColorUtil.consumeAmpSequence(text, start);
        if (consumed <= 0) {
            return 0;
        }
        out.append(text, start, start + consumed);
        return consumed;
    }

    private static int consumeAmpSequence(String text, int start) {
        if (start < 0 || start >= text.length() || text.charAt(start) != '&') {
            return 0;
        }
        if (start + 1 >= text.length()) {
            return 0;
        }
        char next = text.charAt(start + 1);
        if (next == '#' && start + 7 < text.length()) {
            for (int i = start + 2; i < start + 8; i++) {
                char h = text.charAt(i);
                if (Character.digit(h, 16) == -1) {
                    return 0;
                }
            }
            return 8;
        }
        return AMP_FORMATS.containsKey(Character.valueOf(Character.toLowerCase(next))) ? 2 : 0;
    }

    private static int parseHexRgb(String hex6) {
        try {
            return Integer.parseInt(hex6, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static String toHexRgb(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    private static int lerpRgb(int startRgb, int endRgb, float t) {
        int r1 = startRgb >> 16 & 0xFF;
        int g1 = startRgb >> 8 & 0xFF;
        int b1 = startRgb & 0xFF;
        int r2 = endRgb >> 16 & 0xFF;
        int g2 = endRgb >> 8 & 0xFF;
        int b2 = endRgb & 0xFF;
        int r = Math.round((float)r1 + (float)(r2 - r1) * t) & 0xFF;
        int g = Math.round((float)g1 + (float)(g2 - g1) * t) & 0xFF;
        int b = Math.round((float)b1 + (float)(b2 - b1) * t) & 0xFF;
        return r << 16 | g << 8 | b;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hue = h - (float)Math.floor(h);
        float c = v * s;
        float x = c * (1.0f - Math.abs(hue * 6.0f % 2.0f - 1.0f));
        float m = v - c;
        float rPrime;
        float gPrime;
        float bPrime;
        float hueSegment = hue * 6.0f;
        if (hueSegment < 1.0f) {
            rPrime = c;
            gPrime = x;
            bPrime = 0.0f;
        } else if (hueSegment < 2.0f) {
            rPrime = x;
            gPrime = c;
            bPrime = 0.0f;
        } else if (hueSegment < 3.0f) {
            rPrime = 0.0f;
            gPrime = c;
            bPrime = x;
        } else if (hueSegment < 4.0f) {
            rPrime = 0.0f;
            gPrime = x;
            bPrime = c;
        } else if (hueSegment < 5.0f) {
            rPrime = x;
            gPrime = 0.0f;
            bPrime = c;
        } else {
            rPrime = c;
            gPrime = 0.0f;
            bPrime = x;
        }
        int r = Math.round((rPrime + m) * 255.0f) & 0xFF;
        int g = Math.round((gPrime + m) * 255.0f) & 0xFF;
        int b = Math.round((bPrime + m) * 255.0f) & 0xFF;
        return r << 16 | g << 8 | b;
    }

    private static boolean isValidColorTagContent(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        if (content.startsWith("#") && content.length() == 7) {
            try {
                Integer.parseInt(content.substring(1), 16);
                return true;
            }
            catch (NumberFormatException ignored) {
                return false;
            }
        }
        return COLOR_NAMES.containsKey(content = content.toLowerCase(Locale.ROOT)) || FORMAT_NAMES.containsKey(content) || content.startsWith("/") && FORMAT_NAMES.containsKey(content.substring(1)) || content.equals("reset");
    }

    public static String stripColorTags(String text) {
        if (text == null) {
            return "";
        }
        text = AMP_CODE.matcher(text).replaceAll("");
        text = text.replaceAll("(?i)<gradient:#([A-F0-9]{6}):#([A-F0-9]{6})>", "");
        text = HEX_TAG.matcher(text).replaceAll("");
        text = COLOR_TAG.matcher(text).replaceAll("");
        text = text.replaceAll("</?[a-zA-Z_]+>", "");
        return text;
    }

    public static String stripFiguraTags(String input) {
        if (input == null) {
            return null;
        }
        return FIGURA_TAG.matcher(input).replaceAll("");
    }

    private static void appendStyled(MutableComponent into, String text, Integer color, Set<ChatFormatting> formats) {
        if (text.isEmpty()) {
            return;
        }
        MutableComponent comp = Component.literal((String)text);
        Style style = Style.EMPTY;
        if (color != null) {
            style = style.withColor(color.intValue());
        }
        for (ChatFormatting fmt : formats) {
            style = style.applyFormat(fmt);
        }
        comp.setStyle(style);
        into.append((Component)comp);
    }

    static {
        COLOR_NAMES.put("black", ChatFormatting.BLACK);
        COLOR_NAMES.put("dark_blue", ChatFormatting.DARK_BLUE);
        COLOR_NAMES.put("dark_green", ChatFormatting.DARK_GREEN);
        COLOR_NAMES.put("dark_aqua", ChatFormatting.DARK_AQUA);
        COLOR_NAMES.put("dark_red", ChatFormatting.DARK_RED);
        COLOR_NAMES.put("dark_purple", ChatFormatting.DARK_PURPLE);
        COLOR_NAMES.put("gold", ChatFormatting.GOLD);
        COLOR_NAMES.put("gray", ChatFormatting.GRAY);
        COLOR_NAMES.put("dark_gray", ChatFormatting.DARK_GRAY);
        COLOR_NAMES.put("blue", ChatFormatting.BLUE);
        COLOR_NAMES.put("green", ChatFormatting.GREEN);
        COLOR_NAMES.put("aqua", ChatFormatting.AQUA);
        COLOR_NAMES.put("red", ChatFormatting.RED);
        COLOR_NAMES.put("light_purple", ChatFormatting.LIGHT_PURPLE);
        COLOR_NAMES.put("yellow", ChatFormatting.YELLOW);
        COLOR_NAMES.put("white", ChatFormatting.WHITE);
        FORMAT_NAMES.put("bold", ChatFormatting.BOLD);
        FORMAT_NAMES.put("b", ChatFormatting.BOLD);
        FORMAT_NAMES.put("italic", ChatFormatting.ITALIC);
        FORMAT_NAMES.put("i", ChatFormatting.ITALIC);
        FORMAT_NAMES.put("obfuscated", ChatFormatting.OBFUSCATED);
        FORMAT_NAMES.put("magic", ChatFormatting.OBFUSCATED);
        FORMAT_NAMES.put("obf", ChatFormatting.OBFUSCATED);
        FORMAT_NAMES.put("underlined", ChatFormatting.UNDERLINE);
        FORMAT_NAMES.put("underline", ChatFormatting.UNDERLINE);
        FORMAT_NAMES.put("u", ChatFormatting.UNDERLINE);
        FORMAT_NAMES.put("strikethrough", ChatFormatting.STRIKETHROUGH);
        FORMAT_NAMES.put("strike", ChatFormatting.STRIKETHROUGH);
        FORMAT_NAMES.put("s", ChatFormatting.STRIKETHROUGH);
        AMP_FORMATS.put(Character.valueOf('0'), ChatFormatting.BLACK);
        AMP_FORMATS.put(Character.valueOf('1'), ChatFormatting.DARK_BLUE);
        AMP_FORMATS.put(Character.valueOf('2'), ChatFormatting.DARK_GREEN);
        AMP_FORMATS.put(Character.valueOf('3'), ChatFormatting.DARK_AQUA);
        AMP_FORMATS.put(Character.valueOf('4'), ChatFormatting.DARK_RED);
        AMP_FORMATS.put(Character.valueOf('5'), ChatFormatting.DARK_PURPLE);
        AMP_FORMATS.put(Character.valueOf('6'), ChatFormatting.GOLD);
        AMP_FORMATS.put(Character.valueOf('7'), ChatFormatting.GRAY);
        AMP_FORMATS.put(Character.valueOf('8'), ChatFormatting.DARK_GRAY);
        AMP_FORMATS.put(Character.valueOf('9'), ChatFormatting.BLUE);
        AMP_FORMATS.put(Character.valueOf('a'), ChatFormatting.GREEN);
        AMP_FORMATS.put(Character.valueOf('b'), ChatFormatting.AQUA);
        AMP_FORMATS.put(Character.valueOf('c'), ChatFormatting.RED);
        AMP_FORMATS.put(Character.valueOf('d'), ChatFormatting.LIGHT_PURPLE);
        AMP_FORMATS.put(Character.valueOf('e'), ChatFormatting.YELLOW);
        AMP_FORMATS.put(Character.valueOf('f'), ChatFormatting.WHITE);
        AMP_FORMATS.put(Character.valueOf('k'), ChatFormatting.OBFUSCATED);
        AMP_FORMATS.put(Character.valueOf('l'), ChatFormatting.BOLD);
        AMP_FORMATS.put(Character.valueOf('m'), ChatFormatting.STRIKETHROUGH);
        AMP_FORMATS.put(Character.valueOf('n'), ChatFormatting.UNDERLINE);
        AMP_FORMATS.put(Character.valueOf('o'), ChatFormatting.ITALIC);
        AMP_FORMATS.put(Character.valueOf('r'), ChatFormatting.RESET);
    }
}
