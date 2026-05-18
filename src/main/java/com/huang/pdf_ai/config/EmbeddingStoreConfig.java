package com.huang.pdf_ai.config;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RedisEmbeddingStore redisEmbeddingStore;//redis作为向量数据库

    // 全局统一分块器：英文500token、重叠100token（适配哈利波特英文PDF）
    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(500, 100);
    }

    // 动态生成：按PDF ID过滤的检索器（官方标准写法，多PDF隔离核心）
    public ContentRetriever getPdfContentRetriever(Long bindPdfId) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(redisEmbeddingStore)
                .embeddingModel(embeddingModel)
                .filter(MetadataFilterBuilder.metadataKey("pdf_id").isEqualTo(bindPdfId))
                .maxResults(3)
                .minScore(0.5)
                .build();
    }
}