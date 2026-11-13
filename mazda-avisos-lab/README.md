# Mazda Avisos Lab v0.12 — tablero visual

- Portada con ilustración vectorial original de un crossover y diseño de tarjetas inspirado en la organización de Mazda Europa (sin copiar sus imágenes o su identidad).
- Batería y temperatura interior como indicadores principales; además autonomía, carga, bloqueo y ventanas cuando estén disponibles.
- Los números solo aparecen cuando se han recibido realmente: conexión MQTT no implica telemetría disponible. Se muestra la antigüedad de la última lectura y una advertencia si supera 20 minutos.
- Datos mínimos guardados en el dispositivo únicamente para visualizar las últimas lecturas (SOC, temperatura, autonomía, estados básicos), sin guardar VIN, credenciales ni mensajes MQTT completos.
- Agrupa en una sola notificación las ventanas que cambien juntas dentro del mismo informe, evitando cuatro avisos simultáneos.
- Herramientas técnicas plegadas, botón de configuración visible, historial local de notificaciones.
- Monitoreo MQTT directo en Android sin Home Assistant y funciones previas intactas.
- No es alarma de seguridad; la recepción de mensajes de ocupantes del Centro de Mensajes sigue sin demostrar.

## Firma estable desde v0.13

Para evitar desinstalar en cada prueba, GitHub Actions debe usar siempre la misma clave
de firma. El archivo privado de firma se proporciona mediante el secreto de repositorio
`MAZDA_SIGNING_BUNDLE_B64` (base64 de ZIP que contiene mazda_release.p12 y store_password.txt).
Nunca subir el material de firma ni el secreto a este repositorio público.

El workflow compila primero debug solo para validación y **publica exclusivamente**
la APK release firmada. Si el secreto falta, la compilación falla y no publica
ninguna APK con firma nueva. Asegura conservar una copia del secreto y de la clave.
Android necesitará una última desinstalación de la v0.12 (debug) para instalar
la primera release con esta firma. Los siguientes versionCode crecientes se instalan
como actualización conservando datos.

Además, v0.13 consulta el estado inicial del coche por API Mazda Europa al autenticar la
cuenta secundaria, alimentando las tarjetas de batería y temperatura sin esperar
actualizaciones MQTT.
