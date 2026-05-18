package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

import java.util.List;

/**
 * 学生端 Hybrid v0 预热状态。
 */
@Getter
public class QaHybridWarmupResponse {

    private final boolean ready;
    private final String status;
    private final String message;
    private final String dataDirUri;
    private final boolean cached;
    private final boolean textUnitsReady;
    private final List<String> missing;

    private QaHybridWarmupResponse(
            boolean ready,
            String status,
            String message,
            String dataDirUri,
            boolean cached,
            boolean textUnitsReady,
            List<String> missing
    ) {
        this.ready = ready;
        this.status = status;
        this.message = message;
        this.dataDirUri = dataDirUri;
        this.cached = cached;
        this.textUnitsReady = textUnitsReady;
        this.missing = missing == null ? List.of() : List.copyOf(missing);
    }

    public static QaHybridWarmupResponse of(
            boolean ready,
            String status,
            String message,
            String dataDirUri,
            boolean cached,
            boolean textUnitsReady,
            List<String> missing
    ) {
        return new QaHybridWarmupResponse(ready, status, message, dataDirUri, cached, textUnitsReady, missing);
    }
}
