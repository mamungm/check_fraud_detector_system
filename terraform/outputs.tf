output "alb_dns_name" {
  description = "Public ALB DNS name for the Fraud Detector orchestrator."
  value       = aws_lb.public.dns_name
}

output "api_base_url" {
  description = "API base URL. Use this in React env."
  value       = var.acm_certificate_arn == "" ? "http://${aws_lb.public.dns_name}" : "https://${aws_lb.public.dns_name}"
}

output "websocket_url" {
  description = "WebSocket URL. Use this in React env."
  value       = var.acm_certificate_arn == "" ? "ws://${aws_lb.public.dns_name}/ws" : "wss://${aws_lb.public.dns_name}/ws"
}

output "react_ui_cloudfront_url" {
  description = "CloudFront URL for React UI."
  value       = "https://${aws_cloudfront_distribution.ui.domain_name}"
}

output "react_ui_s3_bucket" {
  description = "S3 bucket where React build files should be uploaded."
  value       = aws_s3_bucket.ui.bucket
}

output "check_images_bucket" {
  description = "Private S3 bucket for check images."
  value       = aws_s3_bucket.check_images.bucket
}

output "ecr_repository_urls" {
  description = "ECR repositories for all Docker images."
  value       = { for k, repo in aws_ecr_repository.services : k => repo.repository_url }
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint."
  value       = aws_db_instance.postgres.address
}

output "msk_bootstrap_brokers" {
  description = "MSK plaintext bootstrap brokers."
  value       = aws_msk_cluster.main.bootstrap_brokers
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_names" {
  description = "ECS service names."
  value       = { for k, svc in aws_ecs_service.services : k => svc.name }
}
