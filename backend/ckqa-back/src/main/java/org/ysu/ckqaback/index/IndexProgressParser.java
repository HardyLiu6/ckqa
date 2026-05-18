package org.ysu.ckqaback.index;

import org.springframework.stereotype.Component;
import org.ysu.ckqaback.index.dto.IndexProgress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 graphrag {@code process.log} 得到实时进度。
 *
 * <p>日志由 {@code ProcessRunner.drainAndTee} 写入，每行带 {@code [stdout] } 或
 * {@code [stderr] } 前缀。本解析器只关注 stdout 行。</p>
 */
@Component
public class IndexProgressParser {

    private static final Pattern PIPELINE_LINE = Pattern.compile(
            "Starting pipeline with workflows:\\s*(.+)\\s*$");
    private static final Pattern START_WORKFLOW = Pattern.compile(
            "Starting workflow:\\s*(\\S+)\\s*$");
    private static final Pattern COMPLETE_WORKFLOW = Pattern.compile(
            "Workflow complete:\\s*(\\S+)\\s*$");
    private static final Pattern SUB_PROGRESS = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    private static final Map<String, Integer> WEIGHTS = Map.of(
            "load_input_documents", 1,
            "create_base_text_units", 2,
            "create_final_documents", 1,
            "extract_graph", 22,
            "finalize_graph", 2,
            "extract_covariates", 1,
            "create_communities", 2,
            "create_final_text_units", 2,
            "create_community_reports", 50,
            "generate_text_embeddings", 17
    );
    private static final int DEFAULT_WEIGHT = 2;

    public Optional<IndexProgress> parse(Path logFile) {
        if (logFile == null || !Files.isRegularFile(logFile)) {
            return Optional.empty();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return Optional.empty();
        }
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        List<String> pipelineWorkflows = null;
        List<String> completed = new ArrayList<>();
        String currentWorkflow = null;
        String currentWorkflowSubProgressLine = null;

        for (String raw : lines) {
            String line = stripPrefix(raw);
            if (line == null) {
                continue;
            }
            Matcher pipelineMatcher = PIPELINE_LINE.matcher(line);
            if (pipelineMatcher.find()) {
                pipelineWorkflows = parseWorkflowList(pipelineMatcher.group(1));
                continue;
            }
            Matcher startMatcher = START_WORKFLOW.matcher(line);
            if (startMatcher.find()) {
                currentWorkflow = startMatcher.group(1);
                currentWorkflowSubProgressLine = null;
                continue;
            }
            Matcher completeMatcher = COMPLETE_WORKFLOW.matcher(line);
            if (completeMatcher.find()) {
                String done = completeMatcher.group(1);
                if (!completed.contains(done)) {
                    completed.add(done);
                }
                if (done.equals(currentWorkflow)) {
                    currentWorkflowSubProgressLine = null;
                }
                continue;
            }
            // 同一 workflow 内累积的 "N / M" 行：用最新一行的最后一个匹配
            if (currentWorkflow != null && SUB_PROGRESS.matcher(line).find()) {
                currentWorkflowSubProgressLine = line;
            }
        }

        if (pipelineWorkflows == null || pipelineWorkflows.isEmpty()) {
            return Optional.empty();
        }
        if (currentWorkflow == null) {
            currentWorkflow = pipelineWorkflows.get(0);
        }
        int currentIndex = Math.max(0, pipelineWorkflows.indexOf(currentWorkflow));

        IndexProgress.SubProgress sub = extractLastSubProgress(currentWorkflowSubProgressLine);
        int percentage = computePercentage(pipelineWorkflows, currentIndex, completed, sub);

        return Optional.of(IndexProgress.builder()
                .pipelineWorkflows(pipelineWorkflows)
                .currentWorkflowIndex(currentIndex)
                .currentWorkflowKey(currentWorkflow)
                .completedWorkflowKeys(completed)
                .subProgress(sub)
                .percentage(percentage)
                .build());
    }

    private String stripPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("[stdout] ")) {
            return raw.substring("[stdout] ".length());
        }
        if (raw.startsWith("[stderr] ")) {
            return null; // 进度只看 stdout
        }
        return raw;
    }

    private List<String> parseWorkflowList(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private IndexProgress.SubProgress extractLastSubProgress(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = SUB_PROGRESS.matcher(line);
        int current = 0;
        int total = 0;
        while (matcher.find()) {
            current = Integer.parseInt(matcher.group(1));
            total = Integer.parseInt(matcher.group(2));
        }
        if (total <= 0) {
            return null;
        }
        return IndexProgress.SubProgress.builder().current(current).total(total).build();
    }

    private int computePercentage(
            List<String> pipelineWorkflows,
            int currentIndex,
            List<String> completed,
            IndexProgress.SubProgress sub
    ) {
        // 分母：本次 pipeline 实际涉及的 workflow 权重总和
        int totalWeight = pipelineWorkflows.stream()
                .mapToInt(w -> WEIGHTS.getOrDefault(w, DEFAULT_WEIGHT))
                .sum();
        if (totalWeight <= 0) {
            return 0;
        }
        // 分子：已完成的权重 + 当前 workflow 的子进度比例 * 权重
        Map<String, Boolean> doneMap = new LinkedHashMap<>();
        completed.forEach(c -> doneMap.put(c, Boolean.TRUE));

        int accumulated = 0;
        for (int i = 0; i < pipelineWorkflows.size(); i++) {
            String key = pipelineWorkflows.get(i);
            int weight = WEIGHTS.getOrDefault(key, DEFAULT_WEIGHT);
            if (i < currentIndex || doneMap.containsKey(key)) {
                accumulated += weight;
            } else if (i == currentIndex && sub != null && sub.getTotal() > 0) {
                double ratio = Math.min(1.0, (double) sub.getCurrent() / sub.getTotal());
                accumulated += (int) Math.round(weight * ratio);
                break;
            } else {
                break;
            }
        }
        double pct = 100.0 * accumulated / totalWeight;
        int rounded = (int) Math.round(pct);
        if (rounded < 0) return 0;
        if (rounded > 99) return 99;
        return rounded;
    }
}
