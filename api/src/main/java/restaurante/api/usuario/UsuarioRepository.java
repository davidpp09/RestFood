package restaurante.api.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;


public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Page<Usuario> findAllByEstatusTrue(Pageable pagina);

    UserDetails findByEmail(String email);
}
