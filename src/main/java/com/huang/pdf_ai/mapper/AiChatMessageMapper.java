package com.huang.pdf_ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.pdf_ai.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}