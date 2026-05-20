package com.huang.pdf_ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.pdf_ai.entity.PdfChunk;
import com.huang.pdf_ai.entity.PdfDocument;
import com.huang.pdf_ai.mapper.PdfChunkMapper;
import com.huang.pdf_ai.mapper.PdfDocumentMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
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

    /**
     * 适配 langchain4j 1.3.0‑beta9 版本
     * 解析PDF、分页、分块、存MySQL、Redis向量
     */
    @Transactional
    public void parseSinglePdf(String pdfPath,
                               Long userId,
                               String originalFilename,  // 原始文件名
                               Long fileSize             // 真实文件大小
                                ) {
        // 1. 插入PDF主表元数据
        PdfDocument pdfDocument = new PdfDocument();
        pdfDocument.setUserId(userId);
        pdfDocument.setName(originalFilename);
        pdfDocument.setFilePath(pdfPath);
        pdfDocument.setFileSize(fileSize);
        pdfDocument.setParseStatus(0);

        // ===================== 只加载一次 PDF =====================
        // try-with-resources 自动关闭，最安全
        try (PDDocument pdDocument = PDDocument.load(new URL(pdfPath).openStream())) {

            // ===================== 阶段1：获取总页数 =====================
            int totalPages;
            try {
                totalPages = pdDocument.getNumberOfPages();
            } catch (Exception e) {
                throw new RuntimeException("【错误】读取PDF总页数失败", e);
            }

            pdfDocument.setTotalPages(totalPages);
            // 插入主表
            pdfDocumentMapper.insert(pdfDocument);
            Long pdfId = pdfDocument.getId();

            // ===================== 阶段2：逐页解析文本 =====================
            List<Document> pageDocuments = new ArrayList<>();
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int pageNum = 1; pageNum <= 5; pageNum++) {
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    String pageText = stripper.getText(pdDocument);

                    if (pageText.isBlank() || pageText.length() < 20) {
                        System.out.println("⚠️ 跳过第 " + pageNum + " 页（无文字/图片页）");
                        continue;
                    }

                    Document pageDocument = Document.from(
                            pageText,
                            Metadata.metadata("page", String.valueOf(pageNum))
                    );
                    pageDocuments.add(pageDocument);
                }
            } catch (Exception e) {
                throw new RuntimeException("【错误】PDF文本解析失败", e);
            }

            // ===================== 阶段3：分块存储（不变） =====================

            int chunkIndex = 0;
            // 3. 遍历每一页，分块→存MySQL→存Redis
            for (Document pageDocument : pageDocuments) {
                Integer pageNum = Integer.parseInt(Objects.requireNonNull(pageDocument.metadata().getString("page")));
                // 新版：返回的是 TextSegment，不是 Document！！！
                List<TextSegment> chunks = documentSplitter.split(pageDocument);

                for (TextSegment chunk : chunks) { //每页分出很多文本段
                    String chunkText = chunk.text();

                    // 3.1 插入分块表（原文+页码）
                    PdfChunk pdfChunk = new PdfChunk();
                    pdfChunk.setPdfId(pdfId);
                    pdfChunk.setChunkIndex(chunkIndex++);
                    pdfChunk.setContent(chunkText);
                    pdfChunk.setPageNum(pageNum);

                    Metadata metadata = new Metadata();
                    //文本段对应的pdfId和page，后续检索根据这个来隔离不同会话的用户需要的pdf，page显示来源页码
                    metadata.put("page", String.valueOf(pageNum));
                    metadata.put("pdf_id", String.valueOf(pdfId));

                    // 3.2 构建带pdf_id元数据的文本段（检索隔离）
                    TextSegment segment = TextSegment.from(chunkText, metadata);


                    // 3.3 文本段与对应的数字向量存入Redis
                    Embedding embedding = embeddingModel.embed(segment).content();
                    String vectorId = qdrantEmbeddingStore.add(embedding, segment);

                    // 3.4 保存数字向量ID（用于删除pdf）
                    pdfChunk.setVectorId(vectorId);
                    pdfChunkMapper.insert(pdfChunk);
                }
            }

            // 4. 更新解析状态
            pdfDocument.setParseStatus(1);
            pdfDocumentMapper.updateById(pdfDocument);


        }// ===================== 外层catch：只捕获【文件打开/加载失败】 =====================
        catch (Exception e) {
            throw new RuntimeException("【错误】加载PDF文件失败", e);
        }

    }

    /**
     * 删除PDF + 对应Redis向量
     */
    @Transactional
    public void deletePdf(Long pdfId) {
        List<PdfChunk> chunkList = pdfChunkMapper.selectList(
                new LambdaQueryWrapper<PdfChunk>().eq(PdfChunk::getPdfId, pdfId)
        );
        for (PdfChunk chunk : chunkList) { //根据数字向量ID删 redis对应向量
            if (chunk.getVectorId() != null) {
                qdrantEmbeddingStore.remove(chunk.getVectorId());
            }
        }

        //删两张表对应行
        pdfChunkMapper.delete(new LambdaQueryWrapper<PdfChunk>().eq(PdfChunk::getPdfId, pdfId));
        pdfDocumentMapper.deleteById(pdfId);
    }



}