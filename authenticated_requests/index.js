/*
    Authenticated Requests example
*/
import fetch from 'node-fetch';
import acsignature from 'ac-signature'; // https://www.npmjs.com/package/ac-signature

//
// PUT YOUR CREDENTIALS FOR AUTHENTICATED REQUESTS HERE
//
const auth = {
    accessSecret: '1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX',
    accessKey: 'idstXXXXN2X6XXXXpqWBVX',
};

const clientId = 'CLIENT_ID_FOR_YOUR_APP'

// Create Signature
const signature = acsignature.sign5({
    accessSecret: auth.accessSecret,
    path: '/v5/customer',
    payload: {},
});

// Request using signature
const customer = await (
    await fetch('https://api.admiralcloud.com/v5/customer', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'x-admiralcloud-clientid': clientId,
            'x-admiralcloud-accesskey': auth.accessKey,
            'x-admiralcloud-rts': signature.timestamp,
            'x-admiralcloud-hash': signature.hash,
            'x-admiralcloud-version': 5
        },
    })
).json();

console.log('Customer:', customer.name);
