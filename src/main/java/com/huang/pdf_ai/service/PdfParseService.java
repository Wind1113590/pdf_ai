package com.huang.pdf_ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.pdf_ai.entity.PdfChunk;
import com.huang.pdf_ai.entity.PdfDocument;
import com.huang.pdf_ai.mapper.PdfChunkMapper;
import com.huang.pdf_ai.mapper.PdfDocumentMapper;
import com.huang.pdf_ai.util.AliOssUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PdfParseService {

    @Autowired
    private EmbeddingStore<TextSegment> qdrantEmbeddingStore;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private DocumentSplitter documentSplitter;

    @Autowired
    private PdfDocumentMapper pdfDocumentMapper;
    @Autowired
    private PdfChunkMapper pdfChunkMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;   // 新增 Redis
    @Autowired
    private TransactionTemplate transactionTemplate;       // 编程式事务

    @Autowired
    AliOssUtil aliOssUtil;

    // 状态常量
    public static final int PARSE_PENDING = 0;
    public static final int PARSE_PROCESSING = 1;
    public static final int PARSE_SUCCESS = 2;
    public static final int PARSE_FAILED = 3;

    public static Integer TOTAL_PAGES = null;

    // Redis key 前缀
    private static final String PARSE_STATUS_KEY = "pdf:parse:status:";

    /**
     * 异步解析 PDF（对外公开方法）
     * 责状态流转，优先写redis减少数据库IO操作，有效减少前端响应时间，通过编程式事务高效地添加事务（而不是Transactional这样毫无意义可能还会让已经插入但未解析的数据回滚）
     */
    @Async
    public void parseSinglePdf(MultipartFile file, Long pdfId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = "pdf/" + UUID.randomUUID() + extension;
            String pdfPath = aliOssUtil.upload(file.getBytes(), objectName);
            // 3. 耗时操作：在事务外执行（不占数据库连接）
            List<PageContent> pages = extractPages(pdfPath);// 耗时 PDF 解析
            if (pages.isEmpty()) throw new RuntimeException("无文本");

            // 4. 耗时操作：分块 + 向量化 + 组装 chunk（不占数据库连接）
            List<PdfChunk> chunkList = buildChunks(pdfId, pages);     // 内部做分块、向量化、Qdrant 存储

            PdfDocument pdfDocument1 = new PdfDocument();
            pdfDocument1.setFilePath(pdfPath);
            pdfDocument1.setId(pdfId);
            pdfDocumentMapper.updateById(pdfDocument1);

            // 4. 短事务：只更新总页数 + 批量插入 chunk
            transactionTemplate.execute(status -> {
                PdfDocument doc = pdfDocumentMapper.selectById(pdfId);
                doc.setTotalPages(pages.size());
                pdfDocumentMapper.updateById(doc);
                if (!chunkList.isEmpty()) {
                    pdfChunkMapper.insertBatch(chunkList);
                }
                return null;
            });

            // 4. 事务成功，更新状态为 SUCCESS
            PdfDocument pdfDocument2 = new PdfDocument();
            setParseStatus(pdfId, PARSE_SUCCESS);
            pdfDocument2.setId(pdfId);
            pdfDocument2.setParseStatus(PARSE_SUCCESS);
            pdfDocumentMapper.updateById(pdfDocument2);

        } catch (Exception e) {
            log.error("PDF 解析失败，pdfId: {}", pdfId, e);
            // 更新状态为 FAILED
            if (pdfId != null) {
                setParseStatus(pdfId, PARSE_FAILED);

                PdfDocument pdfDocument = new PdfDocument();
                pdfDocument.setId(pdfId);
                pdfDocument.setParseStatus(PARSE_FAILED);
                pdfDocumentMapper.updateById(pdfDocument);

                //清理可能残留的部分向量数据
                cleanPartialVectors(pdfId);
            }
        }
    }

    /**
     * 提取 PDF 所有页的文本内容 路径->加载pdf->取text和对应页码
     */
    private List<PageContent> extractPages(String pdfPath) {
        List<PageContent> pages = new ArrayList<>();
        try (PDDocument pdDocument = PDDocument.load(new URL(pdfPath).openStream())) {
            int totalPages = pdDocument.getNumberOfPages();
            TOTAL_PAGES = totalPages;
            PDFTextStripper stripper = new PDFTextStripper();
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String text = stripper.getText(pdDocument);
                if (text != null && !text.isBlank() && text.length() >= 20) {
                    pages.add(new PageContent(pageNum, text));
                } else {
                    log.warn("PDF 第 {} 页无有效文本，已跳过", pageNum);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF 加载或解析失败: " + pdfPath, e);
        }
        return pages;
    }

    /**
     * 设置 Redis 解析状态（带过期时间）
     */
    private void setParseStatus(Long pdfId, int status) {
        String key = PARSE_STATUS_KEY + pdfId;
        redisTemplate.opsForValue().set(key, String.valueOf(status), 10, TimeUnit.MINUTES);
    }

    /**
     * 清理失败时可能残留的向量数据（根据 pdfId 删除所有关联向量）
     */
    private void cleanPartialVectors(Long pdfId) {
        List<PdfChunk> chunks = pdfChunkMapper.selectList(
                new LambdaQueryWrapper<PdfChunk>().eq(PdfChunk::getPdfId, pdfId)
        );
        for (PdfChunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                qdrantEmbeddingStore.remove(chunk.getVectorId());
            }
        }
    }

    /**
     * 删除 PDF（保持原有事务）
     */
    @Transactional
    public void deletePdf(Long pdfId) {
        // 查询所有分块
        List<PdfChunk> chunks = pdfChunkMapper.selectList(
                new LambdaQueryWrapper<PdfChunk>().eq(PdfChunk::getPdfId, pdfId)
        );
        // 删除 Qdrant 中的向量
        for (PdfChunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                qdrantEmbeddingStore.remove(chunk.getVectorId());
            }
        }
        // 删除 MySQL 记录
        pdfChunkMapper.delete(new LambdaQueryWrapper<PdfChunk>().eq(PdfChunk::getPdfId, pdfId));
        pdfDocumentMapper.deleteById(pdfId);
        // 删除 Redis 状态缓存（可选）
        redisTemplate.delete(PARSE_STATUS_KEY + pdfId);
    }

    // 内部类，封装页数+文本
    @Data
    @AllArgsConstructor
    private static class PageContent {
        private int pageNum;
        private String text;
    }

    // text->chunk(segment)将分块、向量化、Qdrant 存储抽离出来，返回组装好的 chunk 列表（不操作 MySQL）最小的颗粒度应该就是文本块，带pdf_id和page
    private List<PdfChunk> buildChunks(Long pdfId, List<PageContent> pages) {
        List<PdfChunk> chunkList = new ArrayList<>();
        int chunkIndex = 0;
        for (PageContent page : pages) {
            Document doc = Document.from(page.getText(), Metadata.metadata("page", String.valueOf(page.getPageNum())));
            List<TextSegment> segments = documentSplitter.split(doc);//一页文本 -> 很多文本块
            for (TextSegment segment : segments) {//每块都插一条sql
                Metadata metadata = new Metadata();
                metadata.put("page", String.valueOf(page.getPageNum()));
                metadata.put("pdf_id", String.valueOf(pdfId));
                TextSegment enrichedSegment = TextSegment.from(segment.text(), metadata);//带元数据的文本块

                Embedding embedding = embeddingModel.embed(enrichedSegment).content();
                String vectorId = qdrantEmbeddingStore.add(embedding, enrichedSegment);

                PdfChunk chunk = new PdfChunk();
                chunk.setPdfId(pdfId);
                chunk.setChunkIndex(chunkIndex++);
                chunk.setContent(segment.text());
                chunk.setPageNum(page.getPageNum());
                chunk.setVectorId(vectorId);
                chunkList.add(chunk);
            }
        }
        return chunkList;
    }

}