package com.example.core.mcp.tool;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

import com.example.core.mcp.McpTool;
import com.example.core.mcp.McpToolParamParser;
import com.example.core.mcp.McpToolResult;
import com.example.core.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WordReadTool implements McpTool {

    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "read_word";
    }

    @Override
    public String getDescription() {
        return "读取Word文档(.docx)内容，提取文本、段落样式、表格、页眉页脚、标题层级、超链接和分页符等结构信息";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("file_key", "MinIO中文件的存储键(必填)");
        schema.put("include_style", "是否包含段落和文本样式信息(可选，默认true)");
        schema.put("include_structure", "是否包含文档结构信息(表格/页眉页脚/分页符/标题/超链接，可选，默认true)");
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> params) {
        String fileKey = McpToolParamParser.getString(params, "file_key");
        if (fileKey == null || fileKey.isBlank()) {
            return McpToolResult.error("缺少必填参数: file_key");
        }

        boolean includeStyle = McpToolParamParser.getBool(params, "include_style", true);
        boolean includeStructure = McpToolParamParser.getBool(params, "include_structure", true);

        try (InputStream is = fileStorageService.download(fileKey);
                XWPFDocument document = new XWPFDocument(is)) {

            StringBuilder content = new StringBuilder();
            List<Map<String, Object>> paragraphs = new ArrayList<>();
            List<Map<String, Object>> headings = new ArrayList<>();
            List<Map<String, Object>> hyperlinks = new ArrayList<>();
            List<Integer> pageBreaks = new ArrayList<>();
            int paragraphCount = 0;

            for (int i = 0; i < document.getParagraphs().size(); i++) {
                XWPFParagraph paragraph = document.getParagraphs().get(i);
                String text = paragraph.getText();

                // 检测分页符
                if (includeStructure && hasPageBreak(paragraph)) {
                    pageBreaks.add(i);
                }

                // 检测标题
                if (includeStructure) {
                    int headingLevel = getHeadingLevel(paragraph);
                    if (headingLevel > 0 && text != null && !text.isBlank()) {
                        Map<String, Object> headingMap = new LinkedHashMap<>();
                        headingMap.put("paragraph_index", i);
                        headingMap.put("text", text);
                        headingMap.put("level", headingLevel);
                        headings.add(headingMap);
                    }

                    // 检测超链接
                    extractHyperlinks(paragraph, i, document, hyperlinks);
                }

                if (text != null && !text.isBlank()) {
                    content.append(text).append("\n");
                    paragraphCount++;

                    if (includeStyle) {
                        Map<String, Object> paraMap = new LinkedHashMap<>();
                        paraMap.put("index", i);
                        paraMap.put("text", text);

                        Map<String, Object> paraStyle = WordStyleHelper.extractParagraphStyle(paragraph);
                        if (!paraStyle.isEmpty()) {
                            paraMap.put("style", paraStyle);
                        }

                        List<Map<String, Object>> runs = new ArrayList<>();
                        for (int j = 0; j < paragraph.getRuns().size(); j++) {
                            XWPFRun run = paragraph.getRuns().get(j);
                            String runText = run.getText(0);
                            if (runText == null || runText.isEmpty())
                                continue;

                            Map<String, Object> runMap = new LinkedHashMap<>();
                            runMap.put("run_index", j);
                            runMap.put("text", runText);

                            Map<String, Object> runStyle = WordStyleHelper.extractRunStyle(run);
                            if (!runStyle.isEmpty()) {
                                runMap.put("style", runStyle);
                            }
                            runs.add(runMap);
                        }
                        if (!runs.isEmpty()) {
                            paraMap.put("runs", runs);
                        }

                        paragraphs.add(paraMap);
                    }
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("content", content.toString());
            data.put("paragraph_count", paragraphCount);
            data.put("file_key", fileKey);

            if (includeStyle) {
                data.put("paragraphs", paragraphs);
            }

            if (includeStructure) {
                // 提取表格
                data.put("tables", extractTables(document));

                // 提取页眉
                data.put("headers", extractHeaders(document));

                // 提取页脚
                data.put("footers", extractFooters(document));

                // 分页符
                data.put("page_breaks", pageBreaks);

                // 标题
                data.put("headings", headings);

                // 超链接
                data.put("hyperlinks", hyperlinks);
            }

            return McpToolResult.success("Word文档读取成功", data);
        } catch (Exception e) {
            log.error("读取Word文档失败: {}", e.getMessage());
            return McpToolResult.error("读取Word文档失败: " + e.getMessage());
        }
    }

    /**
     * 提取文档中的所有表格
     */
    private List<Map<String, Object>> extractTables(XWPFDocument document) {
        List<Map<String, Object>> tables = new ArrayList<>();
        List<XWPFTable> docTables = document.getTables();

        for (int t = 0; t < docTables.size(); t++) {
            XWPFTable table = docTables.get(t);
            List<XWPFTableRow> rows = table.getRows();

            List<List<Map<String, Object>>> rowData = new ArrayList<>();
            for (XWPFTableRow row : rows) {
                List<Map<String, Object>> cellList = new ArrayList<>();
                for (XWPFTableCell cell : row.getTableCells()) {
                    Map<String, Object> cellMap = new LinkedHashMap<>();
                    cellMap.put("value", cell.getText());

                    Map<String, Object> cellStyle = extractCellStyle(cell);
                    if (!cellStyle.isEmpty()) {
                        cellMap.put("style", cellStyle);
                    }

                    List<Map<String, Object>> runDetails = extractCellRunDetails(cell);
                    if (runDetails != null && !runDetails.isEmpty()) {
                        cellMap.put("runs", runDetails);
                    }

                    cellList.add(cellMap);
                }
                rowData.add(cellList);
            }

            Map<String, Object> tableMap = new LinkedHashMap<>();
            tableMap.put("table_index", t);
            tableMap.put("rows", rowData);
            tableMap.put("row_count", rows.size());
            tableMap.put("col_count", rows.isEmpty() ? 0 : rows.get(0).getTableCells().size());

            List<Double> columnWidths = extractColumnWidths(table);
            if (!columnWidths.isEmpty()) {
                tableMap.put("column_widths", columnWidths);
            }

            List<Map<String, Object>> mergedRegions = extractMergedRegions(table);
            if (!mergedRegions.isEmpty()) {
                tableMap.put("merged_regions", mergedRegions);
            }

            String tableAlignment = extractTableAlignment(table);
            if (tableAlignment != null) {
                tableMap.put("alignment", tableAlignment);
            }

            tables.add(tableMap);
        }

        return tables;
    }

    private Map<String, Object> extractCellStyle(XWPFTableCell cell) {
        Map<String, Object> style = new LinkedHashMap<>();

        for (XWPFParagraph para : cell.getParagraphs()) {
            Map<String, Object> paraStyle = WordStyleHelper.extractParagraphStyle(para);
            if (paraStyle.containsKey("alignment")) {
                style.put("alignment", paraStyle.get("alignment"));
            }

            for (XWPFRun run : para.getRuns()) {
                Map<String, Object> runStyle = WordStyleHelper.extractRunStyle(run);
                if (runStyle.containsKey("font_family") && !style.containsKey("font_family")) {
                    style.put("font_family", runStyle.get("font_family"));
                }
                if (runStyle.containsKey("font_size") && !style.containsKey("font_size")) {
                    style.put("font_size", runStyle.get("font_size"));
                }
                if (runStyle.containsKey("bold") && !style.containsKey("bold")) {
                    style.put("bold", runStyle.get("bold"));
                }
                if (runStyle.containsKey("color") && !style.containsKey("color")) {
                    style.put("color", runStyle.get("color"));
                }
                if (runStyle.containsKey("italic") && !style.containsKey("italic")) {
                    style.put("italic", runStyle.get("italic"));
                }
            }
            break;
        }

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr != null) {
            if (tcPr.isSetShd()) {
                String fill = extractFillColor(tcPr.getShd());
                if (fill != null && !fill.isEmpty() && !"auto".equalsIgnoreCase(fill)) {
                    style.put("background_color", fill);
                }
            }
            if (tcPr.isSetVAlign()) {
                String vAlign = String.valueOf(tcPr.getVAlign().getVal());
                style.put("vertical_alignment", vAlign);
            }
        }

        return style;
    }

    private List<Map<String, Object>> extractCellRunDetails(XWPFTableCell cell) {
        List<Map<String, Object>> runDetails = new ArrayList<>();
        List<XWPFParagraph> paragraphs = cell.getParagraphs();

        if (paragraphs.size() <= 1) {
            XWPFParagraph para = paragraphs.get(0);
            List<XWPFRun> runs = para.getRuns();
            if (runs.size() <= 1) {
                return runDetails;
            }
        }

        for (int p = 0; p < paragraphs.size(); p++) {
            XWPFParagraph para = paragraphs.get(p);
            List<XWPFRun> runs = para.getRuns();
            for (int r = 0; r < runs.size(); r++) {
                XWPFRun run = runs.get(r);
                String text = run.getText(0);
                if (text == null || text.isEmpty())
                    continue;

                Map<String, Object> runInfo = new LinkedHashMap<>();
                runInfo.put("text", text);
                if (paragraphs.size() > 1) {
                    runInfo.put("para_index", p);
                }
                runInfo.put("run_index", r);

                Map<String, Object> runStyle = WordStyleHelper.extractRunStyle(run);
                if (!runStyle.isEmpty()) {
                    runInfo.put("style", runStyle);
                }

                runDetails.add(runInfo);
            }
        }

        return runDetails;
    }

    private List<Double> extractColumnWidths(XWPFTable table) {
        List<Double> widths = new ArrayList<>();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid != null) {
            for (var gridCol : grid.getGridColList()) {
                var wObj = gridCol.getW();
                if (wObj != null) {
                    double wVal = (wObj instanceof BigInteger bi) ? bi.doubleValue()
                            : Double.parseDouble(String.valueOf(wObj));
                    widths.add(Math.round(wVal / 567.0 * 10.0) / 10.0);
                }
            }
        }
        return widths;
    }

    private List<Map<String, Object>> extractMergedRegions(XWPFTable table) {
        List<Map<String, Object>> regions = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();

        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            List<XWPFTableCell> cells = row.getTableCells();
            for (int c = 0; c < cells.size(); c++) {
                XWPFTableCell cell = cells.get(c);
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = cell.getCTTc().getTcPr();
                if (tcPr == null)
                    continue;

                boolean isHRestart = tcPr.isSetHMerge()
                        && tcPr.getHMerge()
                                .getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART;
                boolean isVRestart = tcPr.isSetVMerge()
                        && tcPr.getVMerge()
                                .getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART;

                if (isHRestart || isVRestart) {
                    int endRow = r;
                    int endCol = c;

                    if (isHRestart) {
                        endCol = c;
                        for (int nc = c + 1; nc < cells.size(); nc++) {
                            XWPFTableCell nextCell = cells.get(nc);
                            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr nextTcPr = nextCell.getCTTc()
                                    .getTcPr();
                            if (nextTcPr != null && nextTcPr.isSetHMerge()
                                    && nextTcPr.getHMerge()
                                            .getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE) {
                                endCol = nc;
                            } else {
                                break;
                            }
                        }
                    }

                    if (isVRestart) {
                        endRow = r;
                        for (int nr = r + 1; nr < rows.size(); nr++) {
                            List<XWPFTableCell> nextRowCells = rows.get(nr).getTableCells();
                            if (c < nextRowCells.size()) {
                                XWPFTableCell nextCell = nextRowCells.get(c);
                                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr nextTcPr = nextCell
                                        .getCTTc().getTcPr();
                                if (nextTcPr != null && nextTcPr.isSetVMerge()
                                        && nextTcPr.getVMerge()
                                                .getVal() == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE) {
                                    endRow = nr;
                                } else {
                                    break;
                                }
                            }
                        }
                    }

                    Map<String, Object> region = new LinkedHashMap<>();
                    region.put("start_row", r);
                    region.put("end_row", endRow);
                    region.put("start_col", c);
                    region.put("end_col", endCol);
                    regions.add(region);
                }
            }
        }

        return regions;
    }

    private String extractTableAlignment(XWPFTable table) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr != null && tblPr.isSetJc()) {
            String jcVal = String.valueOf(tblPr.getJc().getVal());
            return switch (jcVal.toLowerCase()) {
                case "left" -> "LEFT";
                case "right" -> "RIGHT";
                case "center" -> "CENTER";
                default -> null;
            };
        }
        return null;
    }

    static String extractFillColor(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd shd) {
        if (shd == null)
            return null;
        try {
            String shdXml = shd.xmlText();
            if (shdXml != null) {
                java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("w:fill=\"([^\"]+)\"").matcher(shdXml);
                if (m1.find()) {
                    String fill = m1.group(1);
                    if (!fill.isEmpty() && !"auto".equalsIgnoreCase(fill)) {
                        return fill;
                    }
                }
                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("fill=\"([^\"]+)\"").matcher(shdXml);
                if (m2.find()) {
                    String fill = m2.group(1);
                    if (!fill.isEmpty() && !"auto".equalsIgnoreCase(fill)) {
                        return fill;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 提取文档中的所有页眉
     */
    private List<Map<String, Object>> extractHeaders(XWPFDocument document) {
        List<Map<String, Object>> headers = new ArrayList<>();

        for (XWPFHeader header : document.getHeaderList()) {
            String headerText = header.getText();
            if (headerText != null && !headerText.isBlank()) {
                Map<String, Object> headerMap = new LinkedHashMap<>();
                headerMap.put("text", headerText.trim());

                // 提取页眉中段落的样式信息
                List<Map<String, Object>> paraStyles = new ArrayList<>();
                for (XWPFParagraph para : header.getParagraphs()) {
                    Map<String, Object> style = WordStyleHelper.extractParagraphStyle(para);
                    if (!style.isEmpty()) {
                        paraStyles.add(style);
                    }
                }
                if (!paraStyles.isEmpty()) {
                    headerMap.put("style", paraStyles);
                }

                headers.add(headerMap);
            }
        }

        return headers;
    }

    /**
     * 提取文档中的所有页脚
     */
    private List<Map<String, Object>> extractFooters(XWPFDocument document) {
        List<Map<String, Object>> footers = new ArrayList<>();

        for (XWPFFooter footer : document.getFooterList()) {
            String footerText = footer.getText();
            if (footerText != null && !footerText.isBlank()) {
                Map<String, Object> footerMap = new LinkedHashMap<>();
                footerMap.put("text", footerText.trim());

                // 提取页脚中段落的样式信息
                List<Map<String, Object>> paraStyles = new ArrayList<>();
                for (XWPFParagraph para : footer.getParagraphs()) {
                    Map<String, Object> style = WordStyleHelper.extractParagraphStyle(para);
                    if (!style.isEmpty()) {
                        paraStyles.add(style);
                    }
                }
                if (!paraStyles.isEmpty()) {
                    footerMap.put("style", paraStyles);
                }

                // 检测页脚中是否包含页码
                footerMap.put("has_page_number", hasPageNumber(footer));

                footers.add(footerMap);
            }
        }

        return footers;
    }

    /**
     * 检测段落中是否包含分页符
     * 分页符可能通过 sectPr (节属性) 或 run 中的 pageBreak 来标记
     */
    private boolean hasPageBreak(XWPFParagraph paragraph) {
        CTP ctp = paragraph.getCTP();

        // 检查段落属性中的 sectPr (节分隔符)
        if (ctp.getPPr() != null && ctp.getPPr().getSectPr() != null) {
            CTSectPr sectPr = ctp.getPPr().getSectPr();
            if (sectPr.getType() != null) {
                return true;
            }
            // sectPr 存在即表示有节分隔
            return true;
        }

        // 检查 run 中的分页符
        for (XWPFRun run : paragraph.getRuns()) {
            for (var br : run.getCTR().getBrList()) {
                if (br.getType() != null && "page".equals(br.getType().toString())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取段落的标题级别 (1-6)，如果不是标题则返回 0
     */
    private int getHeadingLevel(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyle();
        if (styleId != null) {
            int levelFromStyle = getLevelFromStyleId(styleId);
            if (levelFromStyle > 0) {
                return levelFromStyle;
            }
        }

        return getLevelFromDirectFormat(paragraph);
    }

    private int getLevelFromStyleId(String styleId) {
        return switch (styleId) {
            case "Heading1", "heading1", "1" -> 1;
            case "Heading2", "heading2", "2" -> 2;
            case "Heading3", "heading3", "3" -> 3;
            case "Heading4", "heading4", "4" -> 4;
            case "Heading5", "heading5", "5" -> 5;
            case "Heading6", "heading6", "6" -> 6;
            default -> {
                String lower = styleId.toLowerCase();
                if (lower.startsWith("heading") && lower.length() > 7) {
                    try {
                        int level = Integer.parseInt(lower.substring(7));
                        if (level >= 1 && level <= 6) {
                            yield level;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                yield 0;
            }
        };
    }

    private int getLevelFromDirectFormat(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return 0;
        }

        String fontFamily = null;
        boolean bold = false;
        int fontSize = 0;

        for (XWPFRun run : runs) {
            String runFont = run.getFontFamily();
            if (runFont != null) {
                fontFamily = runFont;
            }
            if (run.isBold()) {
                bold = true;
            }
            if (run.getFontSizeAsDouble() != null) {
                fontSize = run.getFontSizeAsDouble().intValue();
            }
        }

        boolean isSimHei = fontFamily != null
                && ("SimHei".equalsIgnoreCase(fontFamily) || "黑体".equals(fontFamily));
        if (isSimHei && bold && fontSize >= 14) {
            if (fontSize >= 16) {
                return 1;
            }
            return 2;
        }

        return 0;
    }

    /**
     * 提取段落中的超链接
     */
    private void extractHyperlinks(XWPFParagraph paragraph, int paragraphIndex,
            XWPFDocument document, List<Map<String, Object>> hyperlinks) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run instanceof XWPFHyperlinkRun hyperlinkRun) {
                String linkText = hyperlinkRun.text();
                String url = null;
                XWPFHyperlink hyperlink = hyperlinkRun.getHyperlink(document);
                if (hyperlink != null) {
                    url = hyperlink.getURL();
                }
                if (url == null) {
                    String rId = hyperlinkRun.getHyperlinkId();
                    if (rId != null) {
                        url = resolveRelationshipUrl(document, rId);
                    }
                }

                if (linkText != null && !linkText.isBlank()) {
                    Map<String, Object> linkMap = new LinkedHashMap<>();
                    linkMap.put("paragraph_index", paragraphIndex);
                    linkMap.put("text", linkText);
                    linkMap.put("url", url != null ? url : "");
                    hyperlinks.add(linkMap);
                }
            }
        }

        CTP ctp = paragraph.getCTP();
        for (var ctHyperlink : ctp.getHyperlinkList()) {
            String rId = ctHyperlink.getId();
            String url = null;
            if (rId != null) {
                url = resolveRelationshipUrl(document, rId);
            }
            if (url == null) {
                url = ctHyperlink.getAnchor();
            }

            StringBuilder linkText = new StringBuilder();
            for (var r : ctHyperlink.getRList()) {
                for (var t : r.getTList()) {
                    linkText.append(t.getStringValue());
                }
            }

            String text = linkText.toString().trim();
            if (!text.isEmpty()) {
                Map<String, Object> linkMap = new LinkedHashMap<>();
                linkMap.put("paragraph_index", paragraphIndex);
                linkMap.put("text", text);
                linkMap.put("url", url != null ? url : "");
                hyperlinks.add(linkMap);
            }
        }
    }

    private String resolveRelationshipUrl(XWPFDocument document, String rId) {
        try {
            var rel = document.getPackagePart().getRelationship(rId);
            if (rel != null && rel.getTargetURI() != null) {
                return rel.getTargetURI().toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 检测页脚中是否包含页码字段
     */
    private boolean hasPageNumber(XWPFFooter footer) {
        for (XWPFParagraph para : footer.getParagraphs()) {
            CTP ctp = para.getCTP();
            // 检查 fldSimple (简单域，如页码)
            for (var fldSimple : ctp.getFldSimpleList()) {
                String instr = fldSimple.getInstr();
                if (instr != null && instr.contains("PAGE")) {
                    return true;
                }
            }
            // 检查 run 中的 fldChar (复杂域)
            for (var run : ctp.getRList()) {
                for (var fldChar : run.getFldCharList()) {
                    // 如果有 fldChar，检查同段落中是否有 PAGE 指令
                    for (var r2 : ctp.getRList()) {
                        for (var instrText : r2.getInstrTextList()) {
                            String instr = instrText.getStringValue();
                            if (instr != null && instr.contains("PAGE")) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
