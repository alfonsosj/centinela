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


## v0.14: bloqueo/desbloqueo remoto experimental

- Controles de bloqueo y desbloqueo con sesión de la cuenta secundaria.
- PIN de Mazda de seis cifras solicitado para cada orden, no se guarda.
- Confirmación obligatoria antes de actuar.
- PIN autorizado por Mazda, número de serie de control descifrado con RSA, petición RSA-SHA256 firmada, consulta del resultado y verificación independiente del estado de las dos cerraduras.
- La sesión de control dura 15 minutos como máximo en RAM, sin persistir token o clave de control.
- Nunca activa operaciones de forma automática ni supone ejecución correcta por una simple respuesta HTTP.
- Debe verificarse en el vehículo real, aparcado y en condiciones seguras.


## 1.0.0 RC1 — compilación integral

Esta compilación agrupa el diseño CX-6e Aero Grey, cuatro iconos de estado,
notificaciones MQTT en servicio de primer plano, alertas configurables,
Centinela básico por cambios de apertura, historial local y consultas de batería,
temperatura, autonomía, carga, cable, objetivo, ventanas, maletero y climatización
cuando Mazda proporciona el valor.

Controles remotos disponibles en la interfaz con consentimiento individual:
- Bloquear/desbloquear puertas: probado con el vehículo del usuario.
- Abrir/cerrar las cuatro ventanas y portón: protocolo europeo conocido;
  requiere PIN, coche aparcado y telemetría reciente; no probado físicamente.
- Climatización remota y objetivo de temperatura, desempañado, calefacción del volante,
  calefacción/ventilación de los asientos delanteros, encontrar vehículo y bocina,
  y porcentaje máximo de carga 60–100%: comandos implementados en base
  a la API comunitaria de Mazda Europa; no validados físicamente.
- Actualización puntual de estado por HTTP autenticado; no envía órdenes.
- Se registra localmente el resultado comunicado por Mazda de cada orden;
  no se almacenan PIN, contraseña o token en el historial.

**No equivalen a funciones verificadas** los comandos que solo han superado
compilación; pueden ser rechazados por permisos, equipamiento, red o cambios en API.
El resultado puede quedar 'sin confirmar' aunque la petición haya sido aceptada.

**No incluidos**: start/stop carga remoto sin protocolo confirmado, eventos
de ocupantes de Centro de Mensajes por MQTT (no observados), DVR/cámaras ni
alarmas reales de intrusión, garantía 24/7 o arranque automático del monitor
tras reiniciar el móvil. El servicio MQTT depende del sistema operativo,
permisos y la validez temporal de la autorización; su estado debe comprobarse.

**Firma**: mantenida mediante secreto privado de GitHub Actions. versionCode 20
permite actualizar v0.18/v0.19 firmadas con la misma clave sin reinstalar.
