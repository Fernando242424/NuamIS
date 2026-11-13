# Trazabilidad HU-01..HU-28 (Informe de Ingeniería de Software)

Estado: ✅ cumplido y probado · 🟡 cumplido parcialmente / simulado · 🏗️ depende de infraestructura u operación (no del código).

| HU | Tema | Estado | Dónde / observación |
|----|------|--------|---------------------|
 | 01 | Consultar calificaciones (filtros, paginación, indicador de carga) | ✅ | `#mantenedor`, `CalificacionService.listar` |
 | 02 | Ingresar calificación manual (validaciones, duplicados, suma 8-19 ≤ 1) | ✅ | `#calificacion/new` |
 | 03 | Modificar calificación (versiones, bloqueo por período cerrado) | ✅ | `calificacion_version` |
 | 04 | Eliminar (borrado lógico, confirmación) | ✅ | |
 | 05 | Detalle e historial | ✅ | |
 | 06 | Carga masiva CSV/XLSX | ✅ | `BulkLoader` |
 | 07 | Notificaciones de vencimiento | 🟡 | Dentro de la aplicación; sin envío de correo |
 | 08 | Usuarios y roles (enlace de 24 h para definir contraseña) | ✅ | Enlace se entrega al administrador (sin servidor de correo) |
 | 09 | Seguimiento de períodos | ✅ | |
 | 10 | Comentarios/observaciones | ✅ | |
 | 11 | Respaldos (02:00, cifrado, verificación, 1 reintento, retención) | ✅ | `Backup` |
 | 12 | 2FA (TOTP) y bloqueo | ✅ | `Auth` |
 | 13 | Versionado | ✅ | |
 | 14 | Búsqueda por instrumento (≥ 3 caracteres, 500 ms) | ✅ | |
 | 15 | Exportar Excel/PDF | ✅ | `Export` |
 | 16 | Dashboard | ✅ | |
 | 17 | Conciliación con el SII | 🟡 | SII simulado (`/mock/sii/dj`) |
 | 18 | Aprobación de períodos | ✅ | |
 | 19 | API de solo lectura (token 60 min, 100 req/min, 405/403/429) | ✅ | |
 | 20 | Expiración de sesión (aviso a los 13 min, cierre a los 15) | ✅ | |
 | 21 | HTTPS / TLS | ✅ | `Tls` · certificado real = pendiente de producción |
 | 22 | Cifrado de datos | 🟡 | Respaldos AES-256-GCM; cifrado del disco de la BD = infraestructura |
 | 23 | Rendimiento (< 3 s, 5000 filas, 50 usuarios) | ✅ | `tests/prueba_carga.py` (p95 = 1,65 s) |
 | 24 | Disponibilidad (/health, mantención, registro de caídas) | 🏗️ | El 99 % depende del hosting; `monitor.sh` alerta |
 | 25 | Escalabilidad (5× datos) | ✅ | Índices; `tests/datos_carga.sql -v n=50000` |
 | 26 | Usabilidad | 🟡 | Tooltips, mensajes accionables; la prueba con usuarios reales es un proceso |
 | 27 | Inmutabilidad e integridad del log (hash encadenado) | ✅ | Trigger + `/auditoria/verificar` |
 | 28 | Validación de archivos de carga | ✅ | `BulkLoader.validar` |

Diferencia de tecnología: el informe nombra Spring Boot; se implementó en Java puro + JDBC.
