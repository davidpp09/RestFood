-- Borrado suave de productos: los que ya tienen ventas en orden_detalle no se
-- pueden borrar físicamente (FK RESTRICT protege el historial); se marcan aquí.
ALTER TABLE productos
    ADD COLUMN eliminado BIT(1) NOT NULL DEFAULT b'0';
