package com.example.core.mcp.tool;

import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelStyleHelper {

    private ExcelStyleHelper() {
    }

    @SuppressWarnings("unchecked")
    public static void applyCellStyle(Cell cell, Map<String, Object> style) {
        if (style == null || style.isEmpty())
            return;
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle cellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();

        if (style.containsKey("bold") && toBool(style.get("bold"))) {
            font.setBold(true);
        }
        if (style.containsKey("italic") && toBool(style.get("italic"))) {
            font.setItalic(true);
        }
        if (style.containsKey("underline") && toBool(style.get("underline"))) {
            font.setUnderline(Font.U_SINGLE);
        }
        if (style.containsKey("strikethrough") && toBool(style.get("strikethrough"))) {
            font.setStrikeout(true);
        }
        if (style.containsKey("font_size")) {
            font.setFontHeightInPoints((short) toInt(style.get("font_size"), 12));
        }
        if (style.containsKey("font_family")) {
            font.setFontName(String.valueOf(style.get("font_family")));
        }
        if (style.containsKey("font_color") && workbook instanceof XSSFWorkbook) {
            String colorHex = toHexColor(String.valueOf(style.get("font_color")));
            if (colorHex != null) {
                ((XSSFFont) font).setColor(new XSSFColor(hexToRgb(colorHex), null));
            }
        }
        if (style.containsKey("background_color") && workbook instanceof XSSFWorkbook) {
            String colorHex = toHexColor(String.valueOf(style.get("background_color")));
            if (colorHex != null) {
                cellStyle.setFillForegroundColor(new XSSFColor(hexToRgb(colorHex), null));
                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        }
        if (style.containsKey("number_format")) {
            String format = String.valueOf(style.get("number_format"));
            cellStyle.setDataFormat(workbook.createDataFormat().getFormat(format));
        }
        if (style.containsKey("alignment")) {
            String alignment = String.valueOf(style.get("alignment")).toUpperCase();
            cellStyle.setAlignment(switch (alignment) {
                case "CENTER" -> HorizontalAlignment.CENTER;
                case "RIGHT" -> HorizontalAlignment.RIGHT;
                default -> HorizontalAlignment.LEFT;
            });
        }
        if (style.containsKey("vertical_alignment")) {
            String vAlign = String.valueOf(style.get("vertical_alignment")).toUpperCase();
            cellStyle.setVerticalAlignment(switch (vAlign) {
                case "TOP" -> VerticalAlignment.TOP;
                case "BOTTOM" -> VerticalAlignment.BOTTOM;
                default -> VerticalAlignment.CENTER;
            });
        } else {
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        }
        if (style.containsKey("wrap_text")) {
            cellStyle.setWrapText(toBool(style.get("wrap_text")));
        }
        if (style.containsKey("locked")) {
            cellStyle.setLocked(toBool(style.get("locked")));
        }
        if (style.containsKey("hidden")) {
            cellStyle.setHidden(toBool(style.get("hidden")));
        }
        if (style.containsKey("rotation")) {
            cellStyle.setRotation((short) toInt(style.get("rotation"), 0));
        }
        if (style.containsKey("indent")) {
            cellStyle.setIndention((short) toInt(style.get("indent"), 0));
        }
        if (style.containsKey("shrink_to_fit")) {
            cellStyle.setShrinkToFit(toBool(style.get("shrink_to_fit")));
        }

        if (style.containsKey("border")) {
            Object borderObj = style.get("border");
            if (borderObj instanceof Map) {
                applyBorders(cellStyle, (Map<String, Object>) borderObj, workbook);
            }
        }

        cellStyle.setFont(font);
        cell.setCellStyle(cellStyle);
    }

    @SuppressWarnings("unchecked")
    private static void applyBorders(CellStyle cellStyle, Map<String, Object> borders, Workbook workbook) {
        String[] positions = { "top", "bottom", "left", "right" };
        for (String pos : positions) {
            Object borderVal = borders.get(pos);
            if (borderVal == null)
                continue;

            BorderStyle borderStyle;
            String borderColor = "000000";

            if (borderVal instanceof Map) {
                Map<String, Object> borderMap = (Map<String, Object>) borderVal;
                String bStyle = borderMap.containsKey("style")
                        ? String.valueOf(borderMap.get("style")).toLowerCase()
                        : "thin";
                borderStyle = parseBorderStyle(bStyle);
                if (borderMap.containsKey("color")) {
                    borderColor = toHexColor(String.valueOf(borderMap.get("color")));
                    if (borderColor == null)
                        borderColor = "000000";
                }
            } else if (toBool(borderVal)) {
                borderStyle = BorderStyle.THIN;
            } else {
                continue;
            }

            byte[] rgb = hexToRgb(borderColor);

            switch (pos) {
                case "top" -> {
                    cellStyle.setBorderTop(borderStyle);
                    if (rgb != null && workbook instanceof XSSFWorkbook) {
                        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) cellStyle)
                                .setTopBorderColor(new XSSFColor(rgb, null));
                    }
                }
                case "bottom" -> {
                    cellStyle.setBorderBottom(borderStyle);
                    if (rgb != null && workbook instanceof XSSFWorkbook) {
                        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) cellStyle)
                                .setBottomBorderColor(new XSSFColor(rgb, null));
                    }
                }
                case "left" -> {
                    cellStyle.setBorderLeft(borderStyle);
                    if (rgb != null && workbook instanceof XSSFWorkbook) {
                        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) cellStyle)
                                .setLeftBorderColor(new XSSFColor(rgb, null));
                    }
                }
                case "right" -> {
                    cellStyle.setBorderRight(borderStyle);
                    if (rgb != null && workbook instanceof XSSFWorkbook) {
                        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) cellStyle)
                                .setRightBorderColor(new XSSFColor(rgb, null));
                    }
                }
            }
        }
    }

    private static BorderStyle parseBorderStyle(String style) {
        return switch (style) {
            case "none" -> BorderStyle.NONE;
            case "thin" -> BorderStyle.THIN;
            case "medium" -> BorderStyle.MEDIUM;
            case "dashed" -> BorderStyle.DASHED;
            case "dotted" -> BorderStyle.DOTTED;
            case "thick" -> BorderStyle.THICK;
            case "double" -> BorderStyle.DOUBLE;
            case "hair" -> BorderStyle.HAIR;
            case "medium_dashed" -> BorderStyle.MEDIUM_DASHED;
            case "dash_dot" -> BorderStyle.DASH_DOT;
            case "medium_dash_dot" -> BorderStyle.MEDIUM_DASH_DOT;
            case "dash_dot_dot" -> BorderStyle.DASH_DOT_DOT;
            case "medium_dash_dot_dot" -> BorderStyle.MEDIUM_DASH_DOT_DOT;
            case "slanted_dash_dot" -> BorderStyle.SLANTED_DASH_DOT;
            default -> BorderStyle.THIN;
        };
    }

    public static Map<String, Object> extractCellStyle(Cell cell) {
        Map<String, Object> style = new java.util.LinkedHashMap<>();
        if (cell == null)
            return style;
        CellStyle cellStyle = cell.getCellStyle();
        if (cellStyle == null)
            return style;

        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        if (font.getBold())
            style.put("bold", true);
        if (font.getItalic())
            style.put("italic", true);
        if (font.getUnderline() != Font.U_NONE)
            style.put("underline", true);
        if (font.getStrikeout())
            style.put("strikethrough", true);
        if (font.getFontHeightInPoints() != 11) {
            style.put("font_size", (int) font.getFontHeightInPoints());
        }
        if (!"Calibri".equals(font.getFontName()) && !"宋体".equals(font.getFontName())) {
            style.put("font_family", font.getFontName());
        }

        if (font instanceof XSSFFont xssfFont) {
            XSSFColor color = xssfFont.getXSSFColor();
            if (color != null && !isBlack(color)) {
                style.put("font_color", rgbToHex(color.getRGB()));
            }
        }

        if (cellStyle.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
            try {
                if (cellStyle instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle) {
                    XSSFColor bgColor = xssfStyle.getFillForegroundColorColor();
                    if (bgColor != null && !isWhite(bgColor)) {
                        style.put("background_color", rgbToHex(bgColor.getRGB()));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (cellStyle.getDataFormat() != 0) {
            String formatStr = cell.getSheet().getWorkbook().createDataFormat().getFormat(cellStyle.getDataFormat());
            if (formatStr != null && !formatStr.isEmpty() && !"General".equals(formatStr)) {
                style.put("number_format", formatStr);
            }
        }

        HorizontalAlignment alignment = cellStyle.getAlignment();
        if (alignment == HorizontalAlignment.CENTER)
            style.put("alignment", "CENTER");
        else if (alignment == HorizontalAlignment.RIGHT)
            style.put("alignment", "RIGHT");

        VerticalAlignment vAlign = cellStyle.getVerticalAlignment();
        if (vAlign == VerticalAlignment.TOP)
            style.put("vertical_alignment", "TOP");
        else if (vAlign == VerticalAlignment.BOTTOM)
            style.put("vertical_alignment", "BOTTOM");

        if (cellStyle.getWrapText())
            style.put("wrap_text", true);
        if (!cellStyle.getLocked())
            style.put("locked", false);
        if (cellStyle.getHidden())
            style.put("hidden", true);
        if (cellStyle.getRotation() != 0)
            style.put("rotation", (int) cellStyle.getRotation());
        if (cellStyle.getIndention() != 0)
            style.put("indent", (int) cellStyle.getIndention());
        if (cellStyle.getShrinkToFit())
            style.put("shrink_to_fit", true);

        Map<String, Object> borderInfo = new java.util.LinkedHashMap<>();
        if (cellStyle.getBorderTop() != BorderStyle.NONE) {
            borderInfo.put("top", Map.of("style", cellStyle.getBorderTop().name().toLowerCase()));
        }
        if (cellStyle.getBorderBottom() != BorderStyle.NONE) {
            borderInfo.put("bottom", Map.of("style", cellStyle.getBorderBottom().name().toLowerCase()));
        }
        if (cellStyle.getBorderLeft() != BorderStyle.NONE) {
            borderInfo.put("left", Map.of("style", cellStyle.getBorderLeft().name().toLowerCase()));
        }
        if (cellStyle.getBorderRight() != BorderStyle.NONE) {
            borderInfo.put("right", Map.of("style", cellStyle.getBorderRight().name().toLowerCase()));
        }
        if (!borderInfo.isEmpty()) {
            style.put("border", borderInfo);
        }

        return style;
    }

    private static byte[] hexToRgb(String hex) {
        if (hex == null || hex.startsWith("#"))
            hex = hex != null ? hex.substring(1) : null;
        if (hex == null || hex.length() != 6)
            return null;
        return new byte[] {
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private static String rgbToHex(byte[] rgb) {
        if (rgb == null || rgb.length < 3)
            return null;
        return String.format("%02X%02X%02X",
                rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
    }

    private static boolean isBlack(XSSFColor color) {
        byte[] rgb = color.getRGB();
        if (rgb == null)
            return true;
        return (rgb[0] & 0xFF) == 0 && (rgb[1] & 0xFF) == 0 && (rgb[2] & 0xFF) == 0;
    }

    private static boolean isWhite(XSSFColor color) {
        byte[] rgb = color.getRGB();
        if (rgb == null)
            return true;
        return (rgb[0] & 0xFF) == 255 && (rgb[1] & 0xFF) == 255 && (rgb[2] & 0xFF) == 255;
    }

    private static String toHexColor(String color) {
        if (color == null || color.isEmpty())
            return null;
        if (color.startsWith("#"))
            return color.substring(1);
        return color;
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

    private static boolean toBool(Object value) {
        if (value == null)
            return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
