package com.example.iotandroidtemplateapp

object ObjectStorageClientFactory {

    fun createClient(provider: String, config: Map<String, String>): ObjectStorageClient {
        return when (provider) {
            "aws" -> AwsStorageClient(
                config["accessKey"] ?: throw IllegalArgumentException("Missing accessKey"),
                config["accessKey"] ?: throw IllegalArgumentException("Missing accessKey"),
                config["accessKey"] ?: throw IllegalArgumentException("Missing accessKey"),
            )
            "minio" -> MinioStorageClient(
                config["endpoint"] ?: throw IllegalArgumentException("Missing endpoint"),
                config["username"] ?: throw IllegalArgumentException("Missing username"),
                config["password"] ?: throw IllegalArgumentException("Missing password"),
            )
            else -> throw IllegalArgumentException("Unknown provider: $provider")
        }
    }

}
