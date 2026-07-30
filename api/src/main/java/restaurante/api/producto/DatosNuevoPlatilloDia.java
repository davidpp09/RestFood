package restaurante.api.producto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Alta de un platillo del día por parte del repartidor.
 *
 * Solo lleva nombre y precio: la categoría la pone el servidor. Si viniera del
 * cliente, quien diera de alta un platillo del día podría colarlo en la carta
 * normal cambiando un número en la petición.
 *
 * El límite de 60 caracteres no es capricho: el renglón del PDF mide 263 puntos
 * y a partir de ahí la letra se encoge hasta quedar ilegible en el menú impreso.
 */
public record DatosNuevoPlatilloDia(

        @NotBlank
        @Size(min = 3, max = 60, message = "El nombre debe tener entre 3 y 60 caracteres")
        String nombre,

        @NotNull
        @Positive
        @DecimalMax(value = "9999.99", message = "El precio no puede pasar de 9999.99")
        BigDecimal precio
) {
}
