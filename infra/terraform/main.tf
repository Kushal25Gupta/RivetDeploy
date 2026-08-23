terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# 1. S3 Bucket (For Immutable Static Artifacts)
resource "aws_s3_bucket" "artifacts_bucket" {
  bucket_prefix = "${var.project_name}-artifacts-"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "public_access" {
  bucket = aws_s3_bucket.artifacts_bucket.id

  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "allow_public_read" {
  bucket = aws_s3_bucket.artifacts_bucket.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "PublicReadGetObject"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.artifacts_bucket.arn}/*"
      },
    ]
  })
  depends_on = [aws_s3_bucket_public_access_block.public_access]
}

# 2. Security Group for EC2
resource "aws_security_group" "allow_web_ssh" {
  name        = "${var.project_name}-sg"
  description = "Allow SSH and API traffic"

  ingress {
    description = "SSH from anywhere"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Standard HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_key_pair" "deployer" {
  key_name   = "${var.project_name}-key"
  public_key = file("~/.ssh/rivetdeploy_rsa.pub")
}

# Find latest Amazon Linux 2023 AMI
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

# 3. EC2 Instance (Free Tier t2.micro)
resource "aws_instance" "worker_node" {
  ami           = data.aws_ami.amazon_linux_2023.id
  instance_type = "t2.micro" # Free Tier eligible
  key_name      = aws_key_pair.deployer.key_name

  vpc_security_group_ids = [aws_security_group.allow_web_ssh.id]

  # 30 GB EBS volume is Free Tier eligible
  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  user_data = <<-EOF
    #!/bin/bash
    # 1. Create a 2GB Swap file so the 1GB RAM VM doesn't crash during Docker builds
    dd if=/dev/zero of=/swapfile bs=128M count=16
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo "/swapfile swap swap defaults 0 0" >> /etc/fstab

    # 2. Install Docker
    dnf update -y
    dnf install -y docker
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ec2-user

    # 3. Install Docker Compose
    curl -SL https://github.com/docker/compose/releases/download/v2.24.5/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose

    # 4. Start Postgres and Redis
    mkdir -p /opt/rivetdeploy
    cat << 'YML' > /opt/rivetdeploy/docker-compose.yml
    services:
      postgres:
        image: postgres:15-alpine
        environment:
          POSTGRES_USER: rivetuser
          POSTGRES_PASSWORD: supersecretpassword
          POSTGRES_DB: rivetdeploy
        ports:
          - "5432:5432"
        restart: always
        volumes:
          - pgdata:/var/lib/postgresql/data
      
      redis:
        image: redis:7-alpine
        ports:
          - "6379:6379"
        restart: always
        volumes:
          - redisdata:/data
    volumes:
      pgdata:
      redisdata:
    YML

    cd /opt/rivetdeploy
    /usr/local/bin/docker-compose up -d
  EOF

  tags = {
    Name = "${var.project_name}-node"
  }
}
