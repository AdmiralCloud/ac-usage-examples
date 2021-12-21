/*
    Authenticated Requests example
*/
import fetch from 'node-fetch';
import acsignature from 'ac-signature';

//
// PUT YOUR CREDENTIALS FOR AUTHENTICATED REQUESTS HERE
//
const auth = {
    accessSecret: '1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX',
    accessKey: 'idstXXXXN2X6XXXXpqWBVX',
};

// Create Signature
const signature = acsignature.sign({
    accessSecret: auth.accessSecret,
    controller: 'customer',
    action: 'find',
    payload: {},
});

// Request using signature
const customer = await (
    await fetch('https://api.admiralcloud.com/v5/customer', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            'x-admiralcloud-accesskey': auth.accessKey,
            'x-admiralcloud-rts': signature.timestamp,
            'x-admiralcloud-hash': signature.hash,
        },
    })
).json();

console.log('Customer:', customer.name);
