package restaurante.api.ordenDetalle;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.List;

public interface OrdenDetalleRepository extends JpaRepository<OrdenDetalle,Long> {


    @Query("SELECT d FROM ordenDetalle d WHERE d.orden.id_ordenes = :id")
    List<OrdenDetalle> findAllByOrdenId(Long id);

}
