# Finanzas Inteligentes (etapa 1)

App Android local (Kotlin, Compose, Material 3, Room). Moneda CAD, español, sin conexión ni cuentas bancarias.

## Implementado
Cuentas manuales; ingresos, gastos y transferencias (crear, editar, eliminar con confirmación, búsqueda y filtro por tipo); panel con disponible, saldo, asignado a metas, ingresos/gastos del mes y gastos por categoría; presupuestos mensuales; metas de ahorro con aportación mensual necesaria; modo claro/oscuro del sistema. Importes en centavos (Long).

## Pendiente (no implementado)
Deudas, calendario/recordatorios, suscripciones, estadísticas avanzadas, escáner de recibos, comparador de precios, listas de compras, asistente IA, «Gana más», inversiones, simulador de decisiones, finanzas familiares, herramientas fiscales de Quebec, bloqueo/biometría, exportación y copias de seguridad, selector de fecha y presupuestos semanales.

## Compilar
GitHub Actions (`.github/workflows/android.yml`) ejecuta pruebas unitarias y compila el APK de depuración (artefacto `finanzas-debug-apk`). Localmente: `gradle testDebugUnitTest assembleDebug` con JDK 17 y Android SDK 34 (no se incluye el wrapper de Gradle).

## Firma de lanzamiento
Crea el keystore fuera del repositorio y pasa sus datos por secretos de GitHub o `~/.gradle/gradle.properties`. Nunca subas `.jks` ni contraseñas (están en `.gitignore`).

## Estado de verificación
Este código se escribió sin poder compilarlo ni ejecutar pruebas. La primera ejecución de Actions puede revelar errores de compilación que habrá que corregir.
