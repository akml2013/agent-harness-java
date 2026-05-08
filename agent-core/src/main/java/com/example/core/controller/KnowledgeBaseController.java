package com.example.core.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.core.entity.AgentKnowledgeBase;
import com.example.core.service.KnowledgeBaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/agent/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public Map<String, Object> listKnowledgeBases(@RequestParam Long user_id) {
        try {
            List<AgentKnowledgeBase> kbs = knowledgeBaseService.listKnowledgeBases(user_id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("knowledge_bases", kbs);
            result.put("total", kbs.size());
            return result;
        } catch (Exception e) {
            log.error("获取知识库列表失败: userId={}, error={}", user_id, e.getMessage());
            return errorResponse("获取知识库列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{kbId}")
    public Map<String, Object> getKnowledgeBase(@RequestParam Long user_id,
            @PathVariable String kbId) {
        try {
            AgentKnowledgeBase kb = knowledgeBaseService.getKnowledgeBase(user_id, kbId);
            if (kb == null) {
                return errorResponse("知识库不存在");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("knowledge_base", kb);
            return result;
        } catch (Exception e) {
            log.error("获取知识库详情失败: kbId={}, error={}", kbId, e.getMessage());
            return errorResponse("获取知识库详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadKnowledgeBase(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user_id") Long userId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "chunk_strategy", defaultValue = "PARAGRAPH") String chunkStrategy,
            @RequestParam(value = "chunk_size", defaultValue = "500") Integer chunkSize,
            @RequestParam(value = "chunk_overlap", defaultValue = "50") Integer chunkOverlap) {
        try {
            if (file.isEmpty()) {
                return errorResponse("上传文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return errorResponse("文件名不能为空");
            }

            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (!List.of("docx", "txt", "pdf", "xlsx").contains(ext)) {
                return errorResponse("不支持的文件类型: " + ext + "，仅支持 docx/txt/pdf/xlsx");
            }

            AgentKnowledgeBase kb = knowledgeBaseService.uploadAndCreate(
                    userId,
                    file.getInputStream(),
                    fileName,
                    file.getContentType(),
                    file.getSize(),
                    name,
                    null,
                    chunkStrategy,
                    chunkSize,
                    chunkOverlap);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("kb_id", kb.getKbId());
            result.put("name", kb.getName());
            result.put("status", kb.getStatus());
            result.put("message", "知识库创建成功，正在后台建立索引");
            return result;
        } catch (Exception e) {
            log.error("上传知识库失败: userId={}, error={}", userId, e.getMessage());
            return errorResponse("上传知识库失败: " + e.getMessage());
        }
    }

    @PutMapping("/{kbId}")
    public Map<String, Object> updateKnowledgeBase(
            @RequestParam Long user_id,
            @PathVariable String kbId,
            @RequestBody Map<String, Object> body) {
        try {
            String name = body.get("name") != null ? body.get("name").toString() : null;
            String description = body.get("description") != null ? body.get("description").toString() : null;

            AgentKnowledgeBase kb = knowledgeBaseService.updateKnowledgeBase(user_id, kbId, name, description);
            if (kb == null) {
                return errorResponse("知识库不存在");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("knowledge_base", kb);
            return result;
        } catch (Exception e) {
            log.error("更新知识库失败: kbId={}, error={}", kbId, e.getMessage());
            return errorResponse("更新知识库失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{kbId}")
    public Map<String, Object> deleteKnowledgeBase(
            @RequestParam Long user_id,
            @PathVariable String kbId) {
        try {
            boolean deleted = knowledgeBaseService.deleteKnowledgeBase(user_id, kbId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", deleted);
            result.put("message", deleted ? "知识库删除成功" : "知识库不存在或删除失败");
            return result;
        } catch (Exception e) {
            log.error("删除知识库失败: kbId={}, error={}", kbId, e.getMessage());
            return errorResponse("删除知识库失败: " + e.getMessage());
        }
    }

    @PostMapping("/{kbId}/retry")
    public Map<String, Object> retryIndex(
            @RequestParam Long user_id,
            @PathVariable String kbId) {
        try {
            boolean retried = knowledgeBaseService.retryIndex(user_id, kbId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", retried);
            result.put("message", retried ? "已重新开始索引" : "知识库不存在或状态不允许重试");
            return result;
        } catch (Exception e) {
            log.error("重试索引失败: kbId={}, error={}", kbId, e.getMessage());
            return errorResponse("重试索引失败: " + e.getMessage());
        }
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }
}
