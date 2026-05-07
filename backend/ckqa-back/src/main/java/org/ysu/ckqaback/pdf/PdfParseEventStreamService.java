package org.ysu.ckqaback.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ysu.ckqaback.pdf.dto.PdfFileResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 解析进度 SSE 事件流。
 */
@Slf4j
@Service
public class PdfParseEventStreamService {

    private static final long STREAM_TIMEOUT_MILLIS = 16 * 60 * 1000L;
    private static final long PUSH_INTERVAL_SECONDS = 2L;

    private final CourseMaterialsService courseMaterialsService;
    private final PdfParseStreamTokenService tokenService;
    private final ScheduledExecutorService scheduler;

    public PdfParseEventStreamService(
            CourseMaterialsService courseMaterialsService,
            PdfParseStreamTokenService tokenService,
            @Qualifier("pdfParseEventScheduler") ScheduledExecutorService scheduler
    ) {
        this.courseMaterialsService = courseMaterialsService;
        this.tokenService = tokenService;
        this.scheduler = scheduler;
    }

    public SseEmitter openStream(Long materialId, String token) {
        tokenService.validate(materialId, token);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable cleanup = () -> cancelScheduled(futureRef);

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> pushSnapshot(materialId, emitter, futureRef),
                0,
                PUSH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        futureRef.set(future);
        return emitter;
    }

    private void pushSnapshot(Long materialId, SseEmitter emitter, AtomicReference<ScheduledFuture<?>> futureRef) {
        try {
            PdfFileResponse snapshot = PdfFileResponse.fromEntity(courseMaterialsService.getRequiredById(materialId));
            String eventName = resolveEventName(snapshot.getParseStatus());
            emitter.send(SseEmitter.event().name(eventName).data(snapshot));
            if (isTerminal(snapshot.getParseStatus())) {
                cancelScheduled(futureRef);
                emitter.complete();
            }
        } catch (IOException ex) {
            cancelScheduled(futureRef);
            emitter.completeWithError(ex);
        } catch (RuntimeException ex) {
            cancelScheduled(futureRef);
            log.warn("解析进度事件推送失败, materialId={}", materialId, ex);
            emitter.completeWithError(ex);
        }
    }

    private String resolveEventName(String status) {
        String normalized = String.valueOf(status).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "done" -> "done";
            case "failed" -> "failed";
            default -> "snapshot";
        };
    }

    private boolean isTerminal(String status) {
        String normalized = String.valueOf(status).toLowerCase(Locale.ROOT);
        return "done".equals(normalized) || "failed".equals(normalized);
    }

    private void cancelScheduled(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.getAndSet(null);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }
}
