-- Erratas en los nombres de "Comida del día".
--
-- Hasta ahora estos nombres solo los veía el personal en la pantalla, y nadie
-- se fijaba. Desde que el menú del día se genera en PDF, salen impresos en lo
-- que se manda a los clientes: ahí sí se notan.
--
-- Esta migración toca DATOS, no esquema. Es un uso legítimo de Flyway: la
-- corrección queda versionada y se aplica igual en cada entorno, en vez de
-- que alguien la haga a mano en producción y nadie sepa qué se cambió.
--
-- Se compara con BINARY porque la collation de la base (utf8mb4_unicode_ci)
-- considera iguales 'arbol' y 'árbol': sin BINARY, el WHERE no distinguiría el
-- nombre malo del bueno. Con BINARY, correr esto dos veces no hace nada la
-- segunda: si el nombre ya está corregido, ningún renglón coincide.

-- Letras equivocadas o faltantes
UPDATE productos SET nombre = 'Bistec en salsa verde con nopales'
 WHERE BINARY nombre = 'BIstec en salsa verde con nopales';

UPDATE productos SET nombre = 'Lomo de cerdo en salsa de tamarindo y chipotle'
 WHERE BINARY nombre = 'Lomo de cerde en salsa de tamarindo y chipotle';

UPDATE productos SET nombre = 'Pollo en salsa verde con verdolagas'
 WHERE BINARY nombre = 'Pollo en salsa verde con verdolgas';

-- Acentos que faltaban
UPDATE productos SET nombre = 'Bistec en salsa de árbol'
 WHERE BINARY nombre = 'Bistec en salsa de arbol';

UPDATE productos SET nombre = 'Empanadas de plátano rellenas con carne'
 WHERE BINARY nombre = 'Empanadas de platano rellenas con carne';

UPDATE productos SET nombre = 'Rollitos de jamón con ensalada rusa'
 WHERE BINARY nombre = 'Rollitos de jamon con ensalada rusa';

-- Acento + espacio sobrante al final
UPDATE productos SET nombre = 'Hígado encebollado'
 WHERE BINARY nombre = 'Higado encebollado ';

-- Espacio sobrante al final
UPDATE productos SET nombre = 'Rajas poblanas con pollo'
 WHERE BINARY nombre = 'Rajas Poblanas con pollo ';
