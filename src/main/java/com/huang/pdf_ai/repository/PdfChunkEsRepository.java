package com.huang.pdf_ai.repository;

import com.huang.pdf_ai.document.PdfChunkDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PdfChunkEsRepository extends ElasticsearchRepository<PdfChunkDocument, String> {
    // 按 pdfId 删除所有文档（用于清理）
    void deleteByPdfId(Long pdfId);
}