package restaurante.api.infra.errores;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorDeErrores {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity tratarError404() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity tratarError400(MethodArgumentNotValidException e) {
        var errores = e.getFieldErrors().stream()
                .map(DatosErrorValidacion::new)
                .toList();
        return ResponseEntity.badRequest().body(errores);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity tratarErrorAccesoDenegado() {
        return ResponseEntity.status(403).body(new DatosRespuestaError("Acceso denegado. No tienes los permisos necesarios para este recurso."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity tratarErrorBadCredentials() {
        return ResponseEntity.status(401).body(new DatosRespuestaError("Credenciales inválidas. Por favor, verifica tu correo y contraseña."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity tratarError500(Exception e) {
        return ResponseEntity.status(500).body(new DatosRespuestaError("Error interno en el servidor: " + e.getLocalizedMessage()));
    }
}