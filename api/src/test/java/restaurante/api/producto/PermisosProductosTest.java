package restaurante.api.producto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import restaurante.api.controller.ordenes.ProductosController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quién puede tocar la carta.
 *
 * El 2026-08-11 se le dio al ADMIN la pantalla de Platillos, que hasta entonces
 * era solo de DEV. El frontend por sí solo no alcanza: si el botón aparece pero
 * el backend responde 403, la pantalla miente. Este test vigila el permiso real,
 * que es el del servidor.
 *
 * Compara conjuntos EXACTOS a propósito, no "contiene". Así avisa en las dos
 * direcciones: si alguien le quita el permiso al ADMIN y la pantalla se rompe,
 * y —más importante— si alguien se lo regala de más a un MESERO o a COCINA y
 * cualquiera desde una tablet puede borrar platillos de la carta.
 *
 * No levanta el contexto de Spring: lee las anotaciones de la clase, que es de
 * donde Spring saca la regla. Si el método se renombra, también falla.
 */
class PermisosProductosTest {

    /** Los nombres de rol que aparecen entrecomillados dentro de hasRole/hasAnyRole. */
    private static final Pattern ROL = Pattern.compile("'([A-Z_]+)'");

    private static Set<String> rolesDe(String nombreMetodo) {
        List<Method> metodos = Arrays.stream(ProductosController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(nombreMetodo))
                .toList();

        assertEquals(1, metodos.size(),
                "Se esperaba exactamente un método llamado '" + nombreMetodo + "' en ProductosController");

        PreAuthorize anotacion = metodos.get(0).getAnnotation(PreAuthorize.class);
        assertNotNull(anotacion,
                nombreMetodo + " se quedó sin @PreAuthorize: quedaría abierto a cualquier usuario con sesión");

        Set<String> roles = new LinkedHashSet<>();
        Matcher m = ROL.matcher(anotacion.value());
        while (m.find()) {
            roles.add(m.group(1));
        }
        assertTrue(!roles.isEmpty(),
                nombreMetodo + " tiene un @PreAuthorize que no nombra ningún rol: " + anotacion.value());
        return roles;
    }

    @Test
    @DisplayName("Dar de alta, editar y borrar platillos: solo el dueño (ADMIN) y DEV")
    void administrarLaCarta_SoloAdminYDev() {
        Set<String> esperado = Set.of("ADMIN", "DEV");

        assertEquals(esperado, rolesDe("registrar"));
        assertEquals(esperado, rolesDe("actualizar"));
        assertEquals(esperado, rolesDe("eliminar"));
    }

    @Test
    @DisplayName("Activar y apagar los platillos del día: ADMIN, DEV y el repartidor")
    void menuDelDia_AdminDevYRepartidor() {
        Set<String> esperado = Set.of("ADMIN", "DEV", "REPARTIDOR");

        // El ADMIN ya veía "Platillos del Día" en su menú, pero estos dos endpoints
        // no lo aceptaban: la pantalla cargaba y fallaba al activar. Se corrigió el
        // 2026-08-11 junto con lo de arriba.
        assertEquals(esperado, rolesDe("actualizarDia"));
        assertEquals(esperado, rolesDe("desactivarDia"));

        assertEquals(esperado, rolesDe("registrarDelDia"));
        assertEquals(esperado, rolesDe("archivarDelDia"));
    }

    @Test
    @DisplayName("Leer la carta sí es de todos los que toman órdenes: el mesero no puede quedarse sin menú")
    void listar_IncluyeAlMesero() {
        assertEquals(Set.of("ADMIN", "DEV", "MESERO", "REPARTIDOR"), rolesDe("listar"));
    }
}
