# hello-service

A Spring Boot 4.1.1 web application with a `/hello` endpoint, containerized with Docker and deployed via a GitOps pipeline using GitHub Actions and ArgoCD.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Application | Spring Boot 4.1.1, Java 17, Thymeleaf |
| Build | Maven |
| Container | Docker (eclipse-temurin:17-jre) |
| Registry | Dockerhub |
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
│                        │  Push to Dockerhub
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
| `docker-build-push` | push to master only | Builds + pushes image to dockerhub, smoke tests |
| `update-manifest` | push to master only | Updates `k8s/deployment.yaml` and commits back |

Pull requests only run `build-and-test` — no image push.

---

## GitHub Actions Setup

### Required Secrets

Go to: **GitHub repo → Settings → Secrets and variables → Actions**

| Secret | Description |
|---|---|
| `GIT_TOKEN` | GitHub PAT with `repo` scope (for the manifest commit-back step) |

### Required Permission

Go to: **Settings → Actions → General → Workflow permissions** → enable **Read and write permissions**.

---

## ArgoCD Setup (minikube)

### 1. (Private repo only) Register the Git repo with ArgoCD

```bash
argocd repo add https://github.com/YOUR_USERNAME/hello-service \
  --username YOUR_USERNAME \
  --password YOUR_GIT_TOKEN
```

Skip this step if the repository is public.

### 2. Apply the ArgoCD Application

Update `argocd/application.yaml` — replace `YOUR_GITHUB_USERNAME` with your actual username — then apply:

```bash
kubectl apply -f argocd/application.yaml
```

ArgoCD will immediately sync and deploy. After that, every CI run that updates `k8s/deployment.yaml` triggers an automatic sync.

### 3. Access the app

```bash
minikube service hello-service --url
```

Or directly via NodePort:

```bash
curl http://$(minikube ip):30080/hello
```

---

## Running Everything Locally

---

### 1. Start minikube

```bash
minikube start
```

---

### 2. Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml --server-side

# Wait for ArgoCD to be ready
kubectl wait --for=condition=available --timeout=180s deployment/argocd-server -n argocd
```

---

### 3. Deploy the ArgoCD Application

```bash
kubectl apply -f k8s/argocd-app.yaml
```

ArgoCD will immediately sync `k8s/deployment.yaml` from the GitHub repo and deploy hello-service to minikube.

Check status:

```bash
kubectl get application hello-service -n argocd
```

---

### 4. Access the app

```bash
# Port-forward the service
kubectl port-forward svc/hello-service 9090:80 -n default
```

Then open: `http://localhost:9090/hello`

---

### 5. Access the ArgoCD UI

```bash
# Port-forward the ArgoCD server
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Get the initial admin password
kubectl get secret argocd-initial-admin-secret -n argocd \
  -o jsonpath="{.data.password}" | base64 -d
```

Open `https://localhost:8080` and log in with username `admin`.

---

### 6. Run CI locally with act

Create a `.secrets` file in the project root (already gitignored):

```
DOCKERHUB_USERNAME=your_dockerhub_username
DOCKERHUB_TOKEN=your_dockerhub_token
GIT_TOKEN=your_github_pat
```

Then run the full pipeline:

```bash
act --secret-file .secrets
```

This simulates a push to master: builds the jar, builds and pushes the Docker image, updates `k8s/deployment.yaml` with the new image tag, and pushes the manifest back to GitHub. ArgoCD will detect the change within 3 minutes and roll out the update to minikube.

To force an immediate sync without waiting:

```bash
kubectl annotate application hello-service -n argocd \
  argocd.argoproj.io/refresh=hard --overwrite
```
