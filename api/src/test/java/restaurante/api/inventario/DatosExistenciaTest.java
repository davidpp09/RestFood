package restaurante.api.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La alerta de mínimos vivía dentro de un CASE WHEN en la consulta JPQL, donde
 * no se podía probar sin levantar la base — y además rompía el arranque, porque
 * Hibernate no encontraba constructor que casara. Al bajarla a Java se arregló
 * lo uno y se ganó lo otro.
 */
class DatosExistenciaTest {

    @Test
    @DisplayName("Avisa cuando el stock llega al mínimo, no solo cuando lo cruza")
    void avisaAlLlegarAlMinimo() {
        // Con 20 de mínimo y 20 en existencia ya hay que comprar: si esperáramos
        // a 19, la alerta llegaría el día que ya faltó.
        assertTrue(DatosExistencia.estaBajoMinimo(20, 20));
        assertTrue(DatosExistencia.estaBajoMinimo(19, 20));
        assertTrue(DatosExistencia.estaBajoMinimo(0, 20));
    }

    @Test
    @DisplayName("No avisa cuando hay de sobra")
    void noAvisaConSuficiente() {
        assertFalse(DatosExistencia.estaBajoMinimo(21, 20));
        assertFalse(DatosExistencia.estaBajoMinimo(300, 20));
    }

    @Test
    @DisplayName("Mínimo en 0 significa 'no me avises de este'")
    void minimoCeroNoAvisa() {
        // Sin esta guarda, todo insumo que llegara a cero dispararía alerta
        // aunque nadie la haya pedido — y la pantalla se volvería ruido.
        assertFalse(DatosExistencia.estaBajoMinimo(0, 0));
        assertFalse(DatosExistencia.estaBajoMinimo(5, 0));
    }

    @Test
    @DisplayName("El stock negativo también alerta: significa una compra sin capturar")
    void negativoAlerta() {
        // No se bloquea que el stock quede negativo a propósito: si la cocina
        // no puede registrar una merma porque 'no hay existencias', deja de
        // registrar. Un negativo es una señal, no un error que ocultar.
        assertTrue(DatosExistencia.estaBajoMinimo(-4, 20));
    }
}
