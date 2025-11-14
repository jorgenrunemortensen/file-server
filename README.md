# file-server
A simple general purpose fileserver.

# Building a Docker Image
This project supports two ways of building a Docker image for the file-server:

1. Full build inside Docker using Dockerfile
2. Runtime-only image using Dockerfile.runtime after building the JAR locally

Choose the method that fits your workflow.
## 1. Building the Full Image (Docker Builds the JAR)
Use this method when you want Docker to run Gradle and build the full application, including shared modules located in the parent directory (e.g. ../common).

> **Important:**<br/>
You must run the build from the parent folder of file-server, because the Dockerfile expects access to both file-server/ and the shared modules above it.

From the parent directory:
```bash
docker build -f file-server/Dockerfile -t file-server:1.0 .
```

This will:
* use the Dockerfile inside file-server/
* run Gradle inside Docker to build the project
* produce a runnable Docker image tagged as file-server:1.0

Run the image:
```docker run -p 8085:8085 file-server:1.0```
---
## 2. Building a Runtime-Only Image (Faster Development)
Use this method if you prefer to compile the JAR locally and create a lightweight runtime image.

First build the JAR using Gradle:
```bash
cd file-server
./gradlew clean bootJar -x test
```
Then build the runtime image:
``` bash
docker build -f Dockerfile.runtime -t file-server:1.0 .
```

This image:
* does not run Gradle inside Docker
* only copies the built JAR into a slim runtime image
* is significantly faster to build, especially in CI/CD

Run it:
```bash
docker run -p 8085:8085 file-server:1.0
```
---
## Listing the Built Image
To view your local Docker images:
```bash
docker images
```

You should see something like:
```nginx
REPOSITORY       TAG       IMAGE ID       SIZE
file-server      1.0       abc123def456   250MB
```