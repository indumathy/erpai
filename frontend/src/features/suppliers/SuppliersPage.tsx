import { Alert, Button, Group, Loader, Table, Text, Title } from '@mantine/core'
import { useState } from 'react'
import { showError } from '../../api/errors'
import { ActiveBadge, ActiveFilter } from '../../components/ActiveFilter'
import { type ActiveFilterValue, toActiveParam } from '../../components/activeFilterValue'
import { useSetSupplierActive, useSuppliers } from './api'
import { SupplierFormModal } from './SupplierFormModal'
import type { Supplier } from './types'

/** `undefined` = modal closed, `null` = creating, Supplier = editing that supplier. */
type Editing = Supplier | null | undefined

export function SuppliersPage() {
  const [filter, setFilter] = useState<ActiveFilterValue>('active')
  const [editing, setEditing] = useState<Editing>(undefined)
  const suppliers = useSuppliers(toActiveParam(filter))
  const setActive = useSetSupplierActive()

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={2}>Suppliers</Title>
        <Group>
          <ActiveFilter value={filter} onChange={setFilter} />
          <Button onClick={() => setEditing(null)}>New supplier</Button>
        </Group>
      </Group>

      {suppliers.isPending && <Loader />}
      {suppliers.isError && <Alert color="red">Could not load suppliers: {suppliers.error.message}</Alert>}
      {suppliers.isSuccess && suppliers.data.length === 0 && <Text c="dimmed">No suppliers found.</Text>}
      {suppliers.isSuccess && suppliers.data.length > 0 && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Number</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>VAT ID</Table.Th>
              <Table.Th>Currency</Table.Th>
              <Table.Th>Payment terms</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {suppliers.data.map((s) => (
              <Table.Tr key={s.id}>
                <Table.Td>{s.supplierNumber}</Table.Td>
                <Table.Td>{s.name}</Table.Td>
                <Table.Td>{s.vatId ?? '-'}</Table.Td>
                <Table.Td>{s.currency}</Table.Td>
                <Table.Td>net {s.paymentTermsDays} days</Table.Td>
                <Table.Td>
                  <ActiveBadge active={s.active} />
                </Table.Td>
                <Table.Td>
                  <Group gap="xs" justify="flex-end" wrap="nowrap">
                    <Button size="xs" variant="subtle" onClick={() => setEditing(s)}>
                      Edit
                    </Button>
                    <Button
                      size="xs"
                      variant="subtle"
                      color={s.active ? 'red' : 'green'}
                      onClick={() => setActive.mutate({ id: s.id, active: !s.active }, { onError: showError })}
                    >
                      {s.active ? 'Deactivate' : 'Activate'}
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {editing !== undefined && (
        // key: remount per supplier so the form starts with that supplier's values
        <SupplierFormModal
          key={editing?.id ?? 'new'}
          supplier={editing ?? undefined}
          opened
          onClose={() => setEditing(undefined)}
        />
      )}
    </>
  )
}
