import { Alert, Anchor, Button, Group, Loader, Table, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import { formatDate } from '../../lib/format'
import { useGoodsReceipts } from './api'
import type { GoodsReceiptSummary } from './types'

export function GoodsReceiptsPage() {
  const receipts = useGoodsReceipts(undefined)

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Goods receipts</Title>
        <Button component={Link} to="/goods-receipts/new">
          Receive goods
        </Button>
      </Group>

      {receipts.isPending && <Loader />}
      {receipts.isError && <Alert color="red">Could not load goods receipts: {receipts.error.message}</Alert>}
      {receipts.isSuccess && receipts.data.length === 0 && <Text c="dimmed">No goods received yet.</Text>}
      {receipts.isSuccess && receipts.data.length > 0 && <GoodsReceiptTable receipts={receipts.data} />}
    </>
  )
}

/** Also used on the purchase order detail page. */
export function GoodsReceiptTable({ receipts }: { receipts: GoodsReceiptSummary[] }) {
  return (
    <Table striped highlightOnHover>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>GR number</Table.Th>
          <Table.Th>Purchase order</Table.Th>
          <Table.Th>Supplier</Table.Th>
          <Table.Th>Receipt date</Table.Th>
          <Table.Th>Warehouse</Table.Th>
          <Table.Th>Delivery note</Table.Th>
          <Table.Th ta="right">Lines</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {receipts.map((gr) => (
          <Table.Tr key={gr.id}>
            <Table.Td>
              <Anchor component={Link} to={`/goods-receipts/${gr.id}`}>
                {gr.grNumber}
              </Anchor>
            </Table.Td>
            <Table.Td>
              <Anchor component={Link} to={`/purchase-orders/${gr.purchaseOrder.id}`}>
                {gr.purchaseOrder.poNumber}
              </Anchor>
            </Table.Td>
            <Table.Td>{gr.supplier.name}</Table.Td>
            <Table.Td>{formatDate(gr.receiptDate)}</Table.Td>
            <Table.Td>{gr.warehouse.code}</Table.Td>
            <Table.Td>{gr.deliveryNoteNumber ?? '-'}</Table.Td>
            <Table.Td ta="right">{gr.itemCount}</Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}
