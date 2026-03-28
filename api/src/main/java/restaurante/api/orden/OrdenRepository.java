package restaurante.api.orden;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrdenRepository extends JpaRepository<Orden,Long> {
    Page<Orden> findAllByTipo(Pageable pagina, Tipo tipo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM orden o WHERE o.id_ordenes = :id")
    Optional<Orden> findByIdConBloqueo(Long id);
}
