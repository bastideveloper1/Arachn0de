# Revisión matemática aprobada: 5 tiradas por $1.000

El usuario revisó y aprobó esta tabla el 25 de octubre de 2026 antes de aplicarla. Solo afecta a compras nuevas; las configuraciones, créditos, resultados y premios históricos conservan sus condiciones.

Paquete fijo: 1.000 CLP por 5 tiradas. Coste de cada giro: 200 CLP. Premios del cartel sin cambios.

| Símbolo | Premio CLP | Probabilidad anterior | Probabilidad aprobada | Premio esperado nuevo CLP/giro | Contribución al RTP nuevo |
|---|---:|---:|---:|---:|---:|
| Cerezas | 1.000 | 7,30 % | 1,46 % | 14,60 | 7,30 puntos porcentuales |
| Plátanos | 2.000 | 3,00 % | 0,60 % | 12,00 | 6,00 puntos porcentuales |
| Guacamayo | 3.000 | 2,00 % | 0,40 % | 12,00 | 6,00 puntos porcentuales |
| Plata | 4.000 | 1,20 % | 0,24 % | 9,60 | 4,80 puntos porcentuales |
| Oro | 5.000 | 0,60 % | 0,12 % | 6,00 | 3,00 puntos porcentuales |
| Rubí | 7.000 | 0,30 % | 0,06 % | 4,20 | 2,10 puntos porcentuales |
| Esmeralda | 10.000 | 0,06 % | 0,012 % | 1,20 | 0,60 puntos porcentuales |
| Máscara | 20.000 | 0,01 % | 0,002 % | 0,40 | 0,20 puntos porcentuales |
| Total | | 14,47 % de premio | 2,894 % de premio | 60,00 | 30,00 puntos porcentuales |

Los pesos son [730, 300, 200, 120, 60, 30, 6, 1] sobre 50.000. Suman 1.447 resultados ganadores; 48.553/50.000 no ganan premio. Los símbolos más valiosos son menos frecuentes. No hay ajuste por saldo, premios previos o recuperación de pérdidas.

Cálculo exacto:

```text
E[premio] = (730×1000 + 300×2000 + 200×3000 + 120×4000
             + 60×5000 + 30×7000 + 6×10000 + 1×20000) / 50000
          = 3.000.000 / 50.000 = 60 CLP
RTP = 60 / 200 × 100 = 30 % exacto
```

La tabla anterior con denominador 10.000 espera 300 CLP por giro: era 30 % cuando la tirada costaba 1.000, pero sería 150 % al cobrar 200. Se verificó la nueva tabla mediante aritmética racional, no por redondeo ni por asumir que dividir probabilidades bastaba. La prueba dirigida compara además las ocho contribuciones con los valores exactos de esta tabla.

El RTP es teórico, no una garantía para cinco giros ni una promesa de rentabilidad. Varianza nueva del premio: 118/625 unidades de 1.000 CLP al cuadrado (188.800 CLP²). La reserva mínima sigue siendo 20.000 CLP para la tabla nueva, independiente de que la máscara tenga poca probabilidad.

El redondeo acumulado de ahorro protegido/fondo sigue operando sobre depósitos físicos enteros de 1.000 CLP. El coste estadístico de 200 CLP por crédito es un número entero de pesos, no un movimiento bancario por cada giro.
