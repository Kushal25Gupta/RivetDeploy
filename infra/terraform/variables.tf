variable "project_id" {
  type        = string
  description = "The GCP Project ID to deploy resources into"
}

variable "region" {
  type        = string
  default     = "us-central1"
  description = "GCP Region (us-central1 is Free Tier eligible)"
}

variable "zone" {
  type        = string
  default     = "us-central1-a"
  description = "GCP Zone"
}
