output "bootstrap_brokers" {
  description = "Plaintext bootstrap brokers string for Spring Kafka config"
  value       = aws_msk_cluster.main.bootstrap_brokers
}

output "cluster_arn" {
  value = aws_msk_cluster.main.arn
}
