data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project}-${var.environment}"
  azs         = slice(data.aws_availability_zones.available.names, 0, length(var.public_subnet_cidrs))

  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  service_defaults = {
    orchestrator = {
      repository_name = "fraud-orchestrator"
      cpu             = 512
      memory          = 1024
      desired_count   = 1
      public          = true
      log_group       = "/aws/ecs/${local.name_prefix}/orchestrator"
    }
    ml = {
      repository_name = "fraud-ml-service"
      cpu             = 1024
      memory          = 2048
      desired_count   = 1
      public          = false
      log_group       = "/aws/ecs/${local.name_prefix}/ml"
    }
    rule = {
      repository_name = "fraud-rule-service"
      cpu             = 512
      memory          = 1024
      desired_count   = 1
      public          = false
      log_group       = "/aws/ecs/${local.name_prefix}/rule"
    }
    image = {
      repository_name = "fraud-image-service"
      cpu             = 1024
      memory          = 2048
      desired_count   = 1
      public          = false
      log_group       = "/aws/ecs/${local.name_prefix}/image"
    }
    consortium = {
      repository_name = "fraud-consortium-service"
      cpu             = 512
      memory          = 1024
      desired_count   = 1
      public          = false
      log_group       = "/aws/ecs/${local.name_prefix}/consortium"
    }
  }

  ecr_image_uris = {
    for key, svc in local.service_defaults :
    key => "${aws_ecr_repository.services[key].repository_url}:${var.image_tag}"
  }

  service_images = merge(local.ecr_image_uris, var.service_image_overrides)
}
