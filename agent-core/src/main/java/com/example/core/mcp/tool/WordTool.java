package com.example.core.mcp.tool;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.example.core.mcp.McpTool;
import com.example.core.mcp.McpToolParamParser;
import com.example.core.mcp.McpToolResult;
import com.example.core.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WordTool implements McpTool {

    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "word";
    }

    @Override
    public String getDescription() {
        return "Word文档操作工具。支持创建/修改Word文档，通过actions数组指定多个操作。"
                + "支持段落(添加/插入/删除/样式)、文本(替换/插入/删除/样式)、"
                + "表格(添加/修改/删除/合并/边框/列宽/行高)、图片、页眉页脚、分页分节、超链接、"
                + "水印、书签、脚注、批注、目录、页面设置、文档属性等。"
                + "支持save_as参数另存为新文件名（模板场景）";
    }

    @Override
    public boolean supportsBatch() {
        return true;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("actions",
                "操作数组(必填)，每个元素包含action和对应参数。create必须出现在第一个。"
                        + "示例:[{\"action\":\"create\",\"file_name\":\"a.docx\"},{\"action\":\"add_paragraph\",\"text\":\"内容\"}]。"
                        + "也可以只传单个action(不用actions数组): {\"action\":\"add_paragraph\",\"text\":\"内容\"}");
        schema.put("file_key",
                "文件的MinIO存储路径(格式如users/1/sessions/xxx/abc.docx)。修改已有文件时必填，创建新文件时不需传");
        schema.put("file_name", "文件名(创建新文件时必填，如'周报.docx')。只在create操作中传入");
        schema.put("save_as", "另存为文件名(可选)。模板场景必须传入，避免原文件消失");
        return schema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public McpToolResult execute(Map<String, Object> params) {
        Long userId = McpToolParamParser.getLong(params, "user_id");
        String sessionId = McpToolParamParser.getString(params, "session_id");
        if (userId == null)
            return McpToolResult.error("缺少必填参数: user_id");

        List<Map<String, Object>> actions = resolveActionsParam(params);
        if (actions.isEmpty())
            return McpToolResult.error("缺少actions参数或为空");

        String fileKey = McpToolParamParser.getString(params, "file_key");
        String saveAs = McpToolParamParser.getString(params, "save_as");
        String firstAction = McpToolParamParser.getString(actions.get(0), "action");
        boolean isCreate = "create".equals(firstAction);

        XWPFDocument doc;
        try {
            if (isCreate) {
                doc = new XWPFDocument();
            } else {
                String resolvedFileKey = resolveFileKey(params, actions);
                if (resolvedFileKey == null || resolvedFileKey.isBlank())
                    return McpToolResult.error("修改已有文件时需要file_key参数");
                try (InputStream is = fileStorageService.download(resolvedFileKey)) {
                    doc = new XWPFDocument(is);
                }
                fileKey = resolvedFileKey;
            }
        } catch (Exception e) {
            log.error("Word文档加载失败: {}", e.getMessage());
            return McpToolResult.error("加载文档失败: " + e.getMessage());
        }

        List<Map<String, Object>> actionResults = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        for (Map<String, Object> actionParams : actions) {
            String action = McpToolParamParser.getString(actionParams, "action");
            if (action == null || action.isBlank()) {
                Map<String, Object> errResult = new LinkedHashMap<>();
                errResult.put("action", "unknown");
                errResult.put("status", "failed");
                errResult.put("error", "缺少action参数");
                actionResults.add(errResult);
                failCount++;
                continue;
            }
            try {
                McpToolResult result = executeAction(doc, actionParams, action);
                Map<String, Object> actionResult = new LinkedHashMap<>();
                actionResult.put("action", action);
                actionResult.put("status", result.isSuccess() ? "success" : "failed");
                if (!result.isSuccess())
                    actionResult.put("error", result.getMessage());
                if (result.getData() != null && !result.getData().isEmpty())
                    actionResult.putAll(result.getData());
                actionResults.add(actionResult);
                if (result.isSuccess())
                    successCount++;
                else
                    failCount++;
            } catch (Exception e) {
                log.error("Word操作失败: action={}, error={}", action, e.getMessage());
                Map<String, Object> actionResult = new LinkedHashMap<>();
                actionResult.put("action", action);
                actionResult.put("status", "failed");
                actionResult.put("error", e.getMessage());
                actionResults.add(actionResult);
                failCount++;
            }
        }

        try {
            String fileName = resolveFileName(params, actions, isCreate);
            if (saveAs != null && !saveAs.isBlank())
                fileName = saveAs;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            byte[] bytes = baos.toByteArray();
            doc.close();

            String storageKey = fileStorageService.generateUploadKey(userId, sessionId, fileName);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                fileStorageService.upload(storageKey, bais, bytes.length,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("file_key", storageKey);
            data.put("file_name", fileName);
            data.put("file_size", bytes.length);
            data.put("file_type", "docx");
            if (fileKey != null && !fileKey.isBlank()) {
                data.put("original_file_key", fileKey);
                data.put("is_modify", true);
            }
            if (saveAs != null && !saveAs.isBlank()) {
                data.put("is_template_derived", true);
            }
            data.put("create_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            data.put("actions", actionResults);
            String batchMessage = String.format("Word操作完成(%d个操作: %d成功, %d失败)。file_key: %s",
                    actions.size(), successCount, failCount, storageKey);
            data.put("message", batchMessage);

            return McpToolResult.success(batchMessage, data);
        } catch (Exception e) {
            log.error("Word文档保存失败: {}", e.getMessage());
            return McpToolResult.error("保存文档失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveActionsParam(Map<String, Object> params) {
        Object actionsObj = params.get("actions");
        if (actionsObj instanceof List) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : (List<?>) actionsObj) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            if (!result.isEmpty())
                return result;
        }

        String singleAction = McpToolParamParser.getString(params, "action");
        if (singleAction != null && !singleAction.isBlank()) {
            Map<String, Object> singleMap = new LinkedHashMap<>(params);
            return List.of(singleMap);
        }

        return List.of();
    }

    private String resolveFileKey(Map<String, Object> params, List<Map<String, Object>> actions) {
        String fileKey = McpToolParamParser.getString(params, "file_key");
        if (fileKey != null && !fileKey.isBlank())
            return fileKey;
        if (!actions.isEmpty()) {
            String fk = McpToolParamParser.getString(actions.get(0), "file_key");
            if (fk != null && !fk.isBlank())
                return fk;
        }
        return null;
    }

    private String resolveFileName(Map<String, Object> params, List<Map<String, Object>> actions, boolean isCreate) {
        String fileName = McpToolParamParser.getString(params, "file_name");
        if (fileName != null && !fileName.isBlank())
            return fileName;
        if (isCreate && !actions.isEmpty()) {
            String fn = McpToolParamParser.getString(actions.get(0), "file_name");
            if (fn != null && !fn.isBlank())
                return fn;
        }
        String fileKey = McpToolParamParser.getString(params, "file_key");
        if (fileKey != null && !fileKey.isBlank())
            return fileKey.substring(fileKey.lastIndexOf('/') + 1);
        return "document.docx";
    }

    @SuppressWarnings("unchecked")
    private McpToolResult executeAction(XWPFDocument doc, Map<String, Object> params, String action) throws Exception {
        switch (action) {
            case "create":
                return McpToolResult.success("Word文档创建成功，后续操作无需传file_key，系统会自动传递文档", Map.of(
                        "paragraph_count", doc.getParagraphs().size(),
                        "table_count", doc.getTables().size(),
                        "hint", "批量操作中后续modify操作不需要file_key参数"));
            case "set_page_layout":
                return setPageLayout(doc, params);
            case "add_paragraph":
                return addParagraph(doc, params);
            case "insert_paragraph":
                return insertParagraph(doc, params);
            case "delete_paragraph":
                return deleteParagraph(doc, params);
            case "set_paragraph_style":
                return setParagraphStyle(doc, params);
            case "replace_text":
                return replaceText(doc, params);
            case "insert_text":
                return insertText(doc, params);
            case "delete_text":
                return deleteText(doc, params);
            case "set_text_style":
                return setTextStyle(doc, params);
            case "add_table":
                return addTable(doc, params);
            case "set_cell_value":
                return setCellValue(doc, params);
            case "set_cell_style":
                return setCellStyle(doc, params);
            case "insert_table_row":
                return insertTableRow(doc, params);
            case "insert_table_col":
                return insertTableCol(doc, params);
            case "delete_table_row":
                return deleteTableRow(doc, params);
            case "delete_table_col":
                return deleteTableCol(doc, params);
            case "delete_table":
                return deleteTable(doc, params);
            case "merge_cells":
                return mergeCells(doc, params);
            case "set_table_borders":
                return setTableBorders(doc, params);
            case "set_column_width":
                return setColumnWidth(doc, params);
            case "set_row_height":
                return setRowHeight(doc, params);
            case "set_table_width":
                return setTableWidth(doc, params);
            case "set_table_alignment":
                return setTableAlignment(doc, params);
            case "add_image":
                return addImage(doc, params);
            case "set_header":
                return setHeader(doc, params);
            case "set_footer":
                return setFooter(doc, params);
            case "set_page_number":
                return setPageNumber(doc, params);
            case "insert_page_break":
                return insertPageBreak(doc, params);
            case "insert_section_break":
                return insertSectionBreak(doc, params);
            case "add_hyperlink":
                return addHyperlink(doc, params);
            case "set_watermark":
                return setWatermark(doc, params);
            case "add_bookmark":
                return addBookmark(doc, params);
            case "add_footnote":
                return addFootnote(doc, params);
            case "add_comment":
                return addComment(doc, params);
            case "insert_toc":
                return insertToc(doc, params);
            case "set_document_property":
                return setDocumentProperty(doc, params);
            default:
                return McpToolResult.error("不支持的操作: " + action);
        }
    }

    // ==================== 页面设置 ====================

    private McpToolResult setPageLayout(XWPFDocument doc, Map<String, Object> params) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        Double topMargin = McpToolParamParser.getDouble(params, "top_margin");
        Double bottomMargin = McpToolParamParser.getDouble(params, "bottom_margin");
        Double leftMargin = McpToolParamParser.getDouble(params, "left_margin");
        Double rightMargin = McpToolParamParser.getDouble(params, "right_margin");
        String orientation = McpToolParamParser.getString(params, "orientation", "portrait");
        String paperSize = McpToolParamParser.getString(params, "paper_size", "A4");

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar pageMar = sectPr.addNewPgMar();
        if (topMargin != null)
            pageMar.setTop(BigInteger.valueOf((long) (topMargin * 567)));
        if (bottomMargin != null)
            pageMar.setBottom(BigInteger.valueOf((long) (bottomMargin * 567)));
        if (leftMargin != null)
            pageMar.setLeft(BigInteger.valueOf((long) (leftMargin * 567)));
        if (rightMargin != null)
            pageMar.setRight(BigInteger.valueOf((long) (rightMargin * 567)));

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz pageSz = sectPr.addNewPgSz();
        if ("landscape".equals(orientation)) {
            pageSz.setOrient(org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation.LANDSCAPE);
        }
        switch (paperSize.toUpperCase()) {
            case "A3" -> {
                pageSz.setW(BigInteger.valueOf(16838));
                pageSz.setH(BigInteger.valueOf(11906));
            }
            case "LETTER" -> {
                pageSz.setW(BigInteger.valueOf(12240));
                pageSz.setH(BigInteger.valueOf(15840));
            }
            case "B5" -> {
                pageSz.setW(BigInteger.valueOf(10318));
                pageSz.setH(BigInteger.valueOf(14570));
            }
            case "16K" -> {
                pageSz.setW(BigInteger.valueOf(9742));
                pageSz.setH(BigInteger.valueOf(13653));
            }
            default -> {
                pageSz.setW(BigInteger.valueOf(11906));
                pageSz.setH(BigInteger.valueOf(16838));
            }
        }

        return McpToolResult.success("页面设置完成");
    }

    // ==================== 段落操作 ====================

    @SuppressWarnings("unchecked")
    private McpToolResult addParagraph(XWPFDocument doc, Map<String, Object> params) {
        String text = resolveTextParam(params);
        Map<String, Object> style = parseStyleParam(params);

        XWPFParagraph para = doc.createParagraph();
        applyParagraphFullStyle(para, style);
        XWPFRun run = para.createRun();
        if (!text.isBlank())
            run.setText(text);
        WordStyleHelper.applyRunStyle(run, style);

        return McpToolResult.success("段落添加成功", Map.of(
                "paragraph_index", doc.getParagraphs().size() - 1));
    }

    @SuppressWarnings("unchecked")
    private McpToolResult insertParagraph(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        String text = resolveTextParam(params);
        String position = McpToolParamParser.getString(params, "position", "after");
        Map<String, Object> style = parseStyleParam(params);

        if (paraIdx == null)
            return McpToolResult.error("insert_paragraph需要paragraph_index参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);

        XWPFParagraph refPara = doc.getParagraphs().get(paraIdx);
        XWPFParagraph newPara;
        if ("before".equals(position)) {
            org.apache.xmlbeans.XmlCursor cursor = refPara.getCTP().newCursor();
            newPara = doc.insertNewParagraph(cursor);
            cursor.dispose();
        } else {
            newPara = insertParagraphAfter(doc, refPara);
        }

        applyParagraphFullStyle(newPara, style);
        XWPFRun run = newPara.createRun();
        if (!text.isBlank())
            run.setText(text);
        WordStyleHelper.applyRunStyle(run, style);

        return McpToolResult.success("段落插入成功");
    }

    private McpToolResult deleteParagraph(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        if (paraIdx == null)
            return McpToolResult.error("delete_paragraph需要paragraph_index参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);
        doc.getParagraphs().get(paraIdx).getCTP().newCursor().removeXml();
        return McpToolResult.success("段落删除成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult setParagraphStyle(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        if (paraIdx == null)
            return McpToolResult.error("set_paragraph_style需要paragraph_index参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);
        Map<String, Object> style = parseStyleParam(params);
        XWPFParagraph para = doc.getParagraphs().get(paraIdx);
        applyParagraphFullStyle(para, style);
        return McpToolResult.success("段落样式设置成功");
    }

    @SuppressWarnings("unchecked")
    private void applyParagraphFullStyle(XWPFParagraph para, Map<String, Object> style) {
        if (style == null)
            return;

        if (style.containsKey("heading_level")) {
            int level = toInt(style.get("heading_level"), 1);
            if (level >= 1 && level <= 6) {
                para.setStyle("Heading" + level);
            }
        }

        if (style.containsKey("page_break_before") && toBool(style.get("page_break_before"))) {
            para.setPageBreak(true);
        }

        if (style.containsKey("keep_with_next") && toBool(style.get("keep_with_next"))) {
            para.setKeepNext(true);
        }

        if (style.containsKey("keep_lines_together") && toBool(style.get("keep_lines_together"))) {
            para.getCTP().getPPr().addNewKeepLines()
                    .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STOnOff.TRUE);
        }

        WordStyleHelper.applyParagraphStyle(para, style);
        WordStyleHelper.clearNumberingProperties(para);

        if (style.containsKey("list_type")) {
            String listType = String.valueOf(style.get("list_type")).toLowerCase();
            int listLevel = style.containsKey("list_level") ? toInt(style.get("list_level"), 0) : 0;
            if ("ordered".equals(listType)) {
                para.setNumID(BigInteger.valueOf(1));
            } else if ("unordered".equals(listType)) {
                para.setNumID(BigInteger.valueOf(2));
            }
        }
    }

    // ==================== 文本操作 ====================

    private McpToolResult replaceText(XWPFDocument doc, Map<String, Object> params) {
        String oldText = McpToolParamParser.getString(params, "old_text", "");
        String newText = McpToolParamParser.getString(params, "new_text", "");
        boolean fuzzyMatch = McpToolParamParser.getBool(params, "fuzzy_match", false);
        if (oldText.isBlank())
            return McpToolResult.error("replace_text需要old_text参数");

        int replaceCount = 0;
        String searchOld = fuzzyMatch ? normalizeForFuzzyMatch(oldText) : oldText;

        for (XWPFParagraph para : doc.getParagraphs()) {
            replaceCount += replaceInParagraph(para, oldText, newText, searchOld, fuzzyMatch);
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        replaceCount += replaceInParagraph(para, oldText, newText, searchOld, fuzzyMatch);
                    }
                }
            }
        }

        if (replaceCount == 0) {
            List<String> suggestions = findSimilarTexts(doc, oldText);
            StringBuilder errorMsg = new StringBuilder("未找到要替换的文本: \"").append(oldText).append("\"");
            if (!suggestions.isEmpty()) {
                errorMsg.append("。文档中包含相似文本：");
                for (int i = 0; i < Math.min(suggestions.size(), 3); i++) {
                    errorMsg.append("\n  - \"").append(suggestions.get(i)).append("\"");
                }
                errorMsg.append("\n提示：可使用fuzzy_match=true进行模糊匹配");
            }
            return McpToolResult.error(errorMsg.toString());
        }
        return McpToolResult.success("文本替换成功", Map.of("replace_count", replaceCount));
    }

    private McpToolResult insertText(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        Integer runIdx = McpToolParamParser.getInteger(params, "run_index");
        String value = resolveTextParam(params);
        String pos = McpToolParamParser.getString(params, "position", "after");
        if (paraIdx == null)
            return McpToolResult.error("insert_text需要paragraph_index参数");
        if (runIdx == null)
            return McpToolResult.error("insert_text需要run_index参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);
        XWPFParagraph para = doc.getParagraphs().get(paraIdx);
        if (runIdx < 0 || runIdx >= para.getRuns().size())
            return McpToolResult.error("Run索引越界: " + runIdx);
        XWPFRun run = para.getRuns().get(runIdx);
        String existing = run.getText(0);
        if (existing == null)
            existing = "";
        switch (pos.toLowerCase()) {
            case "before" -> run.setText(value + existing, 0);
            case "replace" -> run.setText(value, 0);
            default -> run.setText(existing + value, 0);
        }
        return McpToolResult.success("文本插入成功");
    }

    private McpToolResult deleteText(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        Integer runIdx = McpToolParamParser.getInteger(params, "run_index");
        Integer startOffset = McpToolParamParser.getInteger(params, "start_offset");
        Integer endOffset = McpToolParamParser.getInteger(params, "end_offset");
        if (paraIdx == null || runIdx == null)
            return McpToolResult.error("delete_text需要paragraph_index和run_index参数");
        if (startOffset == null || endOffset == null)
            return McpToolResult.error("delete_text需要start_offset和end_offset参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);
        XWPFParagraph para = doc.getParagraphs().get(paraIdx);
        if (runIdx < 0 || runIdx >= para.getRuns().size())
            return McpToolResult.error("Run索引越界: " + runIdx);
        XWPFRun run = para.getRuns().get(runIdx);
        String existing = run.getText(0);
        if (existing == null)
            return McpToolResult.error("指定Run没有文本内容");
        if (startOffset < 0 || endOffset > existing.length() || startOffset >= endOffset)
            return McpToolResult.error("偏移量无效");
        run.setText(existing.substring(0, startOffset) + existing.substring(endOffset), 0);
        return McpToolResult.success("文本删除成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult setTextStyle(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        Integer runIdx = McpToolParamParser.getInteger(params, "run_index");
        if (paraIdx == null || runIdx == null)
            return McpToolResult.error("set_text_style需要paragraph_index和run_index参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);
        XWPFParagraph para = doc.getParagraphs().get(paraIdx);
        if (runIdx < 0 || runIdx >= para.getRuns().size())
            return McpToolResult.error("Run索引越界: " + runIdx);
        Map<String, Object> style = parseStyleParam(params);
        WordStyleHelper.applyRunStyle(para.getRuns().get(runIdx), style);
        return McpToolResult.success("文本样式设置成功");
    }

    // ==================== 表格操作 ====================

    @SuppressWarnings("unchecked")
    private McpToolResult addTable(XWPFDocument doc, Map<String, Object> params) {
        Integer rows = McpToolParamParser.getInteger(params, "rows");
        Integer cols = McpToolParamParser.getInteger(params, "cols");
        if (rows == null || cols == null)
            return McpToolResult.error("add_table需要rows和cols参数");
        if (rows < 1 || cols < 1)
            return McpToolResult.error("表格行列数必须大于0");

        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        XWPFTable table;
        if (paraIdx != null) {
            if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
                return McpToolResult.error("段落索引越界: " + paraIdx);
            XWPFParagraph refPara = doc.getParagraphs().get(paraIdx);
            org.apache.xmlbeans.XmlCursor cursor = refPara.getCTP().newCursor();
            cursor.toEndToken();
            cursor.toNextToken();
            table = doc.insertNewTbl(cursor);
            cursor.dispose();
            for (int r = 0; r < rows; r++) {
                XWPFTableRow row = r < table.getNumberOfRows() ? table.getRow(r) : table.createRow();
                while (row.getTableCells().size() < cols)
                    row.addNewTableCell();
            }
        } else {
            table = doc.createTable(rows, cols);
        }

        Object dataRaw = params.get("data");
        if (dataRaw instanceof List) {
            List<List<Object>> tableData = (List<List<Object>>) dataRaw;
            for (int r = 0; r < Math.min(tableData.size(), rows); r++) {
                List<Object> rowData = tableData.get(r);
                XWPFTableRow row = table.getRow(r);
                if (row == null)
                    row = table.createRow();
                for (int c = 0; c < Math.min(rowData.size(), cols); c++) {
                    XWPFTableCell cell = row.getCell(c);
                    if (cell == null)
                        cell = row.addNewTableCell();
                    XWPFParagraph cellPara = cell.getParagraphs().get(0);
                    if (cellPara == null)
                        cellPara = cell.addParagraph();
                    clearParagraph(cellPara);
                    XWPFRun cellRun = cellPara.createRun();
                    cellRun.setText(String.valueOf(rowData.get(c)));
                }
            }
        }

        Map<String, Object> style = parseStyleParam(params);
        applyTableStyle(table, style);

        return McpToolResult.success("表格添加成功", Map.of(
                "table_index", doc.getTables().size() - 1));
    }

    @SuppressWarnings("unchecked")
    private McpToolResult setCellValue(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer rowIdx = McpToolParamParser.getInteger(params, "row_index");
        Integer colIdx = McpToolParamParser.getInteger(params, "col_index");
        if (tableIdx == null || rowIdx == null || colIdx == null)
            return McpToolResult.error("set_cell_value需要table_index, row_index, col_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        if (rowIdx < 0 || rowIdx >= table.getNumberOfRows())
            return McpToolResult.error("行索引越界: " + rowIdx);
        XWPFTableRow row = table.getRow(rowIdx);
        if (colIdx < 0 || colIdx >= row.getTableCells().size())
            return McpToolResult.error("列索引越界: " + colIdx);
        XWPFTableCell cell = row.getCell(colIdx);
        String value = resolveTextParam(params);
        clearCellContent(cell);
        XWPFParagraph cellPara = cell.getParagraphs().get(0);
        XWPFRun cellRun = cellPara.createRun();
        cellRun.setText(value);
        Map<String, Object> style = parseStyleParam(params);
        if (style != null && !style.isEmpty()) {
            WordStyleHelper.applyRunStyle(cellRun, style);
            WordStyleHelper.applyParagraphStyle(cellPara, style);
        }
        return McpToolResult.success("单元格值设置成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult setCellStyle(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer rowIdx = McpToolParamParser.getInteger(params, "row_index");
        Integer colIdx = McpToolParamParser.getInteger(params, "col_index");
        if (tableIdx == null || rowIdx == null || colIdx == null)
            return McpToolResult.error("set_cell_style需要table_index, row_index, col_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        if (rowIdx < 0 || rowIdx >= table.getNumberOfRows())
            return McpToolResult.error("行索引越界: " + rowIdx);
        XWPFTableRow row = table.getRow(rowIdx);
        if (colIdx < 0 || colIdx >= row.getTableCells().size())
            return McpToolResult.error("列索引越界: " + colIdx);
        XWPFTableCell cell = row.getCell(colIdx);
        Map<String, Object> style = parseStyleParam(params);
        if (style.containsKey("background_color")) {
            String bgColor = String.valueOf(style.get("background_color")).replace("#", "");
            cell.setColor(bgColor);
        }
        if (style.containsKey("vertical_alignment")) {
            String vAlign = String.valueOf(style.get("vertical_alignment")).toUpperCase();
            cell.setVerticalAlignment(switch (vAlign) {
                case "TOP" -> XWPFTableCell.XWPFVertAlign.TOP;
                case "BOTTOM" -> XWPFTableCell.XWPFVertAlign.BOTTOM;
                default -> XWPFTableCell.XWPFVertAlign.CENTER;
            });
        }
        for (XWPFParagraph para : cell.getParagraphs()) {
            WordStyleHelper.applyParagraphStyle(para, style);
            for (XWPFRun run : para.getRuns()) {
                WordStyleHelper.applyRunStyle(run, style);
            }
        }
        return McpToolResult.success("单元格样式设置成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult insertTableRow(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer rowIdx = McpToolParamParser.getInteger(params, "row_index");
        if (tableIdx == null)
            return McpToolResult.error("insert_table_row需要table_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        int insertAt = (rowIdx != null) ? rowIdx : table.getNumberOfRows();
        XWPFTableRow newRow = table.insertNewTableRow(insertAt);
        int colCount = table.getRow(0) != null ? table.getRow(0).getTableCells().size() : 1;
        for (int c = 0; c < colCount; c++)
            newRow.addNewTableCell();
        Object dataRaw = params.get("data");
        if (dataRaw instanceof List) {
            List<Object> rowData = (List<Object>) dataRaw;
            for (int c = 0; c < Math.min(rowData.size(), colCount); c++) {
                XWPFTableCell cell = newRow.getCell(c);
                XWPFParagraph cellPara = cell.getParagraphs().get(0);
                clearParagraph(cellPara);
                XWPFRun cellRun = cellPara.createRun();
                cellRun.setText(String.valueOf(rowData.get(c)));
            }
        }
        return McpToolResult.success("行插入成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult insertTableCol(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer colIdx = McpToolParamParser.getInteger(params, "col_index");
        if (tableIdx == null || colIdx == null)
            return McpToolResult.error("insert_table_col需要table_index和col_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        for (int r = 0; r < table.getNumberOfRows(); r++) {
            XWPFTableRow row = table.getRow(r);
            row.addNewTableCell();
        }
        Object dataRaw = params.get("data");
        if (dataRaw instanceof List) {
            List<Object> colData = (List<Object>) dataRaw;
            for (int r = 0; r < Math.min(colData.size(), table.getNumberOfRows()); r++) {
                XWPFTableRow row = table.getRow(r);
                if (colIdx < row.getTableCells().size()) {
                    XWPFTableCell cell = row.getCell(colIdx);
                    XWPFParagraph cellPara = cell.getParagraphs().get(0);
                    clearParagraph(cellPara);
                    XWPFRun cellRun = cellPara.createRun();
                    cellRun.setText(String.valueOf(colData.get(r)));
                }
            }
        }
        return McpToolResult.success("列插入成功");
    }

    private McpToolResult deleteTableRow(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer rowIdx = McpToolParamParser.getInteger(params, "row_index");
        if (tableIdx == null || rowIdx == null)
            return McpToolResult.error("delete_table_row需要table_index和row_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        if (rowIdx < 0 || rowIdx >= table.getNumberOfRows())
            return McpToolResult.error("行索引越界: " + rowIdx);
        table.removeRow(rowIdx);
        return McpToolResult.success("行删除成功");
    }

    private McpToolResult deleteTableCol(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer colIdx = McpToolParamParser.getInteger(params, "col_index");
        if (tableIdx == null || colIdx == null)
            return McpToolResult.error("delete_table_col需要table_index和col_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        for (int r = 0; r < table.getNumberOfRows(); r++) {
            XWPFTableRow row = table.getRow(r);
            if (colIdx < row.getTableCells().size())
                row.getCtRow().removeTc(colIdx);
        }
        return McpToolResult.success("列删除成功");
    }

    private McpToolResult deleteTable(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        if (tableIdx == null)
            return McpToolResult.error("delete_table需要table_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        doc.getTables().get(tableIdx).getCTTbl().newCursor().removeXml();
        return McpToolResult.success("表格删除成功");
    }

    private McpToolResult mergeCells(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col");
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (tableIdx == null || startRow == null || endRow == null || startCol == null || endCol == null)
            return McpToolResult.error("merge_cells需要table_index, start_row, end_row, start_col, end_col参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        table.getRow(startRow).getCell(startCol).getCTTc().addNewTcPr().addNewHMerge()
                .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART);
        for (int c = startCol + 1; c <= endCol; c++) {
            table.getRow(startRow).getCell(c).getCTTc().addNewTcPr().addNewHMerge()
                    .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE);
        }
        if (endRow > startRow) {
            table.getRow(startRow).getCell(startCol).getCTTc().addNewTcPr().addNewVMerge()
                    .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART);
            for (int r = startRow + 1; r <= endRow; r++) {
                table.getRow(r).getCell(startCol).getCTTc().addNewTcPr().addNewVMerge()
                        .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE);
            }
        }
        return McpToolResult.success("单元格合并成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult setTableBorders(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        if (tableIdx == null)
            return McpToolResult.error("set_table_borders需要table_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        Object bordersRaw = params.get("borders");
        if (bordersRaw == null)
            return McpToolResult.error("set_table_borders需要borders参数");
        if (bordersRaw instanceof Map) {
            Map<String, Object> borders = (Map<String, Object>) bordersRaw;
            if (borders.containsKey("preset") && "three_line".equals(borders.get("preset"))) {
                applyThreeLineBorders(table, borders);
            } else {
                applyCustomBorders(table, borders);
            }
        } else {
            return McpToolResult.error("borders参数必须是JSON对象");
        }
        return McpToolResult.success("表格边框设置成功");
    }

    private McpToolResult setColumnWidth(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer colIdx = McpToolParamParser.getInteger(params, "col_index");
        Double widthCm = McpToolParamParser.getDouble(params, "width");
        if (tableIdx == null || colIdx == null || widthCm == null)
            return McpToolResult.error("set_column_width需要table_index, col_index, width参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        int twips = (int) (widthCm * 567);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null)
            grid = table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() <= colIdx)
            grid.addNewGridCol();
        grid.getGridColArray(colIdx).setW(BigInteger.valueOf(twips));
        return McpToolResult.success("列宽设置成功");
    }

    private McpToolResult setRowHeight(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Integer rowIdx = McpToolParamParser.getInteger(params, "row_index");
        Double heightPt = McpToolParamParser.getDouble(params, "height");
        if (tableIdx == null || rowIdx == null || heightPt == null)
            return McpToolResult.error("set_row_height需要table_index, row_index, height参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        if (rowIdx < 0 || rowIdx >= table.getNumberOfRows())
            return McpToolResult.error("行索引越界: " + rowIdx);
        table.getRow(rowIdx).setHeight((int) (heightPt * 20));
        return McpToolResult.success("行高设置成功");
    }

    private McpToolResult setTableWidth(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        Double widthCm = McpToolParamParser.getDouble(params, "width");
        if (tableIdx == null || widthCm == null)
            return McpToolResult.error("set_table_width需要table_index和width参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        int totalWidthTwips = (int) (widthCm * 567);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null)
            tblPr = table.getCTTbl().addNewTblPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth tblWidth = tblPr.isSetTblW()
                ? tblPr.getTblW()
                : tblPr.addNewTblW();
        tblWidth.setW(BigInteger.valueOf(totalWidthTwips));
        tblWidth.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth.DXA);
        return McpToolResult.success("表格宽度设置成功");
    }

    private McpToolResult setTableAlignment(XWPFDocument doc, Map<String, Object> params) {
        Integer tableIdx = McpToolParamParser.getInteger(params, "table_index");
        String align = McpToolParamParser.getString(params, "alignment", "center");
        if (tableIdx == null)
            return McpToolResult.error("set_table_alignment需要table_index参数");
        if (tableIdx < 0 || tableIdx >= doc.getTables().size())
            return McpToolResult.error("表格索引越界: " + tableIdx);
        XWPFTable table = doc.getTables().get(tableIdx);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null)
            tblPr = table.getCTTbl().addNewTblPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJcTable jc = tblPr.isSetJc() ? tblPr.getJc()
                : tblPr.addNewJc();
        jc.setVal(switch (align.toLowerCase()) {
            case "left" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STJcTable.LEFT;
            case "right" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STJcTable.RIGHT;
            default -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STJcTable.CENTER;
        });
        return McpToolResult.success("表格对齐设置成功");
    }

    // ==================== 图片操作 ====================

    private McpToolResult addImage(XWPFDocument doc, Map<String, Object> params) {
        String imageKey = McpToolParamParser.getString(params, "image_key");
        if (imageKey == null || imageKey.isBlank())
            return McpToolResult.error("add_image需要image_key参数");

        Integer widthParam = McpToolParamParser.getInteger(params, "image_width");
        Integer heightParam = McpToolParamParser.getInteger(params, "image_height");
        String caption = McpToolParamParser.getString(params, "image_caption", "");
        Integer paragraphIndex = McpToolParamParser.getInteger(params, "paragraph_index");
        String position = McpToolParamParser.getString(params, "position", "end");
        int maxWidth = McpToolParamParser.getInteger(params, "max_width", 450);
        String alignment = McpToolParamParser.getString(params, "alignment", "center");
        boolean keepAspectRatio = McpToolParamParser.getBool(params, "keep_aspect_ratio", true);

        org.apache.poi.xwpf.usermodel.ParagraphAlignment paraAlignment = switch (alignment) {
            case "left" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT;
            case "right" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT;
            default -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER;
        };

        try (InputStream imageStream = fileStorageService.download(imageKey)) {
            byte[] imageBytes = imageStream.readAllBytes();
            int imageType = guessImageType(imageKey, imageBytes);

            int width, height;
            if (keepAspectRatio) {
                BufferedImage bImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (bImage != null) {
                    int originalWidth = bImage.getWidth();
                    int originalHeight = bImage.getHeight();
                    int targetWidth = (widthParam != null) ? widthParam : maxWidth;
                    if (targetWidth > maxWidth)
                        targetWidth = maxWidth;
                    double scale = (double) targetWidth / originalWidth;
                    width = targetWidth;
                    height = (int) (originalHeight * scale);
                } else {
                    width = (widthParam != null) ? widthParam : maxWidth;
                    height = (heightParam != null) ? heightParam : (int) (width * 0.75);
                    if (width > maxWidth) {
                        double s = (double) maxWidth / width;
                        width = maxWidth;
                        height = (int) (height * s);
                    }
                }
            } else {
                width = (widthParam != null) ? widthParam : 400;
                height = (heightParam != null) ? heightParam : 300;
                if (width > maxWidth) {
                    double s = (double) maxWidth / width;
                    width = maxWidth;
                    height = (int) (height * s);
                }
            }

            XWPFParagraph imagePara;
            if ("before_paragraph".equals(position) && paragraphIndex != null) {
                if (paragraphIndex < 0 || paragraphIndex >= doc.getParagraphs().size())
                    return McpToolResult.error("段落索引越界: " + paragraphIndex);
                if (paragraphIndex == 0) {
                    org.apache.xmlbeans.XmlCursor cursor = doc.getParagraphs().get(0).getCTP().newCursor();
                    imagePara = doc.insertNewParagraph(cursor);
                    cursor.dispose();
                } else {
                    imagePara = insertParagraphAfter(doc, doc.getParagraphs().get(paragraphIndex - 1));
                }
            } else if (("after_paragraph".equals(position)) && paragraphIndex != null) {
                if (paragraphIndex < 0 || paragraphIndex >= doc.getParagraphs().size())
                    return McpToolResult.error("段落索引越界: " + paragraphIndex);
                imagePara = insertParagraphAfter(doc, doc.getParagraphs().get(paragraphIndex));
            } else {
                imagePara = doc.createParagraph();
            }
            imagePara.setAlignment(paraAlignment);
            WordStyleHelper.clearNumberingProperties(imagePara);
            XWPFRun imageRun = imagePara.createRun();
            imageRun.addPicture(new ByteArrayInputStream(imageBytes), imageType,
                    imageKey.substring(imageKey.lastIndexOf('/') + 1),
                    Units.toEMU(width), Units.toEMU(height));

            if (!caption.isBlank()) {
                XWPFParagraph captionPara;
                if (paragraphIndex != null && !"end".equals(position)) {
                    captionPara = insertParagraphAfter(doc, imagePara);
                } else {
                    captionPara = doc.createParagraph();
                }
                captionPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                WordStyleHelper.clearNumberingProperties(captionPara);
                XWPFRun captionRun = captionPara.createRun();
                captionRun.setText(caption);
                captionRun.setFontSize(10);
                captionRun.setItalic(true);
            }
        } catch (Exception e) {
            return McpToolResult.error("插入图片失败: " + e.getMessage());
        }
        return McpToolResult.success("图片添加成功");
    }

    // ==================== 页眉页脚 ====================

    @SuppressWarnings("unchecked")
    private McpToolResult setHeader(XWPFDocument doc, Map<String, Object> params) {
        String text = resolveTextParam(params);
        String headerType = McpToolParamParser.getString(params, "header_type", "default");
        Map<String, Object> style = parseStyleParam(params);
        HeaderFooterType hfType = switch (headerType.toLowerCase()) {
            case "first" -> HeaderFooterType.FIRST;
            case "even" -> HeaderFooterType.EVEN;
            default -> HeaderFooterType.DEFAULT;
        };
        XWPFHeader header = doc.createHeader(hfType);
        XWPFParagraph para = header.createParagraph();
        WordStyleHelper.applyParagraphStyle(para, style);
        WordStyleHelper.clearNumberingProperties(para);
        XWPFRun run = para.createRun();
        run.setText(text);
        WordStyleHelper.applyRunStyle(run, style);
        return McpToolResult.success("页眉设置成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult setFooter(XWPFDocument doc, Map<String, Object> params) {
        String text = resolveTextParam(params);
        boolean includePageNumber = McpToolParamParser.getBool(params, "include_page_number", false);
        String footerType = McpToolParamParser.getString(params, "footer_type", "default");
        Map<String, Object> style = parseStyleParam(params);
        HeaderFooterType hfType = switch (footerType.toLowerCase()) {
            case "first" -> HeaderFooterType.FIRST;
            case "even" -> HeaderFooterType.EVEN;
            default -> HeaderFooterType.DEFAULT;
        };
        XWPFFooter footer = doc.createFooter(hfType);
        XWPFParagraph para = footer.createParagraph();
        WordStyleHelper.applyParagraphStyle(para, style);
        WordStyleHelper.clearNumberingProperties(para);
        if (includePageNumber) {
            para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            if (!text.isBlank()) {
                XWPFRun textRun = para.createRun();
                textRun.setText(text + "  ");
                WordStyleHelper.applyRunStyle(textRun, style);
            }
            addPageNumberField(para, style);
        } else if (!text.isBlank()) {
            XWPFRun run = para.createRun();
            run.setText(text);
            WordStyleHelper.applyRunStyle(run, style);
        }
        return McpToolResult.success("页脚设置成功");
    }

    private McpToolResult setPageNumber(XWPFDocument doc, Map<String, Object> params) {
        String position = McpToolParamParser.getString(params, "position", "footer");
        String alignment = McpToolParamParser.getString(params, "alignment", "center");
        XWPFParagraph para;
        if ("header".equals(position)) {
            XWPFHeader header = doc.getHeaderList().isEmpty()
                    ? doc.createHeader(HeaderFooterType.DEFAULT)
                    : doc.getHeaderList().get(0);
            para = header.createParagraph();
        } else {
            XWPFFooter footer = doc.getFooterList().isEmpty()
                    ? doc.createFooter(HeaderFooterType.DEFAULT)
                    : doc.getFooterList().get(0);
            para = footer.createParagraph();
        }
        para.setAlignment(switch (alignment.toLowerCase()) {
            case "left" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT;
            case "right" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT;
            default -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER;
        });
        WordStyleHelper.clearNumberingProperties(para);
        addPageNumberField(para, null);
        return McpToolResult.success("页码设置成功");
    }

    // ==================== 分页分节 ====================

    private McpToolResult insertPageBreak(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        if (paraIdx != null) {
            if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
                return McpToolResult.error("段落索引越界: " + paraIdx);
            XWPFParagraph newPara = insertParagraphAfter(doc, doc.getParagraphs().get(paraIdx));
            WordStyleHelper.clearNumberingProperties(newPara);
            newPara.setPageBreak(true);
        } else {
            XWPFParagraph para = doc.createParagraph();
            WordStyleHelper.clearNumberingProperties(para);
            para.setPageBreak(true);
        }
        return McpToolResult.success("分页符插入成功");
    }

    private McpToolResult insertSectionBreak(XWPFDocument doc, Map<String, Object> params) {
        String sectionType = McpToolParamParser.getString(params, "section_type", "next_page");
        XWPFParagraph sectPara = doc.createParagraph();
        WordStyleHelper.clearNumberingProperties(sectPara);
        CTSectPr sectPr = sectPara.getCTP().addNewPPr().addNewSectPr();
        switch (sectionType.toLowerCase()) {
            case "continuous" -> sectPr.addNewType().setVal(STSectionMark.CONTINUOUS);
            case "even_page" -> sectPr.addNewType().setVal(STSectionMark.EVEN_PAGE);
            case "odd_page" -> sectPr.addNewType().setVal(STSectionMark.ODD_PAGE);
            default -> sectPr.addNewType().setVal(STSectionMark.NEXT_PAGE);
        }
        return McpToolResult.success("分节符插入成功");
    }

    // ==================== 超链接 ====================

    private McpToolResult addHyperlink(XWPFDocument doc, Map<String, Object> params) {
        String url = McpToolParamParser.getString(params, "url");
        String text = resolveTextParam(params);
        if (text.isBlank())
            text = url != null ? url : "";
        if (url == null || url.isBlank())
            return McpToolResult.error("add_hyperlink需要url参数");
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        XWPFParagraph para;
        if (paraIdx != null) {
            if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
                return McpToolResult.error("段落索引越界: " + paraIdx);
            para = doc.getParagraphs().get(paraIdx);
        } else {
            para = doc.createParagraph();
            WordStyleHelper.clearNumberingProperties(para);
        }

        PackageRelationship rel = doc.getPackagePart()
                .addExternalRelationship(url, PackageRelationshipTypes.HYPERLINK_PART,
                        "rId" + System.currentTimeMillis());

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink ctHyperlink = para.getCTP()
                .addNewHyperlink();
        ctHyperlink.setId(rel.getId());

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = ctHyperlink.addNewR();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText ctText = ctr.addNewT();
        ctText.setStringValue(text);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rpr = ctr.addNewRPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColor color = rpr.addNewColor();
        color.setVal("0563C1");
        rpr.addNewU().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STUnderline.SINGLE);
        var fonts = rpr.addNewRFonts();
        fonts.setAscii("Times New Roman");
        fonts.setHAnsi("Times New Roman");

        return McpToolResult.success("超链接添加成功");
    }

    // ==================== 高级功能 ====================

    private McpToolResult setWatermark(XWPFDocument doc, Map<String, Object> params) {
        String text = resolveTextParam(params);
        if (text.isBlank())
            return McpToolResult.error("set_watermark需要text参数");
        int fontSize = McpToolParamParser.getInteger(params, "font_size", 40);
        String color = McpToolParamParser.getString(params, "color", "D0D0D0").replace("#", "");

        try {
            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph para = header.createParagraph();
            WordStyleHelper.clearNumberingProperties(para);
            para.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = para.getCTP();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr ppr = ctp.addNewPPr();
            ppr.addNewPStyle().setVal("Header");

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR ctr = ctp.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rpr = ctr.addNewRPr();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText watermarkText = ctr.addNewT();
            watermarkText.setStringValue(text);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColor ctColor = rpr.addNewColor();
            ctColor.setVal(color);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure sz = rpr.addNewSz();
            sz.setVal(BigInteger.valueOf(fontSize * 2));
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure szCs = rpr.addNewSzCs();
            szCs.setVal(BigInteger.valueOf(fontSize * 2));

            return McpToolResult.success("水印设置成功（文本水印，显示在页眉中）");
        } catch (Exception e) {
            return McpToolResult.error("水印设置失败: " + e.getMessage());
        }
    }

    private McpToolResult addBookmark(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        String bookmarkName = McpToolParamParser.getString(params, "bookmark_name");
        if (paraIdx == null)
            return McpToolResult.error("add_bookmark需要paragraph_index参数");
        if (bookmarkName == null || bookmarkName.isBlank())
            return McpToolResult.error("add_bookmark需要bookmark_name参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);
        XWPFParagraph para = doc.getParagraphs().get(paraIdx);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = para.getCTP();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmark = ctp.addNewBookmarkStart();
        bookmark.setName(bookmarkName);
        bookmark.setId(BigInteger.valueOf(paraIdx));
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange markEnd = ctp.addNewBookmarkEnd();
        markEnd.setId(BigInteger.valueOf(paraIdx));
        return McpToolResult.success("书签添加成功");
    }

    private McpToolResult addFootnote(XWPFDocument doc, Map<String, Object> params) {
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        String text = resolveTextParam(params);
        if (paraIdx == null)
            return McpToolResult.error("add_footnote需要paragraph_index参数");
        if (text.isBlank())
            return McpToolResult.error("add_footnote需要text参数");
        if (paraIdx < 0 || paraIdx >= doc.getParagraphs().size())
            return McpToolResult.error("段落索引越界: " + paraIdx);

        try {
            XWPFParagraph para = doc.getParagraphs().get(paraIdx);
            BigInteger noteId = BigInteger.valueOf(doc.getFootnotes().size() + 1);

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdn footnote = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdn.Factory
                    .newInstance();
            footnote.setId(noteId);
            footnote.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFtnEdn.NORMAL);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP fnPara = footnote.addNewP();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR fnRun = fnPara.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText fnText = fnRun.addNewT();
            fnText.setStringValue(text);

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR refRun = para.getCTP().addNewR();
            refRun.addNewFootnoteReference().setId(noteId);

            return McpToolResult.success("脚注添加成功");
        } catch (Exception e) {
            return McpToolResult.error("脚注添加失败: " + e.getMessage());
        }
    }

    private McpToolResult addComment(XWPFDocument doc, Map<String, Object> params) {
        return McpToolResult.error("当前POI版本不支持直接添加批注，建议在文档中用特殊标记（如【批注：xxx】）代替");
    }

    private McpToolResult insertToc(XWPFDocument doc, Map<String, Object> params) {
        String title = McpToolParamParser.getString(params, "title", "目录");
        Integer paraIdx = McpToolParamParser.getInteger(params, "paragraph_index");
        int maxLevel = McpToolParamParser.getInteger(params, "max_level", 3);

        try {
            XWPFParagraph tocPara;
            if (paraIdx != null && paraIdx >= 0 && paraIdx < doc.getParagraphs().size()) {
                XWPFParagraph refPara = doc.getParagraphs().get(paraIdx);
                tocPara = insertParagraphAfter(doc, refPara);
            } else {
                tocPara = doc.createParagraph();
            }
            WordStyleHelper.clearNumberingProperties(tocPara);

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField tocField = tocPara.getCTP()
                    .addNewFldSimple();
            tocField.setInstr("TOC \\o \"1-" + maxLevel + "\" \\h \\z \\u");
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR tocRun = tocField.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText tocText = tocRun.addNewT();
            tocText.setStringValue(title);

            return McpToolResult.success("目录插入成功（打开文档后右键更新域可刷新目录）");
        } catch (Exception e) {
            return McpToolResult.error("目录插入失败: " + e.getMessage());
        }
    }

    private McpToolResult setDocumentProperty(XWPFDocument doc, Map<String, Object> params) {
        try {
            var coreProps = doc.getProperties().getCoreProperties();
            String title = McpToolParamParser.getString(params, "title");
            if (title != null)
                coreProps.setTitle(title);
            String author = McpToolParamParser.getString(params, "author");
            if (author != null)
                coreProps.setCreator(author);
            String subject = McpToolParamParser.getString(params, "subject");
            if (subject != null)
                coreProps.setSubjectProperty(subject);
            String keywords = McpToolParamParser.getString(params, "keywords");
            if (keywords != null)
                coreProps.setKeywords(keywords);
            String category = McpToolParamParser.getString(params, "category");
            if (category != null)
                coreProps.setCategory(category);
            return McpToolResult.success("文档属性设置成功");
        } catch (Exception e) {
            return McpToolResult.error("文档属性设置失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private void addPageNumberField(XWPFParagraph paragraph, Map<String, Object> style) {
        XWPFRun beginRun = paragraph.createRun();
        beginRun.getCTR().addNewFldChar()
                .setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);
        if (style != null)
            WordStyleHelper.applyRunStyle(beginRun, style);

        XWPFRun instrRun = paragraph.createRun();
        instrRun.getCTR().addNewInstrText().setStringValue(" PAGE ");
        if (style != null)
            WordStyleHelper.applyRunStyle(instrRun, style);

        XWPFRun sepRun = paragraph.createRun();
        sepRun.getCTR().addNewFldChar()
                .setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.SEPARATE);
        if (style != null)
            WordStyleHelper.applyRunStyle(sepRun, style);

        XWPFRun pageRun = paragraph.createRun();
        pageRun.setText("1");
        if (style != null)
            WordStyleHelper.applyRunStyle(pageRun, style);

        XWPFRun endRun = paragraph.createRun();
        endRun.getCTR().addNewFldChar()
                .setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);
        if (style != null)
            WordStyleHelper.applyRunStyle(endRun, style);
    }

    private XWPFParagraph insertParagraphAfter(XWPFDocument document, XWPFParagraph refParagraph) {
        org.apache.xmlbeans.XmlCursor cursor = refParagraph.getCTP().newCursor();
        cursor.toEndToken();
        cursor.toNextToken();
        XWPFParagraph newPara = document.insertNewParagraph(cursor);
        cursor.dispose();
        return newPara;
    }

    private void clearParagraph(XWPFParagraph para) {
        for (int i = para.getRuns().size() - 1; i >= 0; i--)
            para.removeRun(i);
    }

    private void clearCellContent(XWPFTableCell cell) {
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        for (int i = paragraphs.size() - 1; i >= 1; i--)
            cell.removeParagraph(i);
        if (!paragraphs.isEmpty())
            clearParagraph(paragraphs.get(0));
    }

    @SuppressWarnings("unchecked")
    private void applyTableStyle(XWPFTable table, Map<String, Object> style) {
        if (style == null)
            return;

        if (style.containsKey("alignment")) {
            String alignment = String.valueOf(style.get("alignment")).toUpperCase();
            TableRowAlign rowAlign = switch (alignment) {
                case "CENTER" -> TableRowAlign.CENTER;
                case "RIGHT" -> TableRowAlign.RIGHT;
                default -> TableRowAlign.LEFT;
            };
            table.setTableAlignment(rowAlign);
        }

        String headerBgColor = style.containsKey("header_background")
                ? String.valueOf(style.get("header_background")).replace("#", "")
                : null;
        String headerFontColor = style.containsKey("header_font_color")
                ? String.valueOf(style.get("header_font_color")).replace("#", "")
                : null;
        boolean headerBold = style.containsKey("header_bold") && toBool(style.get("header_bold"));
        int headerFontSize = style.containsKey("header_font_size") ? toInt(style.get("header_font_size"), 0) : 0;

        if (headerBgColor != null || headerFontColor != null || headerBold || headerFontSize > 0) {
            XWPFTableRow headerRow = table.getRow(0);
            if (headerRow != null) {
                for (XWPFTableCell cell : headerRow.getTableCells()) {
                    if (headerBgColor != null)
                        cell.setColor(headerBgColor);
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        for (XWPFRun run : para.getRuns()) {
                            if (headerFontColor != null)
                                run.setColor(headerFontColor);
                            if (headerBold)
                                run.setBold(true);
                            if (headerFontSize > 0)
                                run.setFontSize(headerFontSize);
                        }
                    }
                }
            }
        }

        if (style.containsKey("borders") && !toBool(style.get("borders"))) {
            table.removeBorders();
        }

        if (style.containsKey("column_widths")) {
            Object widthsRaw = style.get("column_widths");
            if (widthsRaw instanceof List) {
                List<Object> widths = (List<Object>) widthsRaw;
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid grid = table.getCTTbl().getTblGrid();
                if (grid == null)
                    grid = table.getCTTbl().addNewTblGrid();
                for (int i = 0; i < widths.size(); i++) {
                    while (grid.sizeOfGridColArray() <= i)
                        grid.addNewGridCol();
                    double wCm = Double.parseDouble(String.valueOf(widths.get(i)));
                    grid.getGridColArray(i).setW(BigInteger.valueOf((long) (wCm * 567)));
                }
            }
        }

        if (style.containsKey("cell_style")) {
            Map<String, Object> cellStyle = (Map<String, Object>) style.get("cell_style");
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    if (cellStyle.containsKey("background_color")) {
                        cell.setColor(String.valueOf(cellStyle.get("background_color")).replace("#", ""));
                    }
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        WordStyleHelper.applyParagraphStyle(para, cellStyle);
                        for (XWPFRun run : para.getRuns()) {
                            WordStyleHelper.applyRunStyle(run, cellStyle);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyThreeLineBorders(XWPFTable table, Map<String, Object> borders) {
        String color = borders.containsKey("color") ? String.valueOf(borders.get("color")).replace("#", "") : "000000";
        int headerBottomWidth = borders.containsKey("header_bottom_width")
                ? Integer.parseInt(String.valueOf(borders.get("header_bottom_width")))
                : 6;
        int bottomWidth = borders.containsKey("bottom_width")
                ? Integer.parseInt(String.valueOf(borders.get("bottom_width")))
                : 6;
        int bodyBottomWidth = borders.containsKey("body_bottom_width")
                ? Integer.parseInt(String.valueOf(borders.get("body_bottom_width")))
                : 2;

        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null)
            tblPr = table.getCTTbl().addNewTblPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders tblBorders = tblPr.addNewTblBorders();

        var topBorder = tblBorders.addNewTop();
        topBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        topBorder.setSz(BigInteger.valueOf(bottomWidth));
        topBorder.setColor(color);
        topBorder.setSpace(BigInteger.ZERO);

        var bottomBorder = tblBorders.addNewBottom();
        bottomBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        bottomBorder.setSz(BigInteger.valueOf(bottomWidth));
        bottomBorder.setColor(color);
        bottomBorder.setSpace(BigInteger.ZERO);

        var leftBorder = tblBorders.addNewLeft();
        leftBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        leftBorder.setSpace(BigInteger.ZERO);

        var rightBorder = tblBorders.addNewRight();
        rightBorder.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        rightBorder.setSpace(BigInteger.ZERO);

        var insideH = tblBorders.addNewInsideH();
        insideH.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        insideH.setSz(BigInteger.valueOf(bodyBottomWidth));
        insideH.setColor(color);
        insideH.setSpace(BigInteger.ZERO);

        var insideV = tblBorders.addNewInsideV();
        insideV.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        insideV.setSpace(BigInteger.ZERO);

        if (table.getNumberOfRows() > 0) {
            XWPFTableRow headerRow = table.getRow(0);
            for (XWPFTableCell cell : headerRow.getTableCells()) {
                var tcPr = cell.getCTTc().getTcPr();
                if (tcPr == null)
                    tcPr = cell.getCTTc().addNewTcPr();
                var tcBorders = tcPr.addNewTcBorders();
                var cellBottom = tcBorders.addNewBottom();
                cellBottom.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                cellBottom.setSz(BigInteger.valueOf(headerBottomWidth));
                cellBottom.setColor(color);
                cellBottom.setSpace(BigInteger.ZERO);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyCustomBorders(XWPFTable table, Map<String, Object> borders) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null)
            tblPr = table.getCTTbl().addNewTblPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders tblBorders = tblPr.addNewTblBorders();

        String[] positions = { "top", "bottom", "left", "right", "inside_h", "inside_v" };
        for (String pos : positions) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border;
            border = switch (pos) {
                case "top" -> tblBorders.addNewTop();
                case "bottom" -> tblBorders.addNewBottom();
                case "left" -> tblBorders.addNewLeft();
                case "right" -> tblBorders.addNewRight();
                case "inside_h" -> tblBorders.addNewInsideH();
                default -> tblBorders.addNewInsideV();
            };
            Object borderVal = borders.get(pos);
            if (borderVal == null) {
                border.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
                border.setSpace(BigInteger.ZERO);
            } else if (borderVal instanceof Map) {
                Map<String, Object> borderMap = (Map<String, Object>) borderVal;
                String bStyle = borderMap.containsKey("style") ? String.valueOf(borderMap.get("style")).toLowerCase()
                        : "single";
                border.setVal(switch (bStyle) {
                    case "dashed" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DASHED;
                    case "dotted" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOTTED;
                    case "double" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.DOUBLE;
                    case "thick" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.THICK;
                    case "none" -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE;
                    default -> org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE;
                });
                if (borderMap.containsKey("width"))
                    border.setSz(BigInteger.valueOf(Integer.parseInt(String.valueOf(borderMap.get("width")))));
                if (borderMap.containsKey("color")) {
                    String bColor = String.valueOf(borderMap.get("color"));
                    if (bColor.startsWith("#"))
                        bColor = bColor.substring(1);
                    border.setColor(bColor);
                } else
                    border.setColor("000000");
                border.setSpace(BigInteger.ZERO);
            } else {
                if (Boolean.parseBoolean(String.valueOf(borderVal))) {
                    border.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
                    border.setSz(BigInteger.valueOf(4));
                    border.setColor("000000");
                } else
                    border.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
                border.setSpace(BigInteger.ZERO);
            }
        }
    }

    private int guessImageType(String fileName, byte[] imageBytes) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".png"))
            return XWPFDocument.PICTURE_TYPE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return XWPFDocument.PICTURE_TYPE_JPEG;
        if (lower.endsWith(".gif"))
            return XWPFDocument.PICTURE_TYPE_GIF;
        if (lower.endsWith(".bmp"))
            return XWPFDocument.PICTURE_TYPE_BMP;
        if (lower.endsWith(".tiff") || lower.endsWith(".tif"))
            return XWPFDocument.PICTURE_TYPE_TIFF;
        if (imageBytes != null && imageBytes.length >= 4) {
            if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50)
                return XWPFDocument.PICTURE_TYPE_PNG;
            if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8)
                return XWPFDocument.PICTURE_TYPE_JPEG;
            if (imageBytes[0] == 0x47 && imageBytes[1] == 0x49)
                return XWPFDocument.PICTURE_TYPE_GIF;
        }
        return XWPFDocument.PICTURE_TYPE_PNG;
    }

    // ==================== 文本替换辅助 ====================

    private int replaceInParagraph(XWPFParagraph para, String oldText, String newText, String searchOld,
            boolean fuzzyMatch) {
        List<XWPFRun> runs = para.getRuns();
        if (runs.isEmpty())
            return 0;
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text == null)
                continue;
            String compareText = fuzzyMatch ? normalizeForFuzzyMatch(text) : text;
            if (compareText.contains(searchOld)) {
                if (fuzzyMatch) {
                    String normalized = normalizeForFuzzyMatch(text);
                    int idx = normalized.indexOf(searchOld);
                    int actualStart = findActualStartIndex(text, idx, searchOld.length());
                    int actualEnd = findActualEndIndex(text, idx + searchOld.length());
                    String actualOldText = text.substring(actualStart, actualEnd);
                    run.setText(text.replace(actualOldText, newText), 0);
                } else {
                    run.setText(text.replace(oldText, newText), 0);
                }
                return 1;
            }
        }
        return replaceCrossRun(para, oldText, newText, searchOld, fuzzyMatch);
    }

    private int replaceCrossRun(XWPFParagraph para, String oldText, String newText, String searchOld,
            boolean fuzzyMatch) {
        List<XWPFRun> runs = para.getRuns();
        if (runs.size() < 2)
            return 0;

        StringBuilder allText = new StringBuilder();
        List<int[]> runRanges = new ArrayList<>();
        for (XWPFRun run : runs) {
            String t = run.getText(0);
            if (t == null)
                t = "";
            int start = allText.length();
            allText.append(t);
            runRanges.add(new int[] { start, allText.length() });
        }

        String fullText = allText.toString();
        String compareFull = fuzzyMatch ? normalizeForFuzzyMatch(fullText) : fullText;
        int matchIdx = compareFull.indexOf(searchOld);
        if (matchIdx < 0)
            return 0;

        int actualStart, actualEnd;
        if (fuzzyMatch) {
            actualStart = findActualStartIndex(fullText, matchIdx, searchOld.length());
            actualEnd = findActualEndIndex(fullText, matchIdx + searchOld.length());
        } else {
            actualStart = matchIdx;
            actualEnd = matchIdx + oldText.length();
        }

        int startRunIdx = -1, endRunIdx = -1;
        int startOffsetInRun = 0, endOffsetInRun = 0;

        for (int i = 0; i < runRanges.size(); i++) {
            int[] range = runRanges.get(i);
            if (startRunIdx == -1 && actualStart < range[1] && actualStart >= range[0]) {
                startRunIdx = i;
                startOffsetInRun = actualStart - range[0];
            }
            if (actualEnd <= range[1] && actualEnd > range[0]) {
                endRunIdx = i;
                endOffsetInRun = actualEnd - range[0];
                break;
            }
        }

        if (startRunIdx < 0 || endRunIdx < 0)
            return 0;

        if (startRunIdx == endRunIdx) {
            XWPFRun run = runs.get(startRunIdx);
            String t = run.getText(0);
            if (t == null)
                return 0;
            run.setText(t.substring(0, startOffsetInRun) + newText + t.substring(endOffsetInRun), 0);
        } else {
            String firstText = runs.get(startRunIdx).getText(0);
            String lastText = runs.get(endRunIdx).getText(0);
            if (firstText == null)
                firstText = "";
            if (lastText == null)
                lastText = "";
            runs.get(startRunIdx).setText(firstText.substring(0, startOffsetInRun) + newText, 0);
            runs.get(endRunIdx).setText(lastText.substring(endOffsetInRun), 0);
            for (int i = startRunIdx + 1; i < endRunIdx; i++)
                runs.get(i).setText("", 0);
        }
        return 1;
    }

    private List<String> findSimilarTexts(XWPFDocument doc, String targetText) {
        List<String> suggestions = new ArrayList<>();
        String normalizedTarget = normalizeForFuzzyMatch(targetText);
        if (normalizedTarget.length() < 2)
            return suggestions;
        String coreKeyword = normalizedTarget.length() > 4
                ? normalizedTarget.substring(0, normalizedTarget.length() / 2)
                : normalizedTarget;
        java.util.Set<String> seen = new java.util.HashSet<>();

        java.util.function.Consumer<XWPFParagraph> searchInPara = para -> {
            StringBuilder allText = new StringBuilder();
            for (XWPFRun run : para.getRuns()) {
                String t = run.getText(0);
                if (t != null)
                    allText.append(t);
            }
            String fullText = allText.toString();
            if (fullText.isBlank())
                return;
            String normalized = normalizeForFuzzyMatch(fullText);
            if (normalized.contains(coreKeyword) && !fullText.contains(targetText)) {
                int idx = normalized.indexOf(coreKeyword);
                int start = Math.max(0, idx - 5);
                int end = Math.min(fullText.length(), idx + targetText.length() + 5);
                String snippet = fullText.substring(start, end);
                if (seen.add(snippet))
                    suggestions.add(snippet);
            }
        };

        for (XWPFParagraph para : doc.getParagraphs())
            searchInPara.accept(para);
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs())
                        searchInPara.accept(para);
                }
            }
        }
        return suggestions.stream().limit(3).toList();
    }

    @SuppressWarnings("unchecked")
    private static String resolveTextParam(Map<String, Object> params) {
        String text = McpToolParamParser.getString(params, "text", null);
        if (text != null)
            return text;
        String value = McpToolParamParser.getString(params, "value", null);
        if (value != null)
            return value;
        String content = McpToolParamParser.getString(params, "content", null);
        if (content != null)
            return content;
        return "";
    }

    private static Map<String, Object> parseStyleParam(Map<String, Object> params) {
        Object styleRaw = params.get("style");
        if (styleRaw instanceof Map)
            return (Map<String, Object>) styleRaw;
        String styleStr = McpToolParamParser.getString(params, "style", "{}");
        if (styleStr == null || styleStr.isBlank() || "{}".equals(styleStr.trim()))
            return new HashMap<>();
        try {
            return JSON.parseObject(styleStr, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
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

    private static boolean toBool(Object value) {
        if (value == null)
            return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static String normalizeForFuzzyMatch(String text) {
        if (text == null)
            return "";
        return text.trim()
                .replaceAll("[\\s\\u00A0\\u3000]+", "")
                .replaceAll(
                        "[\uFF0C\u3002\uFF1A\uFF1B\uFF01\uFF1F\u3001\u201C\u201D\u2018\u2019\uFF08\uFF09\u3010\u3011\u300A\u300B\\-\u2014\u2026\u00B7.,:;!?\\-()\\[\\]<>]",
                        "");
    }

    private int findActualStartIndex(String original, int normalizedIdx, int normalizedLen) {
        int actualPos = 0;
        int normPos = 0;
        String normalized = normalizeForFuzzyMatch(original);
        while (normPos < normalizedIdx && actualPos < original.length()) {
            String ch = String.valueOf(original.charAt(actualPos));
            String normCh = normalizeForFuzzyMatch(ch);
            if (!normCh.isEmpty())
                normPos++;
            actualPos++;
        }
        return actualPos;
    }

    private int findActualEndIndex(String original, int normalizedEnd) {
        int actualPos = 0;
        int normPos = 0;
        while (normPos < normalizedEnd && actualPos < original.length()) {
            String ch = String.valueOf(original.charAt(actualPos));
            String normCh = normalizeForFuzzyMatch(ch);
            if (!normCh.isEmpty())
                normPos++;
            actualPos++;
        }
        return actualPos;
    }
}
