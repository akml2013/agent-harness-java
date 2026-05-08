package com.example.core.mcp.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
public class ExcelTool implements McpTool {

    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "excel";
    }

    @Override
    public String getDescription() {
        return "Excel文档操作工具。支持创建/修改Excel文档，通过actions数组指定多个操作。"
                + "支持工作表管理(添加/删除/重命名/复制/排序)、"
                + "单元格操作(写入/读取/清除/批量写入行/批量写入列/删除行/插入行/删除列/插入列)、"
                + "样式操作(单元格/范围/行/列样式)、"
                + "合并与拆分、行列尺寸、公式、数据验证、冻结窗格、自动筛选、条件格式、"
                + "打印设置、图片插入、查找替换。"
                + "支持save_as参数另存为新文件名（模板场景）";
    }

    @Override
    public boolean supportsBatch() {
        return true;
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

        Workbook workbook;
        try {
            if (isCreate) {
                workbook = new XSSFWorkbook();
            } else {
                String resolvedFileKey = resolveFileKey(params, actions);
                if (resolvedFileKey == null || resolvedFileKey.isBlank())
                    return McpToolResult.error("修改已有文件时需要file_key参数");
                try (InputStream is = fileStorageService.download(resolvedFileKey)) {
                    workbook = WorkbookFactory.create(is);
                }
                fileKey = resolvedFileKey;
            }
        } catch (Exception e) {
            log.error("Excel文档加载失败: {}", e.getMessage());
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
                McpToolResult result = executeAction(workbook, actionParams, action);
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
                log.error("Excel操作失败: action={}, error={}", action, e.getMessage());
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
            workbook.write(baos);
            byte[] bytes = baos.toByteArray();
            workbook.close();

            String storageKey = fileStorageService.generateUploadKey(userId, sessionId, fileName);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                fileStorageService.upload(storageKey, bais, bytes.length,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("file_key", storageKey);
            data.put("file_name", fileName);
            data.put("file_size", bytes.length);
            data.put("file_type", "xlsx");
            if (fileKey != null && !fileKey.isBlank()) {
                data.put("original_file_key", fileKey);
                data.put("is_modify", true);
            }
            if (saveAs != null && !saveAs.isBlank()) {
                data.put("is_template_derived", true);
            }
            data.put("create_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            data.put("actions", actionResults);
            String batchMessage = String.format("Excel操作完成(%d个操作: %d成功, %d失败)。file_key: %s",
                    actions.size(), successCount, failCount, storageKey);
            data.put("message", batchMessage);

            return McpToolResult.success(batchMessage, data);
        } catch (Exception e) {
            log.error("Excel文档保存失败: {}", e.getMessage());
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
        return "spreadsheet.xlsx";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("actions",
                "操作数组(必填)，每个元素包含action和对应参数。create必须出现在第一个。"
                        + "示例:[{\"action\":\"create\",\"file_name\":\"a.xlsx\",\"sheet_name\":\"Sheet1\"},{\"action\":\"write_rows\",\"rows_data\":[[1,2,3]]}]。"
                        + "也可以只传单个action(不用actions数组): {\"action\":\"write_cell\",\"row\":0,\"col\":0,\"value\":\"hello\"}");
        schema.put("file_key",
                "文件的MinIO存储路径(格式如users/1/sessions/xxx/abc.xlsx)。修改已有文件时必填，创建新文件时不需传");
        schema.put("file_name", "文件名(创建新文件时必填，如'数据表.xlsx')。只在create操作中传入");
        schema.put("save_as", "另存为文件名(可选)。模板场景必须传入，避免原文件消失");
        schema.put("sheet_name", "工作表名称(可选，默认第一个)");
        schema.put("row", "行号(0开始)");
        schema.put("col", "列号(0开始)");
        schema.put("value", "单元格值");
        schema.put("style",
                "样式对象(可选): {bold,italic,underline,font_size,font_family,font_color,background_color,number_format,alignment:LEFT/CENTER/RIGHT,vertical_alignment:TOP/CENTER/BOTTOM,wrap_text,border:{top:{style,color},bottom:{...},left:{...},right:{...}},locked,hidden,rotation,indent,shrink_to_fit}");
        schema.put("rows_data", "行数据数组(write_rows时使用，每行是数组或对象数组)");
        schema.put("columns_data", "列数据数组(write_columns时使用，每列是数组)");
        schema.put("start_row", "起始行号(write_rows/insert_row等时使用)");
        schema.put("end_row", "结束行号(delete_rows/set_range_style等时使用)");
        schema.put("start_col", "起始列号(set_range_style等时使用)");
        schema.put("end_col", "结束列号(set_range_style等时使用)");
        schema.put("count", "数量(insert_row/insert_column时可选，默认1)");
        schema.put("new_name", "新名称(rename_sheet/copy_sheet时使用)");
        schema.put("position", "位置(set_sheet_order时使用，0开始)");
        schema.put("width", "列宽字符数(set_column_width时使用)");
        schema.put("height", "行高磅数(set_row_height时使用)");
        schema.put("formula", "公式(set_formula时使用，如SUM(A1:A10))");
        schema.put("validation_type", "验证类型(set_data_validation时使用): ANY/INTEGER/DECIMAL/LIST/DATE/TEXT_LENGTH");
        schema.put("formula1", "验证公式1(set_data_validation时使用，如1或'A,B,C')");
        schema.put("formula2", "验证公式2(set_data_validation时使用)");
        schema.put("allow_blank", "是否允许空值(set_data_validation时可选，默认true)");
        schema.put("row_split", "冻结行数(freeze_panes时使用)");
        schema.put("col_split", "冻结列数(freeze_panes时使用)");
        schema.put("condition_type", "条件格式类型(set_conditional_format时使用): CELL_VALUE/FORMULA");
        schema.put("condition_operator",
                "条件运算符(set_conditional_format时使用): BETWEEN/NOT_BETWEEN/EQUAL/NOT_EQUAL/GT/LT/GE/LE");
        schema.put("orientation", "打印方向(set_sheet_print_settings时可选): portrait/landscape");
        schema.put("paper_size", "纸张大小(set_sheet_print_settings时可选): A4/A3/Letter");
        schema.put("fit_to_width", "适应宽度页数(set_sheet_print_settings时可选)");
        schema.put("fit_to_height", "适应高度页数(set_sheet_print_settings时可选)");
        schema.put("image_key", "图片文件的MinIO存储键(add_image时必填)");
        schema.put("image_width", "图片宽度像素(add_image时可选)");
        schema.put("image_height", "图片高度像素(add_image时可选)");
        schema.put("text", "查找文本(find_text时使用)");
        schema.put("old_text", "替换旧文本(replace_text时必填)");
        schema.put("new_text", "替换新文本(replace_text时必填)");
        schema.put("match_case", "是否区分大小写(replace_text时可选，默认false)");
        schema.put("max_results", "最大结果数(find_text时可选，默认20)");
        return schema;
    }

    @SuppressWarnings("unchecked")
    private McpToolResult executeAction(Workbook workbook, Map<String, Object> params, String action) throws Exception {
        switch (action) {
            case "create": {
                String sheetName = McpToolParamParser.getString(params, "sheet_name", "Sheet1");
                if (sheetName == null || sheetName.isBlank()) {
                    sheetName = "Sheet1";
                }
                workbook.createSheet(sheetName);
                return McpToolResult.success("Excel文档创建成功，后续操作无需传file_key，系统会自动传递文档", Map.of(
                        "sheet_count", workbook.getNumberOfSheets(),
                        "default_sheet_name", sheetName,
                        "hint", "批量操作中后续modify操作不需要file_key参数，默认工作表名为" + sheetName + "。使用sheet_name参数指定工作表。"));
            }
            case "add_sheet":
                return addSheet(workbook, params);
            case "delete_sheet":
                return deleteSheet(workbook, params);
            case "rename_sheet":
                return renameSheet(workbook, params);
            case "copy_sheet":
                return copySheet(workbook, params);
            case "set_sheet_order":
                return setSheetOrder(workbook, params);
            case "write_cell":
                return writeCell(workbook, params);
            case "read_cell":
                return readCell(workbook, params);
            case "clear_cell":
                return clearCell(workbook, params);
            case "write_rows":
                return writeRows(workbook, params);
            case "write_columns":
                return writeColumns(workbook, params);
            case "delete_row":
                return deleteRow(workbook, params);
            case "delete_rows":
                return deleteRows(workbook, params);
            case "insert_row":
                return insertRow(workbook, params);
            case "delete_column":
                return deleteColumn(workbook, params);
            case "insert_column":
                return insertColumn(workbook, params);
            case "set_cell_style":
                return setCellStyle(workbook, params);
            case "set_range_style":
                return setRangeStyle(workbook, params);
            case "set_row_style":
                return setRowStyle(workbook, params);
            case "set_column_style":
                return setColumnStyle(workbook, params);
            case "merge_cells":
                return mergeCells(workbook, params);
            case "unmerge_cells":
                return unmergeCells(workbook, params);
            case "set_column_width":
                return setColumnWidth(workbook, params);
            case "set_row_height":
                return setRowHeight(workbook, params);
            case "auto_size_column":
                return autoSizeColumn(workbook, params);
            case "set_formula":
                return setFormula(workbook, params);
            case "set_data_validation":
                return setDataValidation(workbook, params);
            case "freeze_panes":
                return freezePanes(workbook, params);
            case "set_auto_filter":
                return setAutoFilter(workbook, params);
            case "set_conditional_format":
                return setConditionalFormat(workbook, params);
            case "set_print_area":
                return setPrintArea(workbook, params);
            case "set_sheet_print_settings":
                return setSheetPrintSettings(workbook, params);
            case "add_image":
                return addImage(workbook, params);
            case "find_text":
                return findText(workbook, params);
            case "replace_text":
                return replaceText(workbook, params);
            default:
                return McpToolResult.error("不支持的操作: " + action);
        }
    }

    private Sheet getSheet(Workbook workbook, Map<String, Object> params) {
        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet != null)
                return sheet;
        }
        if (workbook.getNumberOfSheets() > 0) {
            return workbook.getSheetAt(0);
        }
        return workbook.createSheet("Sheet1");
    }

    private Row getOrCreateRow(Sheet sheet, int rowIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null)
            row = sheet.createRow(rowIdx);
        return row;
    }

    private Cell getOrCreateCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null)
            cell = row.createCell(colIdx);
        return cell;
    }

    private void setCellValue(Cell cell, String value) {
        if (value == null || value.isEmpty()) {
            cell.setBlank();
            return;
        }
        try {
            double numValue = Double.parseDouble(value);
            if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)
                    && Math.abs(numValue) < Long.MAX_VALUE) {
                cell.setCellValue((long) numValue);
            } else {
                cell.setCellValue(numValue);
            }
        } catch (NumberFormatException e) {
            cell.setCellValue(value);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null)
            return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.floor(val) && !Double.isInfinite(val)) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else if (cell.getCellType() == CellType.FORMULA) {
            try {
                return String.valueOf(cell.getNumericCellValue());
            } catch (Exception e) {
                return cell.getStringCellValue();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStyleParam(Map<String, Object> params) {
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

    // ==================== 工作表操作 ====================

    private McpToolResult addSheet(Workbook workbook, Map<String, Object> params) {
        String sheetName = McpToolParamParser.getString(params, "sheet_name",
                "Sheet" + (workbook.getNumberOfSheets() + 1));
        if (workbook.getSheet(sheetName) != null)
            return McpToolResult.error("工作表已存在: " + sheetName);
        workbook.createSheet(sheetName);
        return McpToolResult.success("工作表添加成功", Map.of("sheet_name", sheetName));
    }

    private McpToolResult deleteSheet(Workbook workbook, Map<String, Object> params) {
        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        if (sheetName == null || sheetName.isBlank())
            return McpToolResult.error("delete_sheet需要sheet_name参数");
        if (workbook.getNumberOfSheets() <= 1)
            return McpToolResult.error("工作簿至少需要保留一个工作表");
        int idx = workbook.getSheetIndex(sheetName);
        if (idx < 0)
            return McpToolResult.error("工作表不存在: " + sheetName);
        workbook.removeSheetAt(idx);
        return McpToolResult.success("工作表删除成功");
    }

    private McpToolResult renameSheet(Workbook workbook, Map<String, Object> params) {
        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        String newName = McpToolParamParser.getString(params, "new_name");
        if (sheetName == null || sheetName.isBlank())
            return McpToolResult.error("rename_sheet需要sheet_name参数");
        if (newName == null || newName.isBlank())
            return McpToolResult.error("rename_sheet需要new_name参数");
        int idx = workbook.getSheetIndex(sheetName);
        if (idx < 0)
            return McpToolResult.error("工作表不存在: " + sheetName);
        workbook.setSheetName(idx, newName);
        return McpToolResult.success("工作表重命名成功", Map.of("old_name", sheetName, "new_name", newName));
    }

    private McpToolResult copySheet(Workbook workbook, Map<String, Object> params) {
        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        String newName = McpToolParamParser.getString(params, "new_name");
        if (sheetName == null || sheetName.isBlank())
            return McpToolResult.error("copy_sheet需要sheet_name参数");
        if (newName == null || newName.isBlank())
            return McpToolResult.error("copy_sheet需要new_name参数");
        int idx = workbook.getSheetIndex(sheetName);
        if (idx < 0)
            return McpToolResult.error("工作表不存在: " + sheetName);
        workbook.cloneSheet(idx);
        int newIdx = workbook.getNumberOfSheets() - 1;
        workbook.setSheetName(newIdx, newName);
        return McpToolResult.success("工作表复制成功", Map.of("new_sheet_name", newName));
    }

    private McpToolResult setSheetOrder(Workbook workbook, Map<String, Object> params) {
        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        Integer position = McpToolParamParser.getInteger(params, "position");
        if (sheetName == null || sheetName.isBlank())
            return McpToolResult.error("set_sheet_order需要sheet_name参数");
        if (position == null)
            return McpToolResult.error("set_sheet_order需要position参数");
        int idx = workbook.getSheetIndex(sheetName);
        if (idx < 0)
            return McpToolResult.error("工作表不存在: " + sheetName);
        workbook.setSheetOrder(sheetName, position);
        return McpToolResult.success("工作表顺序调整成功");
    }

    // ==================== 单元格操作 ====================

    @SuppressWarnings("unchecked")
    private McpToolResult writeCell(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer col = McpToolParamParser.getInteger(params, "col");
        String value = McpToolParamParser.getString(params, "value", "");
        if (row == null || col == null)
            return McpToolResult.error("write_cell需要row和col参数");

        Row excelRow = getOrCreateRow(sheet, row);
        Cell cell = getOrCreateCell(excelRow, col);
        setCellValue(cell, value);

        Map<String, Object> style = parseStyleParam(params);
        if (!style.isEmpty()) {
            ExcelStyleHelper.applyCellStyle(cell, style);
        }

        return McpToolResult.success("单元格写入成功", Map.of("row", row, "col", col));
    }

    private McpToolResult readCell(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer col = McpToolParamParser.getInteger(params, "col");
        if (row == null || col == null)
            return McpToolResult.error("read_cell需要row和col参数");

        Row excelRow = sheet.getRow(row);
        if (excelRow == null)
            return McpToolResult.success("单元格为空", Map.of("row", row, "col", col, "value", ""));
        Cell cell = excelRow.getCell(col);
        String value = getCellValue(cell);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("row", row);
        data.put("col", col);
        data.put("value", value);
        data.put("cell_ref", CellReference.convertNumToColString(col) + (row + 1));

        if (cell != null) {
            Map<String, Object> cellStyle = ExcelStyleHelper.extractCellStyle(cell);
            if (!cellStyle.isEmpty()) {
                data.put("style", cellStyle);
            }
            if (cell.getCellType() == CellType.FORMULA) {
                data.put("formula", cell.getCellFormula());
            }
        }

        return McpToolResult.success("单元格读取成功", data);
    }

    private McpToolResult clearCell(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer col = McpToolParamParser.getInteger(params, "col");
        if (row == null || col == null)
            return McpToolResult.error("clear_cell需要row和col参数");

        Row excelRow = sheet.getRow(row);
        if (excelRow == null)
            return McpToolResult.success("单元格已为空");
        Cell cell = excelRow.getCell(col);
        if (cell == null)
            return McpToolResult.success("单元格已为空");
        cell.setBlank();
        return McpToolResult.success("单元格清除成功");
    }

    @SuppressWarnings("unchecked")
    private McpToolResult writeRows(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        if (startRow == null)
            startRow = sheet.getLastRowNum() + 1;

        List<Object> rowsData = resolveListParam(params, "rows_data");
        for (int i = 0; i < rowsData.size(); i++) {
            List<Object> rowData = (List<Object>) rowsData.get(i);
            Row excelRow = sheet.createRow(startRow + i);
            for (int j = 0; j < rowData.size(); j++) {
                Cell cell = excelRow.createCell(j);
                Object cellItem = rowData.get(j);

                if (cellItem instanceof Map) {
                    Map<String, Object> cellMap = (Map<String, Object>) cellItem;
                    String value = String.valueOf(cellMap.getOrDefault("value", ""));
                    setCellValue(cell, value);
                    Map<String, Object> style = cellMap.containsKey("style")
                            ? (Map<String, Object>) cellMap.get("style")
                            : null;
                    ExcelStyleHelper.applyCellStyle(cell, style);
                } else {
                    setCellValue(cell, String.valueOf(cellItem));
                }
            }
        }

        return McpToolResult.success("批量写入行成功", Map.of("row_count", rowsData.size(), "start_row", startRow));
    }

    @SuppressWarnings("unchecked")
    private McpToolResult writeColumns(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startCol = McpToolParamParser.getInteger(params, "start_col", 0);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row", 0);

        List<Object> columnsData = resolveListParam(params, "columns_data");
        for (int c = 0; c < columnsData.size(); c++) {
            List<Object> colData = (List<Object>) columnsData.get(c);
            for (int r = 0; r < colData.size(); r++) {
                Row excelRow = getOrCreateRow(sheet, startRow + r);
                Cell cell = getOrCreateCell(excelRow, startCol + c);
                Object cellItem = colData.get(r);

                if (cellItem instanceof Map) {
                    Map<String, Object> cellMap = (Map<String, Object>) cellItem;
                    String value = String.valueOf(cellMap.getOrDefault("value", ""));
                    setCellValue(cell, value);
                    Map<String, Object> style = cellMap.containsKey("style")
                            ? (Map<String, Object>) cellMap.get("style")
                            : null;
                    ExcelStyleHelper.applyCellStyle(cell, style);
                } else {
                    setCellValue(cell, String.valueOf(cellItem));
                }
            }
        }

        return McpToolResult.success("批量写入列成功", Map.of("col_count", columnsData.size(), "start_col", startCol));
    }

    private McpToolResult deleteRow(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        if (row == null)
            return McpToolResult.error("delete_row需要row参数");
        if (row < 0 || row > sheet.getLastRowNum())
            return McpToolResult.error("行号越界: " + row);
        sheet.shiftRows(row + 1, sheet.getLastRowNum(), -1);
        return McpToolResult.success("行删除成功");
    }

    private McpToolResult deleteRows(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        if (startRow == null || endRow == null)
            return McpToolResult.error("delete_rows需要start_row和end_row参数");
        if (startRow < 0 || endRow > sheet.getLastRowNum() || startRow > endRow)
            return McpToolResult.error("行号范围无效");
        int count = endRow - startRow + 1;
        sheet.shiftRows(endRow + 1, sheet.getLastRowNum(), -count);
        return McpToolResult.success("多行删除成功", Map.of("deleted_count", count));
    }

    private McpToolResult insertRow(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer count = McpToolParamParser.getInteger(params, "count", 1);
        if (row == null)
            return McpToolResult.error("insert_row需要row参数");
        if (row <= sheet.getLastRowNum()) {
            sheet.shiftRows(row, sheet.getLastRowNum(), count);
        }
        for (int i = 0; i < count; i++) {
            sheet.createRow(row + i);
        }
        return McpToolResult.success("行插入成功", Map.of("row", row, "count", count));
    }

    private McpToolResult deleteColumn(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer col = McpToolParamParser.getInteger(params, "col");
        if (col == null)
            return McpToolResult.error("delete_column需要col参数");

        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && col < row.getLastCellNum()) {
                for (int c = col; c < row.getLastCellNum() - 1; c++) {
                    Cell srcCell = row.getCell(c + 1);
                    Cell destCell = getOrCreateCell(row, c);
                    if (srcCell != null) {
                        copyCellValue(srcCell, destCell);
                        copyCellStyle(srcCell, destCell);
                    } else {
                        destCell.setBlank();
                    }
                }
                Cell lastCell = row.getCell(row.getLastCellNum() - 1);
                if (lastCell != null) {
                    row.removeCell(lastCell);
                }
            }
        }
        return McpToolResult.success("列删除成功");
    }

    private McpToolResult insertColumn(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer col = McpToolParamParser.getInteger(params, "col");
        Integer count = McpToolParamParser.getInteger(params, "count", 1);
        if (col == null)
            return McpToolResult.error("insert_column需要col参数");

        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                int lastCol = row.getLastCellNum() - 1;
                for (int c = lastCol; c >= col; c--) {
                    Cell srcCell = row.getCell(c);
                    Cell destCell = getOrCreateCell(row, c + count);
                    if (srcCell != null) {
                        copyCellValue(srcCell, destCell);
                        copyCellStyle(srcCell, destCell);
                    }
                }
                for (int c = col; c < col + count; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        cell.setBlank();
                    }
                }
            }
        }
        return McpToolResult.success("列插入成功", Map.of("col", col, "count", count));
    }

    // ==================== 样式操作 ====================

    private McpToolResult setCellStyle(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer col = McpToolParamParser.getInteger(params, "col");
        if (row == null || col == null)
            return McpToolResult.error("set_cell_style需要row和col参数");

        Row excelRow = getOrCreateRow(sheet, row);
        Cell cell = getOrCreateCell(excelRow, col);

        Map<String, Object> style = parseStyleParam(params);
        ExcelStyleHelper.applyCellStyle(cell, style);
        return McpToolResult.success("单元格样式设置成功");
    }

    private McpToolResult setRangeStyle(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col");
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (startRow == null || endRow == null || startCol == null || endCol == null)
            return McpToolResult.error("set_range_style需要start_row, end_row, start_col, end_col参数");

        Map<String, Object> style = parseStyleParam(params);

        for (int r = startRow; r <= endRow; r++) {
            Row excelRow = getOrCreateRow(sheet, r);
            for (int c = startCol; c <= endCol; c++) {
                Cell cell = getOrCreateCell(excelRow, c);
                ExcelStyleHelper.applyCellStyle(cell, style);
            }
        }
        return McpToolResult.success("范围样式设置成功");
    }

    private McpToolResult setRowStyle(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        if (row == null)
            return McpToolResult.error("set_row_style需要row参数");

        Map<String, Object> style = parseStyleParam(params);
        Row excelRow = getOrCreateRow(sheet, row);

        int lastCol = excelRow.getLastCellNum();
        if (lastCol <= 0) {
            lastCol = 26;
        }
        for (int c = 0; c < lastCol; c++) {
            Cell cell = getOrCreateCell(excelRow, c);
            ExcelStyleHelper.applyCellStyle(cell, style);
        }
        return McpToolResult.success("行样式设置成功");
    }

    private McpToolResult setColumnStyle(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer col = McpToolParamParser.getInteger(params, "col");
        if (col == null)
            return McpToolResult.error("set_column_style需要col参数");

        Map<String, Object> style = parseStyleParam(params);

        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                Cell cell = getOrCreateCell(row, col);
                ExcelStyleHelper.applyCellStyle(cell, style);
            }
        }
        return McpToolResult.success("列样式设置成功");
    }

    // ==================== 合并与拆分 ====================

    private McpToolResult mergeCells(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col");
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (startRow == null || endRow == null || startCol == null || endCol == null)
            return McpToolResult.error("merge_cells需要start_row, end_row, start_col, end_col参数");

        sheet.addMergedRegion(new CellRangeAddress(startRow, endRow, startCol, endCol));
        return McpToolResult.success("单元格合并成功");
    }

    private McpToolResult unmergeCells(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col");
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (startRow == null || endRow == null || startCol == null || endCol == null)
            return McpToolResult.error("unmerge_cells需要start_row, end_row, start_col, end_col参数");

        CellRangeAddress target = new CellRangeAddress(startRow, endRow, startCol, endCol);
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.equals(target)) {
                sheet.removeMergedRegion(i);
                return McpToolResult.success("取消合并成功");
            }
        }
        return McpToolResult.error("未找到指定的合并区域");
    }

    // ==================== 行列尺寸 ====================

    private McpToolResult setColumnWidth(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer col = McpToolParamParser.getInteger(params, "col");
        Integer width = McpToolParamParser.getInteger(params, "width");
        if (col == null || width == null)
            return McpToolResult.error("set_column_width需要col和width参数");

        sheet.setColumnWidth(col, width * 256);
        return McpToolResult.success("列宽设置成功");
    }

    private McpToolResult setRowHeight(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Double height = McpToolParamParser.getDouble(params, "height");
        if (row == null || height == null)
            return McpToolResult.error("set_row_height需要row和height参数");

        Row excelRow = getOrCreateRow(sheet, row);
        excelRow.setHeightInPoints(height.floatValue());
        return McpToolResult.success("行高设置成功");
    }

    private McpToolResult autoSizeColumn(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer col = McpToolParamParser.getInteger(params, "col");
        if (col == null)
            return McpToolResult.error("auto_size_column需要col参数");

        sheet.autoSizeColumn(col);
        return McpToolResult.success("自动列宽设置成功");
    }

    // ==================== 公式 ====================

    private McpToolResult setFormula(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer col = McpToolParamParser.getInteger(params, "col");
        String formula = McpToolParamParser.getString(params, "formula");
        if (row == null || col == null)
            return McpToolResult.error("set_formula需要row和col参数");
        if (formula == null || formula.isBlank())
            return McpToolResult.error("set_formula需要formula参数");

        Row excelRow = getOrCreateRow(sheet, row);
        Cell cell = getOrCreateCell(excelRow, col);
        cell.setCellFormula(formula);

        Map<String, Object> style = parseStyleParam(params);
        if (!style.isEmpty()) {
            ExcelStyleHelper.applyCellStyle(cell, style);
        }

        return McpToolResult.success("公式设置成功", Map.of("cell_ref",
                CellReference.convertNumToColString(col) + (row + 1), "formula", formula));
    }

    // ==================== 数据验证 ====================

    private McpToolResult setDataValidation(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer row = McpToolParamParser.getInteger(params, "row");
        Integer col = McpToolParamParser.getInteger(params, "col");
        String validationType = McpToolParamParser.getString(params, "validation_type", "ANY");
        String formula1 = McpToolParamParser.getString(params, "formula1");
        String formula2 = McpToolParamParser.getString(params, "formula2");
        boolean allowBlank = McpToolParamParser.getBool(params, "allow_blank", true);

        if (row == null || col == null)
            return McpToolResult.error("set_data_validation需要row和col参数");

        if (!(sheet instanceof XSSFSheet)) {
            return McpToolResult.error("数据验证仅支持XLSX格式");
        }

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint;
        String vType = validationType.toUpperCase();
        int opType = ComparisonOperator.BETWEEN;
        String f1 = formula1 != null ? formula1 : "";
        String f2 = formula2 != null ? formula2 : "";

        switch (vType) {
            case "INTEGER" -> constraint = dvHelper.createIntegerConstraint(opType, f1, f2);
            case "DECIMAL" -> constraint = dvHelper.createDecimalConstraint(opType, f1, f2);
            case "LIST" -> {
                if (formula1 != null && formula1.contains(",")) {
                    constraint = dvHelper.createExplicitListConstraint(formula1.split(","));
                } else {
                    constraint = dvHelper.createFormulaListConstraint(formula1 != null ? formula1 : "");
                }
            }
            case "DATE" -> constraint = dvHelper.createDateConstraint(opType, f1, f2, "yyyy-MM-dd");
            case "TEXT_LENGTH" -> constraint = dvHelper.createTextLengthConstraint(opType, f1, f2);
            default -> constraint = dvHelper.createIntegerConstraint(opType, "0", "0");
        }

        CellRangeAddressList addressList = new CellRangeAddressList(row, row, col, col);
        DataValidation validation = dvHelper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        validation.setEmptyCellAllowed(allowBlank);

        String errorTitle = McpToolParamParser.getString(params, "error_title", "输入错误");
        String errorMessage = McpToolParamParser.getString(params, "error_message", "请输入有效值");
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox(errorTitle, errorMessage);

        sheet.addValidationData(validation);
        return McpToolResult.success("数据验证设置成功");
    }

    // ==================== 冻结与筛选 ====================

    private McpToolResult freezePanes(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer rowSplit = McpToolParamParser.getInteger(params, "row_split", 0);
        Integer colSplit = McpToolParamParser.getInteger(params, "col_split", 0);

        if (rowSplit == 0 && colSplit == 0)
            return McpToolResult.error("freeze_panes需要row_split或col_split参数至少一个大于0");

        sheet.createFreezePane(colSplit, rowSplit);
        return McpToolResult.success("冻结窗格设置成功", Map.of("row_split", rowSplit, "col_split", colSplit));
    }

    private McpToolResult setAutoFilter(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col", 0);
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (startRow == null || endRow == null || endCol == null)
            return McpToolResult.error("set_auto_filter需要start_row, end_row, end_col参数");

        sheet.setAutoFilter(new CellRangeAddress(startRow, endRow, startCol, endCol));
        return McpToolResult.success("自动筛选设置成功");
    }

    // ==================== 条件格式 ====================

    private McpToolResult setConditionalFormat(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col");
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (startRow == null || endRow == null || startCol == null || endCol == null)
            return McpToolResult.error("set_conditional_format需要start_row, end_row, start_col, end_col参数");

        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return McpToolResult.error("条件格式仅支持XLSX格式");
        }

        String conditionType = McpToolParamParser.getString(params, "condition_type", "CELL_VALUE");
        String conditionOperator = McpToolParamParser.getString(params, "condition_operator", "GT");
        String formula = McpToolParamParser.getString(params, "formula");
        Map<String, Object> style = parseStyleParam(params);

        CellRangeAddress[] ranges = { new CellRangeAddress(startRow, endRow, startCol, endCol) };

        org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting sheetCF = xssfSheet
                .getSheetConditionalFormatting();

        org.apache.poi.ss.usermodel.ConditionalFormattingRule rule;
        if ("FORMULA".equals(conditionType.toUpperCase())) {
            rule = sheetCF.createConditionalFormattingRule(formula);
        } else {
            byte op = switch (conditionOperator.toUpperCase()) {
                case "BETWEEN" -> ComparisonOperator.BETWEEN;
                case "NOT_BETWEEN" -> ComparisonOperator.NOT_BETWEEN;
                case "EQUAL" -> ComparisonOperator.EQUAL;
                case "NOT_EQUAL" -> ComparisonOperator.NOT_EQUAL;
                case "GT" -> ComparisonOperator.GT;
                case "LT" -> ComparisonOperator.LT;
                case "GE" -> ComparisonOperator.GE;
                case "LE" -> ComparisonOperator.LE;
                default -> ComparisonOperator.GT;
            };
            String formula1 = McpToolParamParser.getString(params, "formula1", formula != null ? formula : "0");
            String formula2 = McpToolParamParser.getString(params, "formula2", "");
            rule = sheetCF.createConditionalFormattingRule(op, formula1, formula2);
        }

        org.apache.poi.ss.usermodel.FontFormatting fontFmt = rule.createFontFormatting();
        if (style.containsKey("font_color")) {
            String colorHex = String.valueOf(style.get("font_color")).replace("#", "");
            byte[] rgb = hexToRgb(colorHex);
            if (rgb != null) {
                if (fontFmt instanceof org.apache.poi.xssf.usermodel.XSSFFontFormatting xff) {
                    xff.setFontColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
                }
            }
        }
        if (style.containsKey("bold") && toBool(style.get("bold"))) {
            fontFmt.setFontStyle(true, false);
        }
        if (style.containsKey("italic") && toBool(style.get("italic"))) {
            fontFmt.setFontStyle(false, true);
        }

        org.apache.poi.ss.usermodel.PatternFormatting patternFmt = rule.createPatternFormatting();
        if (style.containsKey("background_color")) {
            String bgColorHex = String.valueOf(style.get("background_color")).replace("#", "");
            byte[] bgRgb = hexToRgb(bgColorHex);
            if (bgRgb != null) {
                patternFmt.setFillBackgroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(bgRgb, null));
                patternFmt.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
            }
        }

        sheetCF.addConditionalFormatting(ranges, rule);
        return McpToolResult.success("条件格式设置成功");
    }

    // ==================== 打印设置 ====================

    private McpToolResult setPrintArea(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        Integer startRow = McpToolParamParser.getInteger(params, "start_row");
        Integer endRow = McpToolParamParser.getInteger(params, "end_row");
        Integer startCol = McpToolParamParser.getInteger(params, "start_col", 0);
        Integer endCol = McpToolParamParser.getInteger(params, "end_col");
        if (startRow == null || endRow == null || endCol == null)
            return McpToolResult.error("set_print_area需要start_row, end_row, end_col参数");

        int sheetIdx = workbook.getSheetIndex(sheet);
        workbook.setPrintArea(sheetIdx, startCol, endCol, startRow, endRow);
        return McpToolResult.success("打印区域设置成功");
    }

    private McpToolResult setSheetPrintSettings(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        String orientation = McpToolParamParser.getString(params, "orientation");
        String paperSize = McpToolParamParser.getString(params, "paper_size");
        Integer fitToWidth = McpToolParamParser.getInteger(params, "fit_to_width");
        Integer fitToHeight = McpToolParamParser.getInteger(params, "fit_to_height");

        PrintSetup printSetup = sheet.getPrintSetup();

        if (orientation != null) {
            if ("landscape".equalsIgnoreCase(orientation)) {
                printSetup.setLandscape(true);
            } else {
                printSetup.setLandscape(false);
            }
        }

        if (paperSize != null) {
            short ps = switch (paperSize.toUpperCase()) {
                case "A3" -> PrintSetup.A3_PAPERSIZE;
                case "LETTER" -> PrintSetup.LETTER_PAPERSIZE;
                case "B5" -> PrintSetup.B5_PAPERSIZE;
                default -> PrintSetup.A4_PAPERSIZE;
            };
            printSetup.setPaperSize(ps);
        }

        if (fitToWidth != null || fitToHeight != null) {
            sheet.setFitToPage(true);
            if (fitToWidth != null)
                printSetup.setFitWidth((short) fitToWidth.intValue());
            if (fitToHeight != null)
                printSetup.setFitHeight((short) fitToHeight.intValue());
        }

        return McpToolResult.success("打印设置成功");
    }

    // ==================== 图片 ====================

    private McpToolResult addImage(Workbook workbook, Map<String, Object> params) {
        Sheet sheet = getSheet(workbook, params);
        String imageKey = McpToolParamParser.getString(params, "image_key");
        if (imageKey == null || imageKey.isBlank())
            return McpToolResult.error("add_image需要image_key参数");

        Integer row = McpToolParamParser.getInteger(params, "row", 0);
        Integer col = McpToolParamParser.getInteger(params, "col", 0);
        Integer width = McpToolParamParser.getInteger(params, "image_width", 200);
        Integer height = McpToolParamParser.getInteger(params, "image_height", 150);

        try (InputStream imageStream = fileStorageService.download(imageKey)) {
            byte[] imageBytes = imageStream.readAllBytes();
            int imageType = guessImageType(imageKey, imageBytes);

            int pictureIdx = workbook.addPicture(imageBytes, imageType);

            org.apache.poi.ss.usermodel.Drawing<?> drawing = sheet.createDrawingPatriarch();

            org.apache.poi.ss.usermodel.ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
            anchor.setCol1(col);
            anchor.setRow1(row);
            anchor.setCol2(col + 1);
            anchor.setRow2(row + 1);
            anchor.setAnchorType(org.apache.poi.ss.usermodel.ClientAnchor.AnchorType.MOVE_AND_RESIZE);

            drawing.createPicture(anchor, pictureIdx);

            return McpToolResult.success("图片插入成功", Map.of("row", row, "col", col));
        } catch (Exception e) {
            return McpToolResult.error("插入图片失败: " + e.getMessage());
        }
    }

    // ==================== 查找替换 ====================

    private McpToolResult findText(Workbook workbook, Map<String, Object> params) {
        String text = McpToolParamParser.getString(params, "text");
        if (text == null || text.isBlank())
            return McpToolResult.error("find_text需要text参数");
        int maxResults = McpToolParamParser.getInteger(params, "max_results", 20);

        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        List<Map<String, Object>> results = new ArrayList<>();

        int searchSheets = sheetName != null && !sheetName.isBlank() ? 1 : workbook.getNumberOfSheets();
        for (int s = 0; s < searchSheets && results.size() < maxResults; s++) {
            Sheet sheet = sheetName != null && !sheetName.isBlank()
                    ? workbook.getSheet(sheetName)
                    : workbook.getSheetAt(s);
            if (sheet == null)
                continue;

            for (int r = 0; r <= sheet.getLastRowNum() && results.size() < maxResults; r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;
                for (int c = 0; c < row.getLastCellNum() && results.size() < maxResults; c++) {
                    Cell cell = row.getCell(c);
                    String cellValue = getCellValue(cell);
                    if (cellValue.contains(text)) {
                        Map<String, Object> match = new LinkedHashMap<>();
                        match.put("sheet", sheet.getSheetName());
                        match.put("row", r);
                        match.put("col", c);
                        match.put("cell_ref", CellReference.convertNumToColString(c) + (r + 1));
                        match.put("value", cellValue);
                        results.add(match);
                    }
                }
            }
        }

        return McpToolResult.success("查找完成", Map.of("found_count", results.size(), "results", results));
    }

    private McpToolResult replaceText(Workbook workbook, Map<String, Object> params) {
        String oldText = McpToolParamParser.getString(params, "old_text");
        String newText = McpToolParamParser.getString(params, "new_text", "");
        boolean matchCase = McpToolParamParser.getBool(params, "match_case", false);
        if (oldText == null || oldText.isBlank())
            return McpToolResult.error("replace_text需要old_text参数");

        Sheet sheet = getSheet(workbook, params);
        int replaceCount = 0;

        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null || cell.getCellType() != CellType.STRING)
                    continue;
                String cellValue = cell.getStringCellValue();
                String searchIn = matchCase ? cellValue : cellValue.toLowerCase();
                String searchFor = matchCase ? oldText : oldText.toLowerCase();
                if (searchIn.contains(searchFor)) {
                    String newValue = matchCase
                            ? cellValue.replace(oldText, newText)
                            : cellValue.replaceAll("(?i)" + java.util.regex.Pattern.quote(oldText),
                                    java.util.regex.Matcher.quoteReplacement(newText));
                    cell.setCellValue(newValue);
                    replaceCount++;
                }
            }
        }

        if (replaceCount == 0) {
            return McpToolResult.error("未找到要替换的文本: " + oldText);
        }
        return McpToolResult.success("文本替换成功", Map.of("replace_count", replaceCount));
    }

    // ==================== 辅助方法 ====================

    private void copyCellValue(Cell src, Cell dest) {
        switch (src.getCellType()) {
            case STRING -> dest.setCellValue(src.getStringCellValue());
            case NUMERIC -> dest.setCellValue(src.getNumericCellValue());
            case BOOLEAN -> dest.setCellValue(src.getBooleanCellValue());
            case FORMULA -> dest.setCellValue(src.getCellFormula());
            case BLANK -> dest.setBlank();
            default -> dest.setCellValue(String.valueOf(src));
        }
    }

    private void copyCellStyle(Cell src, Cell dest) {
        CellStyle srcStyle = src.getCellStyle();
        if (srcStyle != null) {
            CellStyle newStyle = dest.getSheet().getWorkbook().createCellStyle();
            newStyle.cloneStyleFrom(srcStyle);
            dest.setCellStyle(newStyle);
        }
    }

    private int guessImageType(String fileName, byte[] imageBytes) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".png"))
            return XSSFWorkbook.PICTURE_TYPE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return XSSFWorkbook.PICTURE_TYPE_JPEG;
        if (lower.endsWith(".gif"))
            return XSSFWorkbook.PICTURE_TYPE_GIF;
        if (lower.endsWith(".bmp"))
            return XSSFWorkbook.PICTURE_TYPE_BMP;
        if (lower.endsWith(".tiff") || lower.endsWith(".tif"))
            return XSSFWorkbook.PICTURE_TYPE_TIFF;
        if (imageBytes != null && imageBytes.length >= 4) {
            if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50)
                return XSSFWorkbook.PICTURE_TYPE_PNG;
            if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8)
                return XSSFWorkbook.PICTURE_TYPE_JPEG;
            if (imageBytes[0] == 0x47 && imageBytes[1] == 0x49)
                return XSSFWorkbook.PICTURE_TYPE_GIF;
        }
        return XSSFWorkbook.PICTURE_TYPE_PNG;
    }

    @SuppressWarnings("unchecked")
    private List<Object> resolveListParam(Map<String, Object> params, String paramName) {
        Object raw = params.get(paramName);
        if (raw instanceof List) {
            return (List<Object>) raw;
        }
        String strVal = McpToolParamParser.getString(params, paramName, "[]");
        if (strVal == null || strVal.isBlank() || "[]".equals(strVal.trim())) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(strVal, Object.class);
        } catch (Exception e) {
            log.warn("解析{}参数失败: {}", paramName, e.getMessage());
            return new ArrayList<>();
        }
    }

    private static byte[] hexToRgb(String hex) {
        if (hex == null || hex.isEmpty())
            return null;
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        if (hex.length() != 6)
            return null;
        return new byte[] {
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private static boolean toBool(Object value) {
        if (value == null)
            return false;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
