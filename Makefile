# Makefile for RapidPhotoUpload Deployment
# Manages building, tagging, pushing, and deploying the backend to AWS ECS

# Variables
TERRAFORM_DIR := terraform
AWS_REGION := us-west-1
PLATFORM := linux/amd64
IMAGE_NAME := rapid-photo-upload
IMAGE_TAG := latest

# Terraform outputs (retrieved dynamically)
ECR_URL := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw ecr_repository_url 2>/dev/null || echo "")
ECS_CLUSTER := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw ecs_cluster_name 2>/dev/null || echo "")
ECS_SERVICE := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw ecs_service_name 2>/dev/null || echo "")
ALB_URL := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw alb_url 2>/dev/null || echo "")
WEB_BUCKET := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw web_bucket_name 2>/dev/null || echo "")
CLOUDFRONT_ID := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw cloudfront_distribution_id 2>/dev/null || echo "")
CLOUDFRONT_URL := $(shell terraform -chdir=$(TERRAFORM_DIR) output -raw cloudfront_url 2>/dev/null || echo "")

# Colors for output
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m # No Color

.PHONY: help build tag push deploy deploy-full deploy-web invalidate-cdn logs status clean check-docker check-terraform dev mobile-dev bastion-access

# Default target
help:
	@echo "$(GREEN)RapidPhotoUpload Deployment Makefile$(NC)"
	@echo ""
	@echo "$(YELLOW)Backend Deployment:$(NC)"
	@echo "  $(YELLOW)build$(NC)         - Build backend JAR and Docker image"
	@echo "  $(YELLOW)tag$(NC)           - Tag Docker image for ECR"
	@echo "  $(YELLOW)push$(NC)          - Push Docker image to ECR"
	@echo "  $(YELLOW)deploy$(NC)        - Deploy backend to ECS (force new deployment)"
	@echo "  $(YELLOW)deploy-full$(NC)   - Complete workflow: build → tag → push → deploy (backend + web)"
	@echo "  $(YELLOW)logs$(NC)          - Tail ECS task logs"
	@echo "  $(YELLOW)status$(NC)        - Show ECS service status"
	@echo ""
	@echo "$(YELLOW)Web Client Deployment:$(NC)"
	@echo "  $(YELLOW)deploy-web$(NC)    - Build and deploy web client to S3+CloudFront"
	@echo "  $(YELLOW)invalidate-cdn$(NC)- Invalidate CloudFront cache"
	@echo ""
	@echo "$(YELLOW)Development:$(NC)"
	@echo "  $(YELLOW)dev$(NC)           - Run web frontend dev server (bun dev)"
	@echo "  $(YELLOW)mobile-dev$(NC)    - Run mobile dev server (npx expo start)"
	@echo "  $(YELLOW)bastion-access$(NC) - Add bastion host access to RDS (run after terraform apply)"
	@echo "  $(YELLOW)clean$(NC)         - Clean build artifacts"
	@echo ""
	@echo "Current configuration:"
	@echo "  Region:         $(AWS_REGION)"
	@echo "  Backend:        $(ALB_URL)"
	@echo "  Web (CDN):      $(CLOUDFRONT_URL)"
	@echo "  ECS Cluster:    $(ECS_CLUSTER)"
	@echo "  ECS Service:    $(ECS_SERVICE)"

# Check if Docker daemon is running
check-docker:
	@echo "$(YELLOW)Checking Docker daemon...$(NC)"
	@docker info >/dev/null 2>&1 || (echo "$(RED)Error: Docker daemon is not running. Please start Docker Desktop.$(NC)" && exit 1)
	@echo "$(GREEN)✓ Docker is running$(NC)"

# Check if Terraform outputs are available
check-terraform:
	@echo "$(YELLOW)Checking Terraform outputs...$(NC)"
	@if [ -z "$(ECR_URL)" ]; then \
		echo "$(RED)Error: ECR repository URL not found. Have you run 'terraform apply'?$(NC)"; \
		exit 1; \
	fi
	@echo "$(GREEN)✓ Terraform outputs available$(NC)"

# Build backend JAR and Docker image
build: check-docker
	@echo "$(YELLOW)Building backend JAR with Maven...$(NC)"
	cd backend && mvn clean package -DskipTests
	@echo "$(GREEN)✓ JAR built successfully$(NC)"
	@echo ""
	@echo "$(YELLOW)Building Docker image for $(PLATFORM)...$(NC)"
	docker buildx build --platform $(PLATFORM) -t $(IMAGE_NAME):$(IMAGE_TAG) backend/
	@echo "$(GREEN)✓ Docker image built successfully$(NC)"

