package com.example.core.system.tool;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.AgentFileStorage;
import com.example.core.mapper.AgentFileStorageMapper;
import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RenameFileSystemTool implements SystemTool {

    private final AgentFileStorageMapper fileStorageMapper;

    @Override
    public String getName() {
        return "rename_file";
    }

    @Override
    public String getDescription() {
        return "修改文件的显示名称。当基于用户上传文件首次修改后，建议使用此工具将文件重命名为更有意义的名称。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("file_key", Map.of("type", "string", "description",
                "文件存储键（必填，可通过list_files工具获取）", "required", true));
        schema.put("new_name", Map.of("type", "string", "description",
                "新的文件显示名称（必填，如\"项目周报.docx\"）", "required", true));
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

        String newName = SystemToolParamParser.getString(params, "new_name");
        if (newName == null || newName.isBlank()) {
            return SystemToolResult.error("缺少必填参数: new_name");
        }

        try {
            LambdaQueryWrapper<AgentFileStorage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentFileStorage::getStorageKey, fileKey)
                    .eq(AgentFileStorage::getUserId, userId)
                    .eq(AgentFileStorage::getDeleted, 0);
            AgentFileStorage file = fileStorageMapper.selectOne(wrapper);

            if (file == null) {
                return SystemToolResult.error("未找到指定文件: " + fileKey);
            }

            String oldName = file.getOriginalName();

            LambdaUpdateWrapper<AgentFileStorage> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(AgentFileStorage::getId, file.getId())
                    .set(AgentFileStorage::getOriginalName, newName);
            fileStorageMapper.update(null, updateWrapper);

            log.info("文件重命名成功: userId={}, fileId={}, oldName={}, newName={}",
                    userId, file.getId(), oldName, newName);

            Map<String, Object> data = new HashMap<>();
            data.put("file_id", file.getId());
            data.put("file_key", fileKey);
            data.put("old_name", oldName);
            data.put("new_name", newName);

            return SystemToolResult.success("文件已重命名: " + oldName + " → " + newName, data);
        } catch (Exception e) {
            log.error("重命名文件失败: fileKey={}, error={}", fileKey, e.getMessage());
            return SystemToolResult.error("重命名文件失败: " + e.getMessage());
        }
    }
}
