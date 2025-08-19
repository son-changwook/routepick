# RoutePickr 인프라스트럭처 메인 설정
# AWS 리전: ap-northeast-2 (서울)
# 한국 사용자 최적화

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  # 원격 상태 저장 (S3 + DynamoDB)
  backend "s3" {
    bucket         = "routepick-terraform-state"
    key            = "terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "routepick-terraform-locks"
    encrypt        = true
  }
}

# AWS Provider 설정
provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "RoutePickr"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Owner       = "RoutePickr Team"
    }
  }
}

# Local Values
locals {
  name_prefix = "routepick-${var.environment}"
  
  common_tags = {
    Project     = "RoutePickr"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Data Sources
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}