resource "aws_s3_bucket" "check_images" {
  bucket        = "${local.name_prefix}-check-images-${data.aws_caller_identity.current.account_id}"
  force_destroy = true

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-check-images"
  })
}

resource "aws_s3_bucket_public_access_block" "check_images" {
  bucket                  = aws_s3_bucket.check_images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "check_images" {
  bucket = aws_s3_bucket.check_images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "check_images" {
  bucket = aws_s3_bucket.check_images.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket" "ui" {
  bucket        = "${local.name_prefix}-react-ui-${data.aws_caller_identity.current.account_id}"
  force_destroy = true

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-react-ui"
  })
}

resource "aws_s3_bucket_public_access_block" "ui" {
  bucket                  = aws_s3_bucket.ui.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "ui" {
  name                              = "${local.name_prefix}-ui-oac"
  description                       = "OAC for React UI S3 origin"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "ui" {
  enabled             = true
  default_root_object = "index.html"
  comment             = "${local.name_prefix} React UI"

  origin {
    domain_name              = aws_s3_bucket.ui.bucket_regional_domain_name
    origin_id                = "react-ui-s3-origin"
    origin_access_control_id = aws_cloudfront_origin_access_control.ui.id
  }

  default_cache_behavior {
    target_origin_id       = "react-ui-s3-origin"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = local.common_tags
}

resource "aws_s3_bucket_policy" "ui" {
  bucket = aws_s3_bucket.ui.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowCloudFrontRead"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.ui.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.ui.arn
          }
        }
      }
    ]
  })
}
