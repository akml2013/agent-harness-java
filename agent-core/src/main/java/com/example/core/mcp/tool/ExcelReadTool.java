package com.example.core.mcp.tool;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
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
public class ExcelReadTool implements McpTool {

    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "read_excel";
    }

    @Override
    public String getDescription() {
        return "读取Excel文档(.xlsx/.xls)内容，提取工作表名称、表头、数据和样式信息（加粗、字号、颜色、背景色、数字格式等）";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("file_key", "MinIO中文件的存储键(必填)");
        schema.put("sheet_name", "工作表名称(可选，默认读取第一个)");
        schema.put("max_rows", "最大读取行数(可选，默认100)");
        schema.put("include_style", "是否包含样式信息(可选，默认true)");
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> params) {
        String fileKey = McpToolParamParser.getString(params, "file_key");
        if (fileKey == null || fileKey.isBlank()) {
            return McpToolResult.error("缺少必填参数: file_key");
        }

        String sheetName = McpToolParamParser.getString(params, "sheet_name");
        int maxRows = McpToolParamParser.getInteger(params, "max_rows", 100);
        boolean includeStyle = McpToolParamParser.getBool(params, "include_style", true);

        try (InputStream is = fileStorageService.download(fileKey);
                Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet;
            if (sheetName != null && !sheetName.isBlank()) {
                sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    return McpToolResult.error("工作表不存在: " + sheetName);
                }
            } else {
                sheet = workbook.getSheetAt(0);
            }

            List<String> sheetNames = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }

            List<String> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();
            List<Map<String, Object>> styledHeaders = new ArrayList<>();
            List<List<Map<String, Object>>> styledRows = new ArrayList<>();

            int rowIndex = 0;
            for (Row row : sheet) {
                if (rowIndex >= maxRows)
                    break;

                List<String> rowData = new ArrayList<>();
                List<Map<String, Object>> styledRowData = new ArrayList<>();

                for (int ci = 0; ci < row.getLastCellNum(); ci++) {
                    Cell cell = row.getCell(ci);
                    String cellValue = getCellValue(cell);
                    rowData.add(cellValue);

                    if (rowIndex == 0) {
                        headers.add(cellValue);
                    }

                    if (includeStyle && cell != null) {
                        Map<String, Object> cellInfo = new LinkedHashMap<>();
                        cellInfo.put("value", cellValue);
                        Map<String, Object> cellStyle = ExcelStyleHelper.extractCellStyle(cell);
                        if (!cellStyle.isEmpty()) {
                            cellInfo.put("style", cellStyle);
                        }
                        styledRowData.add(cellInfo);
                    } else if (includeStyle) {
                        Map<String, Object> cellInfo = new LinkedHashMap<>();
                        cellInfo.put("value", cellValue);
                        styledRowData.add(cellInfo);
                    }
                }

                if (rowIndex == 0) {
                    if (includeStyle) {
                        styledHeaders = styledRowData;
                    }
                } else if (!rowData.isEmpty()) {
                    rows.add(rowData);
                    if (includeStyle) {
                        styledRows.add(styledRowData);
                    }
                }
                rowIndex++;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sheet_names", sheetNames);
            data.put("current_sheet", sheet.getSheetName());
            data.put("headers", headers);
            data.put("rows", rows);
            data.put("row_count", rows.size());
            data.put("file_key", fileKey);

            if (includeStyle) {
                data.put("styled_headers", styledHeaders);
                data.put("styled_rows", styledRows);

                List<Integer> columnWidths = new ArrayList<>();
                if (sheet.getRow(0) != null) {
                    for (int ci = 0; ci < sheet.getRow(0).getLastCellNum(); ci++) {
                        columnWidths.add(sheet.getColumnWidth(ci) / 256);
                    }
                }
                data.put("column_widths", columnWidths);

                List<Map<String, Object>> mergedRegions = new ArrayList<>();
                for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                    CellRangeAddress range = sheet.getMergedRegion(i);
                    Map<String, Object> region = new LinkedHashMap<>();
                    region.put("start_row", range.getFirstRow());
                    region.put("end_row", range.getLastRow());
                    region.put("start_col", range.getFirstColumn());
                    region.put("end_col", range.getLastColumn());
                    mergedRegions.add(region);
                }
                if (!mergedRegions.isEmpty()) {
                    data.put("merged_regions", mergedRegions);
                }
            }

            return McpToolResult.success("Excel文档读取成功", data);
        } catch (Exception e) {
            log.error("读取Excel文档失败: {}", e.getMessage());
            return McpToolResult.error("读取Excel文档失败: " + e.getMessage());
        }
    }

    String getCellValue(Cell cell) {
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
                CellType cachedType = cell.getCachedFormulaResultType();
                if (cachedType == CellType.NUMERIC) {
                    double val = cell.getNumericCellValue();
                    if (val == Math.floor(val) && !Double.isInfinite(val)) {
                        return String.valueOf((long) val);
                    }
                    return String.valueOf(val);
                } else if (cachedType == CellType.STRING) {
                    return cell.getStringCellValue();
                } else if (cachedType == CellType.BOOLEAN) {
                    return String.valueOf(cell.getBooleanCellValue());
                }
            } catch (Exception e) {
                log.debug("读取公式缓存值失败，返回公式字符串: {}", e.getMessage());
            }
            return "=" + cell.getCellFormula();
        }
        return "";
    }
}
