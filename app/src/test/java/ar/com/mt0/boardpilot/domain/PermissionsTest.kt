package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionsTest {

    // PackageManager.PERMISSION_GRANTED = 0, PERMISSION_DENIED = -1.
    @Test
    fun `solo el codigo otorgado cuenta como otorgado`() {
        assertEquals(PermissionGrant.GRANTED, permissionGrant(0))
        assertEquals(PermissionGrant.NOT_GRANTED, permissionGrant(-1))
        assertEquals(PermissionGrant.NOT_GRANTED, permissionGrant(1))
    }

    @Test
    fun `sin permiso la camara no se invoca, se pide el permiso`() {
        assertEquals(CameraAction.LAUNCH_CAMERA, cameraAction(permissionGranted = true))
        assertEquals(CameraAction.REQUEST_PERMISSION, cameraAction(permissionGranted = false))
    }

    @Test
    fun `permiso otorgado en el dialogo abre la camara`() {
        assertEquals(
            CameraAfterRequest.LAUNCH_CAMERA,
            cameraAfterRequest(granted = true, shouldShowRationale = false),
        )
        assertEquals(
            CameraAfterRequest.LAUNCH_CAMERA,
            cameraAfterRequest(granted = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `denegado reintentable avisa sin abrir la camara`() {
        assertEquals(
            CameraAfterRequest.SHOW_DENIED,
            cameraAfterRequest(granted = false, shouldShowRationale = true),
        )
    }

    @Test
    fun `denegado permanente manda a ajustes de la app`() {
        assertEquals(
            CameraAfterRequest.SHOW_OPEN_SETTINGS,
            cameraAfterRequest(granted = false, shouldShowRationale = false),
        )
    }
}
