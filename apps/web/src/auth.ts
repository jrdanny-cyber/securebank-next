import Keycloak from 'keycloak-js'

interface RuntimeConfig {
  keycloakUrl: string
  keycloakRealm: string
  keycloakClientId: string
}

declare global {
  interface Window {
    SECUREBANK_CONFIG?: RuntimeConfig
  }
}

const config = window.SECUREBANK_CONFIG

if (
  !config ||
  !config.keycloakUrl ||
  !config.keycloakRealm ||
  !config.keycloakClientId
) {
  throw new Error('Missing SecureBank authentication configuration')
}

export const auth = new Keycloak({
  url: config.keycloakUrl,
  realm: config.keycloakRealm,
  clientId: config.keycloakClientId,
})
