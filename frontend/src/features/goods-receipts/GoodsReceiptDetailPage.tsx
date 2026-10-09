import { Alert, Anchor, Group, Loader, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { formatDate, formatQuantity } from '../../lib/format'
import { useGoodsReceipt } from './api'

export function GoodsReceiptDetailPage() {
  const id = Number(useParams().id)
  const receipt = useGoodsReceipt(id)

  if (receipt.isPending) return <Loader />
  if (receipt.isError) return <Alert color="red">Could not load goods receipt: {receipt.error.message}</Alert>
  const gr = receipt.data

  return (
    <Stack>
      <Anchor component={Link} to="/goods-receipts" size="sm">
        ← Goods receipts
      </Anchor>
      <Group>
        <Title order={2}>{gr.grNumber}</Title>
        <Text c="dimmed">posted {new Date(gr.createdAt).toLocaleString()}</Text>
      </Group>

      <SimpleGrid cols={{ base: 1, sm: 4 }}>
        <Info label="Purchase order">
          <Anchor component={Link} to={`/purchase-orders/${gr.purchaseOrder.id}`}>
            {gr.purchaseOrder.poNumber}
          </Anchor>
        </Info>
        <Info label="Supplier">
          {gr.supplier.supplierNumber} · {gr.supplier.name}
        </Info>
        <Info label="Receipt date / warehouse">
          {formatDate(gr.receiptDate)} · {gr.warehouse.code}
        </Info>
        <Info label="Delivery note">{gr.deliveryNoteNumber ?? '-'}</Info>
      </SimpleGrid>

      <Table striped>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>PO line</Table.Th>
            <Table.Th>SKU</Table.Th>
            <Table.Th>Description</Table.Th>
            <Table.Th ta="right">Quantity received</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {gr.items.map((item) => (
            <Table.Tr key={item.id}>
              <Table.Td>{item.lineNumber}</Table.Td>
              <Table.Td>{item.sku}</Table.Td>
              <Table.Td>{item.description}</Table.Td>
              <Table.Td ta="right">
                {formatQuantity(item.quantityReceived)} {item.unitOfMeasure}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      <Text size="sm" c="dimmed">
        Posted goods receipts are immutable. Their stock movements are listed under Inventory → Stock movements.
      </Text>
    </Stack>
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
