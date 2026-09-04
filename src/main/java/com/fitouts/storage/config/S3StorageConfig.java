package com.fitouts.storage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
@Slf4j
public class S3StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "fitouts.storage.s3", name = "enabled", havingValue = "true")
    public S3Client s3Client(S3StorageProperties properties) {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));

        Region region = resolveBucketRegion(properties, credentials);
        log.info("Connecting to S3 bucket {} in region {}", properties.getBucket(), region);

        S3Client client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .crossRegionAccessEnabled(true)
                .build();

        verifyBucketAccess(client, properties.getBucket(), region);
        return client;
    }

    private Region resolveBucketRegion(S3StorageProperties properties, StaticCredentialsProvider credentials) {
        Region configured = Region.of(properties.getRegion());
        try (S3Client probe = S3Client.builder()
                .region(configured)
                .credentialsProvider(credentials)
                .crossRegionAccessEnabled(true)
                .build()) {
            String location = probe.getBucketLocation(builder -> builder.bucket(properties.getBucket()))
                    .locationConstraintAsString();
            if (location == null || location.isBlank()) {
                return configured;
            }
            return Region.of(location);
        } catch (S3Exception e) {
            Region hinted = parseRegionHint(e.getMessage());
            if (hinted != null) {
                log.info("Resolved S3 bucket {} region from AWS hint: {}", properties.getBucket(), hinted);
                return hinted;
            }
            log.warn(
                    "Could not resolve S3 bucket region for {} (using {}): {}",
                    properties.getBucket(),
                    configured,
                    e.getMessage());
            return configured;
        } catch (Exception e) {
            log.warn(
                    "Could not resolve S3 bucket region for {} (using {}): {}",
                    properties.getBucket(),
                    configured,
                    e.getMessage());
            return configured;
        }
    }

    private Region parseRegionHint(String message) {
        if (message == null || !message.contains("expecting '")) {
            return null;
        }
        int start = message.indexOf("expecting '") + 11;
        int end = message.indexOf('\'', start);
        if (end <= start) {
            return null;
        }
        try {
            return Region.of(message.substring(start, end));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void verifyBucketAccess(S3Client client, String bucket, Region region) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 bucket {} is reachable in {}", bucket, region);
        } catch (S3Exception e) {
            log.error(
                    "S3 bucket {} is not accessible ({}): {}. Uploads will be saved locally only.",
                    bucket,
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : e.statusCode(),
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
        }
    }
}
