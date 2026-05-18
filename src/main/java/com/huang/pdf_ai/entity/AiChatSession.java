package com.huang.pdf_ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_chat_session")
public class AiChatSession { //会话一对多消息
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionName;
    private Long bindPdfId; //该次会话对应哪个pdf
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}