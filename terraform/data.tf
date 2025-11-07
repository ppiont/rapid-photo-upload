# ============================================================================
# RDS Subnet Group
# ============================================================================

resource "aws_db_subnet_group" "main" {
  name        = "${local.name_prefix}-db-subnet-group"
  description = "Subnet group for RDS PostgreSQL database"
  subnet_ids  = aws_subnet.private[*].id

  tags = {
    Name = "${local.name_prefix}-db-subnet-group"
  }
}

# ============================================================================
# RDS Parameter Group
# ============================================================================

resource "aws_db_parameter_group" "postgres" {
  name        = "${local.name_prefix}-postgres-params"
  family      = "postgres17"
  description = "Custom parameter group for PostgreSQL 17"

  # Performance tuning for photo upload workload
  parameter {
    name         = "max_connections"
    value        = "100"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "shared_buffers"
    value        = "{DBInstanceClassMemory/32768}" # 25% of available memory
    apply_method = "pending-reboot"
  }

  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory/16384}" # 75% of available memory
  }

  parameter {
    name  = "work_mem"
    value = "4096" # 4MB per operation
  }

  parameter {
    name  = "maintenance_work_mem"
    value = "65536" # 64MB for maintenance
  }

  # Enable connection pooling optimization
  parameter {
    name  = "random_page_cost"
    value = "1.1" # Optimized for SSDs
  }

  tags = {
    Name = "${local.name_prefix}-postgres-params"
  }
}

# ============================================================================
# RDS PostgreSQL Instance
# ============================================================================

resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-db"

  # Engine Configuration
  engine               = "postgres"
  engine_version       = "17.6"
  instance_class       = var.db_instance_class
  parameter_group_name = aws_db_parameter_group.postgres.name

  # Storage Configuration
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  # Database Configuration
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password
  port     = 5432

  # Network Configuration
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # Multi-AZ and Availability
  multi_az               = false # Single-AZ for cost optimization (demo environment)
  availability_zone      = local.azs[0]

  # Backup Configuration
  backup_retention_period   = 7    # 7 days retention
  backup_window             = "03:00-04:00" # UTC time
  maintenance_window        = "mon:04:00-mon:05:00" # UTC time
  delete_automated_backups  = true
  skip_final_snapshot       = true # Set to false in production
  final_snapshot_identifier = "${local.name_prefix}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  # Performance Insights (optional, adds cost)
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  performance_insights_enabled    = false # Set to true for production monitoring
  # performance_insights_retention_period = 7 # Only if enabled

  # Monitoring
  monitoring_interval = 60 # Enhanced monitoring every 60 seconds
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  # Auto minor version upgrade
  auto_minor_version_upgrade = true

  # Deletion protection (IMPORTANT: Set to true in production)
  deletion_protection = false # Set to true in production

  # Tags are automatically applied via provider default_tags
  tags = {
    Name     = "${local.name_prefix}-db"
    Database = var.db_name
    Engine   = "PostgreSQL"
  }

  depends_on = [
    aws_db_subnet_group.main,
    aws_security_group.rds
  ]
}

# ============================================================================
# IAM Role for RDS Enhanced Monitoring
# ============================================================================

resource "aws_iam_role" "rds_monitoring" {
  name = "${local.name_prefix}-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-rds-monitoring-role"
  }
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
