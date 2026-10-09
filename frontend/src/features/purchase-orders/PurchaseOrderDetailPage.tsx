import { Alert, Anchor, Button, Divider, Group, Loader, Paper, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { Link, useParams } from 'react-router'
import { showError } from '../../api/errors'
import { ConfirmButton } from '../../components/ConfirmButton'
import { formatDate, formatMoney, formatPercent, formatQuantity, formatUnitPrice } from '../../lib/format'
import { useGoodsReceipts } from '../goods-receipts/api'
import { GoodsReceiptTable } from '../goods-receipts/GoodsReceiptsPage'
import { usePurchaseOrder, usePurchaseOrderAction } from './api'
import { PurchaseOrderStatusBadge } from './PurchaseOrderStatusBadge'
import type { PurchaseOrder } from './types'

export function PurchaseOrderDetailPage() {
  const id = Number(useParams().id)
  const order = usePurchaseOrder(id)

  if (order.isPending) return <Loader />
  if (order.isError) return <Alert color="red">Could not load purchase order: {order.error.message}</Alert>
  return <PurchaseOrderDetail po={order.data} />
}

function PurchaseOrderDetail({ po }: { po: PurchaseOrder }) {
  const action = usePurchaseOrderAction()
  const run = (kind: 'approve' | 'cancel' | 'close') =>
    action.mutate(
      { id: po.id, action: kind },
      {
        onSuccess: (updated) =>
          notifications.show({ color: 'green', message: `${updated.poNumber} is now ${updated.status}` }),
        onError: showError,
      },
    )

  const isDraft = po.status === 'DRAFT'
  const canCancel = po.status === 'DRAFT' || po.status === 'APPROVED'
  const canReceive = po.status === 'APPROVED' || po.status === 'PARTIALLY_RECEIVED'
  const canClose = po.status === 'PARTIALLY_RECEIVED' || po.status === 'RECEIVED'

  return (
    <Stack>
      <Anchor component={Link} to="/purchase-orders" size="sm">
        ← Purchase orders
      </Anchor>

      <Group justify="space-between">
        <Group>
          <Title order={2}>{po.poNumber}</Title>
          <PurchaseOrderStatusBadge status={po.status} />
        </Group>
        <Group>
          {isDraft && (
            <Button variant="default" component={Link} to={`/purchase-orders/${po.id}/edit`}>
              Edit
            </Button>
          )}
          {canCancel && (
            <ConfirmButton
              variant="light"
              color="red"
              loading={action.isPending}
              title="Cancel purchase order"
              message={`Cancel ${po.poNumber}? This cannot be undone.`}
              confirmLabel="Cancel order"
              onConfirm={() => run('cancel')}
            >
              Cancel order
            </ConfirmButton>
          )}
          {canClose && (
            <ConfirmButton
              variant="light"
              loading={action.isPending}
              title="Close purchase order"
              message={`Close ${po.poNumber}? No further goods can be received; open quantities are written off.`}
              confirmLabel="Close order"
              onConfirm={() => run('close')}
            >
              Close order
            </ConfirmButton>
          )}
          {canReceive && (
            <Button component={Link} to={`/goods-receipts/new?purchaseOrderId=${po.id}`}>
              Receive goods
            </Button>
          )}
          {isDraft && (
            <ConfirmButton
              loading={action.isPending}
              title="Approve purchase order"
              message={`Approve ${po.poNumber} for ${formatMoney(po.totals.total, po.currency)}? Its lines can no longer be changed afterwards.`}
              confirmLabel="Approve"
              onConfirm={() => run('approve')}
            >
              Approve
            </ConfirmButton>
          )}
        </Group>
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        <Info label="Supplier" value={`${po.supplier.supplierNumber} · ${po.supplier.name}`} />
        <Info label="Order date" value={formatDate(po.orderDate)} />
        <Info label="Currency" value={po.currency} />
      </SimpleGrid>

      <Table striped>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>#</Table.Th>
            <Table.Th>SKU</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th ta="right">Quantity</Table.Th>
            <Table.Th ta="right">Received</Table.Th>
            <Table.Th ta="right">Open</Table.Th>
            <Table.Th ta="right">Unit price</Table.Th>
            <Table.Th ta="right">Tax</Table.Th>
            <Table.Th ta="right">Net amount</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {po.items.map((item) => (
            <Table.Tr key={item.id}>
              <Table.Td>{item.lineNumber}</Table.Td>
              <Table.Td>{item.sku}</Table.Td>
              <Table.Td>{item.description}</Table.Td>
              <Table.Td ta="right">
                {formatQuantity(item.quantity)} {item.unitOfMeasure}
              </Table.Td>
              <Table.Td ta="right">{formatQuantity(item.receivedQuantity)}</Table.Td>
              <Table.Td ta="right" c={item.openQuantity > 0 && po.status !== 'DRAFT' ? 'orange' : undefined}>
                {formatQuantity(item.openQuantity)}
              </Table.Td>
              <Table.Td ta="right">{formatUnitPrice(item.unitPrice, po.currency)}</Table.Td>
              <Table.Td ta="right">{formatPercent(item.taxRate)}</Table.Td>
              <Table.Td ta="right">{formatMoney(item.netAmount, po.currency)}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <Group justify="flex-end">
        <Paper withBorder p="md" miw={320}>
          <TotalRow label="Subtotal (net)" value={formatMoney(po.totals.subtotal, po.currency)} />
          {po.totals.taxBreakdown.map((t) => (
            <TotalRow
              key={t.taxRate}
              label={`Tax ${formatPercent(t.taxRate)} on ${formatMoney(t.netAmount, po.currency)}`}
              value={formatMoney(t.taxAmount, po.currency)}
            />
          ))}
          <Divider my="xs" />
          <TotalRow label="Total (gross)" value={formatMoney(po.totals.total, po.currency)} bold />
        </Paper>
      </Group>

      {po.status !== 'DRAFT' && <PurchaseOrderReceipts purchaseOrderId={po.id} />}
    </Stack>
  )
}

function PurchaseOrderReceipts({ purchaseOrderId }: { purchaseOrderId: number }) {
  const receipts = useGoodsReceipts(purchaseOrderId)
  return (
    <Stack gap="xs">
      <Title order={4}>Goods receipts</Title>
      {receipts.isPending && <Loader size="sm" />}
      {receipts.isError && <Alert color="red">{receipts.error.message}</Alert>}
      {receipts.isSuccess && receipts.data.length === 0 && <Text c="dimmed">Nothing received yet.</Text>}
      {receipts.isSuccess && receipts.data.length > 0 && <GoodsReceiptTable receipts={receipts.data} />}
    </Stack>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Text>{value}</Text>
    </div>
  )
}

function TotalRow({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Group justify="space-between" gap="xl">
      <Text size="sm" fw={bold ? 700 : undefined}>
        {label}
      </Text>
      <Text size="sm" fw={bold ? 700 : undefined}>
        {value}
      </Text>
    </Group>
  )
}
