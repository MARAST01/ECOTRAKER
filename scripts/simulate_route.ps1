# Script PowerShell para simular una ruta GPS usando ADB
# Uso: .\simulate_route.ps1

# Coordenadas de ejemplo (Bogotá, Colombia)
$START_LAT = 4.6097
$START_LNG = -74.0817
$END_LAT = 4.6533
$END_LNG = -74.0836

# Número de puntos intermedios
$POINTS = 20

# Calcular incrementos
$LAT_INCREMENT = ($END_LAT - $START_LAT) / $POINTS
$LNG_INCREMENT = ($END_LNG - $START_LNG) / $POINTS

Write-Host "🚗 Simulando ruta GPS..." -ForegroundColor Green
Write-Host "Inicio: $START_LAT, $START_LNG"
Write-Host "Fin: $END_LAT, $END_LNG"
Write-Host "Puntos: $POINTS"
Write-Host ""

$CURRENT_LAT = $START_LAT
$CURRENT_LNG = $START_LNG

for ($i = 0; $i -le $POINTS; $i++) {
    Write-Host "📍 Punto $i/$POINTS - Lat: $CURRENT_LAT, Lng: $CURRENT_LNG" -ForegroundColor Cyan
    
    # Simular ubicación usando ADB
    adb shell "am start -a android.intent.action.VIEW -d 'geo:$CURRENT_LAT,$CURRENT_LNG'"
    
    # Esperar 2 segundos entre puntos
    Start-Sleep -Seconds 2
    
    # Calcular siguiente punto
    $CURRENT_LAT = $CURRENT_LAT + $LAT_INCREMENT
    $CURRENT_LNG = $CURRENT_LNG + $LNG_INCREMENT
}

Write-Host ""
Write-Host "✅ Ruta simulada completada" -ForegroundColor Green



