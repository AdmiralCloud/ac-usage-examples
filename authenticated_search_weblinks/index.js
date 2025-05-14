import fetch from 'node-fetch';
import acsignature from 'ac-signature';

// Put your credentials for authenticated requests here
const auth = {
    accessSecret: 'XXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXX',
    accessKey: 'XXXXXXXXXXXXXXXXXXX',
};

// Create payload
const payload = {
    basicSearch: false,
    debug: false,
    from: 0,
    minScore: 0,
    query: { bool: {} },
    size: 1000,
    sort: [{ id: 'asc' }]
}

// Create signature
const signature = acsignature.sign({
    accessSecret: auth.accessSecret,
    controller: 'search',
    action: 'search',
    payload
});

// Request using signature
const search = await (
    await fetch('https://api.admiralcloud.com/v5/search', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'x-admiralcloud-accesskey': auth.accessKey,
            'x-admiralcloud-rts': signature.timestamp,
            'x-admiralcloud-hash': signature.hash,
        },
        body: JSON.stringify(payload)
    })
).json();

// Unpack weblinks from search request
if (search && search.hits && search.hits.hits && search.hits.hits.length !== 0) {
    // Create data
    const data = search.hits.hits
        // Map MediaContainers
        .map(item => item._source)
        // Map to object with id and weblink
        .map(item => {
            // Define variables
            let weblink;
            let uuid = item.links.find(link => link.playerConfigurationId === 3 && link.type === 'image');
            // Check uuid and link
            if (uuid && uuid.link) {
                weblink = `https://images.admiralcloud.com/v5/deliverEmbed/${uuid.link}/image`
            }
            // Return id and conditionally weblink
            return {
                id: item.id,
                ...(weblink && { weblink })
            };
        });
    // Console log data
    console.log('Data:', data);
}


