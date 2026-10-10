variable "environment" {
  description = "Environment name used in resource names and tags."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "vpc_cidr" {
  description = "Primary IPv4 CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "Provide a valid IPv4 CIDR block."
  }
}

variable "zones" {
  description = "Availability Zones and subnet CIDRs, keyed by stable labels."
  type = map(object({
    availability_zone = string
    public_cidr       = string
    private_cidr      = string
    database_cidr     = string
  }))

  default = {
    a = {
      availability_zone = "us-east-1a"
      public_cidr       = "10.40.0.0/24"
      private_cidr      = "10.40.16.0/20"
      database_cidr     = "10.40.64.0/24"
    }
    b = {
      availability_zone = "us-east-1b"
      public_cidr       = "10.40.1.0/24"
      private_cidr      = "10.40.32.0/20"
      database_cidr     = "10.40.65.0/24"
    }
  }

  validation {
    condition = (
      contains(keys(var.zones), "a") &&
      length(distinct([
        for zone in values(var.zones) : zone.availability_zone
      ])) >= 2
    )
    error_message = "Provide at least two distinct Availability Zones, including key a for the NAT gateway."
  }
}
