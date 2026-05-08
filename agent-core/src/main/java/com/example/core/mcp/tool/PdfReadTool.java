package com.example.core.mcp.tool;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
public class PdfReadTool implements McpTool {

    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "read_pdf";
    }

    @Override
    public String getDescription() {
        return "读取PDF文档内容，提取全部文本";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("file_key", "MinIO中文件的存储键(必填)");
        schema.put("start_page", "起始页码(可选，从1开始)");
        schema.put("end_page", "结束页码(可选)");
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> params) {
        String fileKey = McpToolParamParser.getString(params, "file_key");
        if (fileKey == null || fileKey.isBlank()) {
            return McpToolResult.error("缺少必填参数: file_key");
        }

        Integer startPage = McpToolParamParser.getInteger(params, "start_page");
        Integer endPage = McpToolParamParser.getInteger(params, "end_page");

        try (InputStream is = fileStorageService.download(fileKey)) {
            byte[] bytes = is.readAllBytes();

            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();

                if (startPage != null && startPage > 0) {
                    stripper.setStartPage(startPage);
                }
                if (endPage != null && endPage > 0) {
                    stripper.setEndPage(endPage);
                }

                String content = stripper.getText(document);
                int totalPages = document.getNumberOfPages();

                Map<String, Object> data = new HashMap<>();
                data.put("content", content);
                data.put("total_pages", totalPages);
                data.put("file_key", fileKey);

                return McpToolResult.success("PDF文档读取成功", data);
            }
        } catch (Exception e) {
            log.error("读取PDF文档失败: {}", e.getMessage());
            return McpToolResult.error("读取PDF文档失败: " + e.getMessage());
        }
    }
}
