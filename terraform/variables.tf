variable "project" {
  description = "Project/application name used in AWS resource names."
  type        = string
  default     = "fraud-detector"
}

variable "environment" {
  description = "Environment name, for example dev, test, prod."
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ca-central-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDRs for public subnets. Use two AZs for ALB/NAT."
  type        = list(string)
  default     = ["10.40.0.0/24", "10.40.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDRs for private subnets. ECS, RDS, and MSK are deployed here."
  type        = list(string)
  default     = ["10.40.10.0/24", "10.40.11.0/24"]
}

variable "single_nat_gateway" {
  description = "true creates one NAT gateway to reduce cost. false creates one NAT gateway per public subnet."
  type        = bool
  default     = true
}

variable "allowed_ingress_cidr" {
  description = "CIDR allowed to reach the public ALB. Use your IP/CIDR for tighter security."
  type        = string
  default     = "0.0.0.0/0"
}

variable "acm_certificate_arn" {
  description = "Optional ACM certificate ARN for HTTPS ALB listener. Leave empty for HTTP-only MVP."
  type        = string
  default     = ""
}

variable "image_tag" {
  description = "Docker image tag used by all services unless overridden later."
  type        = string
  default     = "latest"
}

variable "service_image_overrides" {
  description = "Optional complete image URI overrides per service key. Example: { orchestrator = \"123.dkr.ecr.ca-central-1.amazonaws.com/fraud-orchestrator:v1\" }"
  type        = map(string)
  default     = {}
}

variable "spring_profile" {
  description = "Spring profile for backend containers."
  type        = string
  default     = "prod"
}

variable "container_port" {
  description = "Container port exposed by all Spring Boot services."
  type        = number
  default     = 8080
}

variable "db_name" {
  description = "PostgreSQL database name."
  type        = string
  default     = "frauddb"
}

variable "db_username" {
  description = "PostgreSQL master username."
  type        = string
  default     = "fraud_admin"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "db_multi_az" {
  description = "Enable Multi-AZ RDS. For MVP keep false to reduce cost."
  type        = bool
  default     = false
}

variable "msk_kafka_version" {
  description = "Kafka version for MSK provisioned cluster."
  type        = string
  default     = "3.9.0"
}

variable "msk_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_broker_volume_size" {
  description = "MSK broker EBS volume size in GB."
  type        = number
  default     = 20
}

variable "consortium_api_key" {
  description = "Optional external consortium API key. Stored in Secrets Manager."
  type        = string
  default     = "replace-me"
  sensitive   = true
}

variable "jwt_secret" {
  description = "Optional JWT secret for the orchestrator. Stored in Secrets Manager."
  type        = string
  default     = "replace-me-change-this"
  sensitive   = true
}
