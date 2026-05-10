package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.BatchJob;
import br.com.amaral.cardvault.entities.Card;
import br.com.amaral.cardvault.entities.User;
import br.com.amaral.cardvault.entities.dto.response.BatchJobResponse;
import br.com.amaral.cardvault.repositories.BatchJobRepository;
import br.com.amaral.cardvault.repositories.CardRepository;
import br.com.amaral.cardvault.repositories.UserRepository;
import br.com.amaral.cardvault.utils.CardEncryptionUtil;
import br.com.amaral.cardvault.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles the full lifecycle of asynchronous batch card imports:
 * file validation, job creation, streaming line-by-line processing and status tracking.
 *
 * <p>Processing strategy:
 * <ul>
 *   <li>File is streamed line-by-line — never loaded entirely into memory.</li>
 *   <li>Cards are flushed in chunks of {@value #CHUNK_SIZE} per transaction,
 *       so progress is preserved even if the process crashes mid-file.</li>
 *   <li>Job status updates use {@code REQUIRES_NEW} so they are immediately
 *       visible to polling clients regardless of the chunk transaction state.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessingService {

    static final int CHUNK_SIZE = 500;

    private final BatchJobRepository batchJobRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardEncryptionUtil encryptionUtil;

    /**
     * Validates the uploaded file, persists a new {@link BatchJob} and fires
     * off async processing. Returns immediately with the job status.
     *
     * @param file uploaded multipart file
     * @param username authenticated user
     * @return job response with status PENDING
     */
    @Transactional
    public BatchJobResponse submit(final MultipartFile file, final String username) throws IOException {
        validateFile(file);

        final User user = resolveUser(username);
        final BatchJob job = batchJobRepository.save(BatchJob.builder()
                .jobId(UUID.randomUUID().toString())
                .status(BatchJob.Status.PENDING)
                .filename(file.getOriginalFilename())
                .createdBy(user)
                .build());

        log.info("Batch job created: jobId={}, filename={}, user={}", job.getJobId(), job.getFilename(), username);

        process(job.getJobId(), file.getInputStream(), username);
        return BatchJobResponse.from(job);
    }

    /**
     * Returns the current status of a batch job.
     *
     * @param jobId public job identifier
     * @return job response
     * @throws ResourceNotFoundException if no job exists for the given id
     */
    @Transactional(readOnly = true)
    public BatchJobResponse getStatus(final String jobId) {
        BatchJob job = batchJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch job not found: " + jobId));
        return BatchJobResponse.from(job);
    }

    /**
     * Streams and processes the file asynchronously on the {@code batchExecutor} pool.
     */
    @Async("batchExecutor")
    public void process(final String jobId, final InputStream inputStream, final String username) {
        markProcessing(jobId);
        log.info("Batch processing started: jobId={}", jobId);

        int totalParsed = 0;
        int inserted = 0;
        int skipped = 0;
        String batchName = null;
        List<String> chunk = new ArrayList<>(CHUNK_SIZE);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;

                if (lineNumber == 1) {
                    batchName = extractBatchName(line);
                    continue;
                }

                if (line.startsWith("LOTE")) break;

                if (line.charAt(0) == 'C') {
                    String cardNumber = extractCardNumber(line);
                    if (cardNumber != null) {
                        totalParsed++;
                        chunk.add(cardNumber);

                        if (chunk.size() >= CHUNK_SIZE) {
                            int[] result = flushChunk(chunk, batchName, username);
                            inserted += result[0];
                            skipped  += result[1];
                            chunk.clear();
                        }
                    }
                }
            }

            if (!chunk.isEmpty()) {
                int[] result = flushChunk(chunk, batchName, username);
                inserted += result[0];
                skipped  += result[1];
            }

            markDone(jobId, totalParsed, inserted, skipped);
            log.info("Batch processing finished: jobId={}, total={}, inserted={}, skipped={}",
                    jobId, totalParsed, inserted, skipped);

        } catch (Exception e) {
            log.error("Batch processing failed: jobId={}", jobId, e);
            markFailed(jobId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateFile(final MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] flushChunk(final List<String> cardNumbers, final String batchName, final String username) {
        final User user = resolveUser(username);
        int inserted = 0;
        int skipped = 0;

        for (String cardNumber : cardNumbers) {
            final String hash = encryptionUtil.hash(cardNumber);
            if (cardRepository.existsByCardHash(hash)) {
                skipped++;
                continue;
            }
            cardRepository.save(Card.builder()
                    .externalId(UUID.randomUUID().toString())
                    .cardNumberEnc(encryptionUtil.encrypt(cardNumber))
                    .cardHash(hash)
                    .batchName(batchName)
                    .createdBy(user)
                    .build());
            inserted++;
        }

        return new int[]{inserted, skipped};
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(final String jobId) {
        batchJobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(BatchJob.Status.PROCESSING);
            batchJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(final String jobId, final int totalParsed, final int inserted, final int skipped) {
        batchJobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(BatchJob.Status.DONE);
            job.setTotalParsed(totalParsed);
            job.setInserted(inserted);
            job.setSkipped(skipped);
            batchJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final String jobId, final String errorMessage) {
        batchJobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(BatchJob.Status.FAILED);
            job.setError(errorMessage);
            batchJobRepository.save(job);
        });
    }

    private String extractBatchName(final String header) {
        if (header.contains("LOTE")) {
            int idx = header.indexOf("LOTE");
            return header.substring(idx, Math.min(idx + 14, header.length())).trim();
        }
        return header.substring(0, Math.min(header.length(), 29)).trim();
    }

    private String extractCardNumber(final String line) {
        int i = 1;
        while (i < line.length() && Character.isDigit(line.charAt(i))) i++;
        if (i < line.length() && line.charAt(i) == ' ') i++;
        if (i >= line.length()) return null;

        String raw = line.substring(i);
        int commentIdx = raw.indexOf("//");
        if (commentIdx >= 0) raw = raw.substring(0, commentIdx);
        String cardNumber = raw.trim();

        return cardNumber.matches("\\d{13,19}") ? cardNumber : null;
    }

    private User resolveUser(final String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }
}