resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${local.name_prefix}"
  retention_in_days = 14

  tags = local.common_tags
}

resource "aws_msk_configuration" "main" {
  name           = "${local.name_prefix}-msk-config"
  kafka_versions = [var.msk_kafka_version]

  server_properties = <<PROPERTIES
auto.create.topics.enable=true
delete.topic.enable=true
log.retention.hours=168
num.partitions=3
default.replication.factor=2
min.insync.replicas=1
PROPERTIES
}

resource "aws_msk_cluster" "main" {
  cluster_name           = "${local.name_prefix}-msk"
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = length(aws_subnet.private)

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.msk_broker_volume_size
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  client_authentication {
    unauthenticated = true
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-msk"
  })
}
