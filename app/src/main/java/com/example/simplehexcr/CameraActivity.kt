package com.example.simplehexcr

import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutionException


class CameraActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var previewView: PreviewView
    private lateinit var btnMedia: Button
    private lateinit var outputDirectory: File
    private var cameraIsSetup: Boolean = false
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (permissionName == Manifest.permission.CAMERA) {
                if (isGranted) {
                    Log.v("permission", "Camera permission granted")
                    setupCamera()
                } else {
                    Toast.makeText(this, "Camera permission required to scan", Toast.LENGTH_SHORT)
                }
            } else if (permissionName == Manifest.permission.READ_MEDIA_IMAGES) {
                if (isGranted) {
                    Log.v("permission", "External storage permission granted")
                } else {
                    // don't show anything for denied external storage
                    Log.v("permission", "External storage permission was denied")
                }
            }
        }
    }

    private val getExistingPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleGallerySuccess(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        outputDirectory = getOutputDirectory()

        if (isCameraPermissionGranted()) {
            // start camera
            setupCamera()
        } else {
            requestPermissions()
        }

        btnMedia = findViewById(R.id.btnMedia)
        btnMedia.setOnClickListener {
            openGallery();
        }

    }
    private fun handleCaptureSuccess(photoFile: File) {
        val intent = Intent(this, ImageViewActivity::class.java).apply {
            putExtra("imagePath", photoFile.absolutePath)
        }
        startActivity(intent)
    }
    private fun handleGallerySuccess(uri: Uri) {
        val intent = Intent(this, ImageViewActivity::class.java).apply {
            putExtra("imageUri", uri.toString())
        }
        startActivity(intent)
    }
    private fun setupCamera() {
        if (!cameraIsSetup) {
            cameraIsSetup = true
            cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            previewView = findViewById(R.id.previewView)

            val btnCapture: Button = findViewById(R.id.btnCapture)
            btnCapture.setOnClickListener {
                captureImage()
            }

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider)
                } catch (e: ExecutionException) {
                    // Handle any errors
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(this))
        }

    }
    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun isGalleryPermissionGranted(): Boolean {
        Log.v("CameraActivity", "Checking gallery permission")
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun openGallery() {
        Log.v("CameraActivity", "openGallery clicked")
        if (isGalleryPermissionGranted()) {
            Log.v("CameraActivity", "Gallery permission exists")
            val galleryIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            getExistingPhotoLauncher.launch(galleryIntent)
        } else {
            Log.v("CameraActivity", "Gallery permission not granted")
            requestPermissions()
        }
    }
    private fun requestPermissions() {
        requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES))
    }
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        imageCapture = ImageCapture.Builder().build()

        val camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview, imageCapture)
    }
    private fun captureImage() {
        // Set up image capture listener, which is triggered after a photo has been taken
        val photoFile = File(outputDirectory, "captured_image.jpg")
        imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(photoFile).build(), ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                handleCaptureSuccess(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("capture", "Error on picture capture")
            }
        })
    }
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
}