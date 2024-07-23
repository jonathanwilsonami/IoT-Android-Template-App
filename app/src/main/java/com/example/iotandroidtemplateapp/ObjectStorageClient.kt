package com.example.iotandroidtemplateapp

interface ObjectStorageClient {
    suspend fun saveNode(bucketName: String = "nodes", node: SensorNode)
    suspend fun uploadImage(bucketName: String = "nodes", node: SensorNode, imageType: String, byteArray: ByteArray)
}