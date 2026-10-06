package com.emabuia.pokevault.screens.scanner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.emabuia.pokevault.ocr.OCRTextBlock
import com.emabuia.pokevault.ocr.ScannedFrame
import com.emabuia.pokevault.ocr.ZoneBoundingBox
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureAutoFocusRangeRestrictionNear
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.autoFocusRangeRestriction
import platform.AVFoundation.autoFocusRangeRestrictionSupported
import platform.AVFoundation.focusMode
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraAccess(): CameraAccess {
    var status by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) }
    return CameraAccess(
        granted = when (status) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> null
            else -> false
        },
        deniedForever = status == AVAuthorizationStatusDenied || status == AVAuthorizationStatusRestricted,
        request = {
            if (status == AVAuthorizationStatusNotDetermined) {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    dispatch_async(dispatch_get_main_queue()) {
                        status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
                    }
                }
            } else {
                // Gia' negato: iOS non lo richiede piu', si apre la pagina dell'app nelle Impostazioni.
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
                }
            }
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ScannerCameraPreview(
    onFrameScanned: (ScannedFrame) -> Unit,
    flashEnabled: Boolean,
    scanEnabled: Boolean,
    modifier: Modifier,
) {
    val camera = remember { CardCamera() }

    // La fotocamera gira sulla sua coda: legge sempre gli ultimi valori, non
    // quelli del primo fotogramma.
    SideEffect {
        camera.onFrame = onFrameScanned
        camera.scanEnabled = scanEnabled
    }

    DisposableEffect(Unit) {
        camera.start()
        onDispose { camera.stop() }
    }

    LaunchedEffect(flashEnabled) { camera.setTorch(flashEnabled) }

    UIKitView(
        factory = { camera.view },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * La sessione AVFoundation, l'anteprima e la lettura dei fotogrammi: su
 * Android sono CameraPreview, bindCamera e CardFrameAnalyzer di ScannerScreen.
 *
 * Tutto il lavoro sui fotogrammi gira sulla coda della sessione, una alla
 * volta: un fotogramma che arriva mentre si legge il precedente si scarta
 * (alwaysDiscardsLateVideoFrames), come STRATEGY_KEEP_ONLY_LATEST su Android.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class CardCamera {
    private val session = AVCaptureSession()
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    val view: UIView = PreviewContainer(previewLayer) { width, height ->
        viewWidth = width
        viewHeight = height
    }

    private val queue = dispatch_queue_create("com.emabuia.pokevault.scanner", null)
    private val output = AVCaptureVideoDataOutput()
    private val delegate = FrameDelegate(this)
    private val ciContext = CIContext()
    private var device: AVCaptureDevice? = null
    private var configured = false
    private var lastAnalyzedAt = 0.0

    // Scritti dal thread principale, letti dalla coda della fotocamera.
    var onFrame: (ScannedFrame) -> Unit = {}
    var scanEnabled: Boolean = true
    private var viewWidth = 0.0
    private var viewHeight = 0.0

    fun start() {
        dispatch_async(queue) {
            if (!configured) configure()
            if (!session.running) session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(queue) {
            if (session.running) session.stopRunning()
        }
    }

    fun setTorch(on: Boolean) {
        dispatch_async(queue) {
            val camera = device ?: return@dispatch_async
            if (!camera.hasTorch) return@dispatch_async
            if (camera.lockForConfiguration(null)) {
                camera.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
                camera.unlockForConfiguration()
            }
        }
    }

    private fun configure() {
        configured = true
        session.beginConfiguration()
        // Piu' pixel sul numero da collezione, che e' alto circa 1,6 mm (vedi Android).
        if (session.canSetSessionPreset(AVCaptureSessionPreset1920x1080)) {
            session.sessionPreset = AVCaptureSessionPreset1920x1080
        }
        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (camera == null) {
            session.commitConfiguration()
            return
        }
        device = camera
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null)
        if (input != null && session.canAddInput(input)) session.addInput(input)

        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(delegate, queue)
        if (session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()

        // Fuoco continuo, limitato alle distanze brevi: la carta sta a una spanna.
        if (camera.lockForConfiguration(null)) {
            if (camera.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
                camera.focusMode = AVCaptureFocusModeContinuousAutoFocus
            }
            if (camera.autoFocusRangeRestrictionSupported) {
                camera.autoFocusRangeRestriction = AVCaptureAutoFocusRangeRestrictionNear
            }
            camera.unlockForConfiguration()
        }
    }

    /** Un fotogramma dalla coda della sessione. */
    fun analyze(buffer: CMSampleBufferRef) {
        if (!scanEnabled) return
        val now = NSDate().timeIntervalSince1970 * 1000.0
        if (now - lastAnalyzedAt < ANALYSIS_THROTTLE_MS) return
        lastAnalyzedAt = now

        val frame = autoreleasepool { readFrame(buffer) } ?: return
        // Anche un fotogramma vuoto e' un'informazione: la carta e' stata spostata.
        onFrame(frame)
    }

    private fun readFrame(buffer: CMSampleBufferRef): ScannedFrame? {
        val pixels = CMSampleBufferGetImageBuffer(buffer) ?: return null
        // Il sensore e' orizzontale: la carta si raddrizza come la tiene chi scansiona.
        val upright = CIImage.imageWithCVPixelBuffer(pixels)
            .imageByApplyingCGOrientation(kCGImagePropertyOrientationRight)
            .atOrigin()
        val card = upright.cropToScanZone() ?: return null

        val blocks = recognizeLines(card)
        return ScannedFrame(blocks = blocks, idStripText = readIdStrip(card))
    }

    /**
     * La parte d'immagine dentro la cornice. L'anteprima e' "aspect fill":
     * dello stesso fotogramma si vede solo la parte centrale, e la cornice
     * dello schermo va riportata li', come fa il ViewPort su Android.
     */
    private fun CIImage.cropToScanZone(): CIImage? {
        val (imageWidth, imageHeight) = size()
        if (imageWidth <= 0.0 || imageHeight <= 0.0) return null
        val vw = viewWidth
        val vh = viewHeight
        if (vw <= 0.0 || vh <= 0.0) return this

        val scale = max(vw / imageWidth, vh / imageHeight)
        val offsetX = (imageWidth - vw / scale) / 2.0
        val offsetY = (imageHeight - vh / scale) / 2.0
        val zone = scanZoneRect(vw.toFloat(), vh.toFloat())

        val x = offsetX + zone.left / scale
        val yTop = offsetY + zone.top / scale
        val width = min(zone.width / scale, imageWidth - x)
        val height = min(zone.height / scale, imageHeight - yTop)
        if (width <= 1.0 || height <= 1.0) return null
        // CoreImage misura dal basso, la cornice dall'alto.
        val yBottom = imageHeight - (yTop + height)
        return imageByCroppingToRect(CGRectMake(x, yBottom, width, height)).atOrigin()
    }

    /**
     * La striscia in fondo con il numero, ingrandita e in grigio ad alto
     * contrasto (ImagePreprocessor.enhanceIdStrip su Android). Se il
     * contrasto ha mangiato le cifre, si rilegge la striscia com'e'.
     */
    private fun readIdStrip(card: CIImage): String {
        val (width, height) = card.size()
        val stripHeight = height * (1.0 - ID_STRIP_TOP)
        if (stripHeight <= 1.0) return ""
        // In CoreImage il fondo della carta e' a y = 0.
        val strip = card.imageByCroppingToRect(CGRectMake(0.0, 0.0, width, stripHeight)).atOrigin()
        val scale = (TARGET_ID_STRIP_HEIGHT / stripHeight).coerceIn(1.0, MAX_ID_STRIP_UPSCALE)
        val enhanced = strip
            .imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))
            .imageByApplyingFilter(
                "CIColorControls",
                mapOf<Any?, Any>("inputSaturation" to 0.0, "inputContrast" to 2.0),
            )
            .atOrigin()

        val enhancedText = recognizeLines(enhanced).joinToString("\n") { it.text }
        return if (enhancedText.any { it.isDigit() }) {
            enhancedText
        } else {
            enhancedText + "\n" + recognizeLines(strip).joinToString("\n") { it.text }
        }
    }

    /** Le righe lette da Vision, con la posizione normalizzata sull'immagine (dall'alto). */
    private fun recognizeLines(image: CIImage): List<OCRTextBlock> {
        val cgImage: CGImageRef = ciContext.createCGImage(image, fromRect = image.extent) ?: return emptyList()
        try {
            val request = VNRecognizeTextRequest().apply {
                recognitionLevel = VNRequestTextRecognitionLevelAccurate
                // Nomi propri e numeri: la correzione linguistica li "aggiusterebbe".
                usesLanguageCorrection = false
                recognitionLanguages = listOf("it-IT", "en-US")
            }
            val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
            if (!handler.performRequests(listOf(request), null)) return emptyList()

            return request.results.orEmpty()
                .filterIsInstance<VNRecognizedTextObservation>()
                .mapNotNull { observation ->
                    val best = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText ?: return@mapNotNull null
                    val text = best.string.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    observation.boundingBox.useContents {
                        // Vision misura dal basso a sinistra; i parser vogliono l'alto.
                        val left = origin.x.toFloat()
                        val right = (origin.x + size.width).toFloat()
                        val top = (1.0 - (origin.y + size.height)).toFloat()
                        val bottom = (1.0 - origin.y).toFloat()
                        OCRTextBlock(
                            text = text,
                            confidence = best.confidence,
                            boundingBox = ZoneBoundingBox(left = left, top = top, right = right, bottom = bottom),
                            normalizedY = (top + bottom) / 2f,
                        )
                    }
                }
                .sortedBy { it.normalizedY }
        } finally {
            CGImageRelease(cgImage)
        }
    }

    private fun CIImage.size(): Pair<Double, Double> = extent.useContents { size.width to size.height }

    /** L'immagine spostata con l'angolo in basso a sinistra in (0, 0). */
    private fun CIImage.atOrigin(): CIImage {
        val (x, y) = extent.useContents { origin.x to origin.y }
        if (x == 0.0 && y == 0.0) return this
        return imageByApplyingTransform(CGAffineTransformMakeTranslation(-x, -y))
    }

    private companion object {
        /** Freno minimo: il ritmo vero lo impone la lettura, sincrona. */
        const val ANALYSIS_THROTTLE_MS = 100.0
        /** Altezza a cui portare la striscia del numero prima di leggerla. */
        const val TARGET_ID_STRIP_HEIGHT = 480.0
        /** Oltre questo fattore l'ingrandimento aggiunge solo sfocatura. */
        const val MAX_ID_STRIP_UPSCALE = 4.0
    }
}

/** Il delegato dei fotogrammi: passa ognuno alla fotocamera. */
@OptIn(ExperimentalForeignApi::class)
private class FrameDelegate(
    private val camera: CardCamera,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        camera.analyze(didOutputSampleBuffer ?: return)
    }
}

/** La vista che ospita l'anteprima e ne segue le dimensioni. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewContainer(
    private val previewLayer: AVCaptureVideoPreviewLayer,
    private val onSize: (Double, Double) -> Unit,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Senza animazione implicita: il livello deve seguire la vista, non inseguirla.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
        bounds.useContents { onSize(size.width, size.height) }
    }
}

