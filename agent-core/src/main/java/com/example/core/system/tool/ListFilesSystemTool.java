package com.example.core.system.tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.core.entity.AgentFileStorage;
import com.example.core.mapper.AgentFileStorageMapper;
import com.example.core.storage.FileStorageService;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListFilesSystemTool implements SystemTool {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentFileStorageMapper fileStorageMapper;
    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "查询当前会话的文件列表，按创建时间降序排列。"
                + "同名文件可能有多条记录代表不同版本，请优先使用时间最新的file_key。"
                + "支持按文件类型、时间范围、文件名模糊检索、来源筛选，支持多种排序方式。"
                + "source字段标识文件来源：upload=用户上传，generated=AI生成。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("file_type", Map.of("type", "string", "description",
                "文件类型过滤（可选，如docx/xlsx/pdf）"));
        schema.put("file_name", Map.of("type", "string", "description",
                "按文件名模糊检索（可选，LIKE %keyword%）"));
        schema.put("source", Map.of("type", "string", "description",
                "按来源筛选（可选，upload=用户上传，generated=AI生成）"));
        schema.put("sort_by", Map.of("type", "string", "description",
                "排序字段（可选，create_time(默认)/file_name/file_size）"));
        schema.put("sort_order", Map.of("type", "string", "description",
                "排序方向（可选，desc(默认)/asc）"));
        schema.put("limit", Map.of("type", "integer", "description",
                "返回数量限制（可选，默认10，最大50）"));
        schema.put("time_range", Map.of("type", "string", "description",
                "时间范围筛选（可选，如1h=最近1小时, 24h=最近24小时, 7d=最近7天）"));
        schema.put("user_id", Map.of("type", "integer", "description", "用户ID（系统自动注入）"));
        schema.put("session_id", Map.of("type", "string", "description", "会话ID（系统自动注入）"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        Long userId = SystemToolParamParser.getLong(params, "user_id");
        if (userId == null) {
            return SystemToolResult.error("缺少必填参数: user_id");
        }

        String sessionId = SystemToolParamParser.getString(params, "session_id");
        String fileType = SystemToolParamParser.getString(params, "file_type");
        String fileName = SystemToolParamParser.getString(params, "file_name");
        String source = SystemToolParamParser.getString(params, "source");
        String sortBy = SystemToolParamParser.getString(params, "sort_by", "create_time");
        String sortOrder = SystemToolParamParser.getString(params, "sort_order", "desc");
        String timeRange = SystemToolParamParser.getString(params, "time_range");
        int limit = SystemToolParamParser.getInteger(params, "limit", 10);
        limit = Math.min(Math.max(limit, 1), 50);

        try {
            LambdaQueryWrapper<AgentFileStorage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentFileStorage::getUserId, userId)
                    .eq(AgentFileStorage::getDeleted, 0)
                    .eq(sessionId != null && !sessionId.isBlank(), AgentFileStorage::getSessionId, sessionId)
                    .eq(fileType != null && !fileType.isBlank(), AgentFileStorage::getFileType, fileType)
                    .eq(source != null && !source.isBlank(), AgentFileStorage::getSource, source)
                    .like(fileName != null && !fileName.isBlank(), AgentFileStorage::getOriginalName, fileName);

            if (timeRange != null && !timeRange.isBlank()) {
                LocalDateTime afterTime = parseTimeRange(timeRange);
                if (afterTime != null) {
                    wrapper.ge(AgentFileStorage::getCreateTime, afterTime);
                }
            }

            boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
            switch (sortBy.toLowerCase()) {
                case "file_name" -> wrapper.orderBy(true, isAsc, AgentFileStorage::getOriginalName);
                case "file_size" -> wrapper.orderBy(true, isAsc, AgentFileStorage::getFileSize);
                default -> wrapper.orderBy(true, isAsc, AgentFileStorage::getCreateTime);
            }
            wrapper.last("LIMIT " + limit);

            List<AgentFileStorage> files = fileStorageMapper.selectList(wrapper);

            List<Map<String, Object>> fileList = new ArrayList<>();
            for (AgentFileStorage file : files) {
                Map<String, Object> fileInfo = new LinkedHashMap<>();
                fileInfo.put("file_name", file.getOriginalName());
                fileInfo.put("file_key", file.getStorageKey());
                fileInfo.put("file_type", file.getFileType());
                fileInfo.put("source", file.getSource());
                fileInfo.put("create_time",
                        file.getCreateTime() != null ? file.getCreateTime().format(TIME_FMT) : null);
                fileInfo.put("file_size", file.getFileSize());
                fileList.add(fileInfo);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("files", fileList);
            data.put("total", fileList.size());
            data.put("hint", "文件按" + getSortDescription(sortBy, sortOrder) + "排列。"
                    + "source字段: upload=用户上传, generated=AI生成。"
                    + "同名文件可能有多条记录，请优先使用时间最新的file_key");

            return SystemToolResult.success("查询到" + fileList.size() + "个文件", data);
        } catch (Exception e) {
            log.error("查询文件列表失败: {}", e.getMessage());
            return SystemToolResult.error("查询文件列表失败: " + e.getMessage());
        }
    }

    private String getSortDescription(String sortBy, String sortOrder) {
        String fieldDesc = switch (sortBy.toLowerCase()) {
            case "file_name" -> "文件名";
            case "file_size" -> "文件大小";
            default -> "创建时间";
        };
        String orderDesc = "asc".equalsIgnoreCase(sortOrder) ? "升序" : "降序";
        return fieldDesc + orderDesc;
    }

    private LocalDateTime parseTimeRange(String timeRange) {
        try {
            String trimmed = timeRange.trim().toLowerCase();
            if (trimmed.endsWith("h")) {
                long hours = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                return LocalDateTime.now().minusHours(hours);
            } else if (trimmed.endsWith("d")) {
                long days = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                return LocalDateTime.now().minusDays(days);
            } else if (trimmed.endsWith("m")) {
                long minutes = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                return LocalDateTime.now().minusMinutes(minutes);
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析时间范围: {}", timeRange);
        }
        return null;
    }
}
