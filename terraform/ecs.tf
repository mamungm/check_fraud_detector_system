resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "ecs" {
  for_each          = local.service_defaults
  name              = each.value.log_group
  retention_in_days = 14

  tags = merge(local.common_tags, {
    Service = each.key
  })
}

resource "aws_ecs_task_definition" "services" {
  for_each                 = local.service_defaults
  family                   = "${local.name_prefix}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = each.key
      image     = local.service_images[each.key]
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = var.spring_profile },
        { name = "SERVER_PORT", value = tostring(var.container_port) },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "CHECK_IMAGE_BUCKET", value = aws_s3_bucket.check_images.bucket },
        { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers },
        { name = "KAFKA_BOOTSTRAP_SERVERS_TLS", value = aws_msk_cluster.main.bootstrap_brokers_tls },
        { name = "FRONTEND_ORIGIN", value = "https://${aws_cloudfront_distribution.ui.domain_name}" },
        { name = "ALB_BASE_URL", value = var.acm_certificate_arn == "" ? "http://${aws_lb.public.dns_name}" : "https://${aws_lb.public.dns_name}" },
        { name = "TOPIC_ML_COMMAND", value = "fraud.ml.cmd" },
        { name = "TOPIC_ML_RESPONSE", value = "fraud.ml.response" },
        { name = "TOPIC_RULE_COMMAND", value = "fraud.rule.cmd" },
        { name = "TOPIC_RULE_RESPONSE", value = "fraud.rule.response" },
        { name = "TOPIC_IMAGE_COMMAND", value = "fraud.image.cmd" },
        { name = "TOPIC_IMAGE_RESPONSE", value = "fraud.image.response" },
        { name = "TOPIC_CONSORTIUM_COMMAND", value = "fraud.consortium.cmd" },
        { name = "TOPIC_CONSORTIUM_RESPONSE", value = "fraud.consortium.response" },
        { name = "TOPIC_FINAL_RESULT", value = "fraud.final.result" },
        { name = "TOPIC_ERROR", value = "fraud.error" }
      ]

      secrets = concat(
        [
          { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn },
          { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn }
        ],
        each.key == "consortium" ? [
          { name = "CONSORTIUM_API_KEY", valueFrom = aws_secretsmanager_secret.consortium_api_key.arn }
        ] : []
      )

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs[each.key].name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = each.key
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.container_port}/actuator/health || curl -fsS http://localhost:${var.container_port}/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 90
      }
    }
  ])

  tags = merge(local.common_tags, {
    Service = each.key
  })
}

resource "aws_ecs_service" "services" {
  for_each        = local.service_defaults
  name            = "${local.name_prefix}-${each.key}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = each.value.desired_count
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200
  enable_execute_command             = true

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = each.key == "orchestrator" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.orchestrator.arn
      container_name   = each.key
      container_port   = var.container_port
    }
  }

  depends_on = [
    aws_lb_listener.http_forward,
    aws_lb_listener.http_redirect,
    aws_lb_listener.https,
    aws_msk_cluster.main,
    aws_db_instance.postgres
  ]

  tags = merge(local.common_tags, {
    Service = each.key
  })
}
