variable "cluster_name" {
  description = "Name of the development EKS cluster."
  type        = string
  default     = "securebank-next-dev"
}

variable "kubernetes_version" {
  description = "Explicit Kubernetes minor version."
  type        = string
  default     = "1.36"
}

variable "admin_principal_arn" {
  description = "IAM principal granted explicit Kubernetes administrator access."
  type        = string
  default     = "arn:aws:iam::535079144516:user/dannyjr"
}

variable "admin_public_cidr" {
  description = "Administrator workstation public IPv4 address as a /32 CIDR."
  type        = string

  validation {
    condition = (
      can(cidrnetmask(var.admin_public_cidr)) &&
      endswith(var.admin_public_cidr, "/32")
    )
    error_message = "Provide one valid IPv4 address with a /32 prefix."
  }
}
