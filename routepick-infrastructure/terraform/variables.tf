# Terraform Variables for RoutePickr Infrastructure

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

# Database Configuration
variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage (GB)"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Database name"
  type        = string
  default     = "routepick"
}

variable "db_username" {
  description = "Database username"
  type        = string
  default     = "admin"
  sensitive   = true
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

# Redis Configuration
variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.t3.micro"
}

variable "redis_num_cache_nodes" {
  description = "Number of Redis cache nodes"
  type        = number
  default     = 1
}

# EC2 Configuration
variable "ec2_instance_type" {
  description = "EC2 instance type for application servers"
  type        = string
  default     = "t3.small"
}

variable "ec2_min_size" {
  description = "Minimum number of EC2 instances in ASG"
  type        = number
  default     = 2
}

variable "ec2_max_size" {
  description = "Maximum number of EC2 instances in ASG"
  type        = number
  default     = 10
}

variable "ec2_desired_capacity" {
  description = "Desired number of EC2 instances in ASG"
  type        = number
  default     = 2
}

# S3 Configuration
variable "s3_bucket_name" {
  description = "S3 bucket name for file uploads"
  type        = string
  default     = "routepick-uploads"
}

# SSL Certificate
variable "domain_name" {
  description = "Domain name for SSL certificate"
  type        = string
  default     = "routepick.com"
}

variable "certificate_arn" {
  description = "ACM certificate ARN (optional)"
  type        = string
  default     = ""
}

# Application Configuration
variable "app_name" {
  description = "Application name"
  type        = string
  default     = "routepick"
}

variable "app_port" {
  description = "Application port"
  type        = number
  default     = 8080
}

# 한국 특화 설정
variable "korea_settings" {
  description = "Korea-specific settings"
  type = object({
    timezone                = string
    default_language        = string
    social_login_providers  = list(string)
    gps_bounds = object({
      min_latitude  = number
      max_latitude  = number
      min_longitude = number
      max_longitude = number
    })
  })
  
  default = {
    timezone               = "Asia/Seoul"
    default_language       = "ko"
    social_login_providers = ["GOOGLE", "KAKAO", "NAVER", "FACEBOOK"]
    gps_bounds = {
      min_latitude  = 33.0
      max_latitude  = 38.6
      min_longitude = 124.0
      max_longitude = 132.0
    }
  }
}

# 태그 설정
variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}