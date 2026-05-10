output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = module.eks.cluster_endpoint
  sensitive   = true
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (host:port)"
  value       = module.rds.endpoint
}

output "elasticache_endpoint" {
  description = "ElastiCache Redis primary endpoint"
  value       = module.elasticache.primary_endpoint
}

output "msk_bootstrap_brokers" {
  description = "MSK plaintext bootstrap brokers string"
  value       = module.msk.bootstrap_brokers
}

output "secrets_manager_arn" {
  description = "ARN of the Secrets Manager secret for External Secrets Operator"
  value       = aws_secretsmanager_secret.sentinel.arn
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "backup_s3_bucket" {
  description = "S3 bucket for Velero + pg_dump backups"
  value       = aws_s3_bucket.backup.id
}

output "velero_iam_role_arn" {
  value = aws_iam_role.velero.arn
}
