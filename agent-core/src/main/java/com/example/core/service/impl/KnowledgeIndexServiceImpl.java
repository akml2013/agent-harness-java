package com.example.core.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.AgentKnowledgeBase;
import com.example.core.entity.AgentKnowledgeChunk;
import com.example.core.management.VectorDataManager;
import com.example.core.mapper.AgentKnowledgeBaseMapper;
import com.example.core.mapper.AgentKnowledgeChunkMapper;
import com.example.core.memory.service.EmbeddingService;
import com.example.core.service.DeepseekService;
import com.example.core.service.KnowledgeIndexService;
import com.example.core.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    private static final String COLLECTION_NAME = "knowledge_chunks";
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百千零\\d]+[章篇节]|[一二三四五六七八九十]+[、.]|\\d+[、.]\\s)\\s*.*$",
            Pattern.MULTILINE);

    private final AgentKnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentKnowledgeChunkMapper knowledgeChunkMapper;
    private final FileStorageService fileStorageService;
    private final VectorDataManager vectorDataManager;
    private final EmbeddingService embeddingService;
    private final DeepseekService deepseekService;

    @Override
    @Async("knowledgeIndexExecutor")
    public void indexKnowledgeBase(String kbId) {
        log.info("开始异步索引知识库: kbId={}", kbId);
        try {
            updateStatus(kbId, AgentKnowledgeBase.STATUS_CHUNKING);

            AgentKnowledgeBase kb = getKb(kbId);
            if (kb == null) {
                log.error("知识库不存在: kbId={}", kbId);
                return;
            }

            String text = parseDocument(kb);
            if (text == null || text.isBlank()) {
                updateStatusWithError(kbId, "文档解析结果为空");
                return;
            }

            List<String> chunks = splitIntoChunks(text, kb.getChunkStrategy(), kb.getChunkSize(), kb.getChunkOverlap());
            if (chunks.isEmpty()) {
                updateStatusWithError(kbId, "文档分块结果为空");
                return;
            }

            List<AgentKnowledgeChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                AgentKnowledgeChunk chunk = new AgentKnowledgeChunk();
                chunk.setChunkId(UUID.randomUUID().toString().replace("-", ""));
                chunk.setKbId(kbId);
                chunk.setUserId(kb.getUserId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunkText);
                chunk.setContentHash(md5Hash(chunkText));
                chunk.setCharCount(chunkText.length());
                chunk.setMetadata(null);
                knowledgeChunkMapper.insert(chunk);
                chunkEntities.add(chunk);
            }

            LambdaUpdateWrapper<AgentKnowledgeBase> countWrapper = new LambdaUpdateWrapper<>();
            countWrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                    .set(AgentKnowledgeBase::getChunkCount, chunks.size());
            knowledgeBaseMapper.update(null, countWrapper);

            updateStatus(kbId, AgentKnowledgeBase.STATUS_EMBEDDING);

            int total = chunkEntities.size();
            for (int i = 0; i < total; i++) {
                AgentKnowledgeChunk chunk = chunkEntities.get(i);
                try {
                    float[] embedding = embeddingService.embed(chunk.getContent());
                    String milvusId = insertToMilvus(chunk, embedding, kb.getUserId());

                    LambdaUpdateWrapper<AgentKnowledgeChunk> chunkUpdate = new LambdaUpdateWrapper<>();
                    chunkUpdate.eq(AgentKnowledgeChunk::getChunkId, chunk.getChunkId())
                            .set(AgentKnowledgeChunk::getMilvusId, milvusId);
                    knowledgeChunkMapper.update(null, chunkUpdate);

                } catch (Exception e) {
                    log.warn("分块Embedding失败(跳过): chunkId={}, error={}", chunk.getChunkId(), e.getMessage());
                }

                int progress = (int) ((i + 1) * 100.0 / total);
                updateProgress(kbId, progress);
            }

            updateProgress(kbId, 100);

            if (kb.getDescription() == null || kb.getDescription().isBlank()) {
                String description = generateDescriptionWithRetry(kbId, text);
                if (description == null) {
                    updateStatusWithError(kbId, "知识库描述生成失败(3次重试均失败)");
                    return;
                }
            }

            updateStatus(kbId, AgentKnowledgeBase.STATUS_ACTIVE);
            log.info("知识库索引完成: kbId={}, chunks={}", kbId, total);

        } catch (Exception e) {
            log.error("知识库索引失败: kbId={}", kbId, e);
            updateStatusWithError(kbId, e.getMessage());
        }
    }

    @Override
    public void deleteKnowledgeBaseIndex(String kbId) {
        try {
            vectorDataManager.deleteByFilter(COLLECTION_NAME, "kbId == \"" + kbId + "\"");
            log.info("知识库Milvus向量删除完成: kbId={}", kbId);
        } catch (Exception e) {
            log.error("知识库Milvus向量删除失败: kbId={}, error={}", kbId, e.getMessage());
        }
    }

    private String parseDocument(AgentKnowledgeBase kb) {
        String fileType = kb.getFileType().toLowerCase();
        try (InputStream is = fileStorageService.download(kb.getStorageKey())) {
            return switch (fileType) {
                case "docx" -> parseDocx(is);
                case "txt" -> new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                case "pdf" -> parsePdf(is);
                case "xlsx" -> parseXlsx(is);
                default -> {
                    log.warn("不支持的文件类型: {}", fileType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("文档解析失败: kbId={}, fileType={}, error={}", kb.getKbId(), fileType, e.getMessage());
            return null;
        }
    }

    private String parseDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n\n");
                }
            }
            return sb.toString();
        }
    }

    private String parsePdf(InputStream is) throws Exception {
        org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(is.readAllBytes());
        try {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        } finally {
            document.close();
        }
    }

    private String parseXlsx(InputStream is) throws Exception {
        org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(is);
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        cells.add(getCellText(cell));
                    }
                    sb.append(String.join("\t", cells)).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } finally {
            workbook.close();
        }
    }

    private String getCellText(org.apache.poi.ss.usermodel.Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    List<String> splitIntoChunks(String text, String strategy, int chunkSize, int chunkOverlap) {
        return switch (strategy) {
            case AgentKnowledgeBase.STRATEGY_PARAGRAPH -> splitByParagraph(text, chunkSize, chunkOverlap);
            case AgentKnowledgeBase.STRATEGY_FIXED_SIZE -> splitByFixedSize(text, chunkSize, chunkOverlap);
            case AgentKnowledgeBase.STRATEGY_CHAPTER -> splitByChapter(text, chunkSize, chunkOverlap);
            default -> splitByParagraph(text, chunkSize, chunkOverlap);
        };
    }

    private List<String> splitByParagraph(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");

        StringBuilder currentChunk = new StringBuilder();
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty())
                continue;

            if (currentChunk.length() + para.length() + 2 > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                if (chunkOverlap > 0 && currentChunk.length() > chunkOverlap) {
                    String overlap = currentChunk.substring(currentChunk.length() - chunkOverlap);
                    currentChunk = new StringBuilder(overlap);
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(para);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private List<String> splitByFixedSize(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end).trim());
            start = end - chunkOverlap;
            if (start >= text.length())
                break;
        }
        return chunks;
    }

    private List<String> splitByChapter(String text, int chunkSize, int chunkOverlap) {
        List<String> chapters = new ArrayList<>();
        Matcher matcher = CHAPTER_PATTERN.matcher(text);

        int lastStart = 0;
        while (matcher.find()) {
            if (matcher.start() > lastStart) {
                String chapter = text.substring(lastStart, matcher.start()).trim();
                if (!chapter.isEmpty()) {
                    chapters.add(chapter);
                }
            }
            lastStart = matcher.start();
        }
        if (lastStart < text.length()) {
            String last = text.substring(lastStart).trim();
            if (!last.isEmpty()) {
                chapters.add(last);
            }
        }

        if (chapters.isEmpty()) {
            return splitByParagraph(text, chunkSize, chunkOverlap);
        }

        List<String> result = new ArrayList<>();
        for (String chapter : chapters) {
            if (chapter.length() <= chunkSize) {
                result.add(chapter);
            } else {
                result.addAll(splitByParagraph(chapter, chunkSize, chunkOverlap));
            }
        }
        return result;
    }

    private String insertToMilvus(AgentKnowledgeChunk chunk, float[] embedding, Long userId) {
        Map<String, Object> record = new HashMap<>();
        record.put("chunkId", chunk.getChunkId());
        record.put("kbId", chunk.getKbId());
        record.put("userId", userId);
        record.put("content", chunk.getContent().length() > 4096
                ? chunk.getContent().substring(0, 4096)
                : chunk.getContent());
        record.put("embedding", embedding);

        vectorDataManager.insert(COLLECTION_NAME, List.of(record));
        return chunk.getChunkId();
    }

    private AgentKnowledgeBase getKb(String kbId) {
        return knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getKbId, kbId)
                .eq(AgentKnowledgeBase::getDeleted, 0));
    }

    private void updateStatus(String kbId, String status) {
        LambdaUpdateWrapper<AgentKnowledgeBase> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                .set(AgentKnowledgeBase::getStatus, status);
        knowledgeBaseMapper.update(null, wrapper);
    }

    private void updateProgress(String kbId, int progress) {
        LambdaUpdateWrapper<AgentKnowledgeBase> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                .set(AgentKnowledgeBase::getProgress, progress);
        knowledgeBaseMapper.update(null, wrapper);
    }

    private void updateStatusWithError(String kbId, String errorMessage) {
        LambdaUpdateWrapper<AgentKnowledgeBase> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                .set(AgentKnowledgeBase::getStatus, AgentKnowledgeBase.STATUS_ERROR)
                .set(AgentKnowledgeBase::getErrorMessage, errorMessage != null && errorMessage.length() > 2000
                        ? errorMessage.substring(0, 2000)
                        : errorMessage);
        knowledgeBaseMapper.update(null, wrapper);
    }

    private String md5Hash(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String generateDescriptionWithRetry(String kbId, String documentText) {
        String sample = documentText.length() > 2000 ? documentText.substring(0, 2000) : documentText;
        String prompt = "请用一句话（不超过50个字）总结以下文档的核心内容，作为知识库的描述：\n\n" + sample;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String response = deepseekService.chatWithSystem(
                        "你是一个文档摘要助手。请简洁地总结文档内容，只输出总结文本，不要输出其他内容。",
                        prompt);
                if (response != null && !response.isBlank()) {
                    String description = response.trim();
                    if (description.length() > 200) {
                        description = description.substring(0, 200);
                    }
                    LambdaUpdateWrapper<AgentKnowledgeBase> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(AgentKnowledgeBase::getKbId, kbId)
                            .set(AgentKnowledgeBase::getDescription, description);
                    knowledgeBaseMapper.update(null, wrapper);
                    log.info("知识库描述生成成功: kbId={}, description={}", kbId, description);
                    return description;
                }
            } catch (Exception e) {
                log.warn("LLM生成知识库描述失败(第{}次): kbId={}, error={}", attempt, kbId, e.getMessage());
            }
        }

        log.warn("知识库描述生成失败(3次重试均失败): kbId={}", kbId);
        return null;
    }
}
