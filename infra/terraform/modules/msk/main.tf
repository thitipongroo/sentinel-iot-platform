resource "aws_security_group" "msk" {
  name        = "${var.name_prefix}-msk-sg"
  description = "Allow Kafka TLS from EKS nodes"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Kafka TLS"
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [var.eks_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-msk-sg" }
}

resource "aws_msk_configuration" "main" {
  name              = "${var.name_prefix}-msk-config"
  kafka_versions    = [var.kafka_version]
  server_properties = <<-EOT
    auto.create.topics.enable=false
    log.retention.hours=168
    num.partitions=3
    default.replication.factor=2
    min.insync.replicas=1
  EOT
}

resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.name_prefix}-kafka"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type  = var.instance_type
    client_subnets = slice(var.subnet_ids, 0, var.broker_count)
    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
    security_groups = [aws_security_group.msk.id]
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  tags = { Name = "${var.name_prefix}-kafka" }
}
