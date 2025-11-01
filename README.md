# 🌱 EcoTracker

EcoTracker es una aplicación móvil desarrollada en **Android Studio (Kotlin + Jetpack Compose)** que permite a los usuarios calcular y visualizar su **huella de carbono** en base a hábitos de transporte y consumo energético.  
La app busca motivar cambios sostenibles mediante estadísticas, recomendaciones y un sistema de insignias.

---

## 📌 Características principales

- 📊 **Registro de hábitos diarios** (transporte, energía, etc.).
- 🔐 **Autenticación con Google y/o email** (Firebase Auth).
- ☁️ **Almacenamiento en la nube** con **Cloud Firestore**.
- 🏆 **Gamificación**: logros e insignias según metas alcanzadas.
- 🗺️ **Google Maps & Location**: registrar trayectos y calcular impacto.
- 📈 **Estadísticas visuales** con gráficos y tendencias.
- 🔔 **Notificaciones (futuro)** para recordar registrar hábitos.

---

## 🏗️ Arquitectura

- **Lenguaje:** Kotlin  
- **UI:** Jetpack Compose + Material 3  
- **Estado:** ViewModel + LiveData/State  
- **Backend:** Firebase (Auth, Firestore, Analytics)  
- **Servicios extra:** Google Maps SDK + Location Services

---

## 🚘 Estructura de registros de transporte

Cada registro de transporte representa una acción del usuario dentro de la app (por ejemplo, un viaje en bus o carro) y se guarda en Cloud Firestore, vinculado al usuario autenticado.

---

# 🧩 Estructura del documento (`transport_records`)

| Campo | Tipo | Descripción |
|--------|------|-------------|
| 🧍‍♂️ `userId` | `String` | ID único del usuario autenticado (Firebase UID). |
| 🚗 `transportType` | `String` | Tipo de transporte (carro, bus, bicicleta, caminar, etc.). |
| 📅 `date` | `String` | Fecha del registro en formato `yyyy-MM-dd`. |
| ⏰ `hour` | `String` | Hora seleccionada por el usuario al registrar el transporte. |
| 🕓 `timestamp` | `Long` | Marca de tiempo en milisegundos (para ordenamiento). |
| 📏 `distance` | `Double?` | Distancia recorrida (en kilómetros). Opcional. |
| 🧾 `createdAt` | `Long` | Fecha y hora de creación del registro (en milisegundos). |

---

### 🔗 Asociación con el usuario

Cada registro se guarda asociado al usuario autenticado usando su `uid` de **Firebase Auth**.  
Esto permite que:

- Cada usuario vea **solo sus propios registros**.  
- Las estadísticas y la **huella de carbono** se calculen de forma individual.  
- Se mantenga consistencia entre los datos locales y los almacenados en **Firestore**.

---

### 🧠 Ejemplo de guardado

```kotlin
db.collection("transport_records")
  .add(
      TransportRecord(
          userId = uid,
          transportType = TransportType.BUS,
          date = "2025-10-31",
          hour = "07:30 AM",
          distance = 5.2,
          createdAt = System.currentTimeMillis()
      )
  )
```

## ✅ Validaciones y persistencia

-Solo se permiten valores numéricos y positivos para la distancia.
-El registro no se envía si falta tipo de transporte u hora.
-Los datos se almacenan de forma persistente en Firestore.
Se pueden recuperar mediante:
Usuario + fecha actual → registros del día.

Historial completo → estadísticas y reportes de emisiones.

---

## 🧪 Pruebas realizadas

- 🧍‍♀️ **Validado con usuarios distintos** (cuentas Firebase diferentes).
-📱 **Probado en emulador y dispositivo físico** (Android 13+).
-☁️ **Confirmado el guardado y recuperación de datos desde Firestore**.
-🌎 **Verificado el cálculo de huella de carbono usando los registros asociados**.
