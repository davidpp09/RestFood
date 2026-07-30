package restaurante.api.categoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria,Long> {

    /**
     * La categoría del menú del día. Se busca por nombre y no se recibe del
     * cliente a propósito: quien da de alta un platillo del día no debe poder
     * elegir en qué categoría cae, o podría meter productos en la carta normal.
     * El LIKE cubre 'Comida del dia' y 'Comida del día'.
     */
    @Query("SELECT c FROM categoria c WHERE c.nombre LIKE 'Comida del d%a'")
    Optional<Categoria> findCategoriaDelDia();
}
