package com.huang.pdf_ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider"
        /*,contentRetriever = "contentRetriever"*/)//这里需要自定义检索对象
public interface AiChatService {

    //@SystemMessage(fromResource = "system.txt")
    Flux<String> chat(@MemoryId Long memoryId, @UserMessage String message);
}
