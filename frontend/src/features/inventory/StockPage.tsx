import { Alert, Button, Group, Loader, Select, Table, Text, Title } from '@mantine/core'
import { useState } from 'react'
import { formatQuantity } from '../../lib/format'
import { useStock, useWarehouses } from './api'
import { StockAdjustmentModal } from './StockAdjustmentModal'

export function StockPage() {
  const [warehouseId, setWarehouseId] = useState<number | undefined>(undefined)
  const [adjusting, setAdjusting] = useState(false)
  const warehouses = useWarehouses()
  const stock = useStock(warehouseId)

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Stock</Title>
        <Group>
          <Select
            size="xs"
            placeholder="All warehouses"
            clearable
            data={(warehouses.data ?? []).map((w) => ({ value: String(w.id), label: w.code }))}
            value={warehouseId === undefined ? null : String(warehouseId)}
            onChange={(v) => setWarehouseId(v ? Number(v) : undefined)}
          />
          <Button onClick={() => setAdjusting(true)}>Adjust stock</Button>
        </Group>
      </Group>

      {stock.isPending && <Loader />}
      {stock.isError && <Alert color="red">Could not load stock: {stock.error.message}</Alert>}
      {stock.isSuccess && stock.data.length === 0 && (
        <Text c="dimmed">No stock yet. Stock is created by goods receipts and adjustments.</Text>
      )}
      {stock.isSuccess && stock.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Warehouse</Table.Th>
              <Table.Th>SKU</Table.Th>
              <Table.Th>Product</Table.Th>
              <Table.Th ta="right">On hand</Table.Th>
              <Table.Th>Last change</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {stock.data.map((s) => (
              <Table.Tr key={`${s.warehouse.id}-${s.product.id}`}>
                <Table.Td>{s.warehouse.code}</Table.Td>
                <Table.Td>{s.product.sku}</Table.Td>
                <Table.Td>{s.product.name}</Table.Td>
                <Table.Td ta="right">
                  {formatQuantity(s.quantityOnHand)} {s.product.unitOfMeasure}
                </Table.Td>
                <Table.Td>{new Date(s.updatedAt).toLocaleString()}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {adjusting && <StockAdjustmentModal opened onClose={() => setAdjusting(false)} />}
    </>
  )
}
