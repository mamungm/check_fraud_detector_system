# Fraud Detector AWS Terraform Deployment

This Terraform project deploys the AWS infrastructure for the React + Spring Boot microservice fraud detection architecture.

## Components created

- VPC with public and private subnets
- Internet Gateway and NAT Gateway
- Security groups
- S3 bucket for check images
- S3 + CloudFront for React UI hosting
- ECR repositories for Docker images
- RDS PostgreSQL
- Amazon MSK provisioned Kafka cluster
- ECS Fargate cluster
- ECS services for:
  - orchestrator
  - ML service
  - rule-based service
  - image service
  - consortium service
- Application Load Balancer for the orchestrator
- CloudWatch log groups
- Secrets Manager secrets
- IAM roles and policies

## Service keys

Terraform uses these service keys:

```text
orchestrator
ml
rule
image
consortium
```

## Expected Docker images

By default, Terraform creates ECR repositories and the ECS services expect images like:

```text
<account>.dkr.ecr.<region>.amazonaws.com/fraud-detector-dev-fraud-orchestrator:latest
<account>.dkr.ecr.<region>.amazonaws.com/fraud-detector-dev-fraud-ml-service:latest
<account>.dkr.ecr.<region>.amazonaws.com/fraud-detector-dev-fraud-rule-service:latest
<account>.dkr.ecr.<region>.amazonaws.com/fraud-detector-dev-fraud-image-service:latest
<account>.dkr.ecr.<region>.amazonaws.com/fraud-detector-dev-fraud-consortium-service:latest
```

You can also pass complete image URI overrides using `service_image_overrides`.

## Deployment steps

### 1. Configure AWS credentials

```bash
aws configure
```

### 2. Initialize Terraform

```bash
terraform init
```

### 3. Create your variable file

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`.

### 4. Apply only ECR first

Because ECS cannot start until your images exist, create ECR first:

```bash
terraform apply -target=aws_ecr_repository.services
```

### 5. Push Docker images

Get the ECR URLs:

```bash
terraform output ecr_repository_urls
```

Login:

```bash
aws ecr get-login-password --region ca-central-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.ca-central-1.amazonaws.com
```

Build and push each service. Example for orchestrator:

```bash
docker build -t fraud-orchestrator ./fraud-orchestrator

docker tag fraud-orchestrator:latest \
  <account-id>.dkr.ecr.ca-central-1.amazonaws.com/fraud-detector-dev-fraud-orchestrator:latest

docker push \
  <account-id>.dkr.ecr.ca-central-1.amazonaws.com/fraud-detector-dev-fraud-orchestrator:latest
```

Repeat for:

```text
fraud-ml-service
fraud-rule-service
fraud-image-service
fraud-consortium-service
```

### 6. Apply the full infrastructure

```bash
terraform apply
```

### 7. Deploy React UI

After `terraform apply`, get the React S3 bucket and API outputs:

```bash
terraform output react_ui_s3_bucket
terraform output api_base_url
terraform output websocket_url
```

Create your React production environment:

For Vite:

```env
VITE_API_BASE_URL=<api_base_url>
VITE_WS_URL=<websocket_url>
```

Then build and upload:

```bash
npm run build
aws s3 sync dist/ s3://$(terraform output -raw react_ui_s3_bucket) --delete
```

For Create React App, use:

```env
REACT_APP_API_BASE_URL=<api_base_url>
REACT_APP_WS_URL=<websocket_url>
```

### 8. Open the application

```bash
terraform output react_ui_cloudfront_url
```

## Important notes

- This is an MVP/prototype deployment. For production, use HTTPS with a real domain and ACM certificate.
- MSK and NAT Gateway can be expensive. Destroy the stack when not in use.
- RDS is private. Access it from inside the VPC, for example through ECS Exec, SSM, or a bastion host.
- Internal services are not exposed to the internet.
- The orchestrator is the only backend service behind the public ALB.
- Kafka topics are configured with `auto.create.topics.enable=true` for MVP simplicity.
- The ECS container health check uses `wget` or `curl`. Ensure your Docker images include at least one of them, or remove the health check from `ecs.tf`.

## Cleanup

```bash
terraform destroy
```
