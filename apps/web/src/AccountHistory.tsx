import { useEffect, useState } from 'react'
import { auth } from './auth'

const PAGE_SIZE = 20

type AccountOption = {
  id: string
  accountName: string
  accountReference: string
}

type HistoryEntry = {
  transactionId: string
  direction: 'DEBIT' | 'CREDIT'
  amount: string
  currency: string
  description: string
  recordedAt: string
}

type HistoryPage = {
  items: HistoryEntry[]
  page: number
  size: number
  hasNext: boolean
}

type HistoryState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: HistoryPage }
  | { kind: 'error'; message: string }

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value)
}

function isHistoryEntry(value: unknown): value is HistoryEntry {
  return isRecord(value) &&
    typeof value.transactionId === 'string' &&
    (value.direction === 'DEBIT' || value.direction === 'CREDIT') &&
    typeof value.amount === 'string' &&
    /^\d+\.\d{2}$/.test(value.amount) &&
    typeof value.currency === 'string' &&
    /^[A-Z]{3}$/.test(value.currency) &&
    typeof value.description === 'string' &&
    typeof value.recordedAt === 'string' &&
    Number.isFinite(Date.parse(value.recordedAt))
}

function isHistoryPage(value: unknown): value is HistoryPage {
  return isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isHistoryEntry) &&
    typeof value.page === 'number' &&
    Number.isInteger(value.page) &&
    value.page >= 0 &&
    value.size === PAGE_SIZE &&
    value.items.length <= PAGE_SIZE &&
    typeof value.hasNext === 'boolean'
}

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function HistoryEntries({
  accountId,
  accountName,
}: {
  accountId: string
  accountName: string
}) {
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(0)
  const [state, setState] = useState<HistoryState>({ kind: 'loading' })

  useEffect(() => {
    const controller = new AbortController()

    async function loadHistory() {
      try {
        try {
          await auth.updateToken(30)
        } catch {
          throw new Error('Your session has expired. Please sign in again.')
        }

        if (controller.signal.aborted) return

        if (!auth.token) {
          throw new Error('Please sign in again to view your transactions.')
        }

        const response = await fetch(
          `/api/v1/accounts/${encodeURIComponent(accountId)}/transactions` +
          `?page=${page}&size=${PAGE_SIZE}`,
          {
            headers: {
              Authorization: `Bearer ${auth.token}`,
              Accept: 'application/json',
            },
            cache: 'no-store',
            signal: controller.signal,
          },
        )

        if (response.status === 401) {
          throw new Error('Your session could not be verified. Please sign in again.')
        }

        if (response.status === 403) {
          throw new Error('Your login does not have permission to view transactions.')
        }

        if (response.status === 404) {
          throw new Error('This account is unavailable.')
        }

        if (!response.ok) {
          throw new Error('Transaction history is temporarily unavailable.')
        }

        const data: unknown = await response.json()

        if (!isHistoryPage(data) || data.page !== page) {
          throw new Error('The transaction service returned an unexpected response.')
        }

        if (!controller.signal.aborted) {
          setState({ kind: 'ready', data })
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setState({
            kind: 'error',
            message: error instanceof Error
              ? error.message
              : 'Unable to load transaction history.',
          })
        }
      }
    }

    void loadHistory()

    return () => controller.abort()
  }, [accountId, page, retry])

  function changePage(nextPage: number) {
    setState({ kind: 'loading' })
    setPage(nextPage)
  }

  function retryLoading() {
    setState({ kind: 'loading' })
    setRetry(value => value + 1)
  }

  if (state.kind === 'loading') {
    return <p className="notice" role="status">Loading transactions…</p>
  }

  if (state.kind === 'error') {
    return (
      <div className="notice error" role="alert">
        <p>{state.message}</p>
        <button type="button" onClick={retryLoading}>
          Try again
        </button>
      </div>
    )
  }

  return (
    <>
      {state.data.items.length === 0 ? (
        <p className="notice">
          No transactions have been recorded for this account.
        </p>
      ) : (
        <div
          className="history-scroll"
          role="region"
          aria-label={`Transactions for ${accountName}`}
          tabIndex={0}
        >
          <table className="history-table">
            <caption>Transactions for {accountName}</caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Description</th>
                <th scope="col">Direction</th>
                <th scope="col">Amount</th>
                <th scope="col">Transaction reference</th>
              </tr>
            </thead>

            <tbody>
              {state.data.items.map(entry => (
                <tr key={entry.transactionId}>
                  <td>
                    <time dateTime={entry.recordedAt}>
                      {dateFormatter.format(new Date(entry.recordedAt))}
                    </time>
                  </td>

                  <td>{entry.description}</td>

                  <td>
                    {entry.direction === 'CREDIT' ? 'Credit' : 'Debit'}
                  </td>

                  <td
                    className={`history-amount ${
                      entry.direction === 'CREDIT'
                        ? 'history-credit'
                        : 'history-debit'
                    }`}
                  >
                    {entry.direction === 'CREDIT' ? '+' : '−'}
                    {entry.currency} {entry.amount}
                  </td>

                  <td>
                    <code className="history-reference">
                      {entry.transactionId}
                    </code>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <nav className="history-pagination" aria-label="Transaction history pages">
        <button
          type="button"
          className="secondary"
          disabled={page === 0}
          onClick={() => changePage(page - 1)}
        >
          Previous
        </button>

        <span>Page {page + 1}</span>

        <button
          type="button"
          className="secondary"
          disabled={!state.data.hasNext || page >= 10_000}
          onClick={() => changePage(page + 1)}
        >
          Next
        </button>
      </nav>
    </>
  )
}

export default function AccountHistory({
  accounts,
  revision,
}: {
  accounts: AccountOption[]
  revision: number
}) {
  const [selectedId, setSelectedId] = useState('')

  const selectedAccount =
    accounts.find(account => account.id === selectedId) ?? accounts[0]

  if (!selectedAccount) return null

  return (
    <section className="history-panel" aria-labelledby="history-heading">
      <h3 id="history-heading">Transaction history</h3>
      <p className="history-help">
        Most recent first. Times use your local time zone.
      </p>

      <label className="history-account">
        <span>Account</span>
        <select
          value={selectedAccount.id}
          onChange={event => setSelectedId(event.target.value)}
        >
          {accounts.map(account => (
            <option key={account.id} value={account.id}>
              {account.accountName} — {account.accountReference}
            </option>
          ))}
        </select>
      </label>

      <HistoryEntries
        key={`${selectedAccount.id}:${revision}`}
        accountId={selectedAccount.id}
        accountName={selectedAccount.accountName}
      />
    </section>
  )
}
