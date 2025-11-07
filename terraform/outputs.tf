# Network Outputs
output "vpc_id" {
  description = "ID of the VPC"
  value       = try(aws_vpc.main.id, null)
}

output "public_subnet_ids" {
  description = "IDs of public subnets"
  value       = try(aws_subnet.public[*].id, [])
}

output "private_subnet_ids" {
  description = "IDs of private subnets"
  value       = try(aws_subnet.private[*].id, [])
}

# Database Outputs
output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = try(aws_db_instance.main.endpoint, null)
  sensitive   = true
}

output "rds_database_name" {
  description = "RDS database name"
  value       = try(aws_db_instance.main.db_name, null)
}

# Storage Outputs
output "s3_bucket_name" {
  description = "S3 bucket name for photos"
  value       = try(aws_s3_bucket.photos.id, null)
}

output "s3_bucket_arn" {
  description = "S3 bucket ARN"
  value       = try(aws_s3_bucket.photos.arn, null)
}

# Compute Outputs
output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = try(aws_lb.main.dns_name, null)
}

output "alb_url" {
  description = "URL of the Application Load Balancer"
  value       = try("http://${aws_lb.main.dns_name}", null)
}

output "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  value       = try(aws_ecs_cluster.main.name, null)
}

output "ecs_service_name" {
  description = "Name of the ECS service"
  value       = try(aws_ecs_service.main.name, null)
}

output "ecr_repository_url" {
  description = "URL of the ECR repository"
  value       = try(aws_ecr_repository.main.repository_url, null)
}

output "cloudwatch_log_group" {
  description = "CloudWatch log group name"
  value       = try(aws_cloudwatch_log_group.ecs.name, null)
}

# Summary Output
output "deployment_summary" {
  description = "Summary of deployed resources"
  value = {
    application_url = try("http://${aws_lb.main.dns_name}", "Not deployed")
    s3_bucket      = try(aws_s3_bucket.photos.id, "Not deployed")
    database       = try(aws_db_instance.main.endpoint, "Not deployed")
    ecs_cluster    = try(aws_ecs_cluster.main.name, "Not deployed")
    region         = var.aws_region
    environment    = var.environment
  }
}
