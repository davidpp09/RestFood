package restaurante.api.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductoInsumoRepository extends JpaRepository<ProductoInsumo, Long> {

    /** La receta vista desde el insumo: qué platillos lo consumen. */
    @Query("""
            SELECT r FROM productoInsumo r
            JOIN FETCH r.producto p
            WHERE r.insumo.id_insumos = :idInsumo
            ORDER BY p.nombre ASC
            """)
    List<ProductoInsumo> porInsumo(Long idInsumo);

    /** La receta vista desde el platillo: qué consume. La usará el descuento automático. */
    @Query("""
            SELECT r FROM productoInsumo r
            JOIN FETCH r.insumo
            WHERE r.producto.id_productos = :idProducto
            """)
    List<ProductoInsumo> porProducto(Long idProducto);

    /**
     * Consulta explícita y no un nombre derivado: Spring Data usa el guion bajo
     * como separador para navegar propiedades, y los campos de este proyecto ya
     * lo llevan en el nombre (`id_insumos`). Un `deleteByInsumo_Id_insumos` se
     * interpreta como Insumo.Id.insumos y revienta al arrancar.
     */
    @Modifying
    @Query("DELETE FROM productoInsumo r WHERE r.insumo.id_insumos = :idInsumo")
    void borrarPorInsumo(Long idInsumo);

    /** Cuántos platillos tiene relacionados cada insumo — para el resumen de la pantalla. */
    @Query("""
            SELECT r.insumo.id_insumos, COUNT(r)
            FROM productoInsumo r
            GROUP BY r.insumo.id_insumos
            """)
    List<Object[]> conteoPorInsumo();
}
