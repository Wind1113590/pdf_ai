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
import org.springframework.transaction.annotation.Transactional;
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
    //@Transactional//主线程加事务无法给到新的线程（threadLocal），后面因为引入了线程池查数据库保证一致性，事务相当于失效，所以调用线程池的方法需要额外加事务
    //这里加了也没用，因为事务天生无法夸线程，一个事务就是一个连接，就应该属于一个线程
    public Flux<String> chatWithPdf(Long sessionId, Long userId, String question) {
        // 1. 获取会话绑定的PDF
        AiChatSession session = aiChatSessionMapper.selectById(sessionId);
        Long bindPdfId = session.getBindPdfId();

        if (bindPdfId == null) {
            return Flux.just("请先选择一个PDF");
        }
        //搞了半天，redis不支持meta过滤
        Filter filter = metadataKey("pdf_id").isEqualTo(bindPdfId.toString());


        /*
        // 2. 构建【只检索当前PDF】的检索器（核心：pdf_id过滤）
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(qdrantEmbeddingStore)
                .embeddingModel(embeddingModel)
                .filter(filter)
                .maxResults(3)
                .minScore(0.5)
                .build();
        */

        // 3. 手动检索，获取【原文 + 页码】
        List<Content> contentList;

        try {
            contentList = hybridRetrieve(question, filter, 3);
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
                    chatDbExecutor.execute(() -> {//这里其实和@Async + Transactional(Requires.NEW)差不多
                            aiChatMessageTxService.saveChatMessage(sessionId,userId,question,fullAnswer.get(),retrievalContent,pageNums);
                    });
                });
    }

    /**
     * 混合检索：向量检索 + 关键词（TF-IDF）融合，使用 RRF 排序
     */
    private List<Content> hybridRetrieve(String question, Filter filter, int topK) {
        // 1. 向量检索获取更多候选（例如 topK * 3）
        EmbeddingStoreContentRetriever vectorRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(qdrantEmbeddingStore)
                .embeddingModel(embeddingModel)
                .filter(filter)
                .maxResults(topK * 3)
                .minScore(0.5)
                .build();
        List<Content> vectorResults = vectorRetriever.retrieve(Query.from(question));
        if (vectorResults.isEmpty()) return Collections.emptyList();

        // 2. 关键词打分（基于文本中问题词的出现次数，简单但有效）
        String[] queryWords = question.toLowerCase().split("\\W+");
        Map<Content, Integer> keywordScores = new HashMap<>();// key-content value-score
        for (Content content : vectorResults) {
            String text = content.textSegment().text().toLowerCase();
            int score = 0;
            for (String word : queryWords) {
                if (word.length() < 2) continue;
                int index = -1;
                while ((index = text.indexOf(word, index + 1)) != -1) score++;
            }
            keywordScores.put(content, score);//词频计数
        }

        // 3. RRF 融合排序（倒数排名融合，k=60）
        Map<Content, Double> rrfScores = new HashMap<>();

        // 向量排名权重
        for (int i = 0; i < vectorResults.size(); i++) {
            Content c = vectorResults.get(i);
            double rrf = 1.0 / (60 + i + 1);//i+1 代表排名，这里算初始向量检索的排名的分数
            rrfScores.merge(c, rrf, Double::sum);
        }
        // 关键词排名权重
        List<Content> sortedByKeyword = new ArrayList<>(vectorResults);//再复制一份用于关键词排名
        sortedByKeyword.sort((a, b) -> keywordScores.get(b) - keywordScores.get(a));//原本的向量作为key去取关键词词频统计，根据词频统计算排名，降序
        for (int i = 0; i < sortedByKeyword.size(); i++) {
            Content c = sortedByKeyword.get(i);
            double rrf = 1.0 / (60 + i + 1);//算词频排名分数
            rrfScores.merge(c, rrf, Double::sum);
        }

        // 按融合分数排序取 topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Content, Double>comparingByValue().reversed())//降序
                .limit(topK)//取分数最高的前topK个
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

}