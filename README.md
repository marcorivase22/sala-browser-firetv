# Sala Browser

Prototipo de navegador privado para Android y Fire TV. Abre un sitio de video en
un `WebView`, bloquea nuevas ventanas y redirecciones externas, filtra solicitudes
publicitarias conocidas y conserva localmente las páginas visitadas para
“Seguir viendo”.

## Funciones actuales

- Android móvil, Android TV y Fire TV desde un solo APK.
- Popups y descargas bloqueados.
- Navegación de la ventana principal limitada al dominio configurado.
- Filtro local y ampliable de hosts publicitarios.
- Reproducción HTML5 en pantalla completa.
- Historial local de hasta 30 páginas, sin cuenta ni servidor.
- Interfaz enfocable mediante control remoto.

## Compilar

El entorno local ya tiene JDK 17, Android SDK 35 y Gradle Wrapper 8.9.

```sh
./gradlew assembleDebug
```

El APK se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

También puedes abrir esta carpeta en Android Studio y ejecutar el proyecto desde
ahí.

Para Fire TV, instala el APK mediante ADB:

```sh
adb connect IP_DEL_FIRE_TV
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configuración

El sitio inicial está en `app/src/main/res/values/strings.xml`.
La lista de filtros está en
`app/src/main/java/org/salabrowser/app/RequestBlocker.java`.

## Límites

El historial guarda la página o episodio, no garantiza el segundo exacto. Los
reproductores alojados en dominios de terceros suelen ejecutarse dentro de
iframes y el navegador impide leer su estado por la política de mismo origen.

Este proyecto no descarga, descifra, retransmite ni aloja contenido. Úsalo
únicamente con sitios y contenido para los que tengas autorización, respetando
sus términos y las leyes aplicables.
