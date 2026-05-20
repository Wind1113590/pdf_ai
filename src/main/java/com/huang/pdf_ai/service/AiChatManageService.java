package com.huang.pdf_ai.service;

import com.huang.pdf_ai.entity.AiChatSession;
import com.huang.pdf_ai.mapper.AiChatSessionMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@RequiredArgsConstructor
public class AiChatManageService {

    @Autowired
    private AiChatService aiChatService;
    @Autowired AiChatMessageTxService aiChatMessageTxService;

    @Autowired
    private EmbeddingStore<TextSegment> qdrantEmbeddingStore;
    @Autowired
    private EmbeddingModel embeddingModel;

    // 你的mapper
    @Autowired
    private AiChatSessionMapper aiChatSessionMapper;


    @Autowired
    // 注入 Spring 管理的线程池
    private Executor chatDbExecutor;



    /**
     * 用户选中某个PDF后聊天
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param question  问题
     * @return 流式回答
     */
    //@Transactional
    public Flux<String> chatWithPdf(Long sessionId, Long userId, String question) {
        // 1. 获取会话绑定的PDF
        AiChatSession session = aiChatSessionMapper.selectById(sessionId);
        Long bindPdfId = session.getBindPdfId();

        if (bindPdfId == null) {
            return Flux.just("请先选择一个PDF");
        }
        //搞了半天，redis不支持meta过滤
        Filter filter = metadataKey("pdf_id").isEqualTo(bindPdfId.toString());

        // 2. 构建【只检索当前PDF】的检索器（核心：pdf_id过滤）
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(qdrantEmbeddingStore)
                .embeddingModel(embeddingModel)
                .filter(filter)
                .maxResults(3)
                .minScore(0.5)
                .build();

        // 3. 手动检索，获取【原文 + 页码】
        List<Content> contentList;

        try {
            contentList = retriever.retrieve(Query.from(question));
        } catch (Exception e) {//这里后面可以自定义一个检索异常
            // 检索异常兜底：直接让大模型回答，不返回错误
            Flux<String> responseFlux = aiChatService.chat(sessionId, "用户问题：" + question + "\n提示：未找到相关文档内容，请基于通用知识回答");

            // ========== 正确版本，永不报错 ==========
            AtomicReference<String> fullAnswer = new AtomicReference<>("");

            return responseFlux
                    .doOnNext(part -> fullAnswer.set(fullAnswer.get() + part))
                    .doFinally(signalType -> {
                        chatDbExecutor.execute(() -> {
                            aiChatMessageTxService.saveChatMessage(sessionId,userId,question,fullAnswer.get());

                        });
                    });
        }

        if (contentList.isEmpty()) {
            return Flux.just("未在当前PDF中找到相关内容，请换个问题试试");
        }


        List<TextSegment> segments = contentList.stream()
                .map(Content::textSegment)
                .toList();

        String retrievalContent = segments.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n---\n"));


        // 5. 收集所有页码（去重、数字升序）


        String pageNums = segments.stream()
                .map(s -> s.metadata().getString("page"))
                .filter(page -> page != null && !page.isBlank())
                .distinct()
                .map(Integer::parseInt)       // 转数字，实现自然排序
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));


        System.out.println("最终页码：" + pageNums);
        System.out.println("最终内容：" + retrievalContent);


        // 6. 调用AI流式聊天
        // 把【检索内容 + 页码】一起拼进问题里发给 AI
        String prompt = "参考内容：\n" + retrievalContent +
                "\n来源页码：" + pageNums +
                "\n用户问题：" + question;

        Flux<String> responseFlux = aiChatService.chat(sessionId, prompt);

        // ========== 正确版本，永不报错 ==========
        AtomicReference<String> fullAnswer = new AtomicReference<>("");

        return responseFlux
                .doOnNext(part -> fullAnswer.set(fullAnswer.get() + part))
                .doFinally(signalType -> {
                    chatDbExecutor.execute(() -> {
                            aiChatMessageTxService.saveChatMessage(sessionId,userId,question,fullAnswer.get(),retrievalContent,pageNums);
                    });
                });
    }

}