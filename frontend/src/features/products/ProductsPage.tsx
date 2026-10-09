import { Alert, Button, Group, Loader, Table, Text, Title } from '@mantine/core'
import { useState } from 'react'
import { showError } from '../../api/errors'
import { ActiveBadge, ActiveFilter } from '../../components/ActiveFilter'
import { type ActiveFilterValue, toActiveParam } from '../../components/activeFilterValue'
import { useProducts, useSetProductActive } from './api'
import { ProductFormModal } from './ProductFormModal'
import type { Product } from './types'

/** `undefined` = modal closed, `null` = creating, Product = editing that product. */
type Editing = Product | null | undefined

export function ProductsPage() {
  const [filter, setFilter] = useState<ActiveFilterValue>('active')
  const [editing, setEditing] = useState<Editing>(undefined)
  const products = useProducts(toActiveParam(filter))
  const setActive = useSetProductActive()

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Products</Title>
        <Group>
          <ActiveFilter value={filter} onChange={setFilter} />
          <Button onClick={() => setEditing(null)}>New product</Button>
        </Group>
      </Group>

      {products.isPending && <Loader />}
      {products.isError && <Alert color="red">Could not load products: {products.error.message}</Alert>}
      {products.isSuccess && products.data.length === 0 && <Text c="dimmed">No products found.</Text>}
      {products.isSuccess && products.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>SKU</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Description</Table.Th>
              <Table.Th>Unit</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {products.data.map((p) => (
              <Table.Tr key={p.id}>
                <Table.Td>{p.sku}</Table.Td>
                <Table.Td>{p.name}</Table.Td>
                <Table.Td>
                  <Text size="sm" lineClamp={1}>
                    {p.description ?? '-'}
                  </Text>
                </Table.Td>
                <Table.Td>{p.unitOfMeasure}</Table.Td>
                <Table.Td>
                  <ActiveBadge active={p.active} />
                </Table.Td>
                <Table.Td>
                  <Group gap="xs" justify="flex-end" wrap="nowrap">
                    <Button size="xs" variant="subtle" onClick={() => setEditing(p)}>
                      Edit
                    </Button>
                    <Button
                      size="xs"
                      variant="subtle"
                      color={p.active ? 'red' : 'green'}
                      onClick={() => setActive.mutate({ id: p.id, active: !p.active }, { onError: showError })}
                    >
                      {p.active ? 'Deactivate' : 'Activate'}
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {editing !== undefined && (
        <ProductFormModal
          key={editing?.id ?? 'new'}
          product={editing ?? undefined}
          opened
          onClose={() => setEditing(undefined)}
        />
      )}
    </>
  )
}
