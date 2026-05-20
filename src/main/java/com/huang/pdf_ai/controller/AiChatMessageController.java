package com.huang.pdf_ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huang.pdf_ai.entity.AiChatMessage;
import com.huang.pdf_ai.mapper.AiChatMessageMapper;
import com.huang.pdf_ai.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class AiChatMessageController {

    @Autowired
    private final AiChatMessageMapper aiChatMessageMapper;

    /**
     * 4.1 查询会话历史聊天记录
     */
    @GetMapping("/list")
    public Result<Page<AiChatMessage>> list(
            @RequestParam Long sessionId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize
    ) {
        Page<AiChatMessage> page = new Page<>(pageNum, pageSize);
        Page<AiChatMessage> result = aiChatMessageMapper.selectPage(page,
                Wrappers.lambdaQuery(AiChatMessage.class)
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getCreateTime)
        );
        return Result.ok(result);
    }

    /**
     * 4.2 清空会话聊天记录
     */
    @DeleteMapping("/clear/{sessionId}")
    public Result<Void> clear(@PathVariable Long sessionId) {
        aiChatMessageMapper.delete(
                Wrappers.lambdaQuery(AiChatMessage.class)
                        .eq(AiChatMessage::getSessionId, sessionId)
        );
        return Result.ok();
    }
}