-- Fase 3 del inventario: el costo entra al kardex.
--
-- Una columna, y solo la llena COMPRA: cuánto se pagó EN TOTAL por esa entrada
-- ("24 pechugas, $1,560"), que es como viene en la nota del proveedor y como
-- lo piensa quien captura. El costo unitario se deriva (total / cantidad), no
-- se captura — pedir el unitario obligaría al encargado a hacer la división a
-- mano, y cada división a mano es un error esperando turno.
--
-- Es NULL a propósito, por dos razones:
--   1. Los movimientos que no son compra (venta, merma, ajuste...) no tienen
--      costo propio: su valor se deriva del promedio de las compras.
--   2. Una compra SIN costo sigue valiendo: el kardex de cantidades no debe
--      perder una entrada porque el encargado no tenía la nota a la mano. El
--      costo promedio simplemente la ignora, y el reporte de costos avisa
--      que el número está incompleto.
--
-- DECIMAL y no DOUBLE por la misma razón que los precios de productos: el
-- dinero no se guarda en punto flotante.
ALTER TABLE `movimientos_inventario`
    ADD COLUMN `costo_total` DECIMAL(10,2) NULL AFTER `motivo`;
