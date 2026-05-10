locals {
  name_prefix = "sentinel-iot-${var.environment}"

  private_subnet_cidrs = [
    cidrsubnet(var.vpc_cidr, 4, 0),
    cidrsubnet(var.vpc_cidr, 4, 1),
    cidrsubnet(var.vpc_cidr, 4, 2),
  ]
  public_subnet_cidrs = [
    cidrsubnet(var.vpc_cidr, 4, 8),
    cidrsubnet(var.vpc_cidr, 4, 9),
    cidrsubnet(var.vpc_cidr, 4, 10),
  ]
}

# ── VPC ───────────────────────────────────────────────────────────────────────

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "${local.name_prefix}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name_prefix}-igw" }
}

resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "${local.name_prefix}-public-${count.index + 1}"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_subnet" "private" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  cidr_block        = local.private_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name                              = "${local.name_prefix}-private-${count.index + 1}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

resource "aws_eip" "nat" {
  count  = 1
  domain = "vpc"
  tags   = { Name = "${local.name_prefix}-nat-eip" }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id
  tags          = { Name = "${local.name_prefix}-nat" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "${local.name_prefix}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }
  tags = { Name = "${local.name_prefix}-private-rt" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ── EKS ───────────────────────────────────────────────────────────────────────

module "eks" {
  source = "./modules/eks"

  name_prefix          = local.name_prefix
  vpc_id               = aws_vpc.main.id
  private_subnet_ids   = aws_subnet.private[*].id
  cluster_version      = var.eks_cluster_version
  node_instance_types  = var.eks_node_instance_types
  node_min_size        = var.eks_node_min_size
  node_max_size        = var.eks_node_max_size
  node_desired_size    = var.eks_node_desired_size
}

# ── RDS (PostgreSQL) ──────────────────────────────────────────────────────────

module "rds" {
  source = "./modules/rds"

  name_prefix       = local.name_prefix
  vpc_id            = aws_vpc.main.id
  subnet_ids        = aws_subnet.private[*].id
  eks_sg_id         = module.eks.node_security_group_id
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  db_name           = var.rds_db_name
  username          = var.rds_username
  password          = var.rds_password
}

# ── ElastiCache (Redis) ───────────────────────────────────────────────────────

module "elasticache" {
  source = "./modules/elasticache"

  name_prefix = local.name_prefix
  vpc_id      = aws_vpc.main.id
  subnet_ids  = aws_subnet.private[*].id
  eks_sg_id   = module.eks.node_security_group_id
  node_type   = var.elasticache_node_type
}

# ── MSK (Kafka) ───────────────────────────────────────────────────────────────

module "msk" {
  source = "./modules/msk"

  name_prefix    = local.name_prefix
  vpc_id         = aws_vpc.main.id
  subnet_ids     = aws_subnet.private[*].id
  eks_sg_id      = module.eks.node_security_group_id
  instance_type  = var.msk_instance_type
  kafka_version  = var.msk_kafka_version
  broker_count   = var.msk_broker_count
}

# ── Argo CD (bootstrapped via Helm) ───────────────────────────────────────────

resource "helm_release" "argocd" {
  name             = "argocd"
  namespace        = "argocd"
  create_namespace = true
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  version          = var.argocd_chart_version

  set {
    name  = "server.service.type"
    value = "ClusterIP"
  }

  depends_on = [module.eks]
}

# ── External Secrets Operator ─────────────────────────────────────────────────

resource "helm_release" "external_secrets" {
  name             = "external-secrets"
  namespace        = "external-secrets"
  create_namespace = true
  repository       = "https://charts.external-secrets.io"
  chart            = "external-secrets"
  version          = var.external_secrets_chart_version

  depends_on = [module.eks]
}

# ── Secrets Manager entries (initial creation only) ───────────────────────────

resource "aws_secretsmanager_secret" "sentinel" {
  name                    = "sentinel/${var.environment}/secrets"
  description             = "Sentinel IoT platform runtime secrets"
  recovery_window_in_days = 7
}
