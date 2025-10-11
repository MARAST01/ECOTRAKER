# 🌱 EcoTracker

EcoTracker es una aplicación móvil desarrollada en **Android Studio (Kotlin + Jetpack Compose)** que permite a los usuarios calcular y visualizar su **huella de carbono** en base a hábitos de transporte y consumo energético.  
La app busca motivar cambios sostenibles mediante estadísticas, recomendaciones y un sistema de insignias.

---

## 📌 Características principales

- 📊 **Registro de hábitos diarios** (transporte, energía, etc.)
- 🔐 **Autenticación con Google y/o email** (Firebase Auth)
- ☁️ **Almacenamiento en la nube** con **Cloud Firestore**
- 🏆 **Gamificación**: logros e insignias según metas alcanzadas
- 🗺️ **Google Maps & Location**: registrar trayectos y calcular impacto
- 📈 **Estadísticas visuales** con gráficos y tendencias
- 🔔 **Notificaciones (futuro)** para recordar registrar hábitos

---

## 🏗️ Arquitectura

- **Lenguaje:** Kotlin  
- **UI:** Jetpack Compose + Material 3  
- **Estado:** ViewModel + LiveData/State  
- **Backend:** Firebase (Auth, Firestore, Analytics)  
- **Servicios extra:** Google Maps SDK + Location Services  