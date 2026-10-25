# Chihuan

Lector de pagos por voz para Android: escucha las notificaciones de Yape y de Interbank (Plin) y
las anuncia en voz alta con el monto, el nombre de quien pagó y el código de seguridad.

Pensada para choferes, taxistas y bodegueros que no pueden mirar la pantalla mientras trabajan.

## Estado

En desarrollo. La base del proyecto está creada y el trabajo está planificado en
`openspec/changes/yape-voice-announcer/`.

**Importante:** los formatos reales de las notificaciones de Yape e Interbank todavía no están
verificados con muestras reales. Hasta que se capturen, los parsers son supuestos calibrables, no
hechos.

## Cómo funciona

1. La app usa el permiso de Acceso a notificaciones (`NotificationListenerService`). Android no lo
   puede conceder solo: el usuario lo activa a mano desde Ajustes.
2. Filtra las notificaciones de las fuentes activas (Yape, Interbank).
3. Extrae monto, nombre y código con reglas deterministas en Kotlin puro. No hay IA, no hay nube.
4. Convierte el monto a palabras en español y lo lee con el `TextToSpeech` del sistema.
5. Si no puede interpretar una notificación, igual avisa: nunca se queda en silencio.
6. Guarda un historial local del día.

Nada sale del teléfono. No hay backend, ni cuentas, ni publicidad.

## Stack

- Kotlin 2.2.10, AGP 9.3.3, Gradle 9.5.0
- Jetpack Compose + Material3
- Room (historial local) y SharedPreferences (ajustes)
- `minSdk 24`, `targetSdk 36`

## Compilar

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

El APK de debug queda en `app/build/outputs/apk/debug/`.

## Documentación

- `openspec/project.md` — contexto del proyecto y stack detectado
- `openspec/changes/yape-voice-announcer/proposal.md` — alcance, no-objetivos y riesgos
- `openspec/changes/yape-voice-announcer/specs/` — especificaciones por capacidad
- `openspec/changes/yape-voice-announcer/design.md` — diseño técnico
