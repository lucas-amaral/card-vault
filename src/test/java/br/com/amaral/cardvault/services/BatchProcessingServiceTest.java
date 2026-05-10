package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.dto.response.BatchJobResponse;
import br.com.amaral.cardvault.entities.BatchJob;
import br.com.amaral.cardvault.entities.Card;
import br.com.amaral.cardvault.entities.User;
import br.com.amaral.cardvault.exceptions.ResourceNotFoundException;
import br.com.amaral.cardvault.repositories.BatchJobRepository;
import br.com.amaral.cardvault.repositories.CardRepository;
import br.com.amaral.cardvault.repositories.UserRepository;
import br.com.amaral.cardvault.utils.CardEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BatchProcessingService}.
 */
@ExtendWith(MockitoExtension.class)
class BatchProcessingServiceTest {

    @Mock private BatchJobRepository batchJobRepository;
    @Mock private CardRepository     cardRepository;
    @Mock private UserRepository     userRepository;
    @Mock private CardEncryptionUtil encryptionUtil;

    @InjectMocks
    private BatchProcessingService batchProcessingService;

    private User testUser;
    private BatchJob pendingJob;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L).username("testuser").password("hashed")
                .role("ROLE_USER").enabled(true).build();

        pendingJob = BatchJob.builder()
                .id(1L)
                .jobId(UUID.randomUUID().toString())
                .status(BatchJob.Status.PENDING)
                .filename("test.txt")
                .createdBy(testUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // submit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("submit — valid file creates PENDING job and returns response")
    void submit_validFile_createsPendingJob() throws IOException {
        MockMultipartFile file = buildFile("C1 4111111111111111\nLOTE0001000001\n");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(batchJobRepository.save(any(BatchJob.class))).thenReturn(pendingJob);

        BatchJobResponse response = batchProcessingService.submit(file, "testuser");

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.jobId()).isEqualTo(pendingJob.getJobId());
        verify(batchJobRepository).save(any(BatchJob.class));
    }

    @Test
    @DisplayName("submit — empty file throws IllegalArgumentException")
    void submit_emptyFile_throwsException() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> batchProcessingService.submit(file, "testuser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        verify(batchJobRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // getStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStatus — existing job returns correct response")
    void getStatus_existingJob_returnsResponse() {
        pendingJob.setStatus(BatchJob.Status.DONE);
        pendingJob.setTotalParsed(10);
        pendingJob.setInserted(8);
        pendingJob.setSkipped(2);

        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));

        BatchJobResponse response = batchProcessingService.getStatus(pendingJob.getJobId());

        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.totalParsed()).isEqualTo(10);
        assertThat(response.inserted()).isEqualTo(8);
        assertThat(response.skipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("getStatus — unknown jobId throws ResourceNotFoundException")
    void getStatus_unknownJobId_throwsResourceNotFoundException() {
        when(batchJobRepository.findByJobId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> batchProcessingService.getStatus("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // -------------------------------------------------------------------------
    // flushChunk
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("flushChunk — new cards are saved, duplicates are skipped")
    void flushChunk_mixedCards_insertsNewSkipsDuplicates() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("5500005555555559")).thenReturn("hash2");
        when(encryptionUtil.encrypt(any())).thenReturn("encrypted");
        when(cardRepository.existsByCardHash("hash1")).thenReturn(false);
        when(cardRepository.existsByCardHash("hash2")).thenReturn(true); // duplicate

        int[] result = batchProcessingService.flushChunk(
                List.of("4111111111111111", "5500005555555559"), "LOTE0001", "testuser");

        assertThat(result[0]).isEqualTo(1); // inserted
        assertThat(result[1]).isEqualTo(1); // skipped
        verify(cardRepository, times(1)).save(any(Card.class));
    }

    @Test
    @DisplayName("flushChunk — all new cards: all inserted")
    void flushChunk_allNew_allInserted() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash(any())).thenReturn("uniquehash");
        when(encryptionUtil.encrypt(any())).thenReturn("encrypted");
        when(cardRepository.existsByCardHash(any())).thenReturn(false);
        when(cardRepository.save(any())).thenReturn(new Card());

        // Three distinct hashes needed — reset stubbing to use argument-specific
        when(encryptionUtil.hash("4111111111111111")).thenReturn("h1");
        when(encryptionUtil.hash("5500005555555559")).thenReturn("h2");
        when(encryptionUtil.hash("4916338506082832")).thenReturn("h3");
        when(cardRepository.existsByCardHash("h1")).thenReturn(false);
        when(cardRepository.existsByCardHash("h2")).thenReturn(false);
        when(cardRepository.existsByCardHash("h3")).thenReturn(false);

        int[] result = batchProcessingService.flushChunk(
                List.of("4111111111111111", "5500005555555559", "4916338506082832"), "LOTE0001", "testuser");

        assertThat(result[0]).isEqualTo(3);
        assertThat(result[1]).isEqualTo(0);
        verify(cardRepository, times(3)).save(any(Card.class));
    }

    @Test
    @DisplayName("flushChunk — all duplicates: none inserted")
    void flushChunk_allDuplicates_noneInserted() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash(any())).thenReturn("existinghash");
        when(cardRepository.existsByCardHash("existinghash")).thenReturn(true);

        int[] result = batchProcessingService.flushChunk(
                List.of("4111111111111111", "5500005555555559"), "LOTE0001", "testuser");

        assertThat(result[0]).isEqualTo(0);
        assertThat(result[1]).isEqualTo(2);
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("flushChunk — saved card has correct batchName")
    void flushChunk_savedCard_hasBatchName() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("h1");
        when(encryptionUtil.encrypt("4111111111111111")).thenReturn("enc");
        when(cardRepository.existsByCardHash("h1")).thenReturn(false);

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        when(cardRepository.save(cardCaptor.capture())).thenReturn(new Card());

        batchProcessingService.flushChunk(List.of("4111111111111111"), "LOTE0042", "testuser");

        assertThat(cardCaptor.getValue().getBatchName()).isEqualTo("LOTE0042");
    }

    // -------------------------------------------------------------------------
    // markProcessing / markDone / markFailed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markProcessing — updates job status to PROCESSING")
    void markProcessing_updatesStatus() {
        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));

        batchProcessingService.markProcessing(pendingJob.getJobId());

        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BatchJob.Status.PROCESSING);
    }

    @Test
    @DisplayName("markDone — updates job status and counters")
    void markDone_updatesStatusAndCounters() {
        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));

        batchProcessingService.markDone(pendingJob.getJobId(), 100, 90, 10);

        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(captor.capture());
        BatchJob saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BatchJob.Status.DONE);
        assertThat(saved.getTotalParsed()).isEqualTo(100);
        assertThat(saved.getInserted()).isEqualTo(90);
        assertThat(saved.getSkipped()).isEqualTo(10);
    }

    @Test
    @DisplayName("markFailed — updates job status and error message")
    void markFailed_updatesStatusAndError() {
        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));

        batchProcessingService.markFailed(pendingJob.getJobId(), "disk full");

        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository).save(captor.capture());
        BatchJob saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BatchJob.Status.FAILED);
        assertThat(saved.getError()).isEqualTo("disk full");
    }

    @Test
    @DisplayName("markProcessing — unknown jobId is silently ignored")
    void markProcessing_unknownJobId_doesNothing() {
        when(batchJobRepository.findByJobId("unknown")).thenReturn(Optional.empty());

        batchProcessingService.markProcessing("unknown");

        verify(batchJobRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // process (streaming logic)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("process — valid TXT marks job DONE with correct counts")
    void process_validFile_marksJobDone() {
        String txt = """
                DESAFIO-HYPERATIVA 20180524LOTE0001000003
                C1 4111111111111111
                C2 5500005555555559
                C3 4916338506082832
                LOTE0001000003
                """;

        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash(any())).thenAnswer(i -> "hash_" + i.getArgument(0));
        when(encryptionUtil.encrypt(any())).thenReturn("encrypted");
        when(cardRepository.existsByCardHash(any())).thenReturn(false);
        when(cardRepository.save(any())).thenReturn(new Card());

        batchProcessingService.process(
                pendingJob.getJobId(), toStream(txt), "testuser");

        // markDone should be called with 3 total, 3 inserted, 0 skipped
        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository, atLeastOnce()).save(captor.capture());
        BatchJob lastSave = captor.getAllValues().stream()
                .filter(j -> j.getStatus() == BatchJob.Status.DONE)
                .findFirst()
                .orElseThrow();
        assertThat(lastSave.getTotalParsed()).isEqualTo(3);
        assertThat(lastSave.getInserted()).isEqualTo(3);
        assertThat(lastSave.getSkipped()).isEqualTo(0);
    }

    @Test
    @DisplayName("process — IO error marks job FAILED")
    void process_ioError_marksJobFailed() throws IOException {
        InputStream broken = mock(InputStream.class);
        when(broken.read(any(), anyInt(), anyInt())).thenThrow(new IOException("disk error"));
        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));

        batchProcessingService.process(pendingJob.getJobId(), broken, "testuser");

        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository, atLeastOnce()).save(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
                .anyMatch(j -> j.getStatus() == BatchJob.Status.FAILED);
        assertThat(hasFailed).isTrue();
    }

    @Test
    @DisplayName("process — stops at footer line, ignores lines after it")
    void process_stopsAtFooter() {
        String txt = """
                DESAFIO-HYPERATIVA 20180524LOTE0001000001
                C1 4111111111111111
                LOTE0001000001
                C2 5500005555555559
                """;

        when(batchJobRepository.findByJobId(pendingJob.getJobId())).thenReturn(Optional.of(pendingJob));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash(any())).thenReturn("somehash");
        when(encryptionUtil.encrypt(any())).thenReturn("encrypted");
        when(cardRepository.existsByCardHash(any())).thenReturn(false);
        when(cardRepository.save(any())).thenReturn(new Card());

        batchProcessingService.process(pendingJob.getJobId(), toStream(txt), "testuser");

        // Only 1 card should be parsed (the one before LOTE footer)
        ArgumentCaptor<BatchJob> captor = ArgumentCaptor.forClass(BatchJob.class);
        verify(batchJobRepository, atLeastOnce()).save(captor.capture());
        captor.getAllValues().stream()
                .filter(j -> j.getStatus() == BatchJob.Status.DONE)
                .findFirst()
                .ifPresent(j -> assertThat(j.getTotalParsed()).isEqualTo(1));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MockMultipartFile buildFile(String content) {
        return new MockMultipartFile("file", "test.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
