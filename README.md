# hello-service

A Spring Boot 4.1.1 web application with a `/hello` endpoint, containerized with Docker and deployed via a GitOps pipeline using GitHub Actions and ArgoCD.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Application | Spring Boot 4.1.1, Java 17, Thymeleaf |
| Build | Maven |
| Container | Docker (eclipse-temurin:17-jre) |
| Registry | JFrog Artifactory |
| CI | GitHub Actions |
| CD | ArgoCD |
| Runtime | Kubernetes (minikube) |

---

## Project Structure

```
hello-service/
├── src/
│   ├── main/
│   │   ├── java/com/greeting/
│   │   │   ├── HelloServiceApplication.java
│   │   │   └── HelloController.java
│   │   └── resources/
│   │       ├── templates/hello.html
│   │       └── application.properties
│   └── test/java/com/greeting/
│       └── HelloControllerTest.java
├── k8s/
│   ├── deployment.yaml          # Kubernetes Deployment (image tag updated by CI)
│   └── service.yaml             # NodePort Service on port 30080
├── argocd/
│   └── application.yaml         # ArgoCD Application manifest
├── .github/
│   └── workflows/
│       └── ci.yml               # GitHub Actions pipeline
├── Dockerfile
└── pom.xml
```

---

## Application

### Endpoint

```
GET /hello?name={name}
```

Returns an HTML page with a greeting. The `name` parameter is optional and defaults to `World`.

**Example:**
```
http://localhost:8080/hello
http://localhost:8080/hello?name=Jasjit
```

### Running locally

```bash
mvn spring-boot:run
```

### Running tests

```bash
mvn test
```

---

## Docker

### Build

```bash
docker build -t hello-service:1.0.0 .
```

### Run

```bash
docker run -p 8080:8080 hello-service:1.0.0
```

---

## CI/CD Pipeline Flow

```
Developer pushes to master
        │
        ▼
┌──────────────────┐
│  build-and-test  │  mvn test + mvn package
└────────┬─────────┘
         │
         ▼
┌────────────────────────┐
│  docker-build-push     │  Build image, tag as {version}_{sha7}_{timestamp}
│                        │  Push to JFrog (dmp-docker-development.repo.*)
│                        │  Smoke test via docker run + curl /hello
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  update-manifest       │  sed: update image tag in k8s/deployment.yaml
│                        │  git commit + push back to master
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│  ArgoCD (minikube)     │  Detects new commit → syncs → rolling update
└────────────────────────┘
```

### Image tag format

Matches the project's existing Jenkins convention:

```
1.0.0_abc1234_2026-09-24_143022
  │       │         │
  │       │         └─ UTC timestamp (YYYY-MM-DD_HHmmss)
  │       └─────────── first 7 chars of git SHA
  └─────────────────── app version from pom.xml
```

### Jobs

| Job | Trigger | What it does |
|---|---|---|
| `build-and-test` | push + PR | Compiles, runs tests, uploads jar |
| `docker-build-push` | push to master only | Builds + pushes image to JFrog, smoke tests |
| `update-manifest` | push to master only | Updates `k8s/deployment.yaml` and commits back |

Pull requests only run `build-and-test` — no image push.

---

## GitHub Actions Setup

### Required Secrets

Go to: **GitHub repo → Settings → Secrets and variables → Actions**

| Secret | Description |
|---|---|
| `JFROG_USERNAME` | JFrog Artifactory username |
| `JFROG_PASSWORD` | JFrog Artifactory password or API token |
| `GIT_TOKEN` | GitHub PAT with `repo` scope (for the manifest commit-back step) |

### Required Permission

Go to: **Settings → Actions → General → Workflow permissions** → enable **Read and write permissions**.

---

## ArgoCD Setup (minikube)

### 1. Create imagePullSecret

ArgoCD/Kubernetes needs credentials to pull the image from JFrog:

```bash
kubectl create secret docker-registry jfrog-registry-secret \
  --docker-server=dmp-docker-development.pub-repo.prod.us-west-2.aws.fico.com \
  --docker-username=YOUR_JFROG_USERNAME \
  --docker-password=YOUR_JFROG_PASSWORD \
  --namespace=default
```

### 2. (Private repo only) Register the Git repo with ArgoCD

```bash
argocd repo add https://github.com/YOUR_USERNAME/hello-service \
  --username YOUR_USERNAME \
  --password YOUR_GIT_TOKEN
```

Skip this step if the repository is public.

### 3. Apply the ArgoCD Application

Update `argocd/application.yaml` — replace `YOUR_GITHUB_USERNAME` with your actual username — then apply:

```bash
kubectl apply -f argocd/application.yaml
```

ArgoCD will immediately sync and deploy. After that, every CI run that updates `k8s/deployment.yaml` triggers an automatic sync.

### 4. Access the app

```bash
minikube service hello-service --url
```

Or directly via NodePort:

```bash
curl http://$(minikube ip):30080/hello
```

---

## Local CI Simulation (nektos/act)

Test the pipeline locally without pushing to GitHub:

```bash
# Run only build-and-test (simulates a pull_request event)
act pull_request

# Run the full pipeline (simulates a push to master)
act push \
  -s JFROG_USERNAME=youruser \
  -s JFROG_PASSWORD=yourpassword \
  -s GIT_TOKEN=yourtoken
```
