# Cumplimiento de Principios de Accesibilidad WCAG

Este documento detalla dónde se cumplen los cuatro principios de accesibilidad WCAG (Web Content Accessibility Guidelines) en la aplicación EcoTracker.

## 1. PERCEPTIBLE (Perceivable)

La información debe ser presentada de manera que los usuarios puedan percibirla.

### ✅ Texto Alternativo y Descripciones de Contenido

**Ubicación:**
- `BottomNavigationBar.kt` (líneas 56-59, 80-83, 104-107)
  - Todos los botones tienen `contentDescription` descriptivo
  - Ejemplo: "Abrir selección de transporte", "Abrir registros", "Cerrar sesión"

- `TransportSelectionScreen.kt` (línea 235)
  - Opciones de transporte: `contentDescription = "Opción de transporte ${transport.displayName}"`
  - Estado: `stateDescription = if (isSelected) "Seleccionado" else "No seleccionado"`

- `DashboardScreen.kt` (líneas 332, 355)
  - Botón expandir/contraer: `contentDescription` dinámico según estado
  - Iconos con descripciones: "Expandir", "Contraer"

- `RegisterScreen.kt` (líneas 299, 339)
  - Iconos de mostrar/ocultar contraseña con `contentDescription` descriptivo

### ✅ Estructura Semántica (Encabezados)

**Ubicación:**
- `DashboardScreen.kt` (líneas 342, 377)
  - "EcoTracker" marcado como `heading()`
  - "Tu huella de carbono hoy" marcado como `heading()`

- `TransportSelectionScreen.kt` (líneas 77, 91, 307)
  - "Seleccionar Transporte" marcado como `heading()`
  - "¿Cómo te transportaste hoy?" marcado como `heading()`
  - "Hora del transporte" marcado como `heading()`

### ✅ Contraste de Colores

**Ubicación:**
- `TransportSelectionScreen.kt` (línea 264)
  - Texto cambia de color según estado: blanco cuando está seleccionado, negro cuando no
  - Asegura contraste adecuado en ambos estados

- `DashboardScreen.kt` (línea 164)
  - Texto del botón deshabilitado usa `MaterialTheme.colorScheme.onSurface` para mantener contraste

### ✅ Tamaño de Fuente y Escalabilidad

**Ubicación:**
- Uso de `MaterialTheme.typography` en todos los textos
- Los textos respetan la configuración de escala del sistema
- No se usa `maxLines = 1` en textos importantes que podrían truncarse

---

## 2. OPERABLE (Operable)

Los componentes de la interfaz deben ser operables.

### ✅ Tamaños Táctiles Mínimos (48dp)

**Ubicación:**
- `RegisterScreen.kt` (líneas 298, 338)
  - IconButtons de contraseña: `modifier = Modifier.size(48.dp)`

- `TransportSelectionScreen.kt` (línea 69)
  - Botón "Atrás": `modifier = Modifier.height(48.dp)`

### ✅ Navegación por Teclado/Control Externo

**Ubicación:**
- `BottomNavigationBar.kt` (líneas 30-32, 51-55, 75-79, 99-103)
  - `FocusRequester` para cada botón
  - `focusProperties` define orden circular de navegación:
    - Transporte → Registro → Salir → Transporte
  - Permite navegación completa con teclado o control externo

### ✅ Orden de Foco Lógico

**Ubicación:**
- `BottomNavigationBar.kt` (líneas 52-55, 76-79, 100-103)
  - Cada botón define `next` y `previous` explícitamente
  - Orden de foco predecible y circular

### ✅ Sin Contenido que Cause Convulsiones

**Ubicación:**
- Las animaciones usan duraciones razonables (300ms)
- No hay animaciones parpadeantes o intermitentes
- `AnimatedVisibility` con transiciones suaves

---

## 3. COMPRENSIBLE (Understandable)

La información y la operación de la interfaz deben ser comprensibles.

### ✅ Mensajes de Error Claros

