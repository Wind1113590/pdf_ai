package com.huang.pdf_ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huang.pdf_ai.entity.PdfDocument;
import com.huang.pdf_ai.mapper.PdfDocumentMapper;
import com.huang.pdf_ai.result.Result;
import com.huang.pdf_ai.service.PdfParseService;
import com.huang.pdf_ai.util.AliOssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.huang.pdf_ai.service.PdfParseService.PARSE_PENDING;
import static com.huang.pdf_ai.service.PdfParseService.PARSE_PROCESSING;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
@Slf4j
public class PdfController {

    @Autowired
    private final PdfParseService pdfParseService;

    @Autowired
    private final PdfDocumentMapper pdfDocumentMapper;

    @Autowired
    private final AliOssUtil aliOssUtil;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;   // 新增 Redis

    // Redis key 前缀
    private static final String PARSE_STATUS_KEY = "pdf:parse:status:";

    /**
     * 1.1 OSS上传PDF + 自动解析
     */
    @PostMapping("/upload")
    public Result<Long> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId
    ) {
        PdfDocument pdfDocument = new PdfDocument();
        pdfDocument.setUserId(userId);
        pdfDocument.setName(file.getOriginalFilename());
        pdfDocument.setFileSize(file.getSize());
        pdfDocument.setParseStatus(PARSE_PENDING);
        pdfDocument.setTotalPages(0); // 临时，后面更新
        pdfDocumentMapper.insert(pdfDocument);

        Long pdfId = pdfDocument.getId();
        setParseStatus(pdfId, PARSE_PROCESSING);

        // 2. 调用你的解析Service（传入OSS地址即可）
        pdfParseService.parseSinglePdf(file,pdfId);

        return Result.ok("PDF上传解析中", pdfDocument.getId());
    }

    /**
     * 1.2 分页查询PDF列表
     */
    @GetMapping("/list")
    public Result<Page<PdfDocument>> list(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        Page<PdfDocument> page = new Page<>(pageNum, pageSize);
        Page<PdfDocument> pdfPage = pdfDocumentMapper.selectPage(page,
                Wrappers.lambdaQuery(PdfDocument.class)
                        .eq(PdfDocument::getUserId, userId)
                        .orderByDesc(PdfDocument::getCreateTime)
        );
        return Result.ok(pdfPage);
    }

    /**
     * 1.3 获取PDF详情
     */
    @GetMapping("/{pdfId}")
    public Result<PdfDocument> getById(@PathVariable Long pdfId) {
        return Result.ok(pdfDocumentMapper.selectById(pdfId));
    }

    /**
     * 1.4 删除PDF（MySQL + 向量库）
     */
    @DeleteMapping("/{pdfId}")
    public Result<Void> delete(@PathVariable Long pdfId) {
        pdfParseService.deletePdf(pdfId);
        return Result.ok();
    }

    /**
     * 设置 Redis 解析状态（带过期时间）
     */
    private void setParseStatus(Long pdfId, int status) {
        String key = PARSE_STATUS_KEY + pdfId;
        redisTemplate.opsForValue().set(key, String.valueOf(status), 10, TimeUnit.MINUTES);
    }
}