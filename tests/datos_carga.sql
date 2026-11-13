-- HU-23/25: genera N calificaciones de prueba (con 12 factores cada una) para la corredora indicada.
-- Uso:  psql -U nuam -d nuam -v n=12000 -v corredora=1 -f tests/datos_carga.sql
-- Los registros llevan descripción 'CARGA-PRUEBA' y se eliminan con:  DELETE ... (ver tests/README.md)
INSERT INTO instrumento (nombre, nombre_norm, mercado)
SELECT 'PRUEBA-' || g, 'prueba-' || g, (ARRAY['CL','PE','CO'])[1 + g % 3] FROM generate_series(1, 400) g
ON CONFLICT DO NOTHING;

WITH ins AS (
  INSERT INTO calificacion (instrumento_id, corredora_id, secuencia, ejercicio, periodo_comercial, fecha_pago, descripcion, origen, estado)
  SELECT i.instrumento_id, :corredora, 20000 + g, 2026, (ARRAY['2024','2025','2026'])[1 + g % 3],
         date '2026-01-01' + (g % 300), 'CARGA-PRUEBA dividendo ' || g, (ARRAY['sistema','bolsa','corredor'])[1 + g % 3], 'COMPLETA'
  FROM generate_series(1, :n) g
  JOIN LATERAL (SELECT instrumento_id FROM instrumento WHERE nombre LIKE 'PRUEBA-%' ORDER BY instrumento_id OFFSET (g % 400) LIMIT 1) i ON TRUE
  RETURNING calificacion_id)
INSERT INTO factor (calificacion_id, numero_factor, monto, valor)
SELECT calificacion_id, n, 100, 1.0/12 FROM ins, generate_series(8, 19) n;
