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


## Mazda Avisos 1.0.0 — monitorización estable y diagnóstico

La versión 1.0.0 mantiene los controles remotos verificados por el usuario (bloqueo,
ventanas, maletero, clima y demás funciones de su vehículo) sin modificar sus
comandos. Introduce una máquina de estados de MQTT que distingue **conectado**,
**reconectando**, **sin confirmación**, **detenido** y **requiere volver a iniciar sesión**.

- El servicio mantiene los reintentos con retroceso progresivo mientras Android
  lo permite. Tras tres fallos consecutivos, envía una alerta local de pérdida
  de conexión; cuando el monitor recupera MQTT, avisa de la reconexión.
- Si se interrumpe el servicio, indica que debe autenticarse de nuevo.
- Tras reiniciar el teléfono o actualizar la app, el receiver comprueba si el
  usuario había solicitado monitorización y muestra recordatorio de reactivación.
- No hay claves de acceso, PIN, tokens ni contraseña guardados para reanudar
  automáticamente tras reinicio o caducidad de la sesión.
- La interfaz diferencia una conexión actual comprobada de intentos de reconexión.

**Limitación consciente**: sin almacenar credenciales no es posible restaurar
una conexión que haya perdido sus tokens de sesión. Android puede interrumpir
servicios en primer plano, y la recepción de alertas no está garantizada 24/7.
Las notificaciones de ocupantes detectadas en Centro de Mensajes no tienen
correspondencia MQTT comprobada. No se usa Home Assistant.

Firma release permanente; `versionCode 22` instala sobre las versiones previas
con la misma firma. El control remoto conserva la confirmación y el PIN por acción
cuando correspondan.


## 1.1.0 — controles rápidos y paneles de clima/batería

- El icono de cierre está **relleno únicamente cuando está desbloqueado**.
- Ventanas, portón y climatización ejecutan el comando correspondiente al estado
  reciente al tocar el icono, sin abrir antes el catálogo de controles.
  La pulsación muestra confirmación y PIN para movimientos que lo requieren.
  Los datos desconocidos o antiguos obligan a refrescar en lugar de invertir
  ciegamente el estado.
- Se comparte la lógica de comandos Mazda de `VehicleControlActivity` en
  `MazdaRemoteApi`, conservando las mismas rutas, firmas RSA y comprobación
  posterior; `QuickControl` permite utilizarla desde la portada con consentimiento.
- Una sesión de control expirada obliga a autenticar la cuenta secundaria;
  después de iniciar sesión se regresa al comando seleccionado.
- Pulsar **Temp** abre `ClimateDashboardActivity`: pestañas A/C y Asiento,
  temperatura interior, humedad, PM2.5 (cuando exista lectura), temperatura
  objetivo 16–30 °C, climatización, desempañado y accesos a mandos de asientos.
- Pulsar **Batería** abre `BatteryDashboardActivity`: carga, SOC, autonomía,
  cable y acceso al ajuste remoto del límite (60–100 %).
- Los interruptores de carga programada, intervalo de carga y precalentamiento
  de batería que aparecen en la app oficial siguen **sin activar en esta
  implementación**: no hay configuración de plan verificada para escribir en
  Mazda. Se muestran explicaciones en lugar de simulaciones de interruptores.
- La información faltante se representa como «sin datos» y nunca se inventan
  humedades, PM2.5 ni estados físicos.

La compilación Android y la firma NO prueban por sí solas el comportamiento
de los nuevos mandos rápidos; conviene comprobarlos físicamente con el coche
supervisado. `versionCode=23` permite actualizar la 1.0.0 firmada sin
desinstalarla.
