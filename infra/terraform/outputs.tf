output "ec2_public_ip" {
  value       = aws_instance.worker_node.public_ip
  description = "The public IP of your EC2 instance (API, DB, Redis, Worker)"
}

output "artifact_bucket_name" {
  value       = aws_s3_bucket.artifacts_bucket.bucket
  description = "The S3 bucket holding the static site HTML files"
}

output "artifact_bucket_domain" {
  value       = aws_s3_bucket.artifacts_bucket.bucket_domain_name
  description = "The domain name of the S3 bucket to serve websites"
}