# Tag Docker image for ECR
tag: check-terraform
	@echo "$(YELLOW)Tagging Docker image for ECR...$(NC)"
	docker tag $(IMAGE_NAME):$(IMAGE_TAG) $(ECR_URL):$(IMAGE_TAG)
	@echo "$(GREEN)✓ Image tagged: $(ECR_URL):$(IMAGE_TAG)$(NC)"

# Push Docker image to ECR
push: check-terraform
	@echo "$(YELLOW)Logging in to ECR...$(NC)"
	aws ecr get-login-password --region $(AWS_REGION) | docker login --username AWS --password-stdin $(ECR_URL)
	@echo "$(GREEN)✓ Logged in to ECR$(NC)"
	@echo ""
	@echo "$(YELLOW)Pushing Docker image to ECR...$(NC)"
	docker push $(ECR_URL):$(IMAGE_TAG)
	@echo "$(GREEN)✓ Image pushed successfully$(NC)"

# Deploy to ECS (force new deployment)
deploy: check-terraform
	@echo "$(YELLOW)Deploying to ECS...$(NC)"
	@echo "  Cluster: $(ECS_CLUSTER)"
	@echo "  Service: $(ECS_SERVICE)"
	@echo ""
	aws ecs update-service \
		--cluster $(ECS_CLUSTER) \
		--service $(ECS_SERVICE) \
		--force-new-deployment \
		--region $(AWS_REGION) \
		>/dev/null
	@echo "$(GREEN)✓ Deployment initiated$(NC)"
	@echo ""
	@echo "Monitor deployment progress with: $(YELLOW)make status$(NC)"
	@echo "View logs with: $(YELLOW)make logs$(NC)"
	@echo ""
	@echo "Application URL: $(GREEN)$(ALB_URL)$(NC)"

# Deploy web client to S3 + CloudFront
deploy-web: check-terraform
	@echo "$(YELLOW)Building web client...$(NC)"
	cd web && npm run build
	@echo "$(GREEN)✓ Web client built successfully$(NC)"
	@echo ""
	@echo "$(YELLOW)Uploading files to S3 bucket: $(WEB_BUCKET)$(NC)"
	@# Upload all files except index.html with long-term cache
	aws s3 sync web/dist/ s3://$(WEB_BUCKET)/ \
		--delete \
		--cache-control "public, max-age=31536000, immutable" \
		--exclude "index.html" \
		--exclude "*.map"
	@echo ""
	@echo "$(YELLOW)Uploading index.html with no-cache policy...$(NC)"
	@# Upload index.html separately with no-cache to ensure updates propagate
	aws s3 cp web/dist/index.html s3://$(WEB_BUCKET)/index.html \
		--cache-control "public, max-age=0, must-revalidate"
	@echo "$(GREEN)✓ Files uploaded to S3$(NC)"
	@echo ""
	@echo "$(YELLOW)Invalidating CloudFront cache (ID: $(CLOUDFRONT_ID))...$(NC)"
	aws cloudfront create-invalidation \
		--distribution-id $(CLOUDFRONT_ID) \
		--paths "/*" \
		>/dev/null
	@echo "$(GREEN)✓ CloudFront invalidation created$(NC)"
	@echo ""
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)  Web Deployment Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@echo "Web URL: $(GREEN)$(CLOUDFRONT_URL)$(NC)"
	@echo ""
	@echo "$(YELLOW)Note:$(NC) CloudFront deployment may take 5-15 minutes to propagate globally."

# Invalidate CloudFront cache only
invalidate-cdn: check-terraform
	@echo "$(YELLOW)Invalidating CloudFront distribution $(CLOUDFRONT_ID)...$(NC)"
	aws cloudfront create-invalidation \
		--distribution-id $(CLOUDFRONT_ID) \
		--paths "/*"
	@echo "$(GREEN)✓ Invalidation created$(NC)"

