# RapidPhotoUpload

High-performance photo upload system demonstrating DDD, CQRS, and Vertical Slice Architecture. Supports 100 concurrent uploads per user with direct S3 uploads via pre-signed URLs.

## Architecture

- **Backend:** Spring Boot 3.5.7 + Java 25 (DDD/CQRS/VSA)
- **Infrastructure:** AWS (ECS Fargate, RDS PostgreSQL, S3, ALB)
- **Frontend:** React TypeScript (web) + React Native (mobile)
- **IaC:** Terraform

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker Desktop
- AWS CLI configured
- Terraform 1.13+

### Local Development

```bash
# Backend
cd backend
./mvnw spring-boot:run

# Frontend (when implemented)
cd web
npm install && npm run dev
```

## Deployment to AWS

### 1. Provision Infrastructure

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

This creates:
- VPC with public/private subnets
- RDS PostgreSQL 17.6 database
- S3 bucket for photos
- ECS Fargate cluster
- Application Load Balancer
- ECR repository

### 2. Build and Deploy Backend

#### Step 1: Get ECR Repository URL

```bash
cd terraform
ECR_URL=$(terraform output -raw ecr_repository_url)
echo $ECR_URL
# Output: 971422717446.dkr.ecr.us-west-1.amazonaws.com/rpu-pp-demo
```

#### Step 2: Authenticate Docker with ECR

```bash
aws ecr get-login-password --region us-west-1 | \
  docker login --username AWS --password-stdin $ECR_URL
```

Expected output:
```
Login Succeeded
```

#### Step 3: Build Docker Image for AMD64 Platform

**Important:** If you're on Apple Silicon (M1/M2/M3), you MUST use `--platform linux/amd64`:

```bash
cd ../backend

# Build for AMD64 (required for ECS Fargate)
docker buildx build --platform linux/amd64 \
  -t rapid-photo-upload:latest .
```

Build time: ~3-5 minutes (cross-compilation on ARM Macs takes longer)

#### Step 4: Tag Image for ECR

```bash
docker tag rapid-photo-upload:latest $ECR_URL:latest
```

#### Step 5: Push Image to ECR

```bash
docker push $ECR_URL:latest
```

Upload time: ~2-5 minutes (447MB image)

Progress output:
```
The push refers to repository [971422717446.dkr.ecr.us-west-1.amazonaws.com/rpu-pp-demo]
latest: digest: sha256:... size: 1234
```

#### Step 6: Deploy to ECS

Force ECS to pull the new image and redeploy:

```bash
aws ecs update-service \
  --cluster rpu-pp-demo-cluster \
  --service rpu-pp-demo-service \
  --force-new-deployment \
  --region us-west-1
```

#### Step 7: Monitor Deployment

Watch the deployment progress:

```bash
aws ecs describe-services \
  --cluster rpu-pp-demo-cluster \
  --services rpu-pp-demo-service \
  --region us-west-1 \
  --query 'services[0].deployments' \
  --output table
```

Look for:
- `runningCount: 2` (both tasks running)
- `desiredCount: 2`
- `rolloutState: COMPLETED`

Deployment typically takes 3-5 minutes.

#### Step 8: Get Application URL

```bash
cd ../terraform
ALB_URL=$(terraform output -raw alb_url)
echo "Application URL: http://$ALB_URL"
```

#### Step 9: Verify Health Endpoint

```bash
curl http://$ALB_URL/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### 3. Test API Endpoints

#### Register a New User

```bash
curl -X POST http://$ALB_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "fullName": "Test User"
  }'
```

Response:
```json
{
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "email": "test@example.com",
  "fullName": "Test User",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### Login

```bash
curl -X POST http://$ALB_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

#### Initialize Upload (requires JWT token)

```bash
TOKEN="<your-jwt-token-from-register-or-login>"

curl -X POST http://$ALB_URL/api/upload/initialize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "photos": [
      {
        "filename": "photo1.jpg",
        "fileSizeBytes": 2097152,
        "mimeType": "image/jpeg"
      },
      {
        "filename": "photo2.jpg",
        "fileSizeBytes": 1548576,
        "mimeType": "image/jpeg"
      }
    ]
  }'
