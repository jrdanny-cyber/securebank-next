import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { auth } from './auth'
import './index.css'

const root = createRoot(document.getElementById('root')!)

root.render(
  <main className="startup" role="status">
    Connecting to SecureBank…
  </main>,
)

auth.init({
  onLoad: 'check-sso',
  pkceMethod: 'S256',
  checkLoginIframe: false,
  redirectUri: window.location.origin + '/',
}).then(() => {
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}).catch(() => {
  root.render(
    <main className="startup">
      <h1>Sign-in service unavailable</h1>
      <p>Check that Keycloak is running, then reload this page.</p>
      <button onClick={() => window.location.reload()}>Try again</button>
    </main>,
  )
})
