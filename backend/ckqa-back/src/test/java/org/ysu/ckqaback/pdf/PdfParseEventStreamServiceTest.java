package org.ysu.ckqaback.pdf;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.service.CourseMaterialsService;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PdfParseEventStreamServiceTest {

    @Test
    void shouldScheduleSnapshotPushAndCancelWhenTerminalStatusIsReached() {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        PdfParseStreamTokenService tokenService = mock(PdfParseStreamTokenService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();

        given(tokenService.validate(7L, "stream-token"))
                .willReturn(new PdfParseStreamTokenService.ValidatedStreamToken(7L));
        given(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS)))
                .willAnswer(invocation -> {
                    scheduledTask.set(invocation.getArgument(0));
                    return scheduledFuture;
                });
        given(courseMaterialsService.getRequiredById(7L))
                .willReturn(processingMaterial())
                .willReturn(doneMaterial());

        PdfParseEventStreamService service = new PdfParseEventStreamService(
                courseMaterialsService,
                tokenService,
                scheduler
        );

        service.openStream(7L, "stream-token");

        assertThat(scheduledTask.get()).isNotNull();
        scheduledTask.get().run();
        scheduledTask.get().run();

        then(scheduledFuture).should().cancel(false);
    }

    private static CourseMaterials processingMaterial() {
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setDisplayName("book.pdf");
        material.setParseStatus("processing");
        material.setParseStartedAt(LocalDateTime.parse("2026-05-07T10:00:00"));
        material.setParseProgressPercent(40);
        material.setParseProgressExtractedPages(2);
        material.setParseProgressTotalPages(5);
        return material;
    }

    private static CourseMaterials doneMaterial() {
        CourseMaterials material = processingMaterial();
        material.setParseStatus("done");
        material.setParseProgressPercent(100);
        material.setParseFinishedAt(LocalDateTime.parse("2026-05-07T10:01:00"));
        return material;
    }
}
