# Guía de Pruebas - Sistema de Detección de Trayectos

## Opciones para Probar el Sistema GPS

### 1. **Usar el Emulador de Android Studio** (Recomendado para desarrollo)

#### Configuración:
1. Abre Android Studio
2. Ve a **Tools > Device Manager**
3. Crea o selecciona un emulador
4. En el emulador, abre el menú de configuración (tres puntos `...`)
5. Ve a **Location** o **Extended Controls**

#### Simular Trayectos:
- **Punto único**: Ingresa coordenadas manualmente
- **Ruta**: Usa "Route" para simular un trayecto completo
- **GPX/KML**: Importa archivos de rutas reales

#### Ejemplo de coordenadas para probar:
```
Inicio: 4.6097, -74.0817 (Bogotá, Colombia)
Fin: 4.6533, -74.0836 (Cerca, ~5km de distancia)
```

### 2. **Usar Mock Location Apps** (Dispositivo físico)

#### Apps recomendadas:
- **Mock GPS Location** (Lexa)
- **Fake GPS Location** (ByteRev)
- **GPS JoyStick** (The App Ninjas)

#### Pasos:
1. Instala una app de Mock Location
2. Activa **"Opciones de desarrollador"** en tu dispositivo:
   - Ve a **Configuración > Acerca del teléfono**
   - Toca **"Número de compilación"** 7 veces
3. En **Opciones de desarrollador**, activa **"Ubicación simulada"**
4. Selecciona la app de Mock Location como proveedor
5. Abre la app y selecciona una ubicación o traza una ruta

### 3. **Usar ADB (Android Debug Bridge)** (Más técnico)

#### Simular ubicación única:
```bash
adb shell "su 0 am start -a android.intent.action.VIEW -d 'geo:4.6097,-74.0817'"
```

#### Simular ruta con script:
```bash
# Script para simular movimiento
lat=4.6097
lng=-74.0817
for i in {1..10}; do
  adb shell "su 0 am start -a android.intent.action.VIEW -d 'geo:$lat,$lng'"
  lat=$(echo "$lat + 0.001" | bc)
  lng=$(echo "$lng + 0.001" | bc)
  sleep 2
done
```

### 4. **Usar GPX Simulator** (Más realista)

#### Herramientas:
- **GPS Logger** para Android (grabar rutas reales)
- **GPX Editor** online
- Importar GPX en el emulador o Mock Location app

#### Pasos:
1. Graba una ruta real con GPS Logger
2. Exporta como archivo GPX
3. Importa en el emulador o Mock Location app
4. Reproduce la ruta

### 5. **Pruebas en Dispositivo Físico** (Más realista)

#### Preparación:
1. Asegúrate de tener:
   - Permisos de ubicación otorgados
   - Permisos de segundo plano otorgados
   - Optimizaciones de batería desactivadas
   - Servicio activo (verificar notificación)

2. **Pruebas sugeridas**:
   - **Caminar**: Camina 50-100 metros en línea recta
   - **Vehículo**: Viaja en carro/moto/bus por 1-2 km
   - **Bicicleta**: Recorre en bici por 500 metros
   - **Paradas**: Detente 2 minutos para verificar finalización

### 6. **Herramientas de Debugging Integradas**

#### Logs de Android Studio:
```bash
# Ver logs del servicio
adb logcat | grep TripDetectionService

# Ver logs de ubicación
adb logcat | grep Location

# Ver todos los logs de la app
adb logcat | grep ECOTRACKER
```

#### Verificar estado del servicio:
```bash
# Ver servicios activos
adb shell dumpsys activity services | grep TripDetection

# Ver ubicación actual
adb shell dumpsys location
```

## Checklist de Pruebas

### ✅ Pruebas Básicas:
- [ ] El servicio se inicia automáticamente al autenticarse
- [ ] Aparece la notificación "EcoTracker Activo"
- [ ] El servicio funciona con la app cerrada
- [ ] Se detecta movimiento al caminar
- [ ] Se detecta movimiento en vehículo
- [ ] Se finaliza el trayecto después de estar quieto 90 segundos

### ✅ Pruebas de Detección:
- [ ] Detecta trayectos a pie (≥30 metros)
- [ ] Detecta trayectos en bicicleta
- [ ] Detecta trayectos en moto
- [ ] Detecta trayectos en carro
- [ ] Detecta trayectos en bus
- [ ] No detecta falsos positivos (movimiento mínimo)

### ✅ Pruebas de UI:
- [ ] Los trayectos aparecen en "Registros"
- [ ] El minimapa muestra la ruta completa
- [ ] Se puede seleccionar tipo de transporte
- [ ] La confirmación es automática
- [ ] Los trayectos se guardan correctamente

### ✅ Pruebas de Segundo Plano:
- [ ] El servicio continúa funcionando con app cerrada
- [ ] El servicio se reinicia después de reiniciar el dispositivo
- [ ] No se detiene por optimizaciones de batería
- [ ] La notificación permanece activa

## Comandos Útiles para Debugging

```bash
# Verificar permisos
adb shell dumpsys package com.example.ecotracker | grep permission

# Forzar detener el servicio
adb shell am force-stop com.example.ecotracker

# Iniciar el servicio manualmente
adb shell am startservice -n com.example.ecotracker/.service.TripDetectionService

# Ver notificaciones activas
adb shell dumpsys notification | grep EcoTracker
```

## Notas Importantes

1. **Emulador**: Puede ser lento para simular GPS, usa coordenadas reales
2. **Mock Location**: Requiere activar "Opciones de desarrollador"
3. **Dispositivo físico**: La forma más realista pero requiere movimiento real
4. **Batería**: El GPS consume mucha batería, tenlo en cuenta durante pruebas
5. **Permisos**: Asegúrate de otorgar todos los permisos necesarios

## Solución de Problemas

### El servicio no se inicia:
- Verifica permisos de ubicación
- Verifica permisos de segundo plano (Android 10+)
- Revisa logs: `adb logcat | grep TripDetection`

### No se detectan trayectos:
- Verifica que el GPS esté activado
- Verifica que haya movimiento real (≥30 metros)
- Revisa los umbrales en `TripDetectionService.kt`

### El servicio se detiene:
- Verifica optimizaciones de batería
- Verifica que el WakeLock esté activo
- Revisa la notificación (debe estar visible)



