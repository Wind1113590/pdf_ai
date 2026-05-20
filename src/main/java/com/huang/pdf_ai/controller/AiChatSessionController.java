package com.huang.pdf_ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huang.pdf_ai.entity.AiChatSession;
import com.huang.pdf_ai.mapper.AiChatSessionMapper;
import com.huang.pdf_ai.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class AiChatSessionController {

    @Autowired
    private final AiChatSessionMapper aiChatSessionMapper;

    /**
     * 2.1 创建聊天会话
     */
    @PostMapping("/create")
    public Result<Long> create(@RequestParam Long userId
    ) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setBindPdfId(null);
        session.setCreateTime(LocalDateTime.now());
        aiChatSessionMapper.insert(session);
        return Result.ok(session.getId());
    }

    /**
     * 2.2 绑定PDF到会话
     */
    @PutMapping("/bindPdf")
    public Result<Void> bindPdf(
            @RequestParam Long sessionId,
            @RequestParam Long pdfId
    ) {
        AiChatSession update = new AiChatSession();
        update.setId(sessionId);
        update.setBindPdfId(pdfId);
        aiChatSessionMapper.updateById(update);
        return Result.ok();
    }

    /**
     * 2.3 查询用户所有会话
     */
    @GetMapping("/list")
    public Result<List<AiChatSession>> list(@RequestParam Long userId) {
        List<AiChatSession> list = aiChatSessionMapper.selectList(
                Wrappers.lambdaQuery(AiChatSession.class)
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getCreateTime)
        );
        return Result.ok(list);
    }

    /**
     * 2.4 删除会话
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable Long sessionId) {
        aiChatSessionMapper.deleteById(sessionId);
        return Result.ok();
    }
}