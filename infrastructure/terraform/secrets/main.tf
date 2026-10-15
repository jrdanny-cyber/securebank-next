locals {
  database_secrets = {
    banking-app      = "Banking API database credentials"
    banking-migrator = "Banking migration database credentials"
    keycloak-db      = "Keycloak database credentials"
  }
}

resource "aws_secretsmanager_secret" "database" {
  for_each = local.database_secrets

  name                    = "securebank-next/dev/${each.key}"
  description             = each.value
  recovery_window_in_days = 30

  lifecycle {
    prevent_destroy = true
  }
}
