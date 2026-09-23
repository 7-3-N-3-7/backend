const express = require('express');
const jwt = require('jsonwebtoken');
const forge = require('node-forge');

const app = express();
app.use(express.json());

const keypair = forge.pki.rsa.generateKeyPair({ bits: 2048, e: 0x10001 });
const privateKey = forge.pki.privateKeyToPem(keypair.privateKey);
const publicKey = forge.pki.publicKeyToPem(keypair.publicKey);

const getJwk = (pubPem) => {
  const pub = forge.pki.publicKeyFromPem(pubPem);
  const n = Buffer.from(pub.n.toByteArray()).toString('base64url');
  const e = Buffer.from(pub.e.toByteArray()).toString('base64url');
  return { kty: 'RSA', alg: 'RS256', use: 'sig', kid: 'mock-key-1', n, e };
};

app.get('/.well-known/openid-configuration', (req, res) => {
  res.json({
    issuer: 'http://127.0.0.1:8085',
    jwks_uri: 'http://127.0.0.1:8085/oauth/v2/keys',
    token_endpoint: 'http://127.0.0.1:8085/oauth/v2/token'
  });
});

app.get('/oauth/v2/keys', (req, res) => {
  res.json({ keys: [getJwk(publicKey)] });
});

const PORT = 8085;
app.listen(PORT, "127.0.0.1", () => {
  console.log(`Mock Keycloak Identity Provider listening on port ${PORT}`);
  
  const token = jwt.sign({ sub: 'admin-user', roles: ['admin'] }, privateKey, {
    algorithm: 'RS256',
    keyid: 'mock-key-1',
    expiresIn: '24h',
    issuer: 'http://127.0.0.1:8085'
  });
  
  console.log('\n--- YOUR TEST JWT TOKEN ---');
  console.log(token);
  console.log('---------------------------\n');
  console.log('Keep this terminal open while testing your API!');
});
