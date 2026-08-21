-- El menú del día pasa de 5 platillos a 6.
--
-- V5 puso el tope en 5 porque el recuadro del PDF tenía 5 renglones. Ese recuadro
-- no ha crecido: los 6 caben porque MenuDiaService apretó la interlínea (de 13.77
-- a ~11.3 pt). Los dos números van juntos — si aquí se sube a 7 sin tocar el PDF,
-- el séptimo platillo se activa en la pantalla y NO sale impreso en el menú.

-- El LIKE cubre las dos escrituras posibles del nombre ('dia' y 'día').
UPDATE categorias SET max_activos = 6 WHERE nombre LIKE 'Comida del d%a';
