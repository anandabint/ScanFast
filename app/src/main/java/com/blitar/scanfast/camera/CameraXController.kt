package com.blitar.scanfast.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.blitar.scanfast.storage.TempPhotoStore
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Setup CameraX (Preview + ImageCapture), torch default ON, capture ke Bitmap in-memory. */
class CameraXController(private val context: Context) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** State torch; default ON tiap controller baru (tidak dipersist antar sesi). */
    var torchOn by mutableStateOf(true)
        private set

    /** Naik tiap capture berhasil; CameraPreviewLayer memakainya sebagai pemicu flash shutter. */
    var shutterCount by mutableIntStateOf(0)
        private set

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            try {
                provider.unbindAll()
                // ViewPort = area yang terlihat di PreviewView, supaya foto yang disimpan
                // sama framing-nya dengan preview (margin persen GuideFrame == margin crop).
                val group = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .apply { previewView.viewPort?.let { setViewPort(it) } }
                    .build()
                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    group
                )
                camera = cam
                applyTorch() // auto-ON awal memakai jalur yang sama dengan toggle manual
                imageCapture = capture
            } catch (e: Exception) {
                Log.e(TAG, "Gagal bind kamera", e)
            }
        }, mainExecutor)
    }

    fun setTorch(on: Boolean) {
        torchOn = on
        applyTorch()
    }

    fun toggleTorch() = setTorch(!torchOn)

    /**
     * Tap-to-focus: fokus + exposure di titik ([x], [y]) koordinat [previewView]. Setelah 3 detik
     * kamera kembali ke autofocus kontinu. Tidak berefek kalau kamera belum siap.
     */
    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            try {
                Log.d(TAG, "focus (${x.toInt()},${y.toInt()}) success=${future.get().isFocusSuccessful}")
            } catch (e: ExecutionException) {
                Log.w(TAG, "focus dibatalkan/gagal: ${e.cause?.message}")
            } catch (e: java.util.concurrent.CancellationException) {
                Log.w(TAG, "focus dibatalkan")
            }
        }, mainExecutor)
    }

    private fun applyTorch() {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    /**
     * Capture ke file [fileName] di folder temp. Hasil dirotasi, di-resize (maks [MAX_SIDE_PX] sisi panjang),
     * lalu disimpan sebagai JPEG di [TempPhotoStore]. Callback dipanggil di main thread
     * dengan path file.
     */
    fun capture(fileName: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("Kamera belum siap"))
            return
        }
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val file = try {
                    image.use { it.saveAsResizedJpeg(TempPhotoStore.fileFor(context, fileName)) }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal simpan foto", e)
                    mainExecutor.execute { onError(e) }
                    return
                }
                mainExecutor.execute {
                    shutterCount++
                    onSuccess(file.absolutePath)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture gagal", exception)
                mainExecutor.execute { onError(exception) }
            }
        })
    }

    fun release() {
        captureExecutor.shutdown()
    }

    /**
     * Crop ke ViewPort (cropRect), rotasi, dan resize dalam satu transformasi, lalu JPEG ke [target].
     * toBitmap() tidak menerapkan cropRect sendiri, jadi dilakukan di sini.
     */
    private fun ImageProxy.saveAsResizedJpeg(target: File): File {
        val raw = toBitmap()
        val crop = Rect(cropRect).apply {
            if (!intersect(0, 0, raw.width, raw.height)) set(0, 0, raw.width, raw.height)
        }
        val scale = minOf(1f, MAX_SIDE_PX.toFloat() / maxOf(crop.width(), crop.height()))
        val degrees = imageInfo.rotationDegrees
        val out = if (degrees == 0 && scale == 1f && crop.width() == raw.width && crop.height() == raw.height) {
            raw
        } else {
            val matrix = Matrix().apply {
                postRotate(degrees.toFloat())
                postScale(scale, scale)
            }
            Bitmap.createBitmap(raw, crop.left, crop.top, crop.width(), crop.height(), matrix, true)
        }
        try {
            FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            if (out !== raw) out.recycle()
            raw.recycle()
        }
        return target
    }

    private companion object {
        const val TAG = "CameraXController"
        const val MAX_SIDE_PX = 3000
        const val JPEG_QUALITY = 90
    }
}
