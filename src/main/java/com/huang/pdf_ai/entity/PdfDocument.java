package com.huang.pdf_ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pdf_document")
public class PdfDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String filePath; //文件路径
    private Long fileSize; //文件大小
    private Integer totalPages; //总页码
    private Integer parseStatus; //解析状态
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}