**Ubicación:**
- `RegisterScreen.kt` (líneas 66-70, 224-227, 246-249, etc.)
  - Validación en tiempo real con mensajes específicos
  - Ejemplos: "El número debe tener 10 dígitos", "Las contraseñas no coinciden"
  - Mensajes de error mostrados en `supportingText` de cada campo

- `DashboardScreen.kt` (líneas 290-295)
  - Mensajes de error de permisos claros y descriptivos

### ✅ Anuncios de Cambios de Estado (Live Regions)

**Ubicación:**
- `DashboardScreen.kt` (líneas 294, 301, 472)
  - `LiveRegionMode.Assertive` para errores críticos
  - `LiveRegionMode.Polite` para estados de carga y mensajes informativos
  - TalkBack anuncia automáticamente cambios importantes

- `MapScreen.kt` (líneas 208, 226, 235)
  - Live regions para estados del mapa (cargando, cargado, errores)

### ✅ Etiquetas y Roles Semánticos

**Ubicación:**
- `BottomNavigationBar.kt` (líneas 57, 81, 105)
  - Todos los botones tienen `role = Role.Button` explícito

- `TransportSelectionScreen.kt` (línea 234)
  - Cards de transporte marcadas como `Role.Button`

- `DashboardScreen.kt` (línea 331)
  - Elemento expandible marcado como `Role.Button`

### ✅ Navegación Predecible

**Ubicación:**
- Estructura consistente en todas las pantallas
- Encabezados marcados permiten navegación por encabezados con TalkBack
- Orden de foco lógico y predecible

---

## 4. ROBUSTO (Robust)

El contenido debe ser lo suficientemente robusto para ser interpretado por tecnologías asistivas.

### ✅ Semantics API Completa

**Ubicación:**
- Uso extensivo de `semantics {}` en todos los componentes interactivos
- `BottomNavigationBar.kt`, `TransportSelectionScreen.kt`, `DashboardScreen.kt`, `RegisterScreen.kt`
  - Roles explícitos (`Role.Button`)
  - Descripciones de contenido (`contentDescription`)
  - Descripciones de estado (`stateDescription`)
  - Encabezados (`heading()`)
  - Live regions (`liveRegion`)

### ✅ Compatibilidad con TalkBack

**Ubicación:**
- Todos los elementos interactivos tienen información semántica completa
- `mergeDescendants = true` en cards complejas para evitar anuncios redundantes
- Ejemplo: `TransportSelectionScreen.kt` (línea 233)

### ✅ Estructura Jerárquica Correcta

**Ubicación:**
- Encabezados marcados correctamente permiten navegación jerárquica
- `DashboardScreen.kt`: "EcoTracker" (H1) → "Tu huella de carbono hoy" (H2)
- `TransportSelectionScreen.kt`: Múltiples niveles de encabezados

### ✅ Validación y Manejo de Errores

**Ubicación:**
- `RegisterScreen.kt`: Validación completa con mensajes claros
- `DashboardScreen.kt`: Manejo de errores de permisos con live regions
- Todos los errores se anuncian a través de tecnologías asistivas

---

## Resumen de Archivos con Implementaciones de Accesibilidad

1. **BottomNavigationBar.kt**
   - Focus management, orden de foco, contentDescription, roles

2. **DashboardScreen.kt**
   - Live regions, encabezados, contentDescription dinámico, roles

3. **TransportSelectionScreen.kt**
   - Encabezados, stateDescription, contentDescription, roles

4. **RegisterScreen.kt**
   - Tamaños táctiles mínimos, contentDescription, validación accesible

5. **MapScreen.kt**
   - Live regions para estados del mapa

---

## Nivel de Cumplimiento

La aplicación cumple con los **4 principios fundamentales de WCAG**:

- ✅ **Perceptible**: Texto alternativo, estructura semántica, contraste
- ✅ **Operable**: Tamaños táctiles, navegación por teclado, orden de foco
- ✅ **Comprensible**: Mensajes claros, anuncios de estado, navegación predecible
- ✅ **Robusto**: Semantics API completa, compatibilidad con TalkBack, estructura jerárquica

**Nivel estimado: WCAG 2.1 AA** (con algunas características de nivel AAA)

