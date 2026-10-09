import { Alert, Anchor, Button, Group, Loader, Select, Table, Text, Title } from '@mantine/core'
import { useState } from 'react'
import { Link } from 'react-router'
import { formatDate, formatMoney } from '../../lib/format'
import { usePurchaseOrders } from './api'
import { PurchaseOrderStatusBadge } from './PurchaseOrderStatusBadge'
import { PURCHASE_ORDER_STATUSES, type PurchaseOrderStatus } from './types'

export function PurchaseOrdersPage() {
  const [status, setStatus] = useState<PurchaseOrderStatus | undefined>(undefined)
  const orders = usePurchaseOrders(status)

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Purchase orders</Title>
        <Group>
          <Select
            size="xs"
            placeholder="All statuses"
            clearable
            data={PURCHASE_ORDER_STATUSES.map((s) => ({ value: s, label: s.replace('_', ' ') }))}
            value={status ?? null}
            onChange={(v) => setStatus((v as PurchaseOrderStatus | null) ?? undefined)}
          />
          <Button component={Link} to="/purchase-orders/new">
            New purchase order
          </Button>
        </Group>
      </Group>

      {orders.isPending && <Loader />}
      {orders.isError && <Alert color="red">Could not load purchase orders: {orders.error.message}</Alert>}
      {orders.isSuccess && orders.data.length === 0 && <Text c="dimmed">No purchase orders found.</Text>}
      {orders.isSuccess && orders.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>PO number</Table.Th>
              <Table.Th>Supplier</Table.Th>
              <Table.Th>Order date</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th ta="right">Items</Table.Th>
              <Table.Th ta="right">Total (gross)</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {orders.data.map((po) => (
              <Table.Tr key={po.id}>
                <Table.Td>
                  <Anchor component={Link} to={`/purchase-orders/${po.id}`}>
                    {po.poNumber}
                  </Anchor>
                </Table.Td>
                <Table.Td>
                  {po.supplier.supplierNumber} · {po.supplier.name}
                </Table.Td>
                <Table.Td>{formatDate(po.orderDate)}</Table.Td>
                <Table.Td>
                  <PurchaseOrderStatusBadge status={po.status} />
                </Table.Td>
                <Table.Td ta="right">{po.itemCount}</Table.Td>
                <Table.Td ta="right">{formatMoney(po.total, po.currency)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </>
  )
}
