/**
 * Thin fetch wrapper for the ERP backend.
 *
 * The backend reports errors as RFC 9457 problem details:
 *   { "status": 400, "title": "Validation failed", "detail": "...", "errors": [{ "field", "message" }] }
 * We turn those into ApiError so forms can show field errors next to the right input.
 */

export interface FieldError {
  field: string
  message: string
}

interface ProblemDetail {
  status?: number
  title?: string
  detail?: string
  errors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: FieldError[]

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = problem.errors ?? []
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {
    const problem: ProblemDetail = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const http = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
}
