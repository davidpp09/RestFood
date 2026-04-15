package restaurante.api.usuario;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id_usuarios")
@Table(name = "usuarios")
@Entity(name = "usuario")
public class Usuario implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_usuarios;

    @Column(unique = true, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Roles rol;

    @Setter
    @Column(nullable = false)
    private String contrasena;

    @Column(nullable = false)
    private Boolean estatus;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true)
    private Integer seccion;


    public Usuario(DatosRegistroUsuario datosRegistroUsuario) {
        this.id_usuarios = null;
        this.nombre = datosRegistroUsuario.nombre().toUpperCase();
        this.rol = datosRegistroUsuario.rol();
        this.contrasena = datosRegistroUsuario.contrasena();
        this.estatus = true;
        this.email = datosRegistroUsuario.email();
        this.seccion = datosRegistroUsuario.seccion();
    }

    public Usuario(Long id_usuarios) {
        this.id_usuarios = id_usuarios;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Aquí convertimos tu Enum Roles en algo que Spring Security entienda
        // Usualmente se le agrega el prefijo "ROLE_"
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return contrasena;
    }

    @Override
    public String getUsername() {
        return email; // Usaremos el email como el identificador de inicio de sesión
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // La cuenta no expira
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // La cuenta no está bloqueada
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Las credenciales no expiran
    }

    @Override
    public boolean isEnabled() {
        return estatus; // Usamos tu campo estatus para saber si está activo
    }

    public void actualizarInformacion(@Valid DatosActualizacionUsuario datos) {
        if (datos.nombre() != null) {
            this.nombre = datos.nombre();
        }
        if (datos.email() != null) {
            this.email = datos.email();
        }

    }

    public void eliminarUsuario(Long id) {
        this.estatus = false;
    }

    public void activarUsuario(Long id) {
        this.estatus = true;
    }

}
