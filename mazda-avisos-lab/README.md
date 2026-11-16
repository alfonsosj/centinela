# Mazda Avisos Lab

## 1.1.12 — Ventanillas sin marco y cierre automático

- Filtra aperturas breves del cristal al abrir puertas: confirma la apertura tras 10 segundos estables y tras el cierre de la puerta cuando se conoce. Suprime también el cierre de una apertura breve descartada.
- El recordatorio de coche aparcado sin asegurar puede solicitar además el bloqueo automático, sin diálogo de confirmación. El ajuste de bloqueo está marcado inicialmente y solo actúa si también está activo el recordatorio.
- Requiere coche aparcado/desbloqueado un minuto, cuatro puertas y maletero cerrados con datos MQTT recientes, 10 segundos de asentamiento, lectura HTTPS previa y autorización de bloqueo válida. Verifica ambas cerraduras; los fallos se notifican sin declarar éxito. Tres intentos por ciclo como máximo, con un minuto entre ellos.
- Autorización de bloqueo ligada al mismo coche del monitor, solo en RAM mientras está activo; conserva su propia copia del PIN, separada de la caducidad de 15 minutos de los controles manuales. Se elimina al desactivar la función, detener el monitor, cerrar sesión o finalizar el proceso. Depende de que Mazda siga aceptando la sesión.
- Los controles manuales y automáticos se serializan; una orden manual interrumpe un automático pendiente y lo aplaza un minuto. Una orden ya recibida por Mazda puede estar en curso.
- No cierra físicamente puertas, ventanillas o maletero ni detecta llaves/ocupantes. No garantiza que el coche quede cerrado siempre.
- `versionCode 35`, misma firma estable. Pruebas de política, ventanas y protocolo con HTTPS simulado, además de compilación Android.


## 1.1.11 — Investigación de funciones chinas

- La lectura europea añade metadatos de `commandCode` y marcas de disponibilidad sin interpretar un código como prueba de ejecución.
- Inventario acotado en memoria de nombres de campos HTTPS/MQTT y valores numéricos de estados de Centinela, cámaras y APA. No almacena mensajes completos, credenciales, VIN ni ubicaciones. Se reinicia al autenticar una sesión.
- Prueba regional opcional de lectura de configuración. Solo tras aceptar el diálogo específico puede enviar el token temporal europeo y el identificador del coche a la ruta china ya identificada, con el canal `chang-ma`. Prueba como máximo el token completo y su tramo TSP; no envía contraseña, PIN ni órdenes, ni migra la vinculación.
- Los cuatro resultados se reúnen en «Copiar informe completo».
- `versionCode 34`, firma estable para instalar sobre la versión anterior.

## 1.1.10 — Diagnóstico de funciones China / Europa

- Acceso en Conexión y herramientas para consultar la configuración de funciones del propio coche en Europa y buscar indicios de Centinela, fotografía interior/exterior, DVR y aparcamiento remoto.
- Prueba anónima de la pasarela china: no envía cuenta, PIN, VIN ni token europeo. No equivale a autenticar una cuenta europea en China.
- Las dos consultas tienen destinos fijos, límites de respuesta y no contienen rutas de control. El informe muestra solo códigos funcionales y omite identificadores y credenciales.
- El análisis estático del APK oficial chino v1.0.72 confirma módulos de Centinela, fotografías y aparcamiento. Esto no acredita que un CX-6e europeo admita esas funciones. Véase `docs/CHINA_RESEARCH_2026-11-14.md`.
- `versionCode 33`, misma firma de actualización.

## 1.1.9 — Icono Mazda con antenas y ojos

- Icono de aplicación aprobado por el usuario: emblema negro de Mazda con antenas y ojos, sobre fondo blanco, conservando el archivo original sin redibujarlo.
- Icono adaptable para las distintas formas de los lanzadores Android.
- Versión de la marca con transparencia para los avisos del coche, la notificación persistente del monitor, sus incidencias/reconexiones, el recordatorio de reactivación y el acceso rápido de Android. El sistema aplica el color correspondiente en la barra de estado.
- `versionCode 32`, misma firma estable para actualizar conservando datos.

## 1.1.8 — Contenido visible junto a las barras de Android

- Las nueve pantallas de la app comparten un contenedor que reserva el espacio real de la barra de estado, la navegación por botones o gestos y el recorte de cámara.
- Los márgenes se recalculan según los insets de Android, también al girar y al mostrar el teclado. No se añaden alturas fijas ni se acumula espacio al reconstruir la portada.
- Se conservan los márgenes de diseño de cada pantalla y se evita aplicar dos veces los de sistema en Notificaciones y Ayuda.
- `versionCode 31`, misma firma estable para actualizar conservando datos.

## 1.1.7 — Notificaciones y Ayuda desde los ajustes

- Dos apartados nuevos en los ajustes de avisos: acceso a Notificaciones y Ayuda.
- Notificaciones reúne los permisos reales de Android, la categoría de avisos, una prueba local, acceso a los ajustes de batería y el historial local de los últimos 100 eventos. La prueba no representa un evento del coche ni se añade al historial.
- Ayuda funciona sin conexión, con 17 temas desplegables y búsqueda sin distinción de acentos: configuración inicial, cuentas, PIN temporal, conexión, controles, paneles, avisos, recordatorios, accesibilidad, historial, privacidad y solución de problemas.
- La ayuda refleja la caducidad real de la sesión de control a los 15 minutos y distingue las funciones implementadas de las pendientes de validación.
- `versionCode 30`, misma firma estable para actualizar la 1.1.6 conservando datos.

## v0.12 — tablero visual

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
