package restaurante.api.controller.ordenes;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.util.UriComponentsBuilder;
import restaurante.api.infra.errores.RecursoNoEncontradoException;
import restaurante.api.infra.errores.ValidacionException;
import restaurante.api.infra.security.DatosLoginRespuesta;
import restaurante.api.infra.security.RoutingService;
import restaurante.api.usuario.*;

import java.net.URI;

@RequestMapping("/usuarios")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'DEV', 'CAJERO')")
public class UsuariosController {

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder; // 1. Inyectamos la herramienta de cifrado

    @Autowired
    private RoutingService routingService;

    // Revalidación de sesión: el frontend llama esto al arrancar para saber si el token sigue siendo válido.
    // Cualquier rol autenticado puede consultar sus propios datos (override del @PreAuthorize de clase).
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DatosLoginRespuesta> me(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new DatosLoginRespuesta(
                null, // no reemitir el token
                usuario.getRol().name(),
                usuario.getNombre(),
                usuario.getId_usuarios(),
                usuario.getSeccion(),
                routingService.rutaPorRol(usuario.getRol())
        ));
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaUsuario> registrar(@RequestBody @Valid DatosRegistroUsuario datosRegistroUsuario, UriComponentsBuilder uriComponentsBuilder) {

        // 2. Creamos la instancia de Usuario
        Usuario usuario = new Usuario(datosRegistroUsuario);

        // 3. Encriptamos la contraseña que viene en el DTO y se la asignamos al usuario
        String passwordEncriptada = passwordEncoder.encode(datosRegistroUsuario.contrasena());
        usuario.setContrasena(passwordEncriptada);

        // 4. Guardamos al usuario ya protegido
        repository.save(usuario);

        DatosRespuestaUsuario datosRespuesta = new DatosRespuestaUsuario(
                usuario.getId_usuarios(),
                usuario.getNombre(),
                usuario.getRol().toString(),
                usuario.getEmail(),
                usuario.getEstatus()
        );

        URI url = uriComponentsBuilder.path("/usuarios/{id}").buildAndExpand(usuario.getId_usuarios()).toUri();
        return ResponseEntity.created(url).body(datosRespuesta);
    }

    @GetMapping
    public ResponseEntity<Page<DatosListaUsuario>> listar(@PageableDefault(size = 10, sort = {"nombre"}) Pageable pagina) {
        var page = repository.findAllByEstatusTrue(pagina).map(DatosListaUsuario::new);
        return ResponseEntity.ok(page);
    }

    @PutMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<DatosRespuestaUsuario> actualizar(@RequestBody @Valid DatosActualizacionUsuario datos,
                                                            @AuthenticationPrincipal Usuario solicitante) {
        var usuario = repository.findById(datos.id_usuarios())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        // Solo un DEV puede tocar cuentas DEV o asignar el rol DEV
        if (solicitante.getRol() != Roles.DEV) {
            if (usuario.getRol() == Roles.DEV) {
                throw new ValidacionException("Solo un usuario DEV puede modificar una cuenta DEV.");
            }
            if (datos.rol() == Roles.DEV) {
                throw new ValidacionException("Solo un usuario DEV puede asignar el rol DEV.");
            }
        }
        usuario.actualizarInformacion(datos);
        return ResponseEntity.ok(new DatosRespuestaUsuario(
                usuario.getId_usuarios(),
                usuario.getNombre(),
                usuario.getRol().toString(),
                usuario.getEmail(),
                usuario.getEstatus()
        ));
    }

    // Reset de contraseña por un administrador (el empleado no necesita la anterior)
    @PutMapping("/{id}/contrasena")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity<Void> cambiarContrasena(@PathVariable Long id,
                                                  @RequestBody @Valid DatosCambioContrasena datos,
                                                  @AuthenticationPrincipal Usuario solicitante) {
        var usuario = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        if (usuario.getRol() == Roles.DEV && solicitante.getRol() != Roles.DEV) {
            throw new ValidacionException("Solo un usuario DEV puede cambiar la contraseña de una cuenta DEV.");
        }
        usuario.setContrasena(passwordEncoder.encode(datos.contrasena()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity eliminarLogico(@PathVariable Long id) {
        var usuario = repository.getReferenceById(id);
        usuario.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/activar/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public ResponseEntity activar(@PathVariable Long id) {
        var usuario = repository.getReferenceById(id);
        usuario.activarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    // Borrado físico: sólo DEV (riesgoso; puede romper FK e historial de órdenes)
    @DeleteMapping("/eliminar/{id}")
    @Transactional
    @PreAuthorize("hasRole('DEV')")
    public ResponseEntity eliminar(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}