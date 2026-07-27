package restaurante.api.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConteoFisicoRepository extends JpaRepository<ConteoFisico, Long> {

    @Query("""
            SELECT c FROM conteoFisico c
            JOIN FETCH c.usuario
            ORDER BY c.fecha DESC
            """)
    List<ConteoFisico> historial();
}
