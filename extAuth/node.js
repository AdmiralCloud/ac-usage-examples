const axios = require('axios')
const fs = require('fs')

const acsignature = require('ac-signature');
const signatureVersion = 3

const apiUser = {
  accessKey: 'ACCESSKEY',
  accessSecret: 'SECRET'
}

const identifier = 'LINK'
const docLink = `https://api.admiralcloud.com/v5/deliverFile/${identifier}`

const createToken = () => {
  const params       = {
    accessSecret: apiUser?.accessSecret,
    controller:   'user',
    action:       'extauth',
    payload:      {
      identifier,
      type: 'embedlink'
    }
  }
  const signedValues = acsignature.sign(params, { version: signatureVersion })
  const headers = {
    'x-admiralcloud-accesskey': apiUser?.accessKey,
    'x-admiralcloud-rts': signedValues?.timestamp,
    'x-admiralcloud-hash': signedValues?.hash,
    'x-admiralcloud-version': signatureVersion,
    'x-admiralcloud-debugSignature': true
  }

  const axiosParams = {
    method: 'post',
    url: 'https://api.admiralcloud.com/v5/extAuth',
    headers,
    data: params.payload
  }
  //console.log(39, axiosParams)

  axios(axiosParams)
    .then((response) => {
      //console.log(13, response?.data);
      callAuthLink({ token: response?.data?.token })
    })
    .catch((error) => {
      // handle error
      console.log(52, error.response.data);
    })
}

const callAuthLink = ({ token }) => {
  const auth = `THE_CLIENT_ID:${token}`
  let axiosParams = {
    url: docLink,
    params: {
      auth: Buffer.from(auth).toString('base64')
    },
    responseType: 'stream',
  }
//  console.log(51, auth, axiosParams)
  axios(axiosParams)
    .then((response) => {
      response.data.pipe(fs.createWriteStream(`./${identifier}.pdf`))
    })
    .catch((error) => {
      // handle error
      console.log(67, error);
    })
}

createToken()
