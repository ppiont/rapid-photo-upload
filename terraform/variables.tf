variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-west-1"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "rpu-pp"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "demo"
}

variable "db_username" {
  description = "Master username for RDS database"
  type        = string
  default     = "photoadmin"
  sensitive   = true
}

variable "db_password" {
  description = "Master password for RDS database"
  type        = string
  sensitive   = true
}

variable "db_name" {
  description = "Name of the database to create"
  type        = string
  default     = "photoupload"
}

variable "container_image" {
  description = "Docker image URL for ECS tasks"
  type        = string
  default     = "placeholder"
}

variable "jwt_secret" {
  description = "Secret key for JWT token generation"
  type        = string
  sensitive   = true
}

variable "availability_zones" {
  description = "Availability zones for multi-AZ deployment"
  type        = list(string)
  default     = ["us-west-1a", "us-west-1c"]
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "ecs_task_cpu" {
  description = "CPU units for ECS task (1024 = 1 vCPU)"
  type        = number
  default     = 1024
}

variable "ecs_task_memory" {
  description = "Memory for ECS task in MB"
  type        = number
  default     = 2048
}

variable "ecs_desired_count" {
  description = "Desired number of ECS tasks"
  type        = number
  default     = 2
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage for RDS in GB"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum storage for RDS autoscaling in GB"
  type        = number
  default     = 50
}
