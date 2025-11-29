#!/bin/bash
# Script para ver logs del sistema de detección de trayectos
# Uso: ./view_logs.sh [filtro]

FILTER=${1:-"TripDetection"}

echo "📋 Mostrando logs de: $FILTER"
echo "Presiona Ctrl+C para detener"
echo ""

adb logcat -c  # Limpiar logs anteriores
adb logcat | grep -i "$FILTER"



