resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name_prefix}-redis-subnets"
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis-sg"
  description = "Allow Redis from EKS nodes"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.eks_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-redis-sg" }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${var.name_prefix}-redis"
  description          = "Sentinel IoT Redis — pub/sub + session cache"

  node_type               = var.node_type
  port                    = 6379
  num_cache_clusters      = 2
  automatic_failover_enabled = true
  multi_az_enabled        = true
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  subnet_group_name       = aws_elasticache_subnet_group.main.name
  security_group_ids      = [aws_security_group.redis.id]
  snapshot_retention_limit = 1

  tags = { Name = "${var.name_prefix}-redis" }
}
