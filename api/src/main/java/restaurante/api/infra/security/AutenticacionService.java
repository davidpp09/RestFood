package restaurante.api.infra.security;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import restaurante.api.usuario.UsuarioRepository;

@Service
public class AutenticacionService implements UserDetailsService {
    
    @Autowired
    private UsuarioRepository repository; // 3. Necesitamos nuestro repositorio

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 4. Aquí es donde ocurre la magia de búsqueda
        return repository.findByEmail(username);
    }
}
