package restaurante.api.ordenDetalle;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import restaurante.api.orden.Tipo;

import java.time.LocalDateTime;
import java.util.List;

public interface OrdenDetalleRepository extends JpaRepository<OrdenDetalle, Long> {


    @Query("SELECT d FROM ordenDetalle d WHERE d.orden.id_ordenes = :id")
    List<OrdenDetalle> findAllByOrdenId(Long id);

    @Query("""
                SELECT COALESCE(SUM(od.cantidad), 0)
                FROM ordenDetalle od
                JOIN od.orden o
                WHERE o.fechaCierre BETWEEN :inicio AND :fin
                AND o.estatus = 'PAGADA'
                AND o.tipo = :tipo
            """)
    Long contarPlatillosPorTipo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("tipo") Tipo tipo
    );

}
