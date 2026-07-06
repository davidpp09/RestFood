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

    @Query("SELECT o FROM orden o WHERE o.tipo = :tipo AND o.fecha_apertura BETWEEN :inicio AND :fin")
    List<Orden> findEntregasDelDia(@Param("tipo") Tipo tipo,
                                   @Param("inicio") LocalDateTime inicio,
                                   @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(o) FROM orden o WHERE o.usuario.id_usuarios = :idUsuario AND o.fecha_apertura BETWEEN :inicio AND :fin")
    Long countByUsuarioIdAndFechaBetween(@Param("idUsuario") Long idUsuario,
                                         @Param("inicio") LocalDateTime inicio,
                                         @Param("fin") LocalDateTime fin);
}
