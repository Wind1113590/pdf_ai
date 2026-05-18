package com.huang.pdf_ai.controller;

import com.huang.pdf_ai.service.AiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    @Autowired
    AiChatService aiChatService;

    @RequestMapping(value = "/chat" ,produces = "text/html;charset=utf-8")
    public Flux<String> chat (Long memoryId,String message) {
        return aiChatService.chat(memoryId,message);
    }

}
