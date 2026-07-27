package restaurante.api.inventario;

/**
 * Las dos únicas unidades del sistema, y ambas se cuentan de una en una.
 *
 * PORCION existe para lo que no llega en piezas naturales (pollo deshebrado,
 * quesos): se porciona antes del servicio y la porción se vuelve un objeto
 * contable. Si la cocina agarrara del bloque conforme salen las órdenes no
 * habría porciones que contar, y el número sería una estimación disfrazada
 * de dato.
 */
public enum Unidad {
    PIEZA,
    PORCION
}
