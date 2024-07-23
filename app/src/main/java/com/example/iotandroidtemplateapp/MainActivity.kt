package com.example.iotandroidtemplateapp

import SensorNodeRepository
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val node: SensorNode = SensorNode()
    private val bucketName: String = "nodes"

    private val nodeRepository = SensorNodeRepository()
    private lateinit var objectStorageClient: ObjectStorageClient

    private lateinit var imgPreview: ImageView
    private var capturedImage: Bitmap? = null
    private lateinit var photoURI: Uri

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    val imageBitmap = contentResolver.openInputStream(photoURI)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    imageBitmap?.let {
                        capturedImage = it
                        imgPreview.setImageBitmap(it)

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                objectStorageClient.uploadImage(bucketName, node, "png", bitmapToByteArray(it))
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "Image uploaded successfully")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Log.e(TAG, "Error uploading image: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding image: ${e.message}")
                    Toast.makeText(this, "Error decoding image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Image capture failed", Toast.LENGTH_SHORT).show()
            }
        }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val TAG = "MainActivity"
        private const val REQUEST_CAMERA_PERMISSION = 100
        private val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
        } else {
            setupUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupUI()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        // Configs for the ObjectStorageClient
        val minio_provider_config = mapOf(
            "provider" to "minio",
            "endpoint" to "http://10.0.2.2:9000",
            "username" to "minioadmin",
            "password" to "minioadmin",
        )

        objectStorageClient = ObjectStorageClientFactory.createClient(
            minio_provider_config["provider"] ?: throw IllegalArgumentException("Missing provider"),
            minio_provider_config
        )

        val nodeNameInputField: EditText = findViewById(R.id.nodeNameInputField)
        val nodeTypeField: EditText = findViewById(R.id.nodeTypeInputField)
        val takeImageBtn: Button = findViewById(R.id.takeImageBtn)
        imgPreview = findViewById(R.id.imagePreview)
        val submitBtn: Button = findViewById(R.id.submitFormBtn)

        takeImageBtn.setOnClickListener {
            Log.d(TAG, "Take image button clicked")
            dispatchTakePictureIntent(takePicture)
        }

        submitBtn.setOnClickListener {
            node.nodeName = nodeNameInputField.text.toString()
            node.nodeType = nodeTypeField.text.toString()

            val nodeJsonString = Json.encodeToString(node)
            println(nodeJsonString)

            saveNode(bucketName, node)

            lifecycleScope.launch { nodeRepository.sendNodeData(node) }
        }
    }

    private fun saveNode(bucketName: String, node: SensorNode) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                objectStorageClient.saveNode(bucketName, node)

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Node saved successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error saving node: ${e.message}")
                }
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return CAMERA_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(applicationContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasCameraFeature(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun dispatchTakePictureIntent(takePicture: ActivityResultLauncher<Intent>) {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        Log.d(TAG, "Starting camera intent")

        if (!hasCameraFeature()) {
            Log.e(TAG, "No camera feature available")
            Toast.makeText(this, "No camera feature available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
            return
        }

        val resolveInfo = packageManager.queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo == null) {
            Log.e(TAG, "No camera app available")
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
            return
        } else {
            Log.d(TAG, "Camera app available")

            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                Log.e(TAG, "Error creating file: ${ex.message}")
                null
            }

            photoFile?.also {
                Log.d(TAG, "Photo file path: ${it.absolutePath}")
                photoURI = FileProvider.getUriForFile(
                    this,
                    "com.example.iotandroidtemplateapp.fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePicture.launch(takePictureIntent)
            }
        }
    }

    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir("Android/data/com.example.iotandroidtemplateapp/files/")
        return try {
            val file = File.createTempFile(
                "PNG_${timeStamp}_",
                ".png",
                storageDir
            ).apply {
                deleteOnExit()
            }
            Log.d(TAG, "File created successfully: ${file.absolutePath}")
            file
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating file: ${ex.message}")
            null
        }
    }


}
