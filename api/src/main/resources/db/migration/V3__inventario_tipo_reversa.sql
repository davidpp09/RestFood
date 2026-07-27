-- Fase 2 del inventario: un tipo de movimiento nuevo, REVERSA.
--
-- POR QUÉ NO ALCANZABA CON AJUSTE.
--
-- Cuando se cancela un platillo que ya se mandó a cocina, el consumo no
-- desaparece —la carne se cocinó— pero deja de ser una venta: es merma. Eso se
-- asienta con dos renglones, una reversa de la venta y la merma equivalente.
--
-- La reversa se hizo primero con AJUSTE, y estaba mal. AJUSTE significa una
-- cosa muy concreta en este sistema: la diferencia que dejó un conteo físico,
-- es decir **lo que nadie pudo explicar**. Es la columna que se mira para
-- detectar merma no anotada, porciones de más o robo. Meter ahí las reversas
-- —que están perfectamente explicadas— ensuciaba justo el número por el que
-- existe todo este frente. Se detectó viendo el reporte con datos reales: la
-- carne molida aparecía con 33% de varianza que en realidad no era varianza.
--
-- Regla que queda: un tipo de movimiento no describe la dirección del número,
-- describe POR QUÉ se movió. Dos causas distintas no comparten tipo aunque
-- sumen igual.
ALTER TABLE `movimientos_inventario`
    MODIFY COLUMN `tipo`
    enum('INICIAL','COMPRA','MERMA','VENTA','AJUSTE','REVERSA') NOT NULL;
