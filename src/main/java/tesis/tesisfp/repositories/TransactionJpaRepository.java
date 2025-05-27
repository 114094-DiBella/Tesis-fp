package tesis.tesisfp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tesis.tesisfp.entities.TransactionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByOrderCode(String orderCode);
    Optional<TransactionEntity> findFirstByOrderCodeOrderByCreatedAtDesc(String orderCode);
    Optional<TransactionEntity> findByReferenceNumber(String referenceNumber);
}