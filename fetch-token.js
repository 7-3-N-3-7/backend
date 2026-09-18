const express = require('express');
const jwt = require('jsonwebtoken');
const forge = require('node-forge');
const { execSync } = require('child_process');

const app = express();
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

// Generate RSA key pair for signing JWTs
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

app.post('/oauth/v2/token', (req, res) => {
  const token = jwt.sign({ sub: 'admin-user', roles: ['admin'] }, privateKey, {
    algorithm: 'RS256',
    keyid: 'mock-key-1',
    expiresIn: '1h',
    issuer: 'http://127.0.0.1:8085'
  });
  res.json({ access_token: token, token_type: 'Bearer', expires_in: 3600 });
});

const PORT = 8085;
const server = app.listen(PORT, async () => {
  console.log(`Mock ZITADEL listening on port ${PORT}`);
  
  try {
    const token = jwt.sign({ sub: 'admin-user', roles: ['admin'] }, privateKey, {
      algorithm: 'RS256',
      keyid: 'mock-key-1',
      expiresIn: '1h',
      issuer: 'http://127.0.0.1:8085'
    });
    
    console.log("Successfully generated mock token. Running Newman tests...");
    
    const { spawn } = require('child_process');
    const child = spawn('npx', ['newman', 'run', 'collection.json', '--env-var', `admin_jwt_token=${token}`], { stdio: 'inherit', shell: true });
    
    child.on('close', (code) => {
      if (code === 0) {
        console.log("Tests completed successfully!");
        server.close();
        process.exit(0);
      } else {
        console.error("Test execution failed with code:", code);
        server.close();
        process.exit(1);
      }
    });
    
  } catch (error) {
    console.error("Test execution failed:", error.message);
    server.close();
    process.exit(1);
  }
});
