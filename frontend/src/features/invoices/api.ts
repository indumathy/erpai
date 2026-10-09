import { useQuery } from '@tanstack/react-query'
import { http } from '../../api/http'
import type { Invoice, InvoiceSummary, MatchResult } from './types'

export const invoiceKeys = {
  all: ['invoices'] as const,
  list: () => ['invoices', 'list'] as const,
  detail: (id: number) => ['invoices', 'detail', id] as const,
  match: (id: number) => ['invoices', 'match', id] as const,
}

export function useInvoices() {
  return useQuery({ queryKey: invoiceKeys.list(), queryFn: () => http.get<InvoiceSummary[]>('/invoices') })
}

export function useInvoice(id: number) {
  return useQuery({ queryKey: invoiceKeys.detail(id), queryFn: () => http.get<Invoice>(`/invoices/${id}`) })
}

/** Computed on the server on every request; nothing is stored. */
export function useInvoiceMatch(id: number) {
  return useQuery({ queryKey: invoiceKeys.match(id), queryFn: () => http.get<MatchResult>(`/invoices/${id}/match`) })
}
