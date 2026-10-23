# user-service

Students, instructors, and login. Listens on port 8080. Swagger UI is at <http://localhost:8080/q/swagger-ui/>.

## Default admin

On startup, if the account table is empty, the service inserts one admin:

- email: `admin@driveplusplus.com`
- password: `testPassword`

Login:

```shell
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"admin@driveplusplus.com\",\"password\":\"testPassword\"}"
```

A successful login returns a JWT.

## JWT signing key

Login signs the token with `smallrye.jwt.sign.key.location=privateKey.pem`. That file must be a PKCS#8 PEM private key at `src/main/resources/privateKey.pem`. `*.pem` is gitignored, so a new checkout does not have it. Without the file, login returns 500 (`MalformedURLException: no protocol: privateKey.pem`).

Generate one, then rebuild the service:

```shell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out src/main/resources/privateKey.pem
```

Run that command from the `user-service` directory. For the Docker image, rebuild after the file exists:

```shell
./gradlew :user-service:quarkusBuild -x test
docker compose -f local/docker-compose.services.yml up -d --build user-service
```
