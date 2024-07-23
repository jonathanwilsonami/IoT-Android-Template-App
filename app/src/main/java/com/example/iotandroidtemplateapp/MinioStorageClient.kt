package com.example.iotandroidtemplateapp

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.InputStream

class MinioStorageClient(
    private val endpoint: String,
    private val username: String,
    private val password: String
) : ObjectStorageClient {

    private val minioClient: MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(username, password)
        .build()

    override suspend fun saveNode(bucketName: String, node: SensorNode) {
        try {
            // Check if the bucket exists
            val bucketExists = BucketExistsArgs.builder().bucket(bucketName).build()
            println("Checking if bucket exists: ${bucketExists.bucket()}")

            if (minioClient.bucketExists(bucketExists)) {
                println("Using the existing bucket: $bucketName")
            } else {
                // Create the bucket if it does not exist
                val bucketArgs = MakeBucketArgs.builder().bucket(bucketName).build()
                minioClient.makeBucket(bucketArgs)
                println("Created bucket: $bucketName")
            }

            // Encode the node object to JSON
            val nodeJson = Json.encodeToString(node)
            println("Encoded node to JSON: $nodeJson")

            // Convert the JSON string to InputStream
            val inputStream = nodeJson.byteInputStream()
            println("Created input stream from JSON")

            // Put the object in the bucket
            val putObjectArgs = PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`("minio-data/${node.uuid}.json")
                .stream(inputStream, nodeJson.length.toLong(), -1)
                .contentType("application/json")
                .build()
            minioClient.putObject(putObjectArgs)
            println("Successfully put the object in the bucket: ${putObjectArgs.`object`()}")
        } catch (e: Exception) {
            println("Error saving node: ${e.message}")
            e.printStackTrace() // Print stack trace for detailed error
        }
    }

    override suspend fun uploadImage(bucketName: String, node: SensorNode, imageType: String, byteArray: ByteArray) {
        try {
            val bucketExists = BucketExistsArgs.builder().bucket(bucketName).build()
            if (minioClient.bucketExists(bucketExists)) {
                println("Using the existing bucket: $bucketName")
            } else {
                val bucketArgs = MakeBucketArgs.builder().bucket(bucketName).build()
                minioClient.makeBucket(bucketArgs)
                println("Created bucket: $bucketName")
            }

            val inputStream: InputStream = ByteArrayInputStream(byteArray)
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`("minio-data/images/${node.uuid}.${imageType}")
                    .stream(inputStream, byteArray.size.toLong(), -1)
                    .contentType("image/png")
                    .build()
            )
        } catch (e: Exception) {
            println("Error uploading image: ${e.message}")
        }
    }
}
