terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.13"
    }
  }

  backend "s3" {
    # Configure via -backend-config or environment variables:
    # TF_VAR_bucket, TF_VAR_key, TF_VAR_region, TF_VAR_dynamodb_table
    bucket         = "sentinel-iot-tfstate"
    key            = "sentinel-iot/terraform.tfstate"
    region         = "ap-southeast-1"
    dynamodb_table = "sentinel-iot-tflock"
    encrypt        = true
  }
}
