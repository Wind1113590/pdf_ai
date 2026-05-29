package com.huang.pdf_ai.dto;

import lombok.Data;

@Data
public class ParseStatusMessage {
    private Long pdfId;
    private Integer status;     // 0-PENDING,1-PROCESSING,2-SUCCESS,3-FAILED
    private String message;     // 额外信息，如“解析成功”或错误原因
    private Integer totalPages; // 成功后返回总页数
}