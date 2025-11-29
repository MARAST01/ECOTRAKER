#!/bin/bash
# Script para simular una ruta GPS usando ADB
# Uso: ./simulate_route.sh

# Coordenadas de ejemplo (Bogotá, Colombia)
START_LAT=4.6097
START_LNG=-74.0817
END_LAT=4.6533
END_LNG=-74.0836

# Número de puntos intermedios
POINTS=20

# Calcular incrementos
LAT_INCREMENT=$(echo "scale=8; ($END_LAT - $START_LAT) / $POINTS" | bc)
LNG_INCREMENT=$(echo "scale=8; ($END_LNG - $START_LNG) / $POINTS" | bc)

echo "🚗 Simulando ruta GPS..."
echo "Inicio: $START_LAT, $START_LNG"
echo "Fin: $END_LAT, $END_LNG"
echo "Puntos: $POINTS"
echo ""

CURRENT_LAT=$START_LAT
CURRENT_LNG=$START_LNG

for i in $(seq 0 $POINTS); do
    echo "📍 Punto $i/$POINTS - Lat: $CURRENT_LAT, Lng: $CURRENT_LNG"
    
    # Simular ubicación usando ADB
    adb shell "am start -a android.intent.action.VIEW -d 'geo:$CURRENT_LAT,$CURRENT_LNG'"
    
    # Esperar 2 segundos entre puntos
    sleep 2
    
    # Calcular siguiente punto
    CURRENT_LAT=$(echo "scale=8; $CURRENT_LAT + $LAT_INCREMENT" | bc)
    CURRENT_LNG=$(echo "scale=8; $CURRENT_LNG + $LNG_INCREMENT" | bc)
done

echo ""
echo "✅ Ruta simulada completada"



