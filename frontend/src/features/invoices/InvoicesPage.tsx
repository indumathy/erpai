import { Alert, Anchor, Loader, Table, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import { formatDate, formatMoney } from '../../lib/format'
import { useInvoices } from './api'
import { MatchBadge } from './MatchBadge'

export function InvoicesPage() {
  const invoices = useInvoices()

  return (
    <>
      <Title order={2} mb="md">
        Supplier invoices
      </Title>

      {invoices.isPending && <Loader />}
      {invoices.isError && <Alert color="red">Could not load invoices: {invoices.error.message}</Alert>}
      {invoices.isSuccess && invoices.data.length === 0 && (
        <Text c="dimmed">No invoices yet. Run scripts/seed-demo-data.mjs to add demo invoices.</Text>
      )}
      {invoices.isSuccess && invoices.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Invoice number</Table.Th>
              <Table.Th>Supplier</Table.Th>
              <Table.Th>Purchase order</Table.Th>
              <Table.Th>Date</Table.Th>
              <Table.Th ta="right">Total</Table.Th>
              <Table.Th>Match</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {invoices.data.map((inv) => (
              <Table.Tr key={inv.id}>
                <Table.Td>
                  <Anchor component={Link} to={`/invoices/${inv.id}`}>
                    {inv.invoiceNumber}
                  </Anchor>
                </Table.Td>
                <Table.Td>{inv.supplier.name}</Table.Td>
                <Table.Td>
                  {inv.purchaseOrder ? (
                    <Anchor component={Link} to={`/purchase-orders/${inv.purchaseOrder.id}`}>
                      {inv.purchaseOrder.poNumber}
                    </Anchor>
                  ) : (
                    '-'
                  )}
                </Table.Td>
                <Table.Td>{formatDate(inv.invoiceDate)}</Table.Td>
                <Table.Td ta="right">{formatMoney(inv.total, inv.currency)}</Table.Td>
                <Table.Td>
                  <MatchBadge exceptionCount={inv.exceptionCount} />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </>
  )
}
