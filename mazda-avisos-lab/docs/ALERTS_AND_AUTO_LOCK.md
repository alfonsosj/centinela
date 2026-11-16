# Ventanillas y cierre automático — 1.1.12

La mejora solicitada tiene dos partes: filtrar la bajada automática del cristal de las puertas sin marco y, junto al recordatorio de coche aparcado sin asegurar, enviar el bloqueo sin pedir confirmación en pantalla. El bloqueo conserva la autenticación y firma exigidas por Mazda y verifica el resultado. No modifica ninguna protección del vehículo ni garantiza un cierre permanente.

## Ventanillas

`WindowAlertFilter` procesa los informes parciales sin completar valores ausentes. Espera 10 segundos de apertura estable; si conoce la puerta, espera además 10 segundos desde su cierre. Mientras la puerta figure abierta no confirma una nueva apertura del cristal. Las bajadas y subidas descartadas no generan tampoco un aviso de cierre. La primera observación establece la referencia; una ventana que se confirme abierta puede participar en el recordatorio de ventanas. El filtro se reinicia al perder o recuperar la conexión.

## Bloqueo sin confirmación en pantalla

Se activa cuando están marcados el recordatorio de coche sin asegurar y el ajuste específico de bloqueo automático. Este último está marcado inicialmente para cumplir el comportamiento solicitado; por sí solo no activa el recordatorio.

La política exige, en la conexión MQTT actual:

- Más de un minuto con estado aparcado y desbloqueado.
- Las cuatro puertas y el maletero confirmados cerrados, al menos 10 segundos desde su cierre.
- Datos individuales de aparcado, desbloqueo y cada cierre con un máximo de dos minutos de antigüedad.
- Monitor conectado, autorización de bloqueo disponible y ausencia de un control manual reciente.

Antes de la orden se consulta el estado por HTTPS europeo. Si indica circulación, falta el estado necesario o aparece una puerta/maletero abierto, no se envía el bloqueo. Si ambas cerraduras ya están bloqueadas, se evita repetir la orden. Se reutiliza el protocolo existente: PIN cifrado, autorización de control, número de serie, firma RSA y orden `doors` con `open=false`. La ruta automática no acepta desbloqueo.

Una solicitud manual invalida un automático pendiente; las transacciones se serializan y el automático se aplaza un minuto después del control manual. Se vuelven a comprobar las condiciones justo antes del envío. Una orden ya recibida por Mazda puede seguir ejecutándose aunque después se desactive el ajuste.

Se comprueban ambas cerraduras después de la orden. Una aceptación de la pasarela o de una sola cerradura no se presenta como cierre confirmado. Hay hasta tres intentos por ciclo, separados al menos un minuto y sujetos a datos recientes. Tras una reconexión o nueva autorización se exige de nuevo evidencia y tiempo de espera. Los errores y la falta de autorización se notifican sin incluir credenciales ni respuestas completas.

## Autorización en memoria

La sesión manual conserva su caducidad de 15 minutos. El cierre automático mantiene una copia separada del PIN y de la sesión, únicamente mientras el monitor está activo y la función habilitada. Solo se renueva para el mismo identificador de vehículo con el que se inició el monitor: los estados de un coche no autorizan a actuar sobre otro.

La copia se borra al desactivar el ajuste o el recordatorio, detener el monitor, cerrar la sesión o finalizar el proceso. No se guarda contraseña/PIN/token en preferencias, ficheros ni historial. Que la copia siga en RAM no prolonga la validez de la autorización en los servidores de Mazda: su rechazo deja el cierre sin confirmar y exige revisar la conexión/autenticación.

## Límites y comprobación

Bloquear las cerraduras no cierra físicamente puertas, maletero ni ventanillas. La app no detecta llaves ni ocupantes y depende de los datos del coche, la autorización Mazda, internet y la ejecución del monitor Android.

Las pruebas `AlertBehaviorChecks` usan tiempo simulado para comprobar indexación del cristal, valores iniciales, agrupación, condiciones de cierre, antigüedad, reconexión, cancelación, reintentos y prioridad manual. `RemoteLockChecks` usa HTTPS completamente simulado y claves RSA de prueba para comprobar el ciclo de vida del PIN en RAM, vinculación al vehículo, comprobación previa, autenticación, firma, bloqueo sin desbloqueo y confirmación de ambas cerraduras. No envía peticiones a un vehículo ni demuestra el comportamiento físico del CX-6e. Ambas suites se ejecutan en GitHub Actions antes de compilar y firmar la APK.
