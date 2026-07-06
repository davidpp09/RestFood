package restaurante.api.usuario;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;


public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Page<Usuario> findAllByEstatusTrue(Pageable pagina);

    UserDetails findByEmail(String email);

    // Lock pesimista: serializa las aperturas de orden concurrentes del mismo usuario
    // para que el cálculo COUNT+1 del número de comanda no produzca duplicados.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM usuario u WHERE u.id_usuarios = :id")
    Optional<Usuario> findByIdConBloqueo(Long id);
}
