/*
 * Copyright 2010-2013 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.samples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.UUID;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * This sample demonstrates how to make basic requests to Amazon S3 using
 * the AWS SDK for Java.
 * <p>
 * <b>Prerequisites:</b> You must have a valid Amazon Web Services developer
 * account, and be signed up to use Amazon S3. For more information on
 * Amazon S3, see http://aws.amazon.com/s3.
 * <p>
 * <b>Important:</b> Be sure to fill in your AWS access credentials in
 * ~/.aws/credentials (C:\Users\USER_NAME\.aws\credentials for Windows
 * users) before you try to run this sample.
 */
public class S3Sample {

    public static void main(String[] args) throws IOException, InterruptedException {
        try (S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_2)
                .build()) {

            while (true) {
                runCycle(s3);
                Thread.sleep(30_000);
            }
        }
    }

    // Set com.amazonaws.samples.S3Sample.runCycle as the custom entry point in Dynatrace.
    // Each call is a discrete unit of work; OneAgent traces it as a separate PurePath.
    static void runCycle(S3Client s3) throws IOException {
        String bucketName = "my-first-s3-bucket-" + UUID.randomUUID();
        String key = "MyObjectKey";

        System.out.println("===========================================");
        System.out.println("Getting Started with Amazon S3");
        System.out.println("===========================================\n");

        try {
            System.out.println("Creating bucket " + bucketName + "\n");
            s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

            System.out.println("Listing buckets");
            for (Bucket bucket : s3.listBuckets().buckets()) {
                System.out.println(" - " + bucket.name());
            }
            System.out.println();

            System.out.println("Uploading a new object to S3 from a file\n");
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                    createSampleFile().toPath());

            System.out.println("Downloading an object");
            ResponseInputStream<GetObjectResponse> object = s3.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(key).build());
            System.out.println("Content-Type: " + object.response().contentType());
            displayTextInputStream(object);

            System.out.println("Listing objects");
            ListObjectsResponse objectListing = s3.listObjects(ListObjectsRequest.builder()
                    .bucket(bucketName)
                    .prefix("My")
                    .build());
            for (S3Object objectSummary : objectListing.contents()) {
                System.out.println(" - " + objectSummary.key() + "  " +
                        "(size = " + objectSummary.size() + ")");
            }
            System.out.println();

            System.out.println("Deleting an object\n");
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());

            System.out.println("Deleting bucket " + bucketName + "\n");
            s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception ase) {
            System.out.println("Caught an S3Exception, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.statusCode());
            System.out.println("AWS Error Code:   " + ase.awsErrorDetails().errorCode());
            System.out.println("Error Type:       " + ase.awsErrorDetails().sdkHttpResponse().statusCode());
            System.out.println("Request ID:       " + ase.requestId());
        }
    }

    /**
     * Creates a temporary file with text data to demonstrate uploading a file
     * to Amazon S3
     *
     * @return A newly created temporary file with text data.
     *
     * @throws IOException
     */
    private static File createSampleFile() throws IOException {
        File file = File.createTempFile("aws-java-sdk-", ".txt");
        file.deleteOnExit();

        Writer writer = new OutputStreamWriter(new FileOutputStream(file));
        writer.write("abcdefghijklmnopqrstuvwxyz\n");
        writer.write("01234567890112345678901234\n");
        writer.write("!@#$%^&*()-=[]{};':',.<>/?\n");
        writer.write("01234567890112345678901234\n");
        writer.write("abcdefghijklmnopqrstuvwxyz\n");
        writer.close();

        return file;
    }

    /**
     * Displays the contents of the specified input stream as text.
     *
     * @param input
     *            The input stream to display as text.
     *
     * @throws IOException
     */
    private static void displayTextInputStream(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;

            System.out.println("    " + line);
        }
        System.out.println();
    }

}
