import Keycloak from 'keycloak-js'

export const auth = new Keycloak({
  url: 'http://localhost:8180',
  realm: 'securebank',
  clientId: 'securebank-web',
})
