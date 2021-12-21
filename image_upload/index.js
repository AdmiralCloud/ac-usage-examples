/*
    Upload image example
*/
import * as fs from 'fs';
import * as path from 'path';
import AWS from 'aws-sdk';
import * as MimeTypes from 'mime-types';
import fetch from 'node-fetch';
import acsignature from 'ac-signature';

//
// PUT YOUR CREDENTIALS FOR AUTHENTICATED REQUESTS HERE
//
const auth = {
    accessSecret: '1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX',
    accessKey: 'idstXXXXN2X6XXXXpqWBVX',
};

const API_HOST = 'https://api.admiralcloud.com';
const PATH_IMAGE = path.resolve('./image_for_upload.jpg');

// ======================================================================
// === Step 1: Initialize Upload
// ======================================================================
const dataCreateUpload = {
    payload: [
        {
            type: getTypeForFile(PATH_IMAGE),
            fileName: path.parse(path.basename(PATH_IMAGE)).name,
            originalFileExtension: path.extname(PATH_IMAGE),
            fileSize: fs.statSync(PATH_IMAGE).size,
        },
    ],
    controlGroups: [],
    tags: [],
};
const signCreateUpload = acsignature.sign({
    accessSecret: auth.accessSecret,
    controller: 's3',
    action: 'createUpload',
    payload: dataCreateUpload,
});
const { jobId } = await (
    await fetch(API_HOST + '/v5/s3/createUpload', {
        method: 'POST',
        body: JSON.stringify(dataCreateUpload),
        headers: {
            'Content-Type': 'application/json',
            'x-admiralcloud-accesskey': auth.accessKey,
            'x-admiralcloud-rts': signCreateUpload.timestamp,
            'x-admiralcloud-hash': signCreateUpload.hash,
        },
    })
).json();

// ======================================================================
// === Step 2: Wait for AWS S3 storage to be initialized
// ======================================================================
// Poll every 500ms until jobResult is no longer { ok: true }
let jobResult;
while (true) {
    const signJobResult = acsignature.sign({
        accessSecret: auth.accessSecret,
        controller: 'activity',
        action: 'jobResult',
        payload: { jobId: jobId },
    });
    jobResult = await (
        await fetch(API_HOST + '/v5/activity/jobResult/' + jobId, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'x-admiralcloud-accesskey': auth.accessKey,
                'x-admiralcloud-rts': signJobResult.timestamp,
                'x-admiralcloud-hash': signJobResult.hash,
            },
        })
    ).json();

    if (jobResult.ok !== true) break;
    await new Promise((res, rej) => setTimeout(res, 500));
}

// ======================================================================
// === Step 3: Upload to AWS S3
// ======================================================================
const { bucket, region, s3Key } = jobResult.processed[0];
const { AccessKeyId, SecretAccessKey, SessionToken } = jobResult.processed[0].credentials;

// Start Upload
let s3 = new AWS.S3({
    accessKeyId: AccessKeyId,
    secretAccessKey: SecretAccessKey,
    sessionToken: SessionToken,
    region,
    signatureVersion: 'v4',
});
const requestUpload = s3.upload({
    Bucket: bucket,
    Key: s3Key,
    ContentType: MimeTypes.lookup(path.extname(PATH_IMAGE)) || 'application/octet-stream',
    Body: fs.createReadStream(PATH_IMAGE),
});

// Display Progress info
requestUpload.on('httpUploadProgress', (progress) => {
    console.log('PROGRESS: ', Math.round((progress.loaded / progress.total) * 100));
});

// Wait for upload to finish
await requestUpload.promise();

// ======================================================================
// === Step 4: Tell AdmiralCloud to process file
// ======================================================================
const dataSuccess = {
    bucket: bucket,
    key: s3Key,
};
const signSuccess = acsignature.sign({
    accessSecret: auth.accessSecret,
    controller: 's3',
    action: 'success',
    payload: dataSuccess,
});

await (
    await fetch(API_HOST + '/v5/s3/success', {
        method: 'POST',
        body: JSON.stringify(dataSuccess),
        headers: {
            'Content-Type': 'application/json',
            'x-admiralcloud-accesskey': auth.accessKey,
            'x-admiralcloud-rts': signSuccess.timestamp,
            'x-admiralcloud-hash': signSuccess.hash,
        },
    })
).json();

console.log('Image available in AdmiralCloud -> https://app.admiralcloud.com/container/' + jobResult.processed[0].id + '/summary/info');

// ======================================================================

/**
 * HELPER
 *
 * getTypeForFile("/path/to/image.jpg") => "image"
 * getTypeForFile("/path/to/video.mp4") => "video"
 * getTypeForFile("/path/to/infos.pdf") => "document"
 */
function getTypeForFile(filePath) {
    const mimeType = MimeTypes.lookup(path.extname(filePath));
    if (mimeType === false || mimeType.startsWith('application')) {
        return 'document';
    }
    return mimeType.split('/')[0];
}
