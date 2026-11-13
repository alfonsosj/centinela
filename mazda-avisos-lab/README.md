# Mazda Avisos Lab v0.5 — diagnóstico de acceso MQTT

Incluye todo lo validado de v0.4: lector de Centro de Mensajes y acceso rápido de Android. Añade una prueba puntual de autorización MQTT, sin conexión al broker ni registro de eventos.

## Uso
1. Instala la APK experimental (si la firma debug es distinta, desinstala SOLO Mazda Avisos Lab, nunca la app oficial).
2. Abre Mazda Avisos Lab y pulsa **Probar acceso MQTT (cuenta secundaria)**.
3. Introduce únicamente las credenciales de una cuenta nueva compartida con el vehículo; no uses la cuenta principal.
4. Si Mazda solicita un código por correo, introdúcelo en la app.
5. El diagnóstico indicará si se obtuvo configuración THU, autorización MQTT, y número de canales properties/report y events/up.

## Privacidad y límites
- El proyecto sí requiere permiso de Internet ahora; solo consulta servicios oficiales HTTPS de Mazda Europa, con certificados TLS del sistema.
- No guarda email, contraseña, tokens ni IDs del vehículo en archivos; el identificador de dispositivo aleatorio de esta app sí se conserva para no repetir el registro.
- No imprime credenciales ni tokens. No subas capturas que contengan información privada.
- No utiliza comandos de control del coche, ni intenta alterar una cuenta o su llave digital.
- No conecta al broker MQTT ni recibe avisos con Mazda cerrada aún: ese objetivo depende de los resultados de esta prueba.
- La app se distribuye con firma debug de GitHub Actions y no se ha validado en un teléfono físico.
