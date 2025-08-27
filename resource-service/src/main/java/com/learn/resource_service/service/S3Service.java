package com.learn.resource_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class S3Service {
    private final S3Client s3Client;
    private final String bucketName;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public S3Service(@Value("${AWS_ACCESS_KEY}") String awsAccessKey,
                     @Value("${AWS_SECRET_KEY}") String awsSecretKey,
                     @Value("${AWS_BUCKET_NAME}") String bucketName,
                     @Value("${AWS_REGION}") String awsRegion) {
        this.bucketName = bucketName;

        this.s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials
                                .create(awsAccessKey, awsSecretKey)))
                .region(Region.of(awsRegion))
                .build();

        verifyBucketAccess();
    }

    private void verifyBucketAccess() {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1)
                    .build();

            s3Client.listObjectsV2(listRequest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot access S3 bucket: " + bucketName + ". Please verify credentials and bucket permissions.", e);
        }
    }

    public String uploadMp3(byte[] mp3Data, String fileName) {
        validateUploadParams(mp3Data, fileName);
        String s3Url = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .contentType("audio/mpeg")
                        .build();
                PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(mp3Data));
                if (response.eTag() == null || response.eTag().isEmpty()) {
                    throw new RuntimeException("Upload verification failed - missing ETag");
                }

                s3Url = String.format("https://%s.s3.amazonaws.com/%s", bucketName, fileName);

                if (!fileExists(fileName)) {
                    throw new RuntimeException("Upload verification failed - file not found after upload");
                }

                break;
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 upload attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Upload interrupted", ie);
                    }
                } else {
                    // Final attempt failed - clean up any partial upload
                    cleanupFailedUpload(fileName);
                }
            }
        }

        if (s3Url == null) {
            throw new RuntimeException("Failed to upload file to S3 after " + MAX_RETRY_ATTEMPTS + " attempts: " + fileName, lastException);
        }

        return s3Url;
    }

    private void cleanupFailedUpload(String fileName) {
        try {
            if (fileExists(fileName)) {
                deleteFile(fileName);
            }
        } catch (Exception e) {
            System.err.println("Failed to cleanup partial upload for file: " + fileName + " - " + e.getMessage());
        }
    }

    private void validateUploadParams(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Upload data cannot be null or empty");
        }
        validateFileName(fileName);
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }

        if (fileName.length() > 255) {
            throw new IllegalArgumentException("File name too long: " + fileName.length() + " characters");
        }
    }

    public void deleteFile(String fileName) {
        validateFileName(fileName);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();

                s3Client.deleteObject(deleteObjectRequest);

                if (fileExists(fileName)) {
                    throw new RuntimeException("Delete verification failed - file still exists after deletion");
                }

                return;
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 delete attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Delete interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to delete file from S3 after " + MAX_RETRY_ATTEMPTS + " attempts: " + fileName, lastException);
    }

    public boolean fileExists(String fileName) {
        validateFileName(fileName);

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);

            return response.contentLength() != null && response.contentLength() > 0;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            // Log the error but assume file exists to prevent accidental overwrites
            System.err.println("Error checking file existence for " + fileName + ": " + e.getMessage());
            return true; // Fail-safe
        }
    }

    public byte[] downloadFile(String fileName) {
        validateFileName(fileName);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();

                byte[] fileContent = s3Client.getObject(getObjectRequest).readAllBytes();

                if (fileContent.length == 0) {
                    throw new RuntimeException("Downloaded file is empty: " + fileName);
                }

                return fileContent;

            } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
                throw new RuntimeException("File not found in S3: " + fileName, e);
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 download attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Download interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to download file from S3 after " + MAX_RETRY_ATTEMPTS + " attempts: " + fileName, lastException);
    }
}
