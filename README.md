# nuam · Mantenedor de Calificaciones Tributarias

Java 17+ (solo JDK + driver JDBC, sin Maven ni frameworks) + PostgreSQL 14+. Interfaz web incluida (`web/`).

## Ejecutar
```bash
# 1) Base de datos (una vez)
psql -U postgres -c "CREATE ROLE nuam LOGIN PASSWORD 'nuam'" -c "CREATE DATABASE nuam_is OWNER nuam"
# (Esta versión, de Ingeniería de Software, usa su propia base «nuam_is»: no toca la «nuam» del Proyecto Integrado. Si el rol nuam ya existe, omite CREATE ROLE.)
# 2) Compilar y arrancar (crea el esquema de sql/schema.sql y carga datos demo)
./run.sh            # https://localhost:8443  (el puerto 8080 redirige; el navegador avisará del certificado de desarrollo: Avanzado → Continuar)
```
Variables: `PORT`, `NUAM_DB_URL`, `NUAM_DB_USER`, `NUAM_DB_PASS`, `NUAM_DEMO` (1 muestra el código 2FA en pantalla; **poner 0 en producción**), `NUAM_SEED`, `NUAM_IDLE_SECONDS` (900), `NUAM_BACKUP_KEY`, `NUAM_API_RATE` (60/min), `NUAM_SII_URL`.

Usuarios iniciales (clave `Nuam2026!`, cámbiela al ingresar): `admin@nuam.cl` (Fernando, administrador), `abel@abel.cl` (Abel, corredor) y `joako@joako.cl` (Joako, supervisor), ambos de Corredora Andes. Abel y Joako configuran el 2FA en su primer ingreso. Los usuarios dados de baja se ocultan en la lista (casilla para verlos).

## Base de datos (`sql/schema.sql`)
Tablas del diagrama: corredora, usuario, instrumento, calificacion, factor, comentario, carga_masiva, carga_masiva_detalle, auditoria.
Añadidas para las historias: calificacion_version, periodo, notificacion, conciliacion, respaldo, api_token, dispositivo, mercado_formula.
`auditoria` es inmutable a nivel de BD (trigger bloquea UPDATE/DELETE/TRUNCATE, incluso para el dueño).

## Historias de usuario
| HU | Dónde |
|---|---|
| 1 Filtros mercado/origen/periodo | Mantenedor |
| 2 Nueva calificación (validación, duplicados, cancelar) | Mantenedor → Nueva |
| 3 Cálculo de factores | botón "Calcular factores" (`POST /api/v1/calcular`) |
| 4 Suma factores 8–19 ≤ 1 | formulario + servidor (manual, API, carga masiva, SII) |
| 5 Auditoría inmutable | Auditoría (ADMIN/AUDITOR) |
| 6 Exportar Excel/PDF | Mantenedor |
| 7 Notificación vencimiento DJ 1948 | campana + banner de plazo vencido |
| 8 Usuarios y roles | Usuarios y roles |
| 9 Dashboard | Dashboard (auto-actualiza) |
| 10 Comentarios · 13 Versiones | detalle de la calificación |
| 11 Respaldos cifrados diarios (23:55) y restauración | Respaldos |
| 12 2FA TOTP (3 intentos → bloqueo 15 min, dispositivo nuevo) | login |
| 14 Búsqueda por nombre (sin tildes, anti-inyección) | Mantenedor |
| 15 Integración SII | Carga masiva (**simulada**: `NUAM_SII_URL` apunta al servicio real) |
| 16 Multi-mercado Chile/Perú/Colombia | filtro país + tabla `mercado_formula` |
| 17 Conciliación certificado vs DJ | detalle de la calificación |
| 18 Aprobación del supervisor y cierre de periodo | Periodos / Aprobaciones |
| 19 API REST con token + límite de uso | API / Tokens, `/api/v1/calificaciones` |
| 20 Cierre de sesión por inactividad (15 min) | automático, queda en auditoría |

## Declaraciones juradas
Cada calificación tiene un tipo de DJ: **1948** (por defecto) o **1922**. Se elige en el formulario, se filtra en el mantenedor, va en la exportación, en la carga masiva (columna `tipo_dj`) y en la importación del SII. El plazo de vencimiento y las notificaciones siguen usando la fecha de la DJ 1948.

