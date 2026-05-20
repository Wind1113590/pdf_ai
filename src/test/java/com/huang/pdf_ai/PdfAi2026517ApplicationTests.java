package com.huang.pdf_ai;

import com.huang.pdf_ai.service.AiChatManageService;
import com.huang.pdf_ai.service.AiChatService;
import com.huang.pdf_ai.service.PdfParseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PdfAi2026517ApplicationTests {

    @Autowired
    PdfParseService pdfParseService;

    @Autowired
    AiChatManageService aiChatManageService;


    @Test
    void testPdfParse() {
        //pdfParseService.parseSinglePdf("D:\\项目\\pdf-ai_2026-5-17\\src\\main\\resources\\pdfs\\Harry Potter and the Chamber of - J.K. Rowling.pdf", 1L);
    }

    @Test
    void testDeletePdf() {
        pdfParseService.deletePdf(2L);
    }

    @Test
    void testAiChat() throws InterruptedException {
        aiChatManageService.chatWithPdf(1L, 4L, "不用回答我什么");

        Thread.sleep(1000);
    }


}
