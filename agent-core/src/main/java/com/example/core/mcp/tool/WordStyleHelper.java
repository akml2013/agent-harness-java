package com.example.core.mcp.tool;

import java.math.BigInteger;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class WordStyleHelper {

    private static final int TWIPS_PER_PT = 20;

    private WordStyleHelper() {
    }

    private static int charCountToTwips(int charCount, Map<String, Object> style) {
        int fontSize = 12;
        if (style != null && style.containsKey("font_size")) {
            fontSize = toInt(style.get("font_size"), 12);
        }
        return charCount * fontSize * TWIPS_PER_PT;
    }

    public static void applyRunStyle(XWPFRun run, Map<String, Object> style) {
        if (style == null)
            return;

        if (style.containsKey("font_family")) {
            String fontFamily = String.valueOf(style.get("font_family"));
            run.setFontFamily(fontFamily);
        }
        if (style.containsKey("font_size")) {
            int fontSize = toInt(style.get("font_size"), 12);
            run.setFontSize(fontSize);
        }
        if (style.containsKey("bold")) {
            run.setBold(toBool(style.get("bold")));
        }
        if (style.containsKey("italic")) {
            run.setItalic(toBool(style.get("italic")));
        }
        if (style.containsKey("underline")) {
            if (toBool(style.get("underline"))) {
                run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
            }
        }
        if (style.containsKey("color")) {
            String color = String.valueOf(style.get("color"));
            if (color.startsWith("#"))
                color = color.substring(1);
            run.setColor(color);
        }
        if (style.containsKey("background_color")) {
            String bgColor = String.valueOf(style.get("background_color"));
            if (bgColor.startsWith("#"))
                bgColor = bgColor.substring(1);
            CTRPr rpr = run.getCTR().getRPr();
            if (rpr == null)
                rpr = run.getCTR().addNewRPr();
            CTShd shd = rpr.addNewShd();
            shd.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR);
            shd.setFill(bgColor);
        }
        if (style.containsKey("strikethrough")) {
            run.setStrikeThrough(toBool(style.get("strikethrough")));
        }
        if (style.containsKey("subscript")) {
            if (toBool(style.get("subscript"))) {
                run.setSubscript(org.apache.poi.xwpf.usermodel.VerticalAlign.SUBSCRIPT);
            }
        }
        if (style.containsKey("superscript")) {
            if (toBool(style.get("superscript"))) {
                run.setSubscript(org.apache.poi.xwpf.usermodel.VerticalAlign.SUPERSCRIPT);
            }
        }
        if (style.containsKey("highlight")) {
            String highlight = String.valueOf(style.get("highlight")).toLowerCase();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rpr = run.getCTR().getRPr();
            if (rpr == null)
                rpr = run.getCTR().addNewRPr();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHighlight hl = rpr.addNewHighlight();
            hl.setVal(switch (highlight) {
                case "yellow" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
                case "green" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.GREEN;
                case "cyan" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.CYAN;
                case "magenta" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.MAGENTA;
                case "blue" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLUE;
                case "red" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.RED;
                case "dark_blue" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_BLUE;
                case "dark_cyan" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_CYAN;
                case "dark_green" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_GREEN;
                case "dark_magenta" ->
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_MAGENTA;
                case "dark_red" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_RED;
                case "dark_yellow" ->
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_YELLOW;
                case "dark_gray" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.DARK_GRAY;
                case "light_gray" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.LIGHT_GRAY;
                case "black" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.BLACK;
                default -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STHighlightColor.YELLOW;
            });
        }
    }

    public static void applyParagraphStyle(XWPFParagraph paragraph, Map<String, Object> style) {
        if (style == null)
            return;

        if (style.containsKey("alignment")) {
            String alignment = String.valueOf(style.get("alignment")).toUpperCase();
            paragraph.setAlignment(switch (alignment) {
                case "CENTER" -> ParagraphAlignment.CENTER;
                case "RIGHT" -> ParagraphAlignment.RIGHT;
                case "BOTH" -> ParagraphAlignment.BOTH;
                default -> ParagraphAlignment.LEFT;
            });
        }

        CTPPr ppr = getOrCreatePPr(paragraph);
        boolean spacingChanged = false;
        CTSpacing spacing = ppr.getSpacing();
        if (spacing == null) {
            spacing = ppr.addNewSpacing();
        }

        if (style.containsKey("line_spacing")) {
            double lineSpacing = toDouble(style.get("line_spacing"), 1.0);
            spacing.setLine(BigInteger.valueOf((long) (lineSpacing * 240)));
            spacing.setLineRule(STLineSpacingRule.AUTO);
            spacingChanged = true;
        }
        if (style.containsKey("spacing_before")) {
            int spacingBefore = toInt(style.get("spacing_before"), 0);
            spacing.setBefore(BigInteger.valueOf(spacingBefore * TWIPS_PER_PT));
            spacingChanged = true;
        }
        if (style.containsKey("spacing_after")) {
            int spacingAfter = toInt(style.get("spacing_after"), 0);
            spacing.setAfter(BigInteger.valueOf(spacingAfter * TWIPS_PER_PT));
            spacingChanged = true;
        }
        if (!spacingChanged) {
            ppr.unsetSpacing();
        }

        boolean indentChanged = false;
        CTInd indent = ppr.getInd();
        if (indent == null) {
            indent = ppr.addNewInd();
        }

        if (style.containsKey("first_line_indent")) {
            int val = toInt(style.get("first_line_indent"), 0);
            int twips = charCountToTwips(val, style);
            indent.setFirstLine(BigInteger.valueOf(twips));
            indentChanged = true;
        }
        if (style.containsKey("hanging_indent")) {
            int val = toInt(style.get("hanging_indent"), 0);
            int twips = charCountToTwips(val, style);
            indent.setHanging(BigInteger.valueOf(twips));
            indentChanged = true;
        }
        if (!indentChanged) {
            ppr.unsetInd();
        }

        if (style.containsKey("border_top") || style.containsKey("border_bottom")
                || style.containsKey("border_left") || style.containsKey("border_right")) {
            CTPBdr pBdr = ppr.getPBdr();
            if (pBdr == null)
                pBdr = ppr.addNewPBdr();
            applyBorder(pBdr.addNewTop(), style.get("border_top"));
            applyBorder(pBdr.addNewBottom(), style.get("border_bottom"));
            applyBorder(pBdr.addNewLeft(), style.get("border_left"));
            applyBorder(pBdr.addNewRight(), style.get("border_right"));
        }

        clearNumberingProperties(paragraph);
    }

    public static void clearNumberingProperties(XWPFParagraph paragraph) {
        try {
            CTPPr ppr = paragraph.getCTP().getPPr();
            if (ppr != null && ppr.isSetNumPr()) {
                ppr.unsetNumPr();
            }
        } catch (Exception e) {
            log.warn("clearNumberingProperties failed", e);
        }
    }

    public static Map<String, Object> extractRunStyle(XWPFRun run) {
        Map<String, Object> style = new java.util.LinkedHashMap<>();
        if (run.getFontFamily() != null)
            style.put("font_family", run.getFontFamily());
        if (run.getFontSizeAsDouble() != null)
            style.put("font_size", run.getFontSizeAsDouble().intValue());
        style.put("bold", run.isBold());
        style.put("italic", run.isItalic());
        if (run.getUnderline() != null && run.getUnderline() != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE) {
            style.put("underline", true);
        }
        String color = run.getColor();
        if (color != null && !color.isEmpty() && !"000000".equals(color.toLowerCase())) {
            style.put("color", color);
        }
        if (run.isStrikeThrough()) {
            style.put("strikethrough", true);
        }
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rprExt = run.getCTR().getRPr();
        if (rprExt != null && rprExt.sizeOfVertAlignArray() > 0) {
            String vertAlign = String.valueOf(rprExt.getVertAlignArray(0).getVal());
            if ("subscript".equalsIgnoreCase(vertAlign)) {
                style.put("subscript", true);
            } else if ("superscript".equalsIgnoreCase(vertAlign)) {
                style.put("superscript", true);
            }
        }
        if (rprExt != null && rprExt.sizeOfShdArray() > 0) {
            CTShd shd = rprExt.getShdArray(0);
            String fill = WordReadTool.extractFillColor(shd);
            if (fill != null && !fill.isEmpty() && !"auto".equalsIgnoreCase(fill)) {
                style.put("background_color", fill);
            }
        }
        return style;
    }

    public static Map<String, Object> extractParagraphStyle(XWPFParagraph paragraph) {
        Map<String, Object> style = new java.util.LinkedHashMap<>();

        if (paragraph.getAlignment() != null) {
            String alignment = switch (paragraph.getAlignment()) {
                case CENTER -> "CENTER";
                case RIGHT -> "RIGHT";
                case BOTH -> "BOTH";
                default -> "LEFT";
            };
            style.put("alignment", alignment);
        }

        CTPPr ppr = paragraph.getCTP().getPPr();
        if (ppr != null) {
            CTSpacing spacing = ppr.getSpacing();
            if (spacing != null) {
                if (spacing.getLine() != null) {
                    long lineVal = toLong(spacing.getLine());
                    style.put("line_spacing", Math.round(lineVal / 240.0 * 10) / 10.0);
                }
                if (spacing.getBefore() != null) {
                    style.put("spacing_before", toLong(spacing.getBefore()) / TWIPS_PER_PT);
                }
                if (spacing.getAfter() != null) {
                    style.put("spacing_after", toLong(spacing.getAfter()) / TWIPS_PER_PT);
                }
            }

            CTInd indent = ppr.getInd();
            if (indent != null) {
                int fontSize = 12;
                if (style.containsKey("font_size")) {
                    fontSize = toInt(style.get("font_size"), 12);
                }
                int twipsPerChar = fontSize * TWIPS_PER_PT;
                if (indent.getFirstLine() != null && toLong(indent.getFirstLine()) > 0) {
                    long firstLineVal = toLong(indent.getFirstLine());
                    style.put("first_line_indent", Math.round((double) firstLineVal / twipsPerChar));
                }
                if (indent.getHanging() != null && toLong(indent.getHanging()) > 0) {
                    long hangingVal = toLong(indent.getHanging());
                    style.put("hanging_indent", Math.round((double) hangingVal / twipsPerChar));
                }
            }
        }

        return style;
    }

    private static CTPPr getOrCreatePPr(XWPFParagraph paragraph) {
        CTP ctp = paragraph.getCTP();
        CTPPr ppr = ctp.getPPr();
        if (ppr == null) {
            ppr = ctp.addNewPPr();
        }
        return ppr;
    }

    private static long toLong(Object value) {
        if (value instanceof BigInteger bi)
            return bi.longValue();
        if (value instanceof Number n)
            return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean toBool(Object value) {
        if (value == null)
            return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static void applyBorder(CTBorder border, Object value) {
        if (value == null) {
            border.setVal(STBorder.NONE);
            return;
        }
        if (value instanceof Boolean && !((Boolean) value)) {
            border.setVal(STBorder.NONE);
            return;
        }
        if (value instanceof String s && "none".equalsIgnoreCase(s)) {
            border.setVal(STBorder.NONE);
            return;
        }
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setSpace(BigInteger.ZERO);
        border.setColor("000000");
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> borderMap = (Map<String, Object>) value;
            if (borderMap.containsKey("style")) {
                String bStyle = String.valueOf(borderMap.get("style")).toLowerCase();
                border.setVal(switch (bStyle) {
                    case "dashed" -> STBorder.DASHED;
                    case "dotted" -> STBorder.DOTTED;
                    case "double" -> STBorder.DOUBLE;
                    case "thick" -> STBorder.THICK;
                    default -> STBorder.SINGLE;
                });
            }
            if (borderMap.containsKey("width")) {
                border.setSz(BigInteger.valueOf(toInt(borderMap.get("width"), 4)));
            }
            if (borderMap.containsKey("color")) {
                String bColor = String.valueOf(borderMap.get("color"));
                if (bColor.startsWith("#"))
                    bColor = bColor.substring(1);
                border.setColor(bColor);
            }
        }
    }
}
