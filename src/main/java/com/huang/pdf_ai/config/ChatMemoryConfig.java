package com.huang.pdf_ai.config;


import com.huang.pdf_ai.repository.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;//上下文存到redis

    @Bean //根据不同id保存上下文，一般用这个
    public ChatMemoryProvider chatMemoryProvider() {
        ChatMemoryProvider chatMemoryProvider = //重写get方法，给memoryId取不同的MessageWindowChatMemory（上下文存储对象）
                memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build();
        return chatMemoryProvider;
    }
}
