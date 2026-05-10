package br.com.amaral.cardvault.repositories;

import br.com.amaral.cardvault.entities.BatchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link BatchJob} entities.
 */
@Repository
public interface BatchJobRepository extends JpaRepository<BatchJob, Long> {

    Optional<BatchJob> findByJobId(String jobId);
}
