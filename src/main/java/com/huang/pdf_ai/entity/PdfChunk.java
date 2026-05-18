package com.huang.pdf_ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pdf_chunk")
public class PdfChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pdfId;  //哪个pdf的文本块
    private Integer chunkIndex; //索引
    private String content; //原文
    private Integer pageNum; //页码
    private String vectorId; //UUID删除用
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}