# Mazda Avisos Lab 0.4 — experimental

App auxiliar de lectura del Centro de Mensajes. No tiene permiso INTERNET, no controla el vehículo y no modifica la aplicación Mazda original.

## Nuevo en v0.4
- Nuevo mosaico en los **Ajustes rápidos** de Samsung, llamado **Comprobar Mazda**.
- Cuando la persona lo pulsa, Android abre Mazda; el servicio de accesibilidad intenta pulsar el icono exacto `iv_home_msg` para mostrar el Centro de Mensajes, y registra mensajes nuevos.
- La solicitud de automatización caduca tras 30 segundos.
- Para usarlo: deslizar dos veces desde arriba, editar botones, añadir "Comprobar Mazda", guardar y pulsarlo con el móvil desbloqueado.
- Conserva la lectura de avisos y las notificaciones de v0.3.

## Límites
No comprueba avisos autónomamente con el móvil bloqueado ni funciona como sustituto de un sistema push real. Android limita la apertura de pantallas desde segundo plano y el complemento no tiene acceso a la API de Mazda.

## Instalación
Cada compilación desde GitHub Actions usa una firma debug temporal. Para sustituir v0.3, desinstala **solamente Mazda Avisos Lab** (pierde el historial local) e instala v0.4; permite accesibilidad y notificaciones de nuevo. **No desinstales ni desvincules Mazda oficial**.

Sin permiso de Internet; diagnósticos técnicos locales de estructura de botones. La información de mensajes se guarda en el móvil y se elimina al desinstalar.
