package com.huang.pdf_ai.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huang.pdf_ai.entity.PdfDocument;
import com.huang.pdf_ai.mapper.PdfDocumentMapper;
import com.huang.pdf_ai.result.Result;
import com.huang.pdf_ai.service.PdfParseService;
import com.huang.pdf_ai.util.AliOssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {

    @Autowired
    private final PdfParseService pdfParseService;
    @Autowired
    private final PdfDocumentMapper pdfDocumentMapper;
    @Autowired
    private final AliOssUtil aliOssUtil;

    /**
     * 1.1 OSS上传PDF + 自动解析
     */
    @PostMapping("/upload")
    public Result<Long> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId
    ) {
        try {
            // 1. OSS 上传（完全沿用你原来的写法）
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = "pdf/" + UUID.randomUUID() + extension;

            // 上传到阿里云OSS
            String ossFilePath = aliOssUtil.upload(file.getBytes(), objectName);

            // 2. 调用你的解析Service（传入OSS地址即可）
            pdfParseService.parseSinglePdf(ossFilePath, userId,originalFilename,file.getSize());

            // 3. 返回刚插入的PDF ID
            PdfDocument pdfDocument = pdfDocumentMapper.selectOne(
                    Wrappers.lambdaQuery(PdfDocument.class)
                            .eq(PdfDocument::getFilePath, ossFilePath)
            );

            return Result.ok("PDF上传解析中", pdfDocument.getId());

        } catch (IOException e) {
            return Result.fail("PDF上传失败：" + e.getMessage());
        }
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
}