## Supuestos a validar
- Fórmulas por país (`mercado_formula`): son parametrizables, pero deben contrastarse con la normativa real (SII/SUNAT/DIAN).
- El SII no se consulta en vivo: hay un servicio simulado en `/mock/sii/dj`.
- Notificaciones solo dentro de la aplicación (sin correo).

## Informe de Ingeniería de Software (HU-01 a HU-28)

El informe final define 28 historias (20 funcionales + 8 no funcionales). La trazabilidad criterio por criterio está en
[`docs/matriz_HU.md`](docs/matriz_HU.md). Lo agregado para cumplir las no funcionales:

| HU | Qué se hizo |
|----|-------------|
| HU-21 | HTTPS con TLS 1.3 (mínimo 1.2) en el puerto 8443; el puerto 8080 redirige (308). Cookie `Secure`+`HttpOnly`, HSTS, alerta al administrador cuando el certificado vence en ≤ 30 días. Certificado autofirmado de desarrollo en `data/nuam.p12` (generado con `keytool`); en producción: `NUAM_KEYSTORE`, `NUAM_KEYSTORE_PASS`. `NUAM_TLS=0` lo desactiva (solo pruebas). |
| HU-22 | Respaldos AES-256-GCM; la clave (`NUAM_BACKUP_KEY`) y la del certificado están en variables de entorno, fuera de los datos. El cifrado de la BD en disco se hace a nivel de volumen/instancia (LUKS/BitLocker/disco cifrado del proveedor): PostgreSQL no lo trae de fábrica. |
| HU-23/25 | Índices de filtros/búsqueda, aviso al administrador si una consulta pasa de 3 s (`CONSULTA_LENTA`), `tests/datos_carga.sql` + `tests/prueba_carga.py` (50 usuarios, 12.000 registros). |
| HU-24 | `/health` (estado + BD), modo mantención con mensaje «Sistema en mantención» (el administrador sigue operando), mantención programada con ≥ 48 h y fuera de L-V 08–19, registro de caídas (`SERVICIO_CAIDA` con inicio, fin y causa), `monitor.sh` para alertar a < 5 min. |
| HU-26 | Tooltips en los factores, mensajes de error con la acción a seguir, diálogo de inactividad accesible. |
| HU-27 | Hash SHA-256 encadenado por evento (trigger en la BD), botón «Verificar integridad del log», triggers que rechazan UPDATE/DELETE/TRUNCATE y registran el intento. |
| HU-28 | Botón «Validar estructura» (extensión, tamaño, filas, columnas) + rechazo por fila; los archivos rechazados quedan en la auditoría. |

Otros ajustes del informe: aviso de inactividad a los 13 min con cuenta regresiva de 2 min y botones Continuar / Cerrar sesión (HU-20),
límite de 100 solicitudes/min con 429 + tiempo de reintento, tokens de API con vigencia de 60 min (configurable), API solo lectura (405/403) (HU-19),
respaldo automático a las 02:00 con verificación de integridad y un reintento (HU-11).

**Diferencias con el informe:** el informe menciona Spring Boot; este proyecto usa Java 17+ con JDBC puro (misma funcionalidad, sin dependencias).
El SII y el envío de correos siguen simulados (notificaciones dentro de la aplicación).

## Nube gratis: Neon (base de datos) + Render (aplicación)

1. **Neon** (neon.tech): crear proyecto → copiar la cadena de conexión *sin pooler* (desactivar «Connection pooling»). De `postgresql://USUARIO:CLAVE@HOST/BASE?sslmode=require` salen:
   `NUAM_DB_URL=jdbc:postgresql://HOST/BASE?sslmode=require`, `NUAM_DB_USER=USUARIO`, `NUAM_DB_PASS=CLAVE`.
2. Subir esta carpeta a un repositorio de GitHub (el `.gitignore` ya excluye `out/`, respaldos y datos).
3. **Render** (render.com): New → Web Service → el repositorio → *Runtime: Docker*, plan *Free*. Cargar las tres variables de arriba (`render.yaml` define el resto).
4. El esquema y los usuarios iniciales se crean solos en el primer arranque. Render entrega el HTTPS.

Limitaciones del plan gratis: Render duerme el servicio tras ~15 min sin uso (la primera visita tarda ~1 min) y su disco es temporal, por lo que los respaldos del módulo «Respaldos» no sobreviven a un reinicio; los datos sí persisten en Neon.
