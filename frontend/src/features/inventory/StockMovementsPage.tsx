import { Alert, Anchor, Badge, Group, Loader, Select, Table, Text, Title } from '@mantine/core'
import { useState } from 'react'
import { Link } from 'react-router'
import { formatQuantity } from '../../lib/format'
import { useProducts } from '../products/api'
import { useStockMovements } from './api'
import type { StockMovement } from './types'

/** The stock ledger: every change to stock, newest first (latest 200). */
export function StockMovementsPage() {
  const [productId, setProductId] = useState<number | undefined>(undefined)
  const products = useProducts(undefined)
  const movements = useStockMovements(productId)

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Stock movements</Title>
        <Select
          size="xs"
          w={260}
          placeholder="All products"
          searchable
          clearable
          data={(products.data ?? []).map((p) => ({ value: String(p.id), label: `${p.sku} · ${p.name}` }))}
          value={productId === undefined ? null : String(productId)}
          onChange={(v) => setProductId(v ? Number(v) : undefined)}
        />
      </Group>

      {movements.isPending && <Loader />}
      {movements.isError && <Alert color="red">Could not load movements: {movements.error.message}</Alert>}
      {movements.isSuccess && movements.data.length === 0 && <Text c="dimmed">No stock movements yet.</Text>}
      {movements.isSuccess && movements.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Time</Table.Th>
              <Table.Th>Warehouse</Table.Th>
              <Table.Th>SKU</Table.Th>
              <Table.Th ta="right">Quantity</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Reference</Table.Th>
              <Table.Th>Note</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {movements.data.map((m) => (
              <Table.Tr key={m.id}>
                <Table.Td>{new Date(m.createdAt).toLocaleString()}</Table.Td>
                <Table.Td>{m.warehouse.code}</Table.Td>
                <Table.Td>{m.product.sku}</Table.Td>
                <Table.Td ta="right" c={m.quantity < 0 ? 'red' : 'teal'}>
                  {m.quantity > 0 ? '+' : ''}
                  {formatQuantity(m.quantity)} {m.product.unitOfMeasure}
                </Table.Td>
                <Table.Td>
                  <Badge variant="light" color={m.movementType === 'GOODS_RECEIPT' ? 'blue' : 'grape'}>
                    {m.movementType.replace('_', ' ')}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Reference movement={m} />
                </Table.Td>
                <Table.Td>{m.note ?? ''}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </>
  )
}

function Reference({ movement }: { movement: StockMovement }) {
  if (movement.referenceType === 'GOODS_RECEIPT' && movement.referenceId !== null) {
    return (
      <Anchor component={Link} to={`/goods-receipts/${movement.referenceId}`}>
        {movement.referenceNumber}
      </Anchor>
    )
  }
  return <>{movement.referenceNumber ?? ''}</>
}
