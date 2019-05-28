// IPFS Setup
const ipfsClient = require("ipfs-http-client");
const ipfs = ipfsClient("/ip4/127.0.0.1/tcp/5001");
const bl = require("bl");

// AWS Setup
const AWS = require("aws-sdk");
// const IAM_USER_KEY = "AKIASWQVFICH4DWOGVG5";
// const IAM_USER_SECRET = "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";
const rekognition = new AWS.Rekognition({
  region: "us-west-2",
  accessKeyId: IAM_USER_KEY,
  secretAccessKey: IAM_USER_SECRET
});
const fs = require("fs");

// Cryptography
const NodeRSA = require("node-rsa");
const atob = require("atob");
const forge = require("node-forge");
/**
 * Resolve current IPNS address to a CID
 */
function ipnsResolve() {
  return new Promise(function(resolve, reject) {
    ipfs.id(function(err, identity) {
      if (err) {
        reject(error);
      }
      ipfs.resolve("/ipns/" + identity.id, function(err, cid) {
        if (err) {
          reject(error);
        }
        const components = cid.split("/");
        const lastElement = components[components.length - 1];
        resolve(lastElement);
      });
    });
  });
}

/**
 * Retrieve a large file from IPFS using stream.
 * @param {String} cid
 */
function ipfsCatStream(cid) {
  const stream = ipfs.catReadableStream(cid);
  return new Promise(function(resolve, reject) {
    stream.pipe(
      bl((err, data) => {
        if (err) {
          reject(err);
        } else {
          resolve(data);
        }
      })
    );
  });
}

/**
 * Check if current string is JSON and if it represent an Image
 * @param {String} str
 */
function isImageResource(str) {
  try {
    json = JSON.parse(str);
    return json.type == "IMAGE";
  } catch (e) {
    return false;
  }
}

/**
 * Detect objects from an image with AWS.Rekognition
 * @param {Bytes} imageData
 */
function processImage(imageData) {
  var params = {
    Image: {
      Bytes: imageData
    },
    MaxLabels: 5
  };
  return new Promise(function(resolve, reject) {
    rekognition.detectLabels(params, function(err, result) {
      if (err) {
        reject(err);
      } else {
        resolve(result);
      }
    });
  });
}

async function processResource(cid) {
  try {
    // 1. Load resource json
    const userResource = await ipfs.cat(cid);
    if (!isImageResource(userResource)) {
      return;
    }
    const ipfsImageJSON = JSON.parse(userResource);

    // 2. Get image IPFS hash
    const ipfsImageHash = ipfsImageJSON.file.hash;
    console.log("Image CID: " + ipfsImageHash + ".");

    // 3. Load image
    const imageBinary = await ipfsCatStream(ipfsImageHash);
    console.log("Image binary loaded.");

    // 4. AWS Rekognition - detect labels
    const detection = await processImage(imageBinary);
    ipfsImageJSON.file.detection = detection;

    // 5. AES Encryption
    var iv = forge.random.getBytesSync(16);
    var salt = forge.random.getBytesSync(16);
    // var aesKey = forge.pkcs5.pbkdf2("pasfdsfdssword", salt, 10000, 16);
    var aesKey = forge.random.getBytesSync(16);
    console.log("AES Key: " + aesKey);
    console.log("Bytes: " + toUTF8Array(aesKey).length);
    var cipher = forge.cipher.createCipher("AES-CBC", aesKey);
    cipher.start({ iv: iv });

    var input = forge.util.createBuffer(JSON.stringify(ipfsImageJSON), "utf8");
    cipher.update(input);
    cipher.finish();
    var aesCipherText = cipher.output.getBytes();

    // 6. Add AES encrypted to IPFS
    var addResult = await ipfs.add(Buffer.from(aesCipherText));
    var aesCipherCID = addResult[0].hash;
    console.log("Added Encrypted CID: " + aesCipherCID);

    // 7. RSA Encrypt
    var publicKey = ipfsImageJSON.peer.publicKey;
    var rsaNode = new NodeRSA();
    var public =
      "-----BEGIN PUBLIC KEY-----\n" +
      publicKey +
      "\n" +
      "-----END PUBLIC KEY-----";
    rsaNode.importKey(public, "pkcs8-public");
    var rsaCipherText = rsaNode.encrypt(aesKey, "base64");
    console.log("AES encrypted key: " + rsaCipherText);

    // 8. Publish new entry
    var ipnsEntry = {
      image_analysis_cid: aesCipherCID,
      image_hash: ipfsImageHash,
      key_rsa: rsaCipherText
    };
    var peerAddress = ipfsImageJSON.peer.addresses[0];
    var peerAddressesComps = peerAddress.split("/");
    var topic = peerAddressesComps[peerAddressesComps.length - 1];
    console.log("Publish to: " + topic);
    await ipfs.pubsub.publish(topic, Buffer.from(JSON.stringify(ipnsEntry)));
    return "IPFS success";
  } catch (error) {
    return error;
  }
}

function toUTF8Array(str) {
  var utf8 = [];
  for (var i=0; i < str.length; i++) {
      var charcode = str.charCodeAt(i);
      if (charcode < 0x80) utf8.push(charcode);
      else if (charcode < 0x800) {
          utf8.push(0xc0 | (charcode >> 6), 
                    0x80 | (charcode & 0x3f));
      }
      else if (charcode < 0xd800 || charcode >= 0xe000) {
          utf8.push(0xe0 | (charcode >> 12), 
                    0x80 | ((charcode>>6) & 0x3f), 
                    0x80 | (charcode & 0x3f));
      }
      // surrogate pair
      else {
          i++;
          charcode = ((charcode&0x3ff)<<10)|(str.charCodeAt(i)&0x3ff)
          utf8.push(0xf0 | (charcode >>18), 
                    0x80 | ((charcode>>12) & 0x3f), 
                    0x80 | ((charcode>>6) & 0x3f), 
                    0x80 | (charcode & 0x3f));
      }
  }
  return utf8;
}

function main() {
  processResource("QmUg8HT2ZHDU6wzsznVoecfQTjUEEQJAwMgDAbgu2kGKs3")
    .then(function(result) {
      console.log(result);
    })
    .catch(function(error) {
      console.log(error);
    });
}
main();