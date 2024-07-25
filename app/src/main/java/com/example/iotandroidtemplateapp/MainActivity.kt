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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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

    private var headNode: SensorNode = SensorNode(nodeType = "head")
    private var currentNode: SensorNode? = null
    private val bucketName: String = "nodes"

    private val nodeRepository = SensorNodeRepository()
    private lateinit var objectStorageClient: ObjectStorageClient

    private lateinit var imgPreview: ImageView
    private var capturedImage: Bitmap? = null
    private lateinit var photoURI: Uri

    private lateinit var nodeMateContainer: ConstraintLayout
    private lateinit var childrenNodesContainer: ConstraintLayout
    private val imageViews = mutableListOf<ImageView>()
    private val photoURIs = mutableListOf<Uri>()

    private val takePicture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val index = imageViews.lastIndex
            if (index >= 0 && index < photoURIs.size) {
                val imageBitmap = contentResolver.openInputStream(photoURIs[index])?.use {
                    BitmapFactory.decodeStream(it)
                }
                imageBitmap?.let {
                    imageViews[index].setImageBitmap(it)
                    capturedImage = it

                    // Upload image to storage
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            currentNode?.let { node ->
                                objectStorageClient.uploadImage(bucketName, node, "png", bitmapToByteArray(it))
                            }
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
            } else {
                Log.e(TAG, "Index $index is out of bounds for imageViews or photoURIs list")
            }
        } else {
            Toast.makeText(this, "Image capture failed", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
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
        val minioProviderConfig = mapOf(
            "provider" to "minio",
            "endpoint" to "http://10.0.2.2:9000",
            "username" to "minioadmin",
            "password" to "minioadmin"
        )

        objectStorageClient = ObjectStorageClientFactory.createClient(
            minioProviderConfig["provider"] ?: throw IllegalArgumentException("Missing provider"),
            minioProviderConfig
        )

        val nodeNameInputField: EditText = findViewById(R.id.nodeNameInputField)
        val nodeTypeField: EditText = findViewById(R.id.nodeTypeInputField)
        val takeImageBtn: Button = findViewById(R.id.takeImageBtn)
        imgPreview = findViewById(R.id.imagePreview)
        val submitBtn: Button = findViewById(R.id.submitFormBtn)

        nodeMateContainer = findViewById(R.id.nodeMateContainer)
        childrenNodesContainer = findViewById(R.id.childrenNodesContainer)

        takeImageBtn.setOnClickListener {
            Log.d(TAG, "Take image button clicked")
            headNode.nodeName = nodeNameInputField.text.toString()
            imageViews.add(imgPreview)
            dispatchTakePictureIntent(headNode)
        }

        submitBtn.setOnClickListener {

            val nodeJsonString = Json.encodeToString(headNode)
            println(nodeJsonString)

            saveNode(bucketName, headNode)

            lifecycleScope.launch { nodeRepository.sendNodeData(headNode) }

            // Call the function to reset the input fields and views
            resetInputFieldsAfterSubmit()
        }

        findViewById<Button>(R.id.addNodeMateBtn).setOnClickListener {
            addNodeMate()
        }

        findViewById<Button>(R.id.addChildNodeBtn).setOnClickListener {
            addChildNode()
        }
    }

    private fun addNodeMate() {
        Log.d(TAG, "Adding node mate")
        nodeMateContainer.visibility = View.VISIBLE
        val nodeMateView = layoutInflater.inflate(R.layout.node_input, nodeMateContainer, false)
        nodeMateContainer.addView(nodeMateView)
        Log.d(TAG, "Node mate added")
        setupDynamicNode(nodeMateView, nodeRelation = "mate")
    }

    private fun addChildNode() {
        Log.d(TAG, "Adding child node")
        childrenNodesContainer.visibility = View.VISIBLE
        val childNodeView = layoutInflater.inflate(R.layout.node_input, childrenNodesContainer, false)
        childrenNodesContainer.addView(childNodeView)
        Log.d(TAG, "Child node added")
        setupDynamicNode(childNodeView, nodeRelation = "child")
    }

    private fun setupDynamicNode(view: View, nodeRelation: String) {
        Log.d(TAG, "Setting up dynamic node")
        val takeImageDynamicBtn: Button = view.findViewById(R.id.takeImageDynamicBtn)
        val imageDynamicPreview: ImageView = view.findViewById(R.id.imageDynamicPreview)
        val nodeNameDynamicInputField: EditText = findViewById(R.id.nodeNameDynamicInputField)
        val nodeTypeDynamicInputField: EditText = findViewById(R.id.nodeTypeDynamicInputField)

        Log.d(TAG, "Button and ImageView found")

        takeImageDynamicBtn.setOnClickListener {
            val node = SensorNode().apply {
                nodeName = nodeNameDynamicInputField.text.toString()
            }

            imageViews.add(imageDynamicPreview)
            Log.d(TAG, "Take image button clicked")
            dispatchTakePictureIntent(node)

            // Ensure connectedNodes and childrenNodes are initialized
            if (headNode.connectedNodes == null) {
                headNode.connectedNodes = ConnectedNodes() // Initialize connectedNodes if null
            }

            if (nodeRelation == "child") {
                // Add the node to childrenNodes
                node.nodeType = nodeRelation
                if (headNode.connectedNodes?.childrenNodes == null) {
                    headNode.connectedNodes?.childrenNodes = mutableListOf()
                }
                // Add the node to childrenNodes
                headNode.connectedNodes?.childrenNodes?.add(node)
            } else {
                if (nodeRelation == "mate") {
                    // Add the node to nodeMate
                    node.nodeType = nodeRelation
                    headNode.connectedNodes?.nodeMate = node
                }
            }
            // Reset the dynamic inputs after handling the image
            resetDynamicInputFields(view)
        }
    }

    private fun dispatchTakePictureIntent(node: SensorNode) {
        currentNode = node
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if (!hasCameraFeature()) {
            Toast.makeText(this, "No camera feature available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSION)
            return
        }

        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating file: ${ex.message}")
            null
        }

        photoFile?.also {
            photoURI = FileProvider.getUriForFile(
                this,
                "com.example.iotandroidtemplateapp.fileprovider",
                it
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            photoURIs.add(photoURI)
            takePicture.launch(takePictureIntent)

        }
    }

    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir("Android/data/com.example.iotandroidtemplateapp/files/")
        return try {
            File.createTempFile(
                "PNG_${timeStamp}_",
                ".png",
                storageDir
            ).apply {
                deleteOnExit()
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating file: ${ex.message}")
            null
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

    private fun resetInputFieldsAfterSubmit() {
        val nodeNameInputField: EditText? = findViewById(R.id.nodeNameInputField)
        val nodeTypeField: EditText? = findViewById(R.id.nodeTypeInputField)
        val nodeNameDynamicInputField: EditText? = findViewById(R.id.nodeNameDynamicInputField)
        val nodeTypeDynamicInputField: EditText? = findViewById(R.id.nodeTypeDynamicInputField)

        // Clear the text fields if they are not null
        nodeNameInputField?.text?.clear()
        nodeTypeField?.text?.clear()
        nodeNameDynamicInputField?.text?.clear()
        nodeTypeDynamicInputField?.text?.clear()

        // Reset the ImageView if not null
        imgPreview.setImageDrawable(null)
        for (imageView in imageViews) {
            imageView.setImageDrawable(null)
        }

        // Clear the list of ImageViews and URIs
        imageViews.clear()
        photoURIs.clear()

        // Reset the currentNode and capturedImage
        currentNode = null
        capturedImage = null

        // Hide dynamic node views if needed
        nodeMateContainer.visibility = View.GONE
        childrenNodesContainer.visibility = View.GONE

        // reset the headNode
        headNode = SensorNode(nodeType = "head")
    }

    private fun resetDynamicInputFields(view: View) {
        // Reset the dynamic node input fields
        val nodeNameDynamicInputField: EditText? = view.findViewById(R.id.nodeNameDynamicInputField)
        val nodeTypeDynamicInputField: EditText? = view.findViewById(R.id.nodeTypeDynamicInputField)
        val imageDynamicPreview: ImageView? = view.findViewById(R.id.imageDynamicPreview)

        // Clear the text fields and reset ImageView if not null
        nodeNameDynamicInputField?.text?.clear()
        nodeTypeDynamicInputField?.text?.clear()
        imageDynamicPreview?.setImageDrawable(null)

        // Remove the dynamic view if needed
        (view.parent as? ViewGroup)?.removeView(view)
    }

}
