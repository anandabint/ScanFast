package com.blitar.scanfast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.activity.viewModels
import android.view.KeyEvent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blitar.scanfast.camera.VolumeShutterHandler
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.model.CaptureViewModel
import com.blitar.scanfast.storage.TempPhotoStore
import com.blitar.scanfast.ui.components.IntroAnimation
import com.blitar.scanfast.ui.screens.CaptureScreen
import com.blitar.scanfast.ui.screens.CropEditorScreen
import com.blitar.scanfast.ui.screens.ProcessingScreen
import com.blitar.scanfast.ui.screens.RangeInputScreen
import com.blitar.scanfast.ui.screens.RetakeScreen
import com.blitar.scanfast.ui.screens.ReviewScreen
import com.blitar.scanfast.ui.screens.SummaryScreen
import com.blitar.scanfast.ui.theme.ScanFastTheme

object Routes {
    const val RANGE_INPUT = "range_input"
    const val CAPTURE = "capture/{start}/{end}"
    fun capture(start: Int, end: Int) = "capture/$start/$end"
    const val PROCESSING = "processing"
    const val REVIEW = "review"
    const val CROP_EDITOR = "crop_editor/{number}"
    fun cropEditor(number: Int) = "crop_editor/$number"
    const val RETAKE = "retake/{number}"
    fun retake(number: Int) = "retake/$number"
    const val SUMMARY = "summary"
}

class MainActivity : ComponentActivity() {
    private val captureViewModel: CaptureViewModel by viewModels()
    private val batchViewModel: BatchViewModel by viewModels()
    private val volumeShutter = VolumeShutterHandler()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        volumeShutter.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        volumeShutter.onKeyUp(keyCode) || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // harus sebelum super.onCreate(); intro animasi ada di Compose (IntroAnimation)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Peluncuran baru: buang sisa foto sementara dari sesi sebelumnya.
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) { TempPhotoStore.clearAll(applicationContext) }
        }
        setContent {
            ScanFastTheme {
                val navController = rememberNavController()
                // Intro hanya pada peluncuran baru; rememberSaveable menjaga agar tidak diulang saat recreate.
                var showIntro by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                // Frame pertama hanya menggambar intro (ringan) supaya layar hitam kosong tidak lama;
                // NavHost baru dikomposisi di frame berikutnya, dan animasi intro mulai setelah itu.
                var phase by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    phase = 1
                    withFrameNanos { }
                    phase = 2
                }
                Box(Modifier.fillMaxSize()) {
                if (phase >= 1) NavHost(
                    navController = navController,
                    startDestination = Routes.RANGE_INPUT,
                    modifier = Modifier.fillMaxSize(),
                    // Motion M3 "fade through": layar lama memudar cepat, layar baru muncul + membesar halus.
                    enterTransition = { fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(300), initialScale = 0.92f) },
                    exitTransition = { fadeOut(tween(90)) },
                    popEnterTransition = { fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(300), initialScale = 0.92f) },
                    popExitTransition = { fadeOut(tween(90)) },
                ) {
                    composable(Routes.RANGE_INPUT) {
                        RangeInputScreen(onStart = { start, end ->
                            captureViewModel.clear()
                            navController.navigate(Routes.capture(start, end))
                        })
                    }
                    composable(
                        Routes.CAPTURE,
                        arguments = listOf(
                            navArgument("start") { type = NavType.IntType },
                            navArgument("end") { type = NavType.IntType },
                        )
                    ) { entry ->
                        CaptureScreen(
                            start = entry.arguments!!.getInt("start"),
                            end = entry.arguments!!.getInt("end"),
                            viewModel = captureViewModel,
                            volumeShutter = volumeShutter,
                            onRun = { navController.navigate(Routes.PROCESSING) },
                            onExit = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.PROCESSING) {
                        ProcessingScreen(
                            captureViewModel = captureViewModel,
                            batchViewModel = batchViewModel,
                            onDone = {
                                navController.navigate(Routes.REVIEW) {
                                    popUpTo(Routes.PROCESSING) { inclusive = true }
                                }
                            },
                            onNoSession = {
                                navController.navigate(Routes.RANGE_INPUT) {
                                    popUpTo(Routes.RANGE_INPUT) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Routes.REVIEW) {
                        ReviewScreen(
                            captureViewModel = captureViewModel,
                            batchViewModel = batchViewModel,
                            onOpenEditor = { navController.navigate(Routes.cropEditor(it)) },
                            onDone = {
                                navController.navigate(Routes.SUMMARY) {
                                    popUpTo(Routes.REVIEW) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(
                        Routes.CROP_EDITOR,
                        arguments = listOf(navArgument("number") { type = NavType.IntType })
                    ) { entry ->
                        CropEditorScreen(
                            number = entry.arguments!!.getInt("number"),
                            captureViewModel = captureViewModel,
                            batchViewModel = batchViewModel,
                            onClose = { navController.popBackStack() },
                            onRetake = { navController.navigate(Routes.retake(it)) },
                        )
                    }
                    composable(
                        Routes.RETAKE,
                        arguments = listOf(navArgument("number") { type = NavType.IntType })
                    ) { entry ->
                        RetakeScreen(
                            number = entry.arguments!!.getInt("number"),
                            captureViewModel = captureViewModel,
                            batchViewModel = batchViewModel,
                            volumeShutter = volumeShutter,
                            onClose = { navController.popBackStack() },
                            // Berhasil: kembali ke Review (melewati editor di back stack).
                            onDone = { navController.popBackStack(Routes.REVIEW, inclusive = false) },
                        )
                    }
                    composable(Routes.SUMMARY) {
                        SummaryScreen(batchViewModel = batchViewModel, onNewSession = {
                            batchViewModel.reset()
                            captureViewModel.clear()
                            navController.navigate(Routes.RANGE_INPUT) {
                                popUpTo(Routes.RANGE_INPUT) { inclusive = true }
                            }
                        })
                    }
                }
                if (showIntro) IntroAnimation(start = phase >= 2, onFinished = { showIntro = false })
                }
            }
        }
    }
}
