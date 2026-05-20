package com.huang.pdf_ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.pdf_ai.entity.PdfChunk;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PdfChunkMapper extends BaseMapper<PdfChunk> {

    void insertBatch(List<PdfChunk> list);
}