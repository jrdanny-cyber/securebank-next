output "vpc_id" {
  value = aws_vpc.this.id
}

output "vpc_cidr" {
  value = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  value = {
    for key, subnet in aws_subnet.public : key => subnet.id
  }
}

output "private_subnet_ids" {
  value = {
    for key, subnet in aws_subnet.private : key => subnet.id
  }
}

output "database_subnet_ids" {
  value = {
    for key, subnet in aws_subnet.database : key => subnet.id
  }
}

output "nat_gateway_id" {
  value = aws_nat_gateway.this.id
}
