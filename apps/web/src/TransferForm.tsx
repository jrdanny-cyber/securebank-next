import { useState, type FormEvent } from 'react'
import { auth } from './auth'

type AccountOption = {
  id: string
  accountName: string
  currency: string
  status: string
  balance: string
}

type TransferAttempt = {
  key: string
  sourceAccountId: string
  destinationAccountId: string
  amount: string
  description: string
}

type TransferFormProps = {
  accounts: AccountOption[]
  onTransferred: () => void
}

function isPositiveUsdAmount(value: string): boolean {
  if (!/^(?:0|[1-9]\d{0,16})(?:\.\d{1,2})?$/.test(value)) {
    return false
  }

  const [units, fraction = ''] = value.split('.')
  const cents = BigInt(units) * 100n + BigInt(fraction.padEnd(2, '0'))
  return cents > 0n
}

function balanceInCents(value: string): bigint {
  const [units, fraction = '00'] = value.replace('-', '').split('.')
  return BigInt(units) * 100n + BigInt(fraction.padEnd(2, '0'))
}

export default function TransferForm({
  accounts,
  onTransferred,
}: TransferFormProps) {
  const available = accounts.filter(
    account => account.status === 'ACTIVE' && account.currency === 'USD',
  )

  const [sourceAccountId, setSourceAccountId] = useState('')
  const [destinationAccountId, setDestinationAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('Transfer between my accounts')
  const [pending, setPending] = useState<TransferAttempt | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const source = available.find(account => account.id === sourceAccountId)
  const destination = available.find(
    account => account.id === destinationAccountId,
  )
  const formLocked = submitting || pending !== null

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSuccess('')

    let attempt = pending

    if (!attempt) {
      if (
        !source ||
        !destination ||
        source.id === destination.id ||
        !isPositiveUsdAmount(amount) ||
        description.trim().length === 0 ||
        description.trim().length > 255
      ) {
        setError('Check the accounts, positive amount, and description.')
        return
      }

      if (source.balance.startsWith('-')) {
        setError('The source account has insufficient funds.')
        return
      }

      const [units, fraction = ''] = amount.split('.')
      const requestedCents =
        BigInt(units) * 100n + BigInt(fraction.padEnd(2, '0'))

      if (requestedCents > balanceInCents(source.balance)) {
        setError('The source account has insufficient funds.')
        return
      }

      attempt = {
        key: crypto.randomUUID(),
        sourceAccountId: source.id,
        destinationAccountId: destination.id,
        amount,
        description: description.trim(),
      }
      setPending(attempt)
    }

    setSubmitting(true)

    try {
      await auth.updateToken(30)

      if (!auth.token) {
        throw new Error('Your session expired. Sign in again before retrying.')
      }

      const response = await fetch('/api/v1/transfers', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${auth.token}`,
          Accept: 'application/json',
          'Content-Type': 'application/json',
          'Idempotency-Key': attempt.key,
        },
        cache: 'no-store',
        body: JSON.stringify({
          sourceAccountId: attempt.sourceAccountId,
          destinationAccountId: attempt.destinationAccountId,
          amount: attempt.amount,
          currency: 'USD',
          description: attempt.description,
        }),
      })

      if (
         response.status >= 500 ||
         response.status === 408 ||
         response.status === 429
     ) {
        throw new Error('The server has not confirmed the transfer result.')
       }
      if (!response.ok) {
        const body: unknown = await response.json().catch(() => null)
        const detail =
          typeof body === 'object' && body !== null && 'detail' in body &&
          typeof body.detail === 'string'
            ? body.detail
            : 'The transfer could not be completed.'

        setPending(null)
        setError(detail)
        return
      }

      const result: unknown = await response.json()

      if (
        typeof result !== 'object' ||
        result === null ||
        !('transactionId' in result) ||
        typeof result.transactionId !== 'string'
      ) {
        throw new Error('The result could not be confirmed. Retry safely.')
      }

      setPending(null)
      setAmount('')
      setSuccess(`Transfer completed. Reference: ${result.transactionId}`)
      onTransferred()
    } catch (requestError) {
      // Keep the exact request and key so retrying cannot post it twice.
      setError(
        requestError instanceof Error
          ? `${requestError.message} Retry to check the same transfer safely.`
          : 'The result is uncertain. Retry the same transfer safely.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="transfer-panel" aria-labelledby="transfer-heading">
      <div className="section-heading">
        <h2 id="transfer-heading">Transfer between my accounts</h2>
        <span>USD transfers</span>
      </div>

      <form className="transfer-form" onSubmit={event => void submit(event)}>
        <label>
          From
          <select
            required
            value={sourceAccountId}
            disabled={formLocked}
            onChange={event => setSourceAccountId(event.target.value)}
          >
            <option value="">Choose a source account</option>
            {available.map(account => (
              <option key={account.id} value={account.id}>
                {account.accountName} · USD {account.balance}
              </option>
            ))}
          </select>
        </label>

        <label>
          To
          <select
            required
            value={destinationAccountId}
            disabled={formLocked}
            onChange={event => setDestinationAccountId(event.target.value)}
          >
            <option value="">Choose a destination account</option>
            {available.map(account => (
              <option
                key={account.id}
                value={account.id}
                disabled={account.id === sourceAccountId}
              >
                {account.accountName}
              </option>
            ))}
          </select>
        </label>

        <label>
          Amount (USD)
          <input
            required
            inputMode="decimal"
            autoComplete="off"
            placeholder="0.00"
            value={amount}
            disabled={formLocked}
            onChange={event => setAmount(event.target.value)}
          />
        </label>

        <label>
          Description
          <input
            required
            maxLength={255}
            value={description}
            disabled={formLocked}
            onChange={event => setDescription(event.target.value)}
          />
        </label>

        {error && <p className="notice error" role="alert">{error}</p>}
        {success && <p className="notice transfer-success" role="status">{success}</p>}

        <button
          type="submit"
          disabled={submitting || available.length < 2}
        >
          {submitting
            ? 'Submitting…'
            : pending
              ? 'Retry this exact transfer'
              : 'Transfer funds'}
        </button>

        {pending && (
          <p className="transfer-help">
            The result is not confirmed. Retry to check the same request;
            keep its details unchanged.
          </p>
        )}
      </form>
    </section>
  )
}
