# Script PowerShell para ver logs del sistema de detección de trayectos
# Uso: .\view_logs.ps1 [filtro]

param(
    [string]$Filter = "TripDetection"
)

Write-Host "📋 Mostrando logs de: $Filter" -ForegroundColor Green
Write-Host "Presiona Ctrl+C para detener"
Write-Host ""

adb logcat -c  # Limpiar logs anteriores
adb logcat | Select-String -Pattern $Filter -CaseSensitive:$false



