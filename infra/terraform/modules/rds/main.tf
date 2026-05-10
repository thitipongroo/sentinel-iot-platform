resource "aws_db_subnet_group" "main" {
  name       = "${var.name_prefix}-rds-subnets"
  subnet_ids = var.subnet_ids
  tags       = { Name = "${var.name_prefix}-rds-subnet-group" }
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds-sg"
  description = "Allow PostgreSQL from EKS nodes"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.eks_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-rds-sg" }
}

resource "aws_db_instance" "main" {
  identifier              = "${var.name_prefix}-postgres"
  engine                  = "postgres"
  engine_version          = "16.3"
  instance_class          = var.instance_class
  allocated_storage       = var.allocated_storage
  max_allocated_storage   = var.allocated_storage * 5
  storage_type            = "gp3"
  storage_encrypted       = true
  db_name                 = var.db_name
  username                = var.username
  password                = var.password
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.rds.id]
  multi_az                = true
  backup_retention_period = 7
  skip_final_snapshot     = false
  final_snapshot_identifier = "${var.name_prefix}-postgres-final"
  deletion_protection     = true
  performance_insights_enabled = true

  tags = { Name = "${var.name_prefix}-postgres" }
}
