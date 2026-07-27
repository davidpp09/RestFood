package restaurante.api.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    Optional<Insumo> findByNombre(String nombre);

    List<Insumo> findByActivoTrueOrderByNombreAsc();

    /**
     * Existencias de todos los insumos activos.
     *
     * El stock sale de sumar el kardex, no de una columna. El LEFT JOIN y el
     * COALESCE son necesarios para que un insumo recién creado, sin ningún
     * movimiento todavía, aparezca en 0 en vez de desaparecer de la lista.
     */
    @Query("""
            SELECT new restaurante.api.inventario.DatosExistencia(
                i.id_insumos,
                i.nombre,
                i.unidad,
                COALESCE(SUM(m.cantidad), 0L),
                i.stock_minimo
            )
            FROM insumo i
            LEFT JOIN movimientoInventario m ON m.insumo = i
            WHERE i.activo = true
            GROUP BY i.id_insumos, i.nombre, i.unidad, i.stock_minimo
            ORDER BY i.nombre ASC
            """)
    List<DatosExistencia> existencias();

    /**
     * Stock de un solo insumo. Misma fuente de verdad que `existencias()`.
     * Devuelve Long porque eso es lo que da SUM(); se convierte donde se usa.
     */
    @Query("""
            SELECT COALESCE(SUM(m.cantidad), 0L)
            FROM movimientoInventario m
            WHERE m.insumo.id_insumos = :idInsumo
            """)
    Long stockDe(Long idInsumo);
}
