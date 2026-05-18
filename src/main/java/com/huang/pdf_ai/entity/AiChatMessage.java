package com.huang.pdf_ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;  //对应哪次会话
    private Long userId;
    private String queryContent;  // 问题
    private String aiResponse;    // 回答
    private String retrievalContent;   // 召回原文
    private String retrievalPageNums;  // 页码比如 1,5,9
    private LocalDateTime createTime;
}