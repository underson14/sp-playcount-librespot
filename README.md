# sp-playcount-librespot
`sp-playcount-librespot` is a modification of `librespot-java` that serves as an enhancement of [Spotify-PlayCount](https://github.com/evilarceus/Spotify-PlayCount). This repository still needs to be optimized.

# Advantages vs. [Spotify-PlayCount](https://github.com/evilarceus/Spotify-PlayCount)
* Does NOT require the Spotify desktop app (woo)
* Lower CPU and memory usage (can be even lower once the code is fully changed)
* More information given in API response

## Requirements
* Java 8+
* Spotify Account (recommended to create another account)

## Installation
1. Compile this repository or [download an executable JAR](https://github.com/evilarceus/sp-playcount-librespot/releases/latest).
2. Run the JAR: `java -jar sp-playcount-librespot.jar <spotify_username> <spotify_password>`
    * You only need to provide your Spotify username and password once. After `creds.json` has been generated, the username and password are not required in the launch arguments.
3. Make any appropriate configuration changes in the generated `config.toml` file (see section below for config options).
4. Run the JAR: `java -jar sp-playcount-librespot.jar`

## Compiling (requires Maven)
1. Clone this repository: `git clone https://github.com/evilarceus/sp-playcount-librespot && cd sp-playcount-librespot`
2. Build with Maven: `mvn clean package`
3. Run JAR file: `java -jar ./core/target/librespot-core-jar-with-dependencies.jar <spotify_username> <spotify_password>`

## Configuration
A `config.toml` file is generated after you run the JAR for the first time. This file can be used to change the settings of the server. The settings are located at the very bottom of the file under `[server]`.
To reset the configuration, simply delete the file and run the JAR again.
```
[server]
port = 8080
endpoint = "/albumPlayCount"
enableHttps = false
httpsKs = ""
httpsKsPass = ""
```
| Option        | Description                                                              |
|---------------|--------------------------------------------------------------------------|
| `port`        | Selects what port to listen for HTTP requests on                         |
| `endpoint`    | Endpoint at which the user can send HTTP GET requests to the API         |
| `enableHttps` | If true, enables HTTPS support (requires certificate, see section below) |
| `httpsKs`     | Location to keystore with HTTPS certificate and key                      |
| `httpsKsPass` | Password to HTTPS keystore file (if applicable)                          |

### HTTPS Configuration
The server can be configured to use HTTPS. If you're using LetsEncrypt, use [this guide](https://www.wissel.net/blog/2018/03/letsencrypt-java-keystore.html) to create a keystore with the certificate.

Then, edit `config.toml`:
```
[server]
...
enableHttps = true
httpsKs = "<location of keystore file>"
httpsKsPass = "<keystore password (if applicable)>"
```
