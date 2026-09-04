package com.fitouts.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "fitouts.storage.s3")
public class S3StorageProperties {

    private boolean enabled;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region = "eu-west-1";
    /** Optional folder prefix inside the bucket, e.g. "fitouts". */
    private String keyPrefix = "";
    /** CloudFront or public bucket base URL, e.g. https://d3l8rommm7tn0.cloudfront.net/ */
    private String publicBaseUrl;
    /** When false, S3 failures are logged but local storage still succeeds. */
    private boolean failOnUploadError = false;
}
