package restaurante.api.orden;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import restaurante.api.mesa.Mesa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrdenRepository extends JpaRepository<Orden, Long> {
    Page<Orden> findAllByTipo(Pageable pagina, Tipo tipo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM orden o WHERE o.id_ordenes = :id")
    Optional<Orden> findByIdConBloqueo(Long id);

    List<Orden> findByFechaCierreBetweenAndEstatus(LocalDateTime inicio, LocalDateTime fin, Estatus estatus);

    List<Orden> findByMesaAndEstatus(Mesa mesa, Estatus estatus);

    // Órdenes "vivas" de una mesa (PREPARANDO o SERVIDO): una orden servida pero
    // aún no pagada sigue ocupando la mesa.
    List<Orden> findByMesaAndEstatusIn(Mesa mesa, List<Estatus> estatus);

    @Query("SELECT o FROM orden o WHERE o.mesa.id_mesas = :id_mesa AND o.estatus = 'PREPARANDO'")
    Optional<Orden> findActivaByMesa(Long id_mesa);

    List<Orden> findByEstatus(Estatus estatus);

    // Las CANCELADAS no cuentan como entregas del día
    @Query("SELECT o FROM orden o WHERE o.tipo = :tipo AND o.fecha_apertura BETWEEN :inicio AND :fin AND o.estatus <> restaurante.api.orden.Estatus.CANCELADA")
    List<Orden> findEntregasDelDia(@Param("tipo") Tipo tipo,
                                   @Param("inicio") LocalDateTime inicio,
                                   @Param("fin") LocalDateTime fin);

    // Entregas del día de UN repartidor: su historial no debe incluir las de otros
    // (además el numero_comanda es por usuario, así que mezclarlos duplica números).
    @Query("SELECT o FROM orden o WHERE o.tipo = :tipo AND o.usuario.id_usuarios = :idUsuario AND o.fecha_apertura BETWEEN :inicio AND :fin AND o.estatus <> restaurante.api.orden.Estatus.CANCELADA")
    List<Orden> findEntregasDelDiaByUsuario(@Param("tipo") Tipo tipo,
                                            @Param("idUsuario") Long idUsuario,
                                            @Param("inicio") LocalDateTime inicio,
                                            @Param("fin") LocalDateTime fin);

    // Todas las órdenes del día (loza y para llevar) ordenadas por empleado y luego
    // por número de comanda, para el panel de admin "Comandas por empleado".
    // Las CANCELADAS no se listan (ya tienen su propio reporte de cancelaciones).
    @Query("SELECT o FROM orden o WHERE o.fecha_apertura BETWEEN :inicio AND :fin AND o.estatus <> restaurante.api.orden.Estatus.CANCELADA ORDER BY o.usuario.nombre ASC, o.numero_comanda ASC")
    List<Orden> findOrdenesDelDia(@Param("inicio") LocalDateTime inicio,
                                  @Param("fin") LocalDateTime fin);

    // Siguiente número de comanda = MAX+1 (no COUNT+1: borrar una orden de en medio
    // del día haría repetir números). Las CANCELADAS no consumen número.
    @Query("SELECT COALESCE(MAX(o.numero_comanda), 0) FROM orden o WHERE o.usuario.id_usuarios = :idUsuario AND o.fecha_apertura BETWEEN :inicio AND :fin AND o.estatus <> restaurante.api.orden.Estatus.CANCELADA")
    Long maxNumeroComandaByUsuarioIdAndFechaBetween(@Param("idUsuario") Long idUsuario,
                                                    @Param("inicio") LocalDateTime inicio,
                                                    @Param("fin") LocalDateTime fin);
}
