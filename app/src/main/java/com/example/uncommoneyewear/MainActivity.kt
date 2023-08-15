package com.example.uncommoneyewear

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.os.Build
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.ByteArrayOutputStream


class MainActivity : AppCompatActivity(), ImageAnalysis.Analyzer {
    private var imageCapture: ImageCapture? = null
    private var bitmap: Bitmap? = null
    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var outputDirectory: File
    private lateinit var imageView: ImageView
    private lateinit var imageCaptureButton: Button
    private lateinit var changeCameraView: Button
    private lateinit var cameraViewLayout: FrameLayout
    private lateinit var imageViewLayout: LinearLayout
    private lateinit var imageViewPhoto: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
//        OpenCVLoader.initDebug()
            setContentView(R.layout.activity_main)

            imageView = findViewById(R.id.grayImageView)
            previewView = findViewById(R.id.previewView)
            imageCaptureButton = findViewById(R.id.image_capture_button)
            changeCameraView = findViewById(R.id.change_camera_view)
            cameraViewLayout = findViewById(R.id.cameraView)
            imageViewLayout = findViewById(R.id.imageView)
            imageViewPhoto = findViewById(R.id.imageViewPhoto)

//        val width = imageView.width
//        val previewViewLayout = previewView.layoutParams
//        previewViewLayout.width = width
//        previewViewLayout.height = width
            // Request camera permissions
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }

            // Set up the listeners for take photo and video capture buttons
            imageCaptureButton.setOnClickListener {
                takePhoto()
            }

            changeCameraView.setOnClickListener { convertCamera() }

            cameraExecutor = Executors.newSingleThreadExecutor()
            outputDirectory = getOutputDirectory()
        }
        catch (e: Exception){
            Log.d("test","test")
        }

    }

    private fun convertCamera() {
        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            startCamera()
        }
        else if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            startCamera()
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
//        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
//            .format(System.currentTimeMillis())
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
//            }
//        }

        val photoFile = File(
            outputDirectory,
            newJpgFileName()
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
//            .Builder(contentResolver,
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                contentValues)
            .Builder(photoFile)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val matrix = Matrix()
                    val bitmap: Bitmap? = null

                    val savedUrl = Uri.fromFile(photoFile)
                    showCaptureImage(savedUrl)
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }
    private fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), this)
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer,
                )

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toGrayscale(bmpOriginal: Bitmap): Bitmap {
        val width = bmpOriginal.width
        val height = bmpOriginal.height

        val bmpGrayscale: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0F);
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        c.drawBitmap(bmpOriginal, 0F, 0F, paint)
        return bmpGrayscale
    }

//    private fun toGrayscaleBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
//        val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
//        val outputStream = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, outputStream)
//        val jpegData = outputStream.toByteArray()
//        val originalBitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
//
//        val grayBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(grayBitmap)
//        val paint = Paint()
//        val colorMatrix = ColorMatrix()
//        colorMatrix.setSaturation(0f)
//        val colorFilter = ColorMatrixColorFilter(colorMatrix)
//        paint.colorFilter = colorFilter
//        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
//
//        return grayBitmap
//    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir
        else filesDir
    }

    private fun newJpgFileName() : String {
        val sdf = SimpleDateFormat(FILENAME_FORMAT)
        val filename = sdf.format(System.currentTimeMillis())
        return "${filename}.jpg"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun showCaptureImage(savedUrl: Uri): Boolean {
        if (imageViewLayout.visibility == View.GONE) {
            cameraViewLayout.visibility = View.GONE
            imageViewLayout.visibility = View.VISIBLE


            imageViewPhoto.setImageURI(savedUrl)
            return false
        }

        return true
    }

    override fun onBackPressed() {
        if (imageViewLayout.visibility == View.VISIBLE) {
            imageViewLayout.visibility = View.GONE
            cameraViewLayout.visibility = View.VISIBLE
//            viewBinding.imageViewPhoto.setImageURI(savedUrl)
        }
    }
    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun analyze(image: ImageProxy) {

        try {
            runOnUiThread {
                bitmap = previewView.bitmap
            }
        } catch (e: Exception){
            Log.d("Test", "test")
        }


        image.close()

        if (bitmap == null) {
            return
        }

        val bitmap1 = toGrayscale(bmpOriginal = bitmap!!)
        imageView.setImageBitmap(bitmap1)
    }
}
