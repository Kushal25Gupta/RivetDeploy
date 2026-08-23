package com.rivetdeploy.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "rivetdeploy.storage.type", havingValue = "s3")
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.accessKeyId:}")
    private String awsAccessKeyId;

    @Value("${aws.secretAccessKey:}")
    private String awsSecretAccessKey;

    @Bean
    public S3Client s3Client() {
        if (!awsAccessKeyId.isEmpty() && !awsSecretAccessKey.isEmpty()) {
            return S3Client.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)))
                    .build();
        } else {
            // Fallback to default credential provider chain
            return S3Client.builder()
                    .region(Region.of(awsRegion))
                    .build();
        }
    }
}
