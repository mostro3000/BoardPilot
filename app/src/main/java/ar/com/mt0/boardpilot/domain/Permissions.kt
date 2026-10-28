package ar.com.mt0.boardpilot.domain

/** Estado de un permiso declarado, tal como lo muestra "Acerca de". */
enum class PermissionGrant { GRANTED, NOT_GRANTED }

/**
 * Interpreta el resultado de `checkSelfPermission`
 * (`PackageManager.PERMISSION_GRANTED` = 0; cualquier otro valor, no otorgado).
 */
fun permissionGrant(checkSelfPermissionResult: Int): PermissionGrant =
    if (checkSelfPermissionResult == 0) PermissionGrant.GRANTED
    else PermissionGrant.NOT_GRANTED

/** Que hacer al tocar "Camara": nunca invocar la camara sin permiso otorgado. */
enum class CameraAction { LAUNCH_CAMERA, REQUEST_PERMISSION }

fun cameraAction(permissionGranted: Boolean): CameraAction =
    if (permissionGranted) CameraAction.LAUNCH_CAMERA
    else CameraAction.REQUEST_PERMISSION

/** Que hacer cuando el sistema responde al pedido de permiso de camara. */
enum class CameraAfterRequest { LAUNCH_CAMERA, SHOW_DENIED, SHOW_OPEN_SETTINGS }

/**
 * Denegado con `shouldShowRationale = false` tras un pedido significa rechazo
 * permanente ("no preguntar de nuevo"): la unica salida es Ajustes de la app.
 * Denegado con rationale todavia puede reintentarse tocando "Camara" de nuevo.
 */
fun cameraAfterRequest(
    granted: Boolean,
    shouldShowRationale: Boolean,
): CameraAfterRequest = when {
    granted -> CameraAfterRequest.LAUNCH_CAMERA
    shouldShowRationale -> CameraAfterRequest.SHOW_DENIED
    else -> CameraAfterRequest.SHOW_OPEN_SETTINGS
}
