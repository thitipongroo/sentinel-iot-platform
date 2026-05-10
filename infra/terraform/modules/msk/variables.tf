variable "name_prefix" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "eks_sg_id" { type = string }
variable "instance_type" { type = string; default = "kafka.t3.small" }
variable "kafka_version" { type = string; default = "3.6.0" }
variable "broker_count" { type = number; default = 2 }
