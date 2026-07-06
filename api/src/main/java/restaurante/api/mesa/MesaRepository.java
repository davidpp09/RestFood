package restaurante.api.mesa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    @Query("SELECT m FROM mesa m WHERE m.id_mesas BETWEEN :inicio AND :fin")
    List<Mesa> buscarPorRango(@Param("inicio") Long inicio, @Param("fin") Long fin);

    // Lock pesimista: dos meseros abriendo la misma mesa a la vez se serializan;
    // el segundo ve la mesa ya OCUPADA en vez de crear una orden duplicada.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM mesa m WHERE m.id_mesas = :id")
    Optional<Mesa> findByIdConBloqueo(Long id);

}
