package com.example.core.system.tool;

import java.util.HashMap;
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
public class DeleteFileSystemTool implements SystemTool {

    private final AgentFileStorageMapper fileStorageMapper;
    private final FileStorageService fileStorageService;

    @Override
    public String getName() {
        return "delete_file";
    }

    @Override
    public String getDescription() {
        return "删除指定文件。通过file_key指定要删除的文件，删除操作不可恢复，请务必谨慎。"
                + "建议在删除前先使用ask_user工具确认用户是否真的要删除该文件。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("file_key", Map.of("type", "string", "description",
                "文件存储键（必填，可通过list_files工具获取）", "required", true));
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

        String fileKey = SystemToolParamParser.getString(params, "file_key");
        if (fileKey == null || fileKey.isBlank()) {
            return SystemToolResult.error("缺少必填参数: file_key");
        }

        try {
            // 根据file_key和user_id查找文件记录
            LambdaQueryWrapper<AgentFileStorage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentFileStorage::getStorageKey, fileKey)
                    .eq(AgentFileStorage::getUserId, userId)
                    .eq(AgentFileStorage::getDeleted, 0);
            AgentFileStorage file = fileStorageMapper.selectOne(wrapper);

            if (file == null) {
                return SystemToolResult.error("未找到指定文件: " + fileKey);
            }

            // 删除存储文件
            try {
                fileStorageService.delete(file.getStorageKey());
            } catch (Exception e) {
                log.warn("删除存储文件失败(将继续删除DB记录): key={}, error={}", file.getStorageKey(), e.getMessage());
            }

            // 物理删除数据库记录
            fileStorageMapper.physicalDeleteById(file.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("file_id", file.getId());
            data.put("file_name", file.getOriginalName());
            data.put("file_key", file.getStorageKey());
            data.put("file_type", file.getFileType());

            Map<String, Object> deletedFile = new HashMap<>();
            deletedFile.put("file_id", file.getId());
            deletedFile.put("file_name", file.getOriginalName());
            deletedFile.put("file_key", file.getStorageKey());
            deletedFile.put("file_type", file.getFileType());
            data.put("deleted_files", java.util.List.of(deletedFile));
            data.put("deleted_count", 1);

            log.info("文件已删除: userId={}, fileId={}, fileName={}", userId, file.getId(), file.getOriginalName());
            return SystemToolResult.success("文件已删除: " + file.getOriginalName(), data);
        } catch (Exception e) {
            log.error("删除文件失败: fileKey={}, error={}", fileKey, e.getMessage());
            return SystemToolResult.error("删除文件失败: " + e.getMessage());
        }
    }
}
