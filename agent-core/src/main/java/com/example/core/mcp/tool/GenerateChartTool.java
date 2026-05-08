package com.example.core.mcp.tool;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.CategorySeries;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.Styler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.example.core.mcp.McpTool;
import com.example.core.mcp.McpToolParamParser;
import com.example.core.mcp.McpToolResult;
import com.example.core.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Lazy
@RequiredArgsConstructor
public class GenerateChartTool implements McpTool {

    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "generate_chart";
    }

    @Override
    public String getDescription() {
        return "根据数据生成图表(柱状图/折线图/饼图/散点图/面积图)，输出为PNG图片文件";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("chart_type", "图表类型(必填): BAR/LINE/PIE/SCATTER/AREA");
        schema.put("title", "图表标题(必填)");
        schema.put("data", "图表数据(必填，JSON格式)");
        schema.put("x_axis_label", "X轴标签(可选)");
        schema.put("y_axis_label", "Y轴标签(可选)");
        schema.put("width", "图片宽度(可选，默认800)");
        schema.put("height", "图片高度(可选，默认600)");
        schema.put("legend_visible", "是否显示图例(可选，默认true)");
        schema.put("file_name", "输出文件名(可选，默认\"图表.png\")");
        schema.put("user_id", "用户ID(系统自动注入)");
        schema.put("session_id", "会话ID(系统自动注入)");
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> params) {
        Long userId = McpToolParamParser.getLong(params, "user_id");
        String sessionId = McpToolParamParser.getString(params, "session_id");

        String chartType = McpToolParamParser.getString(params, "chart_type");
        if (chartType == null || chartType.isBlank()) {
            return McpToolResult.error("缺少必填参数: chart_type");
        }
        chartType = chartType.toUpperCase();

        String title = McpToolParamParser.getString(params, "title");
        if (title == null || title.isBlank()) {
            return McpToolResult.error("缺少必填参数: title");
        }

        Object dataObj = params.get("data");
        if (dataObj == null) {
            return McpToolResult.error("缺少必填参数: data");
        }

        String xAxisLabel = McpToolParamParser.getString(params, "x_axis_label", "");
        String yAxisLabel = McpToolParamParser.getString(params, "y_axis_label", "");
        int width = McpToolParamParser.getInteger(params, "width", 800);
        int height = McpToolParamParser.getInteger(params, "height", 600);
        boolean legendVisible = McpToolParamParser.getBool(params, "legend_visible", true);
        String fileName = McpToolParamParser.getString(params, "file_name", "图表.png");

        if (width < 200 || width > 4000)
            width = 800;
        if (height < 200 || height > 4000)
            height = 600;

        try {
            byte[] pngBytes;
            switch (chartType) {
                case "BAR" ->
                    pngBytes = generateBarChart(title, dataObj, xAxisLabel, yAxisLabel, width, height, legendVisible);
                case "LINE" ->
                    pngBytes = generateLineChart(title, dataObj, xAxisLabel, yAxisLabel, width, height, legendVisible);
                case "PIE" -> pngBytes = generatePieChart(title, dataObj, width, height, legendVisible);
                case "SCATTER" -> pngBytes = generateScatterChart(title, dataObj, xAxisLabel, yAxisLabel, width, height,
                        legendVisible);
                case "AREA" ->
                    pngBytes = generateAreaChart(title, dataObj, xAxisLabel, yAxisLabel, width, height, legendVisible);
                default -> {
                    return McpToolResult.error("不支持的图表类型: " + chartType + "，支持: BAR/LINE/PIE/SCATTER/AREA");
                }
            }

            if (pngBytes == null || pngBytes.length == 0) {
                return McpToolResult.error("图表生成失败");
            }

            if (!fileName.toLowerCase().endsWith(".png")) {
                fileName = fileName + ".png";
            }

            String storageKey = fileStorageService.generateUploadKey(userId, sessionId, fileName);
            try (InputStream is = new ByteArrayInputStream(pngBytes)) {
                fileStorageService.upload(storageKey, is, pngBytes.length, "image/png");
            }

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("file_key", storageKey);
            resultData.put("file_name", fileName);
            resultData.put("file_type", "png");
            resultData.put("file_size", pngBytes.length);

            return McpToolResult.success("图表已生成: " + fileName, resultData);

        } catch (Exception e) {
            log.error("图表生成失败: chartType={}, error={}", chartType, e.getMessage(), e);
            return McpToolResult.error("图表生成失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] generateBarChart(String title, Object dataObj, String xAxisLabel, String yAxisLabel,
            int width, int height, boolean legendVisible) throws Exception {
        Map<String, Object> data = toMap(dataObj);
        List<String> categories = toStringList(data.get("categories"));
        List<Map<String, Object>> series = toListOfMaps(data.get("series"));

        CategoryChart chart = new CategoryChartBuilder()
                .width(width).height(height)
                .title(title)
                .xAxisTitle(xAxisLabel).yAxisTitle(yAxisLabel)
                .build();

        Styler styler = chart.getStyler();
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setLegendVisible(legendVisible);
        applyChineseFont(styler);

        for (Map<String, Object> s : series) {
            String name = s.get("name") != null ? s.get("name").toString() : "Series";
            List<Number> values = toNumberList(s.get("values"));
            CategorySeries cs = chart.addSeries(name, categories, values);
            cs.setChartCategorySeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private byte[] generateLineChart(String title, Object dataObj, String xAxisLabel, String yAxisLabel,
            int width, int height, boolean legendVisible) throws Exception {
        Map<String, Object> data = toMap(dataObj);
        List<String> categories = toStringList(data.get("categories"));
        List<Map<String, Object>> series = toListOfMaps(data.get("series"));

        CategoryChart chart = new CategoryChartBuilder()
                .width(width).height(height)
                .title(title)
                .xAxisTitle(xAxisLabel).yAxisTitle(yAxisLabel)
                .build();

        Styler styler = chart.getStyler();
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setLegendVisible(legendVisible);
        applyChineseFont(styler);

        for (Map<String, Object> s : series) {
            String name = s.get("name") != null ? s.get("name").toString() : "Series";
            List<Number> values = toNumberList(s.get("values"));
            CategorySeries cs = chart.addSeries(name, categories, values);
            cs.setChartCategorySeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Line);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private byte[] generatePieChart(String title, Object dataObj, int width, int height,
            boolean legendVisible) throws Exception {
        Map<String, Object> data = toMap(dataObj);
        List<String> labels = toStringList(data.get("labels"));
        List<Number> values = toNumberList(data.get("values"));

        PieChart chart = new PieChartBuilder()
                .width(width).height(height)
                .title(title)
                .build();

        Styler styler = chart.getStyler();
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setLegendVisible(legendVisible);
        applyChineseFont(styler);

        for (int i = 0; i < labels.size() && i < values.size(); i++) {
            chart.addSeries(labels.get(i), values.get(i));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private byte[] generateScatterChart(String title, Object dataObj, String xAxisLabel, String yAxisLabel,
            int width, int height, boolean legendVisible) throws Exception {
        Map<String, Object> data = toMap(dataObj);
        List<Map<String, Object>> series = toListOfMaps(data.get("series"));

        XYChart chart = new XYChartBuilder()
                .width(width).height(height)
                .title(title)
                .xAxisTitle(xAxisLabel).yAxisTitle(yAxisLabel)
                .build();

        Styler styler = chart.getStyler();
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setLegendVisible(legendVisible);
        applyChineseFont(styler);

        for (Map<String, Object> s : series) {
            String name = s.get("name") != null ? s.get("name").toString() : "Series";
            List<List<Number>> points = toListOfNumberLists(s.get("points"));
            List<Double> xData = new ArrayList<>();
            List<Double> yData = new ArrayList<>();
            for (List<Number> point : points) {
                if (point.size() >= 2) {
                    xData.add(point.get(0).doubleValue());
                    yData.add(point.get(1).doubleValue());
                }
            }
            XYSeries xySeries = chart.addSeries(name, xData, yData);
            xySeries.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private byte[] generateAreaChart(String title, Object dataObj, String xAxisLabel, String yAxisLabel,
            int width, int height, boolean legendVisible) throws Exception {
        Map<String, Object> data = toMap(dataObj);
        List<String> categories = toStringList(data.get("categories"));
        List<Map<String, Object>> series = toListOfMaps(data.get("series"));

        CategoryChart chart = new CategoryChartBuilder()
                .width(width).height(height)
                .title(title)
                .xAxisTitle(xAxisLabel).yAxisTitle(yAxisLabel)
                .build();

        Styler styler = chart.getStyler();
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setLegendVisible(legendVisible);
        applyChineseFont(styler);

        for (Map<String, Object> s : series) {
            String name = s.get("name") != null ? s.get("name").toString() : "Series";
            List<Number> values = toNumberList(s.get("values"));
            CategorySeries cs = chart.addSeries(name, categories, values);
            cs.setChartCategorySeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Area);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        return baos.toByteArray();
    }

    private void applyChineseFont(Styler styler) {
        Font baseFont = getChineseFont(Font.PLAIN, 12);
        styler.setBaseFont(baseFont);
        styler.setChartTitleFont(getChineseFont(Font.BOLD, 14));
        styler.setLegendFont(baseFont);
        if (styler instanceof AxesChartStyler axesStyler) {
            Font axisFont = getChineseFont(Font.PLAIN, 11);
            axesStyler.setAxisTitleFont(getChineseFont(Font.BOLD, 12));
            axesStyler.setAxisTickLabelsFont(axisFont);
        }
    }

    private static Font getChineseFont(int style, int size) {
        String[] chineseFonts = { "Microsoft YaHei", "SimHei", "SimSun", "PingFang SC", "Noto Sans CJK SC" };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();
        for (String fontName : chineseFonts) {
            for (String available : availableFonts) {
                if (available.equals(fontName)) {
                    return new Font(fontName, style, size);
                }
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        if (obj instanceof String) {
            return com.alibaba.fastjson2.JSON.parseObject((String) obj);
        }
        throw new IllegalArgumentException("data参数格式错误，期望JSON对象");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toListOfMaps(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        if (obj instanceof String) {
            com.alibaba.fastjson2.JSONArray arr = com.alibaba.fastjson2.JSON.parseArray((String) obj);
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.getJSONObject(i));
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(item != null ? item.toString() : "");
            }
            return result;
        }
        return new ArrayList<>();
    }

    private List<Number> toNumberList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Number> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number) {
                    result.add((Number) item);
                } else if (item instanceof String) {
                    try {
                        result.add(Double.parseDouble((String) item));
                    } catch (NumberFormatException e) {
                        result.add(0);
                    }
                } else {
                    result.add(0);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private List<List<Number>> toListOfNumberLists(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<List<Number>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof List) {
                    result.add(toNumberList(item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}
