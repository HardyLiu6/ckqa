package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.integration.graphrag.GraphRagProgressEventSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 学生端可展示的检索过程事件，只包含外部可解释的检索与聚合信息。
 */
@Getter
public class QaProgressEventResponse {

    private final String type;
    private final String mode;
    private final String summary;
    private final Map<String, Object> metrics;
    private final List<Map<String, Object>> evidence;

    private QaProgressEventResponse(
            String type,
            String mode,
            String summary,
            Map<String, Object> metrics,
            List<Map<String, Object>> evidence
    ) {
        this.type = type;
        this.mode = mode;
        this.summary = summary;
        this.metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static QaProgressEventResponse of(
            String type,
            String mode,
            String summary,
            Map<String, Object> metrics,
            List<Map<String, Object>> evidence
    ) {
        return new QaProgressEventResponse(type, mode, summary, metrics, evidence);
    }

    public static QaProgressEventResponse from(GraphRagProgressEventSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return of(snapshot.type(), snapshot.mode(), snapshot.summary(), snapshot.metrics(), snapshot.evidence());
    }
}
