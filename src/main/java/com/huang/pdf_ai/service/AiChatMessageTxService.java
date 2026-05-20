package com.huang.pdf_ai.service;

import com.huang.pdf_ai.entity.AiChatMessage;
import com.huang.pdf_ai.mapper.AiChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiChatMessageTxService {

    private final AiChatMessageMapper aiChatMessageMapper;

    // 新开独立事务，和外层完全隔离
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChatMessage(Long sessionId, Long userId, String question, String answer, String retrievalContent, String pageNums) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setQueryContent(question);
        message.setAiResponse(answer);
        message.setRetrievalContent(retrievalContent);
        message.setRetrievalPageNums(pageNums);
        aiChatMessageMapper.insert(message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChatMessage(
            Long sessionId,
            Long userId,
            String question,
            String fullAnswer
    ) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setQueryContent(question);
        message.setAiResponse(fullAnswer);
        aiChatMessageMapper.insert(message);
    }
}