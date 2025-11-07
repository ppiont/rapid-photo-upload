# ============================================================================
# S3 Bucket for Photo Storage
# ============================================================================

resource "aws_s3_bucket" "photos" {
  bucket = "${local.name_prefix}-photos-v2-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name    = "${local.name_prefix}-photos"
    Purpose = "PhotoStorage"
  }
}

# ============================================================================
# S3 Bucket Versioning (Disabled for cost optimization)
# ============================================================================

resource "aws_s3_bucket_versioning" "photos" {
  bucket = aws_s3_bucket.photos.id

  versioning_configuration {
    status = "Disabled" # Enable in production for data protection
  }
}

# ============================================================================
# S3 Bucket Public Access Block (All Blocked)
# ============================================================================

resource "aws_s3_bucket_public_access_block" "photos" {
  bucket = aws_s3_bucket.photos.id

  # Block all public access
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ============================================================================
# S3 Bucket CORS Configuration (Required for Direct Uploads)
# ============================================================================

resource "aws_s3_bucket_cors_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "HEAD"]
    allowed_origins = ["*"] # Restrict to specific domains in production
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

# ============================================================================
# S3 Bucket Encryption (Server-Side Encryption with S3-Managed Keys)
# ============================================================================

resource "aws_s3_bucket_server_side_encryption_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # S3-managed keys (no additional cost)
    }
    bucket_key_enabled = true # Reduces KMS request costs if using KMS
  }
}

# ============================================================================
# S3 Bucket Lifecycle Policy (Optional - Cost Optimization)
# ============================================================================

resource "aws_s3_bucket_lifecycle_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  # Transition old photos to cheaper storage classes
  rule {
    id     = "transition-to-ia"
    status = "Enabled"

    transition {
      days          = 90
      storage_class = "STANDARD_IA" # Infrequent Access after 90 days
    }

    transition {
      days          = 180
      storage_class = "GLACIER_IR" # Glacier Instant Retrieval after 180 days
    }
  }

  # Delete incomplete multipart uploads after 7 days
  rule {
    id     = "cleanup-incomplete-uploads"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  # Optional: Auto-delete old test photos in demo environment
  # Uncomment for demo/dev environments
  # rule {
  #   id     = "delete-old-photos"
  #   status = "Enabled"
  #
  #   expiration {
  #     days = 30 # Delete photos older than 30 days
  #   }
  # }
}

# ============================================================================
# S3 Bucket Logging (Optional - for audit trail)
# ============================================================================

# Uncomment if you want to enable access logging
# resource "aws_s3_bucket" "logs" {
#   bucket = "${local.name_prefix}-logs-${data.aws_caller_identity.current.account_id}"
#
#   tags = {
#     Name    = "${local.name_prefix}-logs"
#     Purpose = "AccessLogs"
#   }
# }

# resource "aws_s3_bucket_logging" "photos" {
#   bucket = aws_s3_bucket.photos.id
#
#   target_bucket = aws_s3_bucket.logs.id
#   target_prefix = "s3-access-logs/"
# }

# ============================================================================
# S3 Bucket Ownership Controls
# ============================================================================

resource "aws_s3_bucket_ownership_controls" "photos" {
  bucket = aws_s3_bucket.photos.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}
