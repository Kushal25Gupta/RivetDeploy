variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS Region for deployment"
}

variable "project_name" {
  type        = string
  default     = "rivetdeploy"
  description = "Base name for AWS resources"
}
