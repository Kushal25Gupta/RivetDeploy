terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# 1. Cloud Storage Bucket (For Immutable Static Artifacts)
resource "google_storage_bucket" "artifacts_bucket" {
  name          = "${var.project_id}-rivetdeploy-artifacts"
  location      = "US" # US multi-region is Free Tier eligible (5GB)
  force_destroy = true
  
  uniform_bucket_level_access = true

  cors {
    origin          = ["*"]
    method          = ["GET", "HEAD", "OPTIONS"]
    response_header = ["*"]
    max_age_seconds = 3600
  }
}

# Make Bucket Publicly Readable so sites can be accessed
resource "google_storage_bucket_iam_member" "public_read" {
  bucket = google_storage_bucket.artifacts_bucket.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

# 2. Compute Engine VM (For Postgres, Redis, and Docker Build Worker)
# We use e2-micro to stay in the Always Free tier.
resource "google_compute_instance" "worker_node" {
  name         = "rivetdeploy-worker-node"
  machine_type = "e2-micro"
  zone         = var.zone

  tags = ["ssh", "internal-db-redis"]

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = 30 # 30 GB standard disk is Free Tier eligible
      type  = "pd-standard"
    }
  }

  network_interface {
    network = "default"
    access_config {
      # Ephemeral public IP required for internet access (apt-get, docker pull)
    }
  }

  # Startup script installs Docker and starts the databases
  metadata_startup_script = <<-EOT
    #!/bin/bash
    apt-get update
    apt-get install -y ca-certificates curl gnupg
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

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
    docker compose up -d
  EOT
}

# Firewall rule to allow SSH
resource "google_compute_firewall" "allow_ssh" {
  name    = "rivetdeploy-allow-ssh"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
  source_ranges = ["0.0.0.0/0"]
}

# Firewall rule to allow Cloud Run internal connections to Postgres/Redis
resource "google_compute_firewall" "allow_internal" {
  name    = "rivetdeploy-allow-internal"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["5432", "6379"]
  }
  source_ranges = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
}

# 3. Cloud Run Service (For the Spring Boot API)
resource "google_cloud_run_v2_service" "api_service" {
  name     = "rivetdeploy-api"
  location = var.region
  
  deletion_protection = false

  template {
    containers {
      # Placeholder until we push the real backend container
      image = "us-docker.pkg.dev/cloudrun/container/hello" 
      
      env {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${google_compute_instance.worker_node.network_interface[0].network_ip}:5432/rivetdeploy"
      }
      env {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = "rivetuser"
      }
      env {
        name  = "SPRING_DATASOURCE_PASSWORD"
        value = "supersecretpassword"
      }
      env {
        name  = "SPRING_REDIS_HOST"
        value = google_compute_instance.worker_node.network_interface[0].network_ip
      }
      env {
        name  = "RIVETDEPLOY_STORAGE_TYPE"
        value = "gcs"
      }
      env {
        name  = "RIVETDEPLOY_STORAGE_GCS_BUCKET"
        value = google_storage_bucket.artifacts_bucket.name
      }
    }
    
    # Direct VPC Egress allows Cloud Run to talk to the VM's internal IP for free!
    vpc_access {
      network_interfaces {
        network = "default"
      }
      egress = "PRIVATE_RANGES_ONLY"
    }
  }
}

# Make Cloud Run Publicly Accessible
resource "google_cloud_run_service_iam_member" "api_public" {
  location = google_cloud_run_v2_service.api_service.location
  project  = google_cloud_run_v2_service.api_service.project
  service  = google_cloud_run_v2_service.api_service.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
