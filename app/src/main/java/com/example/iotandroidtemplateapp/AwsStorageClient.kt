package com.example.iotandroidtemplateapp

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

class AwsStorageClient(
    private val region: String,
    private val accessKey: String,
    private val secretKey: String
) : ObjectStorageClient {

    private val s3Client: S3Client

    init {
        val awsCreds = AwsBasicCredentials.create(accessKey, secretKey)
        s3Client = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
            .build()
    }

    override suspend fun saveNode(bucketName: String, node: SensorNode) {
        // Save node to AWS S3
    }

    override suspend fun uploadImage(bucketName: String, node: SensorNode, imageType: String, byteArray: ByteArray) {
        // Upload image to AWS S3
    }

}