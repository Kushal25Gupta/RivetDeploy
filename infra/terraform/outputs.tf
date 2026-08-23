output "api_url" {
  value       = google_cloud_run_v2_service.api_service.uri
  description = "The public URL of the Spring Boot API hosted on Cloud Run"
}

output "artifact_bucket_name" {
  value       = google_storage_bucket.artifacts_bucket.name
  description = "The GCS bucket holding the static site HTML files"
}

output "worker_internal_ip" {
  value       = google_compute_instance.worker_node.network_interface[0].network_ip
  description = "The internal IP of the Worker VM (used by Cloud Run to connect to DB/Redis)"
}

output "worker_public_ip" {
  value       = google_compute_instance.worker_node.network_interface[0].access_config[0].nat_ip
  description = "The public IP of the Worker VM (for SSH access if needed)"
}
