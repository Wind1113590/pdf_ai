package com.huang.pdf_ai.service;

import com.huang.pdf_ai.document.PdfChunkDocument;
import com.huang.pdf_ai.entity.BM25Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

@Service
public class BM25SearchService {

    @Autowired
    private ElasticsearchOperations esOperations;

    public List<BM25Result> search(String question, Long pdfId, int topK) {
        Criteria criteria = Criteria.where("content").matches(question);
        if (pdfId != null) {
            criteria = criteria.and("pdfId").is(pdfId);
        }
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(0, topK));

        SearchHits<PdfChunkDocument> hits = esOperations.search(query, PdfChunkDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> new BM25Result(
                        hit.getContent().getId(),
                        hit.getScore(),
                        hit.getContent().getContent()
                ))
                .collect(Collectors.toList());
    }
}