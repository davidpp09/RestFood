-- Cuántos platillos puede tener activos cada categoría a la vez.
--
-- Antes el tope era un 7 clavado en ProductosController, igual para todas las
-- categorías. El menú del día en PDF solo tiene 5 renglones en su recuadro, así
-- que "Comida del día" necesita un tope de 5 mientras el resto sigue en 7.
-- Dejarlo en la base y no en el código permite ajustarlo sin desplegar.

ALTER TABLE categorias ADD COLUMN max_activos INT NOT NULL DEFAULT 7;

-- El LIKE cubre las dos escrituras posibles del nombre ('dia' y 'día').
UPDATE categorias SET max_activos = 5 WHERE nombre LIKE 'Comida del d%a';
