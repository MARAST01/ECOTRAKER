<h1 align="center">🌱 EcoTracker</h1>
<p align="center">
  <em>Calcula, comprende y reduce tu huella de carbono</em>  
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-Kotlin-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore-FFCA28?logo=firebase&logoColor=white" />
  <img src="https://img.shields.io/badge/Status-En%20desarrollo-yellow" />
</p>

---

## 🌍 Descripción

**EcoTracker** es una aplicación móvil desarrollada en **Android Studio (Kotlin + Jetpack Compose)** que ayuda a los usuarios a **medir su huella de carbono** según sus hábitos de transporte y consumo energético.  
La app fomenta **acciones sostenibles** mediante estadísticas, recomendaciones personalizadas y un sistema de **logros e insignias**.

---

## 📱 Características principales

| Funcionalidad | Descripción |
|---------------|-------------|
| 📊 **Registro de hábitos** | Transporte, energía y consumo diario. |
| 🔐 **Autenticación** | Ingreso con Google o correo electrónico (Firebase Auth). |
| ☁️ **Almacenamiento en la nube** | Cloud Firestore para sincronización de datos. |
| 🏆 **Gamificación** | Sistema de logros e insignias por metas alcanzadas. |
| 🗺️ **Google Maps & Location** | Registro de trayectos y cálculo de impacto ambiental. |
| 📈 **Estadísticas visuales** | Gráficos y tendencias personalizadas. |
| 🔔 **Notificaciones (próximamente)** | Recordatorios para registrar hábitos diarios. |

---

## 🏗️ Arquitectura y Tecnologías

```mermaid
graph TD
    A[UI - Jetpack Compose] --> B[ViewModel]
    B --> C[Repository]
    C --> D[Firebase - Firestore]
    C --> E[Firebase Auth]
    C --> F[Google Maps SDK]
