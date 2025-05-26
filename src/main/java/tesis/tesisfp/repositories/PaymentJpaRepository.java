package tesis.tesisfp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tesis.tesisfp.entities.PaymentMethodEntity;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentMethodEntity, Long> {
}