```

Response:
```json
{
  "jobId": "456e7890-e89b-12d3-a456-426614174001",
  "totalPhotos": 2,
  "photos": [
    {
      "photoId": "789e0123-e89b-12d3-a456-426614174002",
      "filename": "photo1.jpg",
      "uploadUrl": "https://rpu-pp-demo-photos-971422717446.s3.us-west-1.amazonaws.com/photos/..."
    }
  ]
}
```

## Troubleshooting

### Issue: CannotPullContainerError - Platform Mismatch

**Error:**
```
CannotPullContainerError: pull image manifest has been retried 7 time(s):
image Manifest does not contain descriptor matching platform 'linux/amd64'
```

**Cause:** You built the Docker image on Apple Silicon (ARM64) but ECS Fargate requires AMD64.

**Solution:** Rebuild with platform flag:
```bash
docker buildx build --platform linux/amd64 -t rapid-photo-upload:latest .
```

### Issue: Docker login to ECR fails

**Error:**
```
Error saving credentials: error storing credentials
```

**Solution:** Ensure AWS CLI is configured:
```bash
aws configure
# Enter your AWS Access Key ID, Secret Access Key, and region (us-west-1)
```

### Issue: ECS tasks keep stopping

**Check CloudWatch logs:**
```bash
aws logs tail /ecs/rpu-pp-demo --follow --region us-west-1
```

Common issues:
- Database connection failure (check RDS security group)
- Missing environment variables (JWT secret, S3 bucket name)
- Health check failing (ensure `/actuator/health` returns 200)

### Issue: Health check returns 503

**Possible causes:**
1. Database migrations not complete (check logs for Flyway errors)
2. Database connection pool exhausted
3. Application startup not complete (wait 30-60 seconds)

**Check RDS connectivity from ECS:**
```bash
# Get RDS endpoint
cd terraform
terraform output rds_endpoint
```

## Environment Variables

The following environment variables are injected by Terraform into ECS tasks:

- `SPRING_DATASOURCE_URL`: RDS PostgreSQL connection string
- `SPRING_DATASOURCE_USERNAME`: Database username (photoadmin)
- `SPRING_DATASOURCE_PASSWORD`: Database password (from Secrets Manager)
- `AWS_S3_BUCKET_NAME`: S3 bucket name for photos
- `AWS_REGION`: us-west-1
- `JWT_SECRET`: Secret key for JWT signing (must be set manually)

## CI/CD Pipeline (Future)

```yaml
# .github/workflows/deploy.yml
name: Deploy to ECS
on:
  push:
    branches: [main]
jobs:
  deploy:
    steps:
      - Build Docker image
      - Push to ECR
      - Update ECS service
```

## Cost Optimization

To minimize AWS costs when not in use:

```bash
# Scale down to 0 tasks
aws ecs update-service \
  --cluster rpu-pp-demo-cluster \
  --service rpu-pp-demo-service \
  --desired-count 0 \
  --region us-west-1

# Stop RDS instance
aws rds stop-db-instance \
  --db-instance-identifier rpu-pp-demo-db \
  --region us-west-1

# Or destroy everything
cd terraform
terraform destroy
```

## Project Structure

```
rapid-photo-upload/
├── backend/          # Spring Boot application
│   ├── src/
│   │   ├── main/java/com/demo/photoupload/
│   │   │   ├── domain/         # DDD aggregates, value objects
│   │   │   ├── application/    # CQRS handlers
│   │   │   ├── infrastructure/ # JPA, S3 adapters
│   │   │   └── web/            # REST controllers
│   │   └── resources/
│   │       └── db/migration/   # Flyway SQL scripts
│   ├── pom.xml
│   └── Dockerfile
├── terraform/        # Infrastructure as Code
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── vpc.tf
│   ├── rds.tf
│   ├── s3.tf
│   ├── ecs.tf
│   └── alb.tf
├── web/              # React TypeScript web client (future)
├── mobile/           # React Native mobile app (future)
└── README.md         # This file
```

## Architecture Details

See [CLAUDE.md](./CLAUDE.md) for comprehensive architecture documentation, including:
- Domain-Driven Design patterns
- CQRS implementation
- Direct S3 upload flow
- Database schema
- Performance optimization strategies

## License

MIT