# Mazda Avisos Lab 0.3 — experimental

App auxiliar para Mazda CX-6e/6e europea, sin permiso INTERNET y sin control del coche.
- Lee texto visible del Centro de Mensajes de la app Mazda con accesibilidad.
- Al pulsar **Comprobar mensajes de Mazda ahora (PRUEBA)** abre la app Mazda y durante 30 segundos intenta pulsar **solo** el control con view ID `iv_home_msg`. No utiliza coordenadas ni clics genéricos.
- Si no encuentra el botón almacena diagnóstico técnico de IDs y tipos de controles, sin valores de campos o contenido del perfil, para ajustar la próxima versión.
- Con el Centro abierto, importa los mensajes; la primera lectura no produce alertas históricas.
- No consulta el servidor ni puede generar avisos cuando Mazda no está abierta.
- Por seguridad no usa contraseñas, tokens, automatización de controles del vehículo ni acceso a red.

**NOTA DE INSTALACIÓN:** la v0.2 fue firmada con la clave debug temporal de otra ejecución de GitHub Actions. Para instalar la v0.3 tendrás que desinstalar v0.2 (se borrará su historial local) y volver a habilitar accesibilidad/notificaciones; la app Mazda oficial no se desinstala. Para futuras actualizaciones sin reinstalar será necesaria una firma estable y protegida.

Instala el artefacto APK de GitHub Actions tras extraerlo. Es una prueba, no un sistema fiable de alarmas.
