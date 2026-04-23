package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.ysu.ckqaback.entity.ParseResults;
import org.ysu.ckqaback.mapper.ParseResultsMapper;
import org.ysu.ckqaback.service.ParseResultsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 解析结果表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class ParseResultsServiceImpl extends ServiceImpl<ParseResultsMapper, ParseResults> implements ParseResultsService {

    @Override
    public List<ParseResults> listByPdfFileId(Long pdfFileId) {
        LambdaQueryWrapper<ParseResults> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ParseResults::getPdfFileId, pdfFileId)
                .orderByDesc(ParseResults::getCreatedAt)
                .orderByDesc(ParseResults::getId);
        return list(queryWrapper);
    }

    @Override
    public List<ParseResults> listGraphRagOutputs(Long pdfFileId) {
        LambdaQueryWrapper<ParseResults> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ParseResults::getPdfFileId, pdfFileId)
                .likeRight(ParseResults::getFileName, "graphrag_")
                .orderByDesc(ParseResults::getCreatedAt)
                .orderByDesc(ParseResults::getId);
        return list(queryWrapper);
    }

    @Override
    public boolean hasCompleteGraphRagExport(Long pdfFileId, String mode, boolean withPageDocs) {
        Set<String> existingNames = listGraphRagOutputs(pdfFileId).stream()
                .map(ParseResults::getFileName)
                .collect(Collectors.toSet());

        Set<String> expected = new LinkedHashSet<>();
        expected.add("graphrag_normalized_docs.json");
        expected.add("page".equals(mode) ? "graphrag_page_docs.json" : "graphrag_section_docs.json");
        if (withPageDocs) {
            expected.add("graphrag_page_docs.json");
        }

        return existingNames.containsAll(expected);
    }
}
