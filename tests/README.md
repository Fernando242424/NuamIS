# Pruebas de carga (HU-23 y HU-25)

1. Con el sistema en marcha: `psql -U nuam -d nuam -v n=12000 -v corredora=1 -f tests/datos_carga.sql`
   (para 5× el volumen: `-v n=50000`). Cambie `corredora` por el id de su corredora.
2. `python3 tests/prueba_carga.py https://localhost:8443 <correo-corredor> 50 10`
   Resultado esperado: «CUMPLE (95 % < 3 s)».
3. Limpieza: `DELETE FROM factor WHERE calificacion_id IN (SELECT calificacion_id FROM calificacion WHERE descripcion LIKE 'CARGA-PRUEBA%');`
   y luego `DELETE FROM calificacion WHERE descripcion LIKE 'CARGA-PRUEBA%';`

Resultado de referencia (sandbox, 12.000 registros, 50 usuarios, 500 consultas): p50 1,18 s · p95 1,65 s · máx 1,87 s · 0 errores.
