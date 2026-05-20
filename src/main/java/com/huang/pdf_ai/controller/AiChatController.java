package com.huang.pdf_ai.controller;

import com.huang.pdf_ai.service.AiChatManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AiChatController {

    @Autowired
    private final AiChatManageService aiChatManageService;

    /**
     * 3.1 PDF流式问答（SSE）
     * 注意：流式接口不能包Result
     */
    @PostMapping(value = "/pdf", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithPdf(
            @RequestParam Long sessionId,
            @RequestParam Long userId,
            @RequestParam String question
    ) {
        return aiChatManageService.chatWithPdf(sessionId, userId, question);
    }
}