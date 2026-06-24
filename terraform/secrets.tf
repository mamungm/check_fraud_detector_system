resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "db_password" {
  name        = "${local.name_prefix}/db/password"
  description = "RDS PostgreSQL password for ${local.name_prefix}"

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name        = "${local.name_prefix}/app/jwt-secret"
  description = "JWT secret for ${local.name_prefix}"

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = var.jwt_secret
}

resource "aws_secretsmanager_secret" "consortium_api_key" {
  name        = "${local.name_prefix}/consortium/api-key"
  description = "External consortium API key for ${local.name_prefix}"

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "consortium_api_key" {
  secret_id     = aws_secretsmanager_secret.consortium_api_key.id
  secret_string = var.consortium_api_key
}
