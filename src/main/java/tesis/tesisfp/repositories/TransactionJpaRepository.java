package tesis.tesisfp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tesis.tesisfp.entities.TransactionEntity;

@Repository
public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, Long> {
}
