# Local values for resource naming and tagging
locals {
  # Resource naming convention: {project_name}-{environment}-{resource_type}
  name_prefix = "${var.project_name}-${var.environment}"

  # Common tags applied to all resources via provider default_tags
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Owner       = "DevOps"
    CostCenter  = "Engineering"
    Application = "RapidPhotoUpload"
  }

  # Availability zones
  azs = var.availability_zones
}

# Data source to get current AWS account ID
data "aws_caller_identity" "current" {}

# Data source to get current AWS region
data "aws_region" "current" {}
