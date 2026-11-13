# Mazda Avisos Lab v0.6 (experimental)

Escucha MQTT de prueba usando una cuenta secundaria compartida. Se conecta por TLS al broker autorizado por Mazda, suscribe properties/report y events/up y realiza el login técnico de solo lectura.

**Prueba:** abre «Probar acceso MQTT», autentica la cuenta de pruebas (nunca la principal), y pulsa «Escuchar MQTT durante 2 minutos». Con el coche aparcado, bloquea/desbloquea desde la aplicación Mazda. Comprueba los contadores y si llega la notificación experimental.

**Límites:** no es un servicio persistente ni sustituye ninguna alerta de seguridad; los avisos de ocupantes del Centro de Mensajes no están comprobados. No se almacenan contraseñas, claves, contenidos de eventos ni tokens. Puede interrumpir temporalmente otra conexión MQTT que use la misma identidad.

La APK se compila con clave debug. Si la firma de v0.5 no coincide, desinstala exclusivamente Mazda Avisos Lab (con pérdida de su historial) y reinstala la nueva APK; no afecta a la app oficial.