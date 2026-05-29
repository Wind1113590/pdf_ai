package com.huang.pdf_ai.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingStoreConfig {
    @Bean
    @Primary
    public EmbeddingStore<TextSegment> qdrantEmbeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host("127.0.0.1")
                .port(6334)
                .collectionName("pdf_collection")
                .build();
    }

    /*@Bean
    DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(500, 100);
    }

     */

    @Bean
    DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(75, 0);
    }
}