# Complete deployment workflow (backend + web)
deploy-full: build tag push deploy deploy-web
	@echo ""
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)  Full Stack Deployment Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@echo "Backend API: $(GREEN)$(ALB_URL)$(NC)"
	@echo "Web Client:  $(GREEN)$(CLOUDFRONT_URL)$(NC)"
	@echo ""
	@echo "Next steps:"
	@echo "  1. Monitor status: $(YELLOW)make status$(NC)"
	@echo "  2. View logs:      $(YELLOW)make logs$(NC)"

# Tail ECS task logs
logs: check-terraform
	@echo "$(YELLOW)Tailing ECS logs...$(NC)"
	@LOG_GROUP=$$(terraform -chdir=$(TERRAFORM_DIR) output -raw cloudwatch_log_group 2>/dev/null); \
	if [ -z "$$LOG_GROUP" ]; then \
		echo "$(RED)Error: CloudWatch log group not found$(NC)"; \
		exit 1; \
	fi; \
	aws logs tail $$LOG_GROUP --follow --region $(AWS_REGION)

# Show ECS service status
status: check-terraform
	@echo "$(YELLOW)Fetching ECS service status...$(NC)"
	@echo ""
	@aws ecs describe-services \
		--cluster $(ECS_CLUSTER) \
		--services $(ECS_SERVICE) \
		--region $(AWS_REGION) \
		--query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount,Pending:pendingCount,Deployments:deployments[*].{Status:rolloutState,Running:runningCount,Desired:desiredCount,UpdatedAt:updatedAt}}' \
		--output table
	@echo ""
	@echo "Task details:"
	@TASK_ARN=$$(aws ecs list-tasks --cluster $(ECS_CLUSTER) --service-name $(ECS_SERVICE) --region $(AWS_REGION) --query 'taskArns[0]' --output text 2>/dev/null); \
	if [ "$$TASK_ARN" != "None" ] && [ -n "$$TASK_ARN" ]; then \
		aws ecs describe-tasks --cluster $(ECS_CLUSTER) --tasks $$TASK_ARN --region $(AWS_REGION) \
			--query 'tasks[0].{LastStatus:lastStatus,HealthStatus:healthStatus,CPU:cpu,Memory:memory,CreatedAt:createdAt}' \
			--output table; \
	else \
		echo "$(YELLOW)No tasks currently running$(NC)"; \
	fi

# Clean build artifacts
clean:
	@echo "$(YELLOW)Cleaning build artifacts...$(NC)"
	cd backend && mvn clean
	rm -rf web/dist
	@echo "$(GREEN)✓ Clean complete$(NC)"

# Build just the backend JAR (without Docker)
build-jar:
	@echo "$(YELLOW)Building backend JAR with Maven...$(NC)"
	cd backend && mvn clean package -DskipTests
	@echo "$(GREEN)✓ JAR built successfully$(NC)"

# Run tests
test:
	@echo "$(YELLOW)Running backend tests...$(NC)"
	cd backend && mvn test
	@echo "$(GREEN)✓ Tests complete$(NC)"

# Build Docker image only (without JAR rebuild)
build-docker: check-docker
	@echo "$(YELLOW)Building Docker image for $(PLATFORM)...$(NC)"
	docker buildx build --platform $(PLATFORM) -t $(IMAGE_NAME):$(IMAGE_TAG) backend/
	@echo "$(GREEN)✓ Docker image built successfully$(NC)"

# Quick deploy (assumes image is already built)
quick-deploy: tag push deploy
	@echo "$(GREEN)Quick deployment complete!$(NC)"

# Development targets
# Run web frontend dev server (backend runs on AWS)
dev:
	@echo "$(YELLOW)Starting web frontend dev server...$(NC)"
	@echo "$(GREEN)Frontend: http://localhost:5173$(NC)"
	@echo "$(GREEN)Backend:  $(ALB_URL)$(NC)"
	@echo ""
	cd web && bun dev

# Run mobile dev server (backend runs on AWS)
mobile-dev:
	@echo "$(YELLOW)Starting mobile dev server...$(NC)"
	@echo "$(GREEN)Mobile:  Expo DevTools$(NC)"
	@echo "$(GREEN)Backend: $(ALB_URL)$(NC)"
	@echo ""
	cd mobile && bunx expo start

# Add bastion host access to RDS security group
# Note: This is not managed by Terraform as the bastion host is external infrastructure
bastion-access:
	@echo "$(YELLOW)Adding bastion host access to RDS security group...$(NC)"
	@bash scripts/add-bastion-rds-rule.sh
	@echo "$(GREEN)✓ Bastion access configured$(NC)"
