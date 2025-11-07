# ============================================================================
# VPC Configuration
# ============================================================================

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

# ============================================================================
# Internet Gateway
# ============================================================================

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

# ============================================================================
# Public Subnets
# ============================================================================

resource "aws_subnet" "public" {
  count = length(var.public_subnet_cidrs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-subnet-${count.index + 1}"
    Tier = "Public"
    AZ   = local.azs[count.index]
  }
}

# ============================================================================
# Private Subnets
# ============================================================================

resource "aws_subnet" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name = "${local.name_prefix}-private-subnet-${count.index + 1}"
    Tier = "Private"
    AZ   = local.azs[count.index]
  }
}

# ============================================================================
# Elastic IP for NAT Gateway
# ============================================================================

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-nat-eip"
  }

  depends_on = [aws_internet_gateway.main]
}

# ============================================================================
# NAT Gateway (in first public subnet for cost optimization)
# ============================================================================

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "${local.name_prefix}-nat-gateway"
  }

  depends_on = [aws_internet_gateway.main]
}

# ============================================================================
# Route Table for Public Subnets
# ============================================================================

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-public-rt"
    Tier = "Public"
  }
}

# Route to Internet Gateway
resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

# Associate public subnets with public route table
resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ============================================================================
# Route Table for Private Subnets
# ============================================================================

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-private-rt"
    Tier = "Private"
  }
}

# Route to NAT Gateway
resource "aws_route" "private_nat" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main.id
}

# Associate private subnets with private route table
resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ============================================================================
# Security Group: Application Load Balancer
# ============================================================================

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Security group for Application Load Balancer"
  vpc_id      = aws_vpc.main.id

  # Inbound HTTP from anywhere
  ingress {
    description = "HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Inbound HTTPS from anywhere (for future use)
  ingress {
    description = "HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Outbound to anywhere (required for ALB to forward to ECS tasks)
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-alb-sg"
  }
}

# ============================================================================
# Security Group: ECS Tasks
# ============================================================================

resource "aws_security_group" "ecs_tasks" {
  name        = "${local.name_prefix}-ecs-tasks-sg"
  description = "Security group for ECS tasks"
  vpc_id      = aws_vpc.main.id

  # Inbound from ALB on application port
  ingress {
    description     = "Allow inbound from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Outbound to internet (for pulling Docker images, accessing AWS services)
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-ecs-tasks-sg"
  }
}

# ============================================================================
# Security Group: RDS Database
# ============================================================================

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Security group for RDS PostgreSQL database"
  vpc_id      = aws_vpc.main.id

  # Inbound from ECS tasks on PostgreSQL port
  ingress {
    description     = "PostgreSQL from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  # No outbound rules needed for RDS (it doesn't initiate connections)
  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-rds-sg"
  }
}

# ============================================================================
# VPC Endpoints for Cost Optimization (Optional)
# ============================================================================

# Uncomment these if you want to avoid NAT Gateway data transfer costs
# by routing S3 and ECR traffic through VPC endpoints instead

# # S3 Gateway Endpoint (free)
# resource "aws_vpc_endpoint" "s3" {
#   vpc_id       = aws_vpc.main.id
#   service_name = "com.amazonaws.${var.aws_region}.s3"
#
#   route_table_ids = [aws_route_table.private.id]
#
#   tags = {
#     Name = "${local.name_prefix}-s3-endpoint"
#   }
# }

# # ECR API Interface Endpoint (charged per hour + data transfer)
# resource "aws_vpc_endpoint" "ecr_api" {
#   vpc_id              = aws_vpc.main.id
#   service_name        = "com.amazonaws.${var.aws_region}.ecr.api"
#   vpc_endpoint_type   = "Interface"
#   private_dns_enabled = true
#
#   subnet_ids         = aws_subnet.private[*].id
#   security_group_ids = [aws_security_group.vpc_endpoints.id]
#
#   tags = {
#     Name = "${local.name_prefix}-ecr-api-endpoint"
#   }
# }

# # ECR DKR Interface Endpoint (charged per hour + data transfer)
# resource "aws_vpc_endpoint" "ecr_dkr" {
#   vpc_id              = aws_vpc.main.id
#   service_name        = "com.amazonaws.${var.aws_region}.ecr.dkr"
#   vpc_endpoint_type   = "Interface"
#   private_dns_enabled = true
#
#   subnet_ids         = aws_subnet.private[*].id
#   security_group_ids = [aws_security_group.vpc_endpoints.id]
#
#   tags = {
#     Name = "${local.name_prefix}-ecr-dkr-endpoint"
#   }
# }

# # Security Group for VPC Endpoints
# resource "aws_security_group" "vpc_endpoints" {
#   name        = "${local.name_prefix}-vpc-endpoints-sg"
#   description = "Security group for VPC endpoints"
#   vpc_id      = aws_vpc.main.id
#
#   ingress {
#     description     = "HTTPS from ECS tasks"
#     from_port       = 443
#     to_port         = 443
#     protocol        = "tcp"
#     security_groups = [aws_security_group.ecs_tasks.id]
#   }
#
#   egress {
#     description = "Allow all outbound"
#     from_port   = 0
#     to_port     = 0
#     protocol    = "-1"
#     cidr_blocks = ["0.0.0.0/0"]
#   }
#
#   tags = {
#     Name = "${local.name_prefix}-vpc-endpoints-sg"
#   }
# }
