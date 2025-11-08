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

# Colors for output
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m # No Color

.PHONY: help build tag push deploy deploy-full logs status clean check-docker check-terraform dev

# Default target
help:
	@echo "$(GREEN)RapidPhotoUpload Deployment Makefile$(NC)"
	@echo ""
	@echo "Available targets:"
	@echo "  $(YELLOW)build$(NC)         - Build backend JAR and Docker image"
	@echo "  $(YELLOW)tag$(NC)           - Tag Docker image for ECR"
	@echo "  $(YELLOW)push$(NC)          - Push Docker image to ECR"
	@echo "  $(YELLOW)deploy$(NC)        - Deploy to ECS (force new deployment)"
	@echo "  $(YELLOW)deploy-full$(NC)   - Complete workflow: build → tag → push → deploy"
	@echo "  $(YELLOW)logs$(NC)          - Tail ECS task logs"
	@echo "  $(YELLOW)status$(NC)        - Show ECS service status"
	@echo "  $(YELLOW)clean$(NC)         - Clean Maven target directory"
	@echo ""
	@echo "Development targets:"
	@echo "  $(YELLOW)dev$(NC)           - Run frontend dev server (bun dev)"
	@echo ""
	@echo "Current configuration:"
	@echo "  Region:      $(AWS_REGION)"
	@echo "  ECR URL:     $(ECR_URL)"
	@echo "  ECS Cluster: $(ECS_CLUSTER)"
	@echo "  ECS Service: $(ECS_SERVICE)"
	@echo "  ALB URL:     $(ALB_URL)"

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

# Complete deployment workflow
deploy-full: build tag push deploy
	@echo ""
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)  Deployment Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@echo "Application URL: $(ALB_URL)"
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

# Clean Maven target directory
clean:
	@echo "$(YELLOW)Cleaning Maven target directory...$(NC)"
	cd backend && mvn clean
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
# Run frontend dev server (backend runs on AWS)
dev:
	@echo "$(YELLOW)Starting frontend dev server...$(NC)"
	@echo "$(GREEN)Frontend: http://localhost:5173$(NC)"
	@echo "$(GREEN)Backend:  $(ALB_URL)$(NC)"
	@echo ""
	cd web && bun dev
