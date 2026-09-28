import { useEffect, useState } from 'react'
import { auth } from './auth'
import TransferForm from './TransferForm'
import AccountHistory from './AccountHistory'
type Account = {
  id: string
  accountReference: string
  accountName: string
  currency: string
  status: string
  balance: string
}

type AccountState =
  | { kind: 'loading' }
  | { kind: 'ready'; accounts: Account[] }
  | { kind: 'error'; message: string }

function isAccount(value: unknown): value is Account {
  if (typeof value !== 'object' || value === null) return false

  return (
    'id' in value && typeof value.id === 'string' &&
    'accountReference' in value && typeof value.accountReference === 'string' &&
    'accountName' in value && typeof value.accountName === 'string' &&
    'currency' in value && typeof value.currency === 'string' &&
    'status' in value && typeof value.status === 'string' &&
    'balance' in value && typeof value.balance === 'string' &&
     /^-?\d+\.\d{2}$/.test(value.balance)
  )
}

function Accounts() {
  const [state, setState] = useState<AccountState>({ kind: 'loading' })
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    async function loadAccounts() {
      try {
        try {
          await auth.updateToken(30)
        } catch {
          throw new Error('Your session has expired. Please sign in again.')
        }

        if (controller.signal.aborted) return

        if (!auth.token) {
          throw new Error('Please sign in again to view your accounts.')
        }

        const response = await fetch('/api/v1/accounts', {
          headers: {
            Authorization: `Bearer ${auth.token}`,
            Accept: 'application/json',
          },
          cache: 'no-store',
          signal: controller.signal,
        })

        if (response.status === 401) {
          throw new Error('Your session could not be verified. Please sign in again.')
        }

        if (response.status === 403) {
          throw new Error('Your login does not have permission to view accounts.')
        }

        if (!response.ok) {
          throw new Error('Accounts are temporarily unavailable. Please try again.')
        }

        const data: unknown = await response.json()

        if (!Array.isArray(data) || !data.every(isAccount)) {
          throw new Error('The account service returned an unexpected response.')
        }

        if (!controller.signal.aborted) {
          setState({ kind: 'ready', accounts: data })
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setState({
            kind: 'error',
            message: error instanceof Error
              ? error.message
              : 'Unable to load accounts.',
          })
        }
      }
    }

    void loadAccounts()

    return () => controller.abort()
  }, [revision])

  if (state.kind === 'loading') {
    return <p className="notice" role="status">Loading your accounts…</p>
  }

  if (state.kind === 'error') {
    return (
      <section className="notice error" role="alert">
        <p>{state.message}</p>
        <button onClick={() => window.location.reload()}>Reload</button>
      </section>
    )
  }

  if (state.accounts.length === 0) {
    return (
      <p className="notice">
        No accounts are linked to your customer profile yet.
      </p>
    )
  }

  return (
    <div className="account-grid">
      {state.accounts.map(account => (
        <article className="account-card" key={account.id}>
          <div className="card-top">
            <span className="currency">{account.currency}</span>
            <span className="status">{account.status}</span>
          </div>

	  <TransferForm
             accounts={state.accounts}
             onTransferred={() => setRevision(value => value + 1)}
          />

	  <AccountHistory
             accounts={state.accounts}
             revision={revision}
          />

          <h3>{account.accountName}</h3>
          <p className="reference">{account.accountReference}</p>

          <div className="balance">
            <span>Balance</span>
            <strong>{account.currency} {account.balance}</strong>
          </div>
        </article>
      ))}
    </div>
  )
}

export default function App() {
  const [actionError, setActionError] = useState('')

  async function signIn() {
    try {
      await auth.login({ redirectUri: window.location.origin + '/' })
    } catch {
      setActionError('Unable to start sign-in. Please try again.')
    }
  }

  async function signOut() {
    try {
      await auth.logout({ redirectUri: window.location.origin + '/' })
    } catch {
      setActionError('Unable to complete sign-out. Please try again.')
    }
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href="/">SecureBank<span> / </span></a>
        <p className="sidebar-caption">CUSTOMER PORTAL</p>
        <nav aria-label="Main navigation">
          <a className="nav-active" href="/" aria-current="page">
            Account overview
          </a>
        </nav>
        <div className="demo-label">
          <strong>Demo environment</strong>
          <span>Simulated accounts and funds</span>
        </div>
      </aside>

      <main className="content">
        <header className="topbar">
          <span>Personal banking</span>
          {auth.authenticated ? (
            <button className="secondary" onClick={() => void signOut()}>
              Sign out
            </button>
          ) : (
            <button onClick={() => void signIn()}>Sign in</button>
          )}
        </header>

        {actionError && <p className="notice error" role="alert">{actionError}</p>}

        <section className="intro">
          <p className="eyebrow">YOUR EVERYDAY BANKING</p>
          <h1>{auth.authenticated ? 'Your accounts, at a glance.' : 'Welcome to SecureBank.'}</h1>
          <p>
            {auth.authenticated
              ? 'A clear view of the accounts connected to your customer profile.'
              : 'Sign in to access your personal account overview.'}
          </p>
        </section>

        {auth.authenticated ? (
          <section aria-labelledby="accounts-heading">
            <div className="section-heading">
              <h2>My accounts</h2>
              <span>Account overview</span>
            </div>
            <Accounts />
          </section>
        ) : (
          <section className="welcome-card">
            <div className="welcome-mark" aria-hidden="true">SB</div>
            <h2>Your banking starts here.</h2>
            <p>Use your customer login to view your accounts.</p>
            <button onClick={() => void signIn()}>Sign in to your account →</button>
          </section>
        )}

        <footer>SecureBank · Learning platform · No real money</footer>
      </main>
    </div>
  )
}
