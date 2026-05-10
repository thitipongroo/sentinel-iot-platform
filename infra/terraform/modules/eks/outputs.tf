output "cluster_name" {
  value = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.main.endpoint
}

output "cluster_ca_certificate" {
  value = aws_eks_cluster.main.certificate_authority[0].data
}

output "node_security_group_id" {
  value = aws_security_group.node.id
}

output "oidc_provider_arn" {
  description = "OIDC provider ARN for IRSA role trust policies"
  value       = aws_iam_openid_connect_provider.main.arn
}

output "oidc_provider" {
  description = "OIDC provider URL (without https://) for Condition keys"
  value       = replace(aws_eks_cluster.main.identity[0].oidc[0].issuer, "https://", "")
}
