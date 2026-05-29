package com.huang.pdf_ai.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "pdf_chunks")
public class PdfChunkDocument {
    @Id
    private String id;          // 使用 vectorId 或 chunkId（唯一）
    private Long pdfId;
    private Integer chunkIndex;
    private Integer pageNum;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;     // 文本块内容，用于 BM25 检索
}