package com.huang.pdf_ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BM25Result {
    private String vectorId;    // 还是保留，便于删除
    private double score;
    private String content;     // 新增：文本块内容，用于匹配向量结果
}