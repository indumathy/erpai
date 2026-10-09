import { Alert, Anchor, Group, Loader, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { formatDate, formatMoney, formatPercent, formatQuantity, formatUnitPrice } from '../../lib/format'
import { useInvoice, useInvoiceMatch } from './api'
import { MatchBadge } from './MatchBadge'
import type { MatchResult } from './types'

export function InvoiceDetailPage() {
  const id = Number(useParams().id)
  const invoice = useInvoice(id)
  const match = useInvoiceMatch(id)

  if (invoice.isPending) return <Loader />
  if (invoice.isError) return <Alert color="red">Could not load invoice: {invoice.error.message}</Alert>
  const inv = invoice.data

  return (
    <Stack>
      <Anchor component={Link} to="/invoices" size="sm">
        ← Supplier invoices
      </Anchor>
      <Group>
        <Title order={2}>{inv.invoiceNumber}</Title>
        {match.isSuccess && <MatchBadge exceptionCount={match.data.exceptions.length} />}
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 4 }}>
        <Info label="Supplier">
          {inv.supplier.supplierNumber} · {inv.supplier.name}
        </Info>
        <Info label="Purchase order">
          {inv.purchaseOrder ? (
            <Anchor component={Link} to={`/purchase-orders/${inv.purchaseOrder.id}`}>
              {inv.purchaseOrder.poNumber}
            </Anchor>
          ) : (
            '-'
          )}
        </Info>
        <Info label="Invoice date">{formatDate(inv.invoiceDate)}</Info>
        <Info label="Total">{formatMoney(inv.totals.total, inv.currency)}</Info>
      </SimpleGrid>

      <Title order={4}>Three-way match</Title>
      {match.isPending && <Loader size="sm" />}
      {match.isError && <Alert color="red">Could not run the match: {match.error.message}</Alert>}
      {match.isSuccess && <MatchExceptions result={match.data} />}

      <Title order={4}>Lines</Title>
      <Table striped>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Line</Table.Th>
            <Table.Th>PO line</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th ta="right">Quantity</Table.Th>
            <Table.Th ta="right">Unit price</Table.Th>
            <Table.Th ta="right">Tax</Table.Th>
            <Table.Th ta="right">Net</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {inv.items.map((item) => (
            <Table.Tr key={item.id}>
              <Table.Td>{item.lineNumber}</Table.Td>
              <Table.Td>{item.purchaseOrderLineNumber ?? '-'}</Table.Td>
              <Table.Td>{item.description}</Table.Td>
              <Table.Td ta="right">{formatQuantity(item.quantity)}</Table.Td>
              <Table.Td ta="right">{formatUnitPrice(item.unitPrice, inv.currency)}</Table.Td>
              <Table.Td ta="right">{formatPercent(item.taxRate)}</Table.Td>
              <Table.Td ta="right">{formatMoney(item.netAmount, inv.currency)}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  )
}

function MatchExceptions({ result }: { result: MatchResult }) {
  if (result.matched) {
    return <Alert color="green">Invoice, purchase order and goods receipts agree.</Alert>
  }
  return (
    <Table>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Rule</Table.Th>
          <Table.Th>Line</Table.Th>
          <Table.Th>Expected</Table.Th>
          <Table.Th>Actual</Table.Th>
          <Table.Th>Details</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {result.exceptions.map((ex, index) => (
          <Table.Tr key={index}>
            <Table.Td>
              <Text fw={600} c="red" size="sm">
                {ex.code}
              </Text>
            </Table.Td>
            <Table.Td>{ex.lineNumber ?? 'header'}</Table.Td>
            <Table.Td>{ex.expected ?? '-'}</Table.Td>
            <Table.Td>{ex.actual ?? '-'}</Table.Td>
            <Table.Td>{ex.message}</Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Text>{children}</Text>
    </div>
  )
}
