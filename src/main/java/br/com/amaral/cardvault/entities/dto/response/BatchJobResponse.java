package br.com.amaral.cardvault.entities.dto.response;

import br.com.amaral.cardvault.entities.BatchJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response returned immediately after a batch upload and on status polling.
 */
@Schema(description = "Async batch job status")
public record BatchJobResponse(

        @Schema(description = "Job identifier to poll for status", example = "550e8400-e29b-41d4-a716-446655440000")
        String jobId,

        @Schema(description = "Current status: PENDING | PROCESSING | DONE | FAILED")
        String status,

        @Schema(description = "Original filename")
        String filename,

        @Schema(description = "Total card lines parsed (available when DONE)")
        Integer totalParsed,

        @Schema(description = "Cards successfully inserted (available when DONE)")
        Integer inserted,

        @Schema(description = "Cards skipped as duplicates (available when DONE)")
        Integer skipped,

        @Schema(description = "Error message (available when FAILED)")
        String error,

        @Schema(description = "When the job was created")
        LocalDateTime createdAt,

        @Schema(description = "When the job was last updated")
        LocalDateTime updatedAt
) {
    public static BatchJobResponse from(final BatchJob job) {
        return new BatchJobResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getFilename(),
                job.getTotalParsed(),
                job.getInserted(),
                job.getSkipped(),
                job.getError(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
