locals {
  name = "securebank-next-${var.environment}"

  zones = var.zones
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = local.name
  }
}

resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${local.name}-default-restricted"
  }
}

resource "aws_subnet" "public" {
  for_each = local.zones

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value.availability_zone
  cidr_block              = each.value.public_cidr
  map_public_ip_on_launch = false

  tags = {
    Name                     = "${local.name}-public-${each.key}"
    Tier                     = "public"
    "kubernetes.io/role/elb" = "1"
  }
}

resource "aws_subnet" "private" {
  for_each = local.zones

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value.availability_zone
  cidr_block              = each.value.private_cidr
  map_public_ip_on_launch = false

  tags = {
    Name                              = "${local.name}-private-${each.key}"
    Tier                              = "private"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

resource "aws_subnet" "database" {
  for_each = local.zones

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value.availability_zone
  cidr_block              = each.value.database_cidr
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name}-database-${each.key}"
    Tier = "database"
  }